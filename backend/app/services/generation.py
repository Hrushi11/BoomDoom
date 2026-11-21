"""Orchestration between the API and the workers (Tech Spec §8.5–8.7)."""

import uuid
from datetime import UTC, datetime

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app import queues
from app.config import get_settings
from app.domain import lifecycle
from app.domain.sharing import shortfall
from app.errors import ApiError
from app.models import Job, Lesson, LessonReport, Topic, UserLesson
from app.schemas import Event

_BLOCKED_TERMS = {"porn", "nsfw", "bomb making", "self harm"}  # placeholder; real check uses gpt-6-luna/moderation


def check_topic_safety(name: str, goal: str | None) -> None:
    text = f"{name} {goal or ''}".lower()
    if any(term in text for term in _BLOCKED_TERMS):
        raise ApiError(422, "topic_not_allowed", "We can't make lessons on this topic")


def _unwatched_ready(db: Session, user_id: uuid.UUID, topic: Topic) -> int:
    if topic.syllabus_id is None:
        return 0
    watched = select(UserLesson.lesson_id).where(UserLesson.user_id == user_id, UserLesson.completed_at.is_not(None))
    return (
        db.scalar(
            select(func.count())
            .select_from(Lesson)
            .where(
                Lesson.syllabus_id == topic.syllabus_id,
                Lesson.status == "ready",
                Lesson.hidden.is_(False),
                Lesson.id.not_in(watched),
            )
        )
        or 0
    )


def _in_flight(db: Session, topic_id: uuid.UUID) -> int:
    return (
        db.scalar(
            select(func.count())
            .select_from(Job)
            .where(Job.topic_id == topic_id, Job.status.in_(["queued", "running"]), Job.stage != "deletion")
        )
        or 0
    )


def enqueue_lessons(db: Session, user_id: uuid.UUID, topic: Topic, count: int) -> list[uuid.UUID]:
    """Create one script job per lesson. The script worker builds/reuses the syllabus and
    reuses shared lessons (same share_key, not yet delivered to this user) before generating."""
    jobs = [Job(user_id=user_id, topic_id=topic.id, stage="script", status="queued") for _ in range(count)]
    db.add_all(jobs)
    db.commit()
    for j in jobs:
        queues.enqueue("script-jobs", j.id)
    return [j.id for j in jobs]


def ensure_stock(db: Session, user_id: uuid.UUID, topic_id: uuid.UUID) -> list[uuid.UUID]:
    s = get_settings()
    topic = db.get(Topic, topic_id)
    if topic is None:
        return []
    have = _unwatched_ready(db, user_id, topic) + _in_flight(db, topic_id)
    need = shortfall(have, target=s.target_stock, refill_below=s.refill_below)
    return enqueue_lessons(db, user_id, topic, need) if need else []


def apply_event(db: Session, user_id: uuid.UUID, ev: Event) -> None:
    ul = db.get(UserLesson, (user_id, ev.lesson_id))
    if ul is None:
        ul = UserLesson(user_id=user_id, lesson_id=ev.lesson_id, interaction_results=[])
        db.add(ul)
    if ev.type == "lesson_started":
        ul.started_at = ul.started_at or ev.at
    elif ev.type == "lesson_completed":
        ul.completed_at = ul.completed_at or ev.at
    elif ev.type == "lesson_skipped":
        ul.skipped = True
    elif ev.type == "lesson_liked":
        ul.liked = True
    elif ev.type == "interaction_answered" and ev.data is not None:
        ul.interaction_results = [*ul.interaction_results, ev.data.model_dump()]
    elif ev.type == "go_deeper_chosen" and ev.data is not None:
        ul.go_deeper_choice = getattr(ev.data, "option", None)


def maybe_hide(db: Session, lesson: Lesson) -> None:
    reporters = (
        db.scalar(
            select(func.count(func.distinct(LessonReport.user_id))).where(
                LessonReport.lesson_id == lesson.id, LessonReport.reason == "wrong_fact"
            )
        )
        or 0
    )
    if lesson.status == lifecycle.LessonStatus.READY and lifecycle.should_hide(reporters):
        lesson.hidden = True


def mark_ready(db: Session, lesson: Lesson) -> None:
    lesson.status = lifecycle.transition(lifecycle.LessonStatus(lesson.status), lifecycle.LessonStatus.READY)
    lesson.ready_at = datetime.now(UTC)
