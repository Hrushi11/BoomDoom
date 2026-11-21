"""Lesson stock, sharing and orchestration (Tech Spec §8.5–8.6).

Rules
- Each (user, topic) keeps `target_stock` unwatched lessons; refill when below `refill_below`.
- Preset topics without a goal use shared syllabi and shared lessons (`share_key`); before generating,
  existing shared lessons the user hasn't received are allocated. Only the shortfall is generated.
- Custom topics or topics with a goal get personal syllabi and personal lessons.

Queue messages are sent only after the DB transaction commits (workers must see the job row).
"""

from __future__ import annotations

import hashlib
import logging
import uuid
from dataclasses import dataclass, field

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app import queues
from app.config import get_settings
from app.db import advisory_lock
from app.domain import lifecycle
from app.domain.lifecycle import LessonStatus
from app.domain.sharing import is_shareable, next_format, share_key, shortfall
from app.errors import ApiError
from app.models import AppUser, Job, Lesson, LessonProfile, LessonReport, Syllabus, SyllabusProgress, Topic, UserLesson

log = logging.getLogger(__name__)

VISUAL_STYLES = ["B1", "B2", "B3"]
ACCENT_COLORS = ["#2F6F5E", "#2EC4B6", "#FF6B3D", "#5B6CFF", "#C2410C", "#7C3AED"]
_BLOCKED_TERMS = {"porn", "nsfw", "bomb making", "self harm", "suicide method"}
INACTIVE = (LessonStatus.FAILED_FINAL.value, LessonStatus.HIDDEN.value)


@dataclass
class Plan:
    """Queue messages to send once the caller's transaction has committed."""

    messages: list[tuple[str, uuid.UUID]] = field(default_factory=list)

    def add(self, queue: str, job_id: uuid.UUID) -> None:
        self.messages.append((queue, job_id))

    def extend(self, other: Plan) -> None:
        self.messages.extend(other.messages)

    @property
    def job_ids(self) -> list[uuid.UUID]:
        return [j for _, j in self.messages]

    def send(self) -> None:
        for queue, job_id in self.messages:
            queues.enqueue(queue, job_id)


def commit_and_send(db: Session, plan: Plan) -> list[uuid.UUID]:
    db.commit()
    plan.send()
    return plan.job_ids


# ---------------------------------------------------------------- helpers


def check_topic_safety(name: str, goal: str | None) -> None:
    """Fast synchronous screen; the script worker runs the model-based check before generating anything."""
    text = f"{name} {goal or ''}".lower()
    if any(term in text for term in _BLOCKED_TERMS):
        raise ApiError(422, "topic_not_allowed", "We can't make lessons on this topic")


