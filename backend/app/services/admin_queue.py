"""POC operator controls for the job queues: see what is queued or running, move work to the front, cancel it.

Priority: queue_message rows are taken in (visible_at, id) order. Prioritising a job moves its messages' visible_at
into the past (the earliest by `boost` order), so it is picked next while ordinary jobs stay first in, first out.

Cancel: a queued job loses its messages and becomes `cancelled`. A running job is marked `cancelled` and stops
cooperatively: the film director checks between stages and the render pipeline checks before publishing
(workers.common.check_cancelled). Its lesson is marked failed_final so it is never shown; note that the refill sweep
may later allocate replacement lessons for active topics (product rule: keep 5 lessons in stock).
"""

from __future__ import annotations

import uuid
from datetime import UTC, datetime, timedelta

from sqlalchemy import select, text
from sqlalchemy.orm import Session

from app.models import Job, Lesson, Topic
from app.services import generation

ACTIVE = ("queued", "running")
CANCELLED = "cancelled"


def overview(db: Session, stage: str | None = None, limit: int = 200) -> dict:
    """Queued and running jobs, queued ones in the order workers will take them."""
    order = {
        str(r.job_id): (i, r.queue)
        for i, r in enumerate(
            db.execute(
                text("select (body::json->>'job_id') as job_id, queue from queue_message order by visible_at, id")
            ).all()
        )
    }
    q = (
        select(Job, Lesson.concept, Topic.name)
        .outerjoin(Lesson, Lesson.id == Job.lesson_id)
        .outerjoin(Topic, Topic.id == Job.topic_id)
        .where(Job.status.in_(ACTIVE))
    )
    if stage:
        q = q.where(Job.stage == stage)
    now = datetime.now(UTC)
    jobs = []
    for job, concept, topic in db.execute(q).all():
        pos = order.get(str(job.id))
        jobs.append(
            {
                "job_id": str(job.id),
                "stage": job.stage,
                "status": job.status,
                "attempts": job.attempts,
                "position": pos[0] if pos and job.status == "queued" else None,
                "lesson": concept,
                "topic": topic,
                "topic_id": str(job.topic_id) if job.topic_id else None,
                "user_id": str(job.user_id) if job.user_id else None,
                "lesson_id": str(job.lesson_id) if job.lesson_id else None,
                "step": job.progress if job.status == "running" else None,
                "created_at": job.created_at.isoformat(),
                "age_s": round((now - job.created_at).total_seconds()),
                "waiting_s": round((now - job.updated_at).total_seconds()),
            }
        )
    jobs.sort(key=lambda j: (j["status"] != "running", j["position"] if j["position"] is not None else 1e9))
    counts: dict[str, dict[str, int]] = {}
    for j in jobs:
        counts.setdefault(j["stage"], {}).setdefault(j["status"], 0)
        counts[j["stage"]][j["status"]] += 1
    return {"counts": counts, "jobs": jobs[:limit]}


def _matching(db: Session, *, job_id=None, user_id=None, topic_id=None, stage=None, statuses=ACTIVE) -> list[Job]:
    q = select(Job).where(Job.status.in_(statuses))
    if job_id:
        q = q.where(Job.id == job_id)
    if user_id:
        q = q.where(Job.user_id == user_id)
    if topic_id:
        q = q.where(Job.topic_id == topic_id)
    if stage:
        q = q.where(Job.stage == stage)
    return list(db.scalars(q.with_for_update()))


def _message_filter() -> str:
    return "(body::json->>'job_id') = any(:ids)"


