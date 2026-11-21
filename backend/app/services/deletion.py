"""Account deletion (Tech Spec §8.10). Target completion < 24 h; runs on the deletion-jobs queue."""

from __future__ import annotations

import logging
import uuid

from sqlalchemy import delete, select, update
from sqlalchemy.orm import Session

from app.models import (
    AppUser,
    Job,
    Lesson,
    LessonProfile,
    LessonReport,
    ProcessedEvent,
    RateBucket,
    RefreshToken,
    Syllabus,
    SyllabusProgress,
    Topic,
    UserLesson,
)
from app.queues import utcnow
from app.storage import get_storage

log = logging.getLogger(__name__)


def delete_user_data(db: Session, user_id: uuid.UUID, keep_job: uuid.UUID | None = None) -> dict[str, int]:
    storage = get_storage()
    personal = list(db.scalars(select(Lesson).where(Lesson.owner_user_id == user_id)))
    objects = sum(storage.delete_prefix(f"lessons/{lesson.id}/") for lesson in personal)
    personal_syllabi = select(Syllabus.id).where(Syllabus.owner_user_id == user_id)

    counts = {"objects": objects, "personal_lessons": len(personal)}
    db.execute(update(LessonReport).where(LessonReport.user_id == user_id).values(user_id=None))
    db.execute(delete(UserLesson).where(UserLesson.user_id == user_id))
    db.execute(delete(SyllabusProgress).where(SyllabusProgress.user_id == user_id))
    db.execute(delete(Job).where(Job.user_id == user_id, Job.id != keep_job))
    db.execute(delete(Lesson).where(Lesson.owner_user_id == user_id))
    db.execute(update(Topic).where(Topic.user_id == user_id).values(syllabus_id=None))
    db.execute(delete(Syllabus).where(Syllabus.id.in_(personal_syllabi)))
    db.execute(delete(Topic).where(Topic.user_id == user_id))
    db.execute(delete(LessonProfile).where(LessonProfile.user_id == user_id))
    db.execute(delete(RefreshToken).where(RefreshToken.user_id == user_id))
    db.execute(delete(ProcessedEvent).where(ProcessedEvent.user_id == user_id))
    db.execute(delete(RateBucket).where(RateBucket.user_id == user_id))
    db.execute(delete(AppUser).where(AppUser.id == user_id))
    return counts


def process_deletion_job(db: Session, job_id: uuid.UUID) -> None:
    job = db.get(Job, job_id)
    if job is None or job.stage != "deletion" or job.status == "done" or job.user_id is None:
        return
    counts = delete_user_data(db, job.user_id, keep_job=job.id)
    job.status, job.finished_at = "done", utcnow()
    log.info("user deleted", extra={"job_id": str(job.id), "status": str(counts)})