def topic_look(topic_norm: str) -> tuple[str, str]:
    """Fixed visual style + accent colour per topic so every lesson in a topic looks consistent."""
    h = int(hashlib.sha256(topic_norm.encode()).hexdigest(), 16)
    return VISUAL_STYLES[h % len(VISUAL_STYLES)], ACCENT_COLORS[(h // 7) % len(ACCENT_COLORS)]


def profile_for(db: Session, user_id: uuid.UUID) -> LessonProfile:
    p = db.get(LessonProfile, user_id)
    if p is None:
        p = LessonProfile(user_id=user_id, language="en", voice_id="af_heart", length_s=45, version=1)
        db.add(p)
        db.flush()
    return p


def _shared(topic: Topic) -> bool:
    return is_shareable(topic.type, topic.goal)


def _goal_hash(goal: str | None) -> str | None:
    return hashlib.sha256(goal.strip().lower().encode()).hexdigest()[:16] if goal and goal.strip() else None


# ---------------------------------------------------------------- syllabus


def ensure_syllabus(db: Session, topic: Topic, profile: LessonProfile, plan: Plan) -> Syllabus:
    if topic.syllabus_id:
        syl = db.get(Syllabus, topic.syllabus_id)
        if syl is not None and syl.status != "failed":
            return syl
    if _shared(topic):
        advisory_lock(db, f"syllabus:{topic.name_norm}|{topic.level}|{profile.language}")
        syl = db.scalar(
            select(Syllabus).where(
                Syllabus.owner_user_id.is_(None),
                Syllabus.topic_norm == topic.name_norm,
                Syllabus.level == topic.level,
                Syllabus.language == profile.language,
            )
        )
        if syl is None:
            syl = Syllabus(
                topic_norm=topic.name_norm,
                level=topic.level,
                language=profile.language,
                concepts=[],
                model=get_settings().openai_model_syllabus,
                status="building",
            )
            db.add(syl)
            db.flush()
            _syllabus_job(db, topic, syl, plan)
        elif syl.status == "failed":
            syl.status = "building"
            _syllabus_job(db, topic, syl, plan)
    else:
        syl = Syllabus(
            topic_norm=topic.name_norm,
            level=topic.level,
            language=profile.language,
            goal_hash=_goal_hash(topic.goal),
            owner_user_id=topic.user_id,
            concepts=[],
            model=get_settings().openai_model_syllabus,
            status="building",
        )
        db.add(syl)
        db.flush()
        _syllabus_job(db, topic, syl, plan)
    topic.syllabus_id = syl.id
    return syl


def _syllabus_job(db: Session, topic: Topic, syl: Syllabus, plan: Plan) -> None:
    job = Job(user_id=topic.user_id, topic_id=topic.id, stage="syllabus", status="queued")
    db.add(job)
    db.flush()
    plan.add("script-jobs", job.id)


# ---------------------------------------------------------------- stock


def active_stock(db: Session, user_id: uuid.UUID, topic_id: uuid.UUID) -> int:
    """Assigned, not finished/skipped, not dead lessons (ready or still being made)."""
    return (
        db.scalar(
            select(func.count())
            .select_from(UserLesson)
            .join(Lesson, Lesson.id == UserLesson.lesson_id)
            .where(
                UserLesson.user_id == user_id,
                UserLesson.topic_id == topic_id,
                UserLesson.completed_at.is_(None),
                UserLesson.skipped.is_(False),
                Lesson.status.not_in(INACTIVE),
                Lesson.hidden.is_(False),
            )
        )
        or 0
    )


def allocate(db: Session, user_id: uuid.UUID, topic: Topic, count: int) -> Plan:
    """Give the user `count` more lessons for `topic`: reuse shared ones first, generate the rest."""
    plan = Plan()
    if count <= 0:
        return plan
    profile = profile_for(db, user_id)
    syl = ensure_syllabus(db, topic, profile, plan)
    if syl.status != "ready":
        return plan  # lessons are allocated when the syllabus job finishes
    concepts = syl.concepts or []
    style, accent = topic_look(topic.name_norm)
    new_indices: list[int]
    key: str | None = None

    if _shared(topic):
        key = share_key(topic.name_norm, topic.level, profile.language, profile.voice_id, style)
        advisory_lock(db, f"share:{key}")
        mine = select(UserLesson.lesson_id).where(UserLesson.user_id == user_id)
        reuse = db.scalars(
            select(Lesson)
            .where(
                Lesson.share_key == key,
                Lesson.owner_user_id.is_(None),
                Lesson.status.not_in(INACTIVE),
                Lesson.hidden.is_(False),
                Lesson.id.not_in(mine),
            )
            .order_by(Lesson.concept_index)
            .limit(count)
        ).all()
        for lesson in reuse:
            db.add(UserLesson(user_id=user_id, lesson_id=lesson.id, topic_id=topic.id, interaction_results=[]))
        count -= len(reuse)
        top = db.scalar(
            select(func.max(Lesson.concept_index)).where(Lesson.share_key == key, Lesson.owner_user_id.is_(None))
        )
        start = 0 if top is None else top + 1
        new_indices = [i for i in range(start, start + count) if i < len(concepts)]
        owner = None
    else:
        progress = db.get(SyllabusProgress, (user_id, syl.id))
        if progress is None:
            progress = SyllabusProgress(user_id=user_id, syllabus_id=syl.id, next_index=0, weak_concepts=[])
            db.add(progress)
        new_indices = [i for i in range(progress.next_index, progress.next_index + count) if i < len(concepts)]
        progress.next_index += len(new_indices)
        owner = user_id

    if len(new_indices) < count:
        log.info("syllabus exhausted", extra={"status": "exhausted"})
    for idx in new_indices:
        lesson = Lesson(
            syllabus_id=syl.id,
            owner_user_id=owner,
            concept=concepts[idx]["title"],
            concept_index=idx,
            format=next_format(idx),
            level=topic.level,
            language=profile.language,
            voice_id=profile.voice_id,
            visual_style=style,
            accent_color=accent,
            share_key=key,
            status=LessonStatus.QUEUED.value,
        )
        db.add(lesson)
        db.flush()
        db.add(UserLesson(user_id=user_id, lesson_id=lesson.id, topic_id=topic.id, interaction_results=[]))
        job = Job(user_id=user_id, topic_id=topic.id, lesson_id=lesson.id, stage="script", status="queued")
        db.add(job)
        db.flush()
        plan.add("script-jobs", job.id)
    return plan


def ensure_stock(db: Session, user_id: uuid.UUID, topic_id: uuid.UUID) -> Plan:
    """Top up a (user, topic) to the target stock. Caller commits, then sends the plan."""
    s = get_settings()
    topic = db.get(Topic, topic_id)
    if topic is None or topic.user_id != user_id or topic.status == "failed":
        return Plan()
    user = db.get(AppUser, user_id)
    if user is None or user.deleted_at is not None:
        return Plan()
    advisory_lock(db, f"topic:{topic.id}")
    need = shortfall(active_stock(db, user_id, topic.id), target=s.target_stock, refill_below=s.refill_below)
    if need == 0 and topic.syllabus_id is None:
        need = s.target_stock  # first call: make sure the syllabus gets started
    return allocate(db, user_id, topic, need)


# ---------------------------------------------------------------- status & events


def refresh_topic_status(db: Session, topic: Topic) -> tuple[str, str]:
    """Recompute topic status from its allocated lessons. Returns (old, new)."""
    old = topic.status
    statuses = [
        LessonStatus(s)
        for s in db.scalars(
            select(Lesson.status)
            .join(UserLesson, UserLesson.lesson_id == Lesson.id)
            .where(UserLesson.topic_id == topic.id)
            .order_by(UserLesson.assigned_at, Lesson.concept_index)
        )
    ]
    syl = db.get(Syllabus, topic.syllabus_id) if topic.syllabus_id else None
    new = "failed" if syl is not None and syl.status == "failed" else lifecycle.topic_status(statuses)
    if old == "ready" and new == "preparing":
        new = "ready"  # once ready, a topic stays ready while more lessons are made
    topic.status = new
    return old, new


def topics_for_lesson(db: Session, lesson_id: uuid.UUID) -> list[Topic]:
    return list(
        db.scalars(
            select(Topic).join(UserLesson, UserLesson.topic_id == Topic.id).where(UserLesson.lesson_id == lesson_id)
        )
    )


def apply_event(db: Session, user_id: uuid.UUID, ev) -> uuid.UUID | None:
    """Apply one lesson event. Returns the topic id if the event may require a refill.

    Events for lessons the backend doesn't know (e.g. bundled starter lessons) are accepted and ignored.
    """
    ul = db.get(UserLesson, (user_id, ev.lesson_id))
    if ul is None:
        if db.get(Lesson, ev.lesson_id) is None:
            return None
        ul = UserLesson(user_id=user_id, lesson_id=ev.lesson_id, interaction_results=[])
        db.add(ul)
    refill = None
    if ev.type == "lesson_started":
        ul.started_at = ul.started_at or ev.at
    elif ev.type == "lesson_completed":
        ul.completed_at = ul.completed_at or ev.at
        refill = ul.topic_id
    elif ev.type == "lesson_skipped":
        ul.skipped = True
        refill = ul.topic_id
    elif ev.type == "lesson_liked":
        ul.liked = True
    elif ev.type == "interaction_answered" and ev.data is not None:
        ul.interaction_results = [*ul.interaction_results, ev.data.model_dump()]
        if getattr(ev.data, "correct", None) is False:
            _mark_weak(db, user_id, ev.lesson_id)
    elif ev.type == "go_deeper_chosen" and ev.data is not None:
        ul.go_deeper_choice = getattr(ev.data, "option", None)
    return refill


def _mark_weak(db: Session, user_id: uuid.UUID, lesson_id: uuid.UUID) -> None:
    """Concepts answered wrong are revisited in later personal scripts (Product Spec §7.4 item 7)."""
    lesson = db.get(Lesson, lesson_id)
    if lesson is None or lesson.syllabus_id is None:
        return
    progress = db.get(SyllabusProgress, (user_id, lesson.syllabus_id))
    if progress is None:
        progress = SyllabusProgress(user_id=user_id, syllabus_id=lesson.syllabus_id, next_index=0, weak_concepts=[])
        db.add(progress)
    if lesson.concept not in progress.weak_concepts:
        progress.weak_concepts = [*progress.weak_concepts, lesson.concept][-10:]


def maybe_hide(db: Session, lesson: Lesson) -> None:
    reporters = (
        db.scalar(
            select(func.count(func.distinct(LessonReport.user_id))).where(
                LessonReport.lesson_id == lesson.id, LessonReport.reason == "wrong_fact"
            )
        )
        or 0
    )
    if lesson.status == LessonStatus.READY.value and lifecycle.should_hide(reporters):
        lesson.hidden = True
        log.warning("lesson hidden after reports", extra={"lesson_id": str(lesson.id)})