def prioritize(db: Session, *, job_id=None, user_id=None, topic_id=None, stage=None) -> int:
    """Move matching queued jobs to the front of their queues (in their current order). Returns how many."""
    jobs = _matching(db, job_id=job_id, user_id=user_id, topic_id=topic_id, stage=stage, statuses=("queued",))
    if not jobs:
        return 0
    ids = [str(j.id) for j in jobs]
    rows = db.execute(
        text(
            f"select id, (body::json->>'job_id') as job_id from queue_message where {_message_filter()} "
            "order by visible_at, id"
        ),
        {"ids": ids},
    ).all()
    # Ahead of every message already queued, keeping the boosted jobs' relative order.
    earliest = db.execute(text("select min(visible_at) from queue_message")).scalar() or datetime.now(UTC)
    start = min(earliest, datetime.now(UTC)) - timedelta(seconds=len(rows) + 1)
    for i, r in enumerate(rows):
        db.execute(
            text("update queue_message set visible_at = :t where id = :id"),
            {"t": start + timedelta(seconds=i), "id": r.id},
        )
    with_message = {r.job_id for r in rows}
    lost = [j for j in jobs if str(j.id) not in with_message]
    if lost:  # queued but its message is gone: send one now (it lands at the back, then boost it)
        from app import queues
        from app.services.sweeps import STAGE_QUEUE

        for j in lost:
            queues.enqueue(STAGE_QUEUE[j.stage], j.id)
        db.execute(
            text(f"update queue_message set visible_at = :t where {_message_filter()}"),
            {"t": start - timedelta(seconds=1), "ids": [str(j.id) for j in lost]},
        )
    return len(jobs)


def cancel(db: Session, *, job_id=None, user_id=None, topic_id=None, stage=None, include_running=False) -> dict:
    """Cancel matching jobs. Running ones only with include_running (they stop at their next checkpoint)."""
    statuses = ACTIVE if include_running else ("queued",)
    jobs = _matching(db, job_id=job_id, user_id=user_id, topic_id=topic_id, stage=stage, statuses=statuses)
    if not jobs:
        return {"cancelled": 0, "running_stopping": 0}
    running = sum(1 for j in jobs if j.status == "running")
    ids = [str(j.id) for j in jobs]
    db.execute(text(f"delete from queue_message where {_message_filter()}"), {"ids": ids})
    from app.domain.lifecycle import LessonStatus

    topics: set[uuid.UUID] = set()
    for j in jobs:
        j.status, j.error = CANCELLED, "cancelled by operator"
        if j.lesson_id:
            lesson = db.get(Lesson, j.lesson_id)
            if lesson and lesson.status != LessonStatus.READY.value:
                lesson.status = LessonStatus.FAILED_FINAL.value
        if j.topic_id:
            topics.add(j.topic_id)
    db.flush()
    for tid in topics:
        topic = db.get(Topic, tid)
        if topic and topic.status == "preparing":
            generation.refresh_topic_status(db, topic)
    return {"cancelled": len(jobs), "running_stopping": running}


def history(db: Session, limit: int = 300) -> list[dict]:
    """Every finished render (newest first) with signed S3 links to its video, thumbnail, manifest and
    project.json (the film's notes, audit issues and token usage)."""
    from app.cdn import sign_url

    rows = db.execute(
        select(Job, Lesson, Topic.name)
        .join(Lesson, Lesson.id == Job.lesson_id)
        .outerjoin(Topic, Topic.id == Job.topic_id)
        .where(Job.stage == "render", Job.status.not_in(ACTIVE))
        .order_by(Job.updated_at.desc())
        .limit(limit)
    ).all()
    out = []
    for job, lesson, topic in rows:
        links = {}
        if lesson.video_key:
            prefix = lesson.video_key.rsplit("/", 1)[0] + "/"
            links = {
                "video": sign_url(lesson.video_key),
                "thumb": sign_url(lesson.thumb_key) if lesson.thumb_key else None,
                "manifest": sign_url(prefix + "manifest.json"),
                "project": sign_url(prefix + "project.json"),
                "s3_key": lesson.video_key,
            }
        out.append(
            {
                "job_id": str(job.id),
                "status": job.status,
                "attempts": job.attempts,
                "error": job.error,
                "lesson_id": str(lesson.id),
                "lesson": lesson.concept,
                "lesson_status": lesson.status,
                "topic": topic,
                "user_id": str(job.user_id) if job.user_id else None,
                "created_at": job.created_at.isoformat(),
                "finished_at": (job.finished_at or job.updated_at).isoformat(),
                "total_s": round(((job.finished_at or job.updated_at) - job.created_at).total_seconds()),
                "render_s": lesson.render_seconds,
                "duration_s": lesson.duration_s,
                "links": links,
            }
        )
    return out
