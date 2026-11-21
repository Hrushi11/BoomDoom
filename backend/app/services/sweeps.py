"""Scheduled maintenance (Tech Spec §8.1 scheduler). Run by EventBridge Scheduler as ECS tasks:

python -m app.cron refill        every 10 min
python -m app.cron sweep-stuck   every 5 min
"""

from __future__ import annotations

import logging

from sqlalchemy import select

from app import metrics, queues
from app.config import get_settings
from app.db import session_scope
from app.domain import lifecycle
from app.models import AppUser, Job, Lesson, Topic
from app.services import generation

log = logging.getLogger(__name__)

STAGE_QUEUE = {"syllabus": "script-jobs", "script": "script-jobs", "render": "render-jobs", "deletion": "deletion-jobs"}
STAGE_TIMEOUT_S = {"syllabus": 120, "script": 120, "deletion": 300}
LOST_MESSAGE_AFTER_S = 1800  # queued job with no worker activity for 30 min: message presumed lost


def refill_all(batch: int = 200) -> int:
    """Top up every active (user, topic). Idempotent; safe to run concurrently with the API."""
    queued, last_id = 0, None
    while True:
        with session_scope() as db:
            q = (
                select(Topic.id, Topic.user_id)
                .join(AppUser, AppUser.id == Topic.user_id)
                .where(AppUser.deleted_at.is_(None), Topic.status != "failed")
                .order_by(Topic.id)
                .limit(batch)
            )
            if last_id is not None:
                q = q.where(Topic.id > last_id)
            rows = db.execute(q).all()
        if not rows:
            break
        for topic_id, user_id in rows:
            with session_scope() as db:
                plan = generation.ensure_stock(db, user_id, topic_id)
                db.commit()
            plan.send()
            queued += len(plan.job_ids)
        last_id = rows[-1][0]
    metrics.emit("refill_jobs_queued", queued)
    return queued


def sweep_stuck() -> dict[str, int]:
    """Reset jobs stuck in `running` for > 3x their timeout; re-send queued jobs whose message was lost."""
    timeouts = {**STAGE_TIMEOUT_S, "render": get_settings().render_visibility_s}
    out = {"requeued": 0, "failed": 0, "resent": 0}
    with session_scope() as db:
        for job in db.scalars(
            select(Job).where(Job.status.in_(["running", "queued"])).with_for_update(skip_locked=True)
        ):
            age = (queues.utcnow() - job.updated_at).total_seconds()
            if job.status == "running" and age > 3 * timeouts.get(job.stage, 900):
                if job.attempts < lifecycle.MAX_ATTEMPTS:
                    job.status = "queued"
                    out["requeued"] += 1
                    _resend(job)
                else:
                    _fail_final(db, job, "stuck")
                    out["failed"] += 1
            elif job.status == "queued" and age > LOST_MESSAGE_AFTER_S:
                job.updated_at = queues.utcnow()
                out["resent"] += 1
                _resend(job)
    for k, v in out.items():
        metrics.emit(f"sweeper_{k}", v)
    return out


def _resend(job: Job) -> None:
    queues.enqueue(STAGE_QUEUE[job.stage], job.id)


def _fail_final(db, job: Job, error: str) -> None:
    job.status, job.error, job.finished_at = "failed_final", error, queues.utcnow()
    if job.lesson_id:
        lesson = db.get(Lesson, job.lesson_id)
        if lesson is not None:
            lesson.status = lifecycle.LessonStatus.FAILED_FINAL.value
            for topic in generation.topics_for_lesson(db, lesson.id):
                generation.refresh_topic_status(db, topic)
