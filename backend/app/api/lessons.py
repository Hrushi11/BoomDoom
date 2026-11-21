import uuid

from fastapi import APIRouter, Depends, Query, status
from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.orm import Session

from app import auth
from app.db import get_session
from app.errors import ApiError
from app.models import Job, Lesson, LessonReport, ProcessedEvent, Topic, UserLesson
from app.schemas import (
    EventBatch,
    EventsAccepted,
    GenerateRequest,
    GenerateResponse,
    JobOut,
    ReportIn,
)
from app.services import generation
from app.services.manifest import signed_manifest

router = APIRouter(tags=["lessons"])


@router.post("/lessons/generate", response_model=GenerateResponse)
def generate(
    body: GenerateRequest, me: uuid.UUID = Depends(auth.current_user_id), db: Session = Depends(get_session)
) -> GenerateResponse:
    topic = db.get(Topic, body.topic_id)
    if topic is None or topic.user_id != me:
        raise ApiError(404, "not_found", "Unknown topic")
    return GenerateResponse(job_ids=generation.enqueue_lessons(db, me, topic, body.count))


@router.get("/jobs/{job_id}", response_model=JobOut)
def get_job(
    job_id: uuid.UUID, me: uuid.UUID = Depends(auth.current_user_id), db: Session = Depends(get_session)
) -> JobOut:
    job = db.get(Job, job_id)
    if job is None or job.user_id != me:
        raise ApiError(404, "not_found", "Unknown job")
    return JobOut(status=job.status, stage=job.stage, error=job.error)


@router.get("/feed")
def feed(
    topic_id: uuid.UUID | None = None,
    limit: int = Query(default=10, ge=1, le=20),
    me: uuid.UUID = Depends(auth.current_user_id),
    db: Session = Depends(get_session),
) -> list[dict]:
    """Ready, unwatched lessons with signed URLs valid for 24 h."""
    topics = db.scalars(select(Topic).where(Topic.user_id == me, *([Topic.id == topic_id] if topic_id else [])))
    syllabus_ids = [t.syllabus_id for t in topics if t.syllabus_id]
    if not syllabus_ids:
        return []
    watched = select(UserLesson.lesson_id).where(UserLesson.user_id == me, UserLesson.completed_at.is_not(None))
    rows = db.scalars(
        select(Lesson)
        .where(
            Lesson.syllabus_id.in_(syllabus_ids),
            Lesson.status == "ready",
            Lesson.hidden.is_(False),
            (Lesson.owner_user_id.is_(None)) | (Lesson.owner_user_id == me),
            Lesson.id.not_in(watched),
        )
        .order_by(Lesson.concept_index)
        .limit(limit)
    ).all()
    return [signed_manifest(lesson) for lesson in rows]


@router.post("/events", response_model=EventsAccepted)
def post_events(
    body: EventBatch, me: uuid.UUID = Depends(auth.current_user_id), db: Session = Depends(get_session)
) -> EventsAccepted:
    accepted = 0
    for ev in body.events:
        res = db.execute(
            insert(ProcessedEvent).values(event_id=ev.event_id, user_id=me, at=ev.at).on_conflict_do_nothing()
        )
        if res.rowcount == 0:  # type: ignore[attr-defined]
            continue  # idempotent replay
        generation.apply_event(db, me, ev)
        accepted += 1
    db.commit()
    return EventsAccepted(accepted=accepted)


@router.post("/lessons/{lesson_id}/reports", status_code=status.HTTP_202_ACCEPTED)
def report(
    lesson_id: uuid.UUID,
    body: ReportIn,
    me: uuid.UUID = Depends(auth.current_user_id),
    db: Session = Depends(get_session),
) -> dict:
    lesson = db.get(Lesson, lesson_id)
    if lesson is None:
        raise ApiError(404, "not_found", "Unknown lesson")
    db.add(
        LessonReport(lesson_id=lesson_id, user_id=me, reason=body.reason, note=body.note, position_s=body.position_s)
    )
    lesson.report_count += 1
    db.flush()
    generation.maybe_hide(db, lesson)
    db.commit()
    return {"status": "received"}
