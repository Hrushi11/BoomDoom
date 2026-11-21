import uuid
from datetime import UTC, datetime

from fastapi import APIRouter, Depends, Query, status
from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.orm import Session

from app.api.deps import active_user
from app.db import get_session
from app.errors import ApiError
from app.models import Job, Lesson, LessonReport, ProcessedEvent, Topic, UserLesson
from app.schemas import EventBatch, EventsAccepted, GenerateRequest, GenerateResponse, JobOut, ReportIn
from app.services import generation, rate_limit
from app.services.manifest import signed_manifest

router = APIRouter(tags=["lessons"])


@router.post("/lessons/generate", response_model=GenerateResponse)
def generate(
    body: GenerateRequest, me: uuid.UUID = Depends(active_user), db: Session = Depends(get_session)
) -> GenerateResponse:
    """Explicit request for more lessons (on top of automatic stock). Free plan: 20 lessons/day."""
    topic = db.get(Topic, body.topic_id)
    if topic is None or topic.user_id != me:
        raise ApiError(404, "not_found", "Unknown topic")
    rate_limit.consume(db, me, "generate", cost=body.count)
    plan = generation.allocate(db, me, topic, body.count)
    return GenerateResponse(job_ids=generation.commit_and_send(db, plan))


@router.get("/jobs/{job_id}", response_model=JobOut)
def get_job(job_id: uuid.UUID, me: uuid.UUID = Depends(active_user), db: Session = Depends(get_session)) -> JobOut:
    job = db.get(Job, job_id)
    if job is None or job.user_id != me:
        raise ApiError(404, "not_found", "Unknown job")
    return JobOut(status=job.status, stage=job.stage, error=job.error if job.status == "failed_final" else None)


@router.get("/feed")
def feed(
    topic_id: uuid.UUID | None = None,
    limit: int = Query(default=10, ge=1, le=20),
    me: uuid.UUID = Depends(active_user),
    db: Session = Depends(get_session),
) -> list[dict]:
    """Ready, unwatched lessons allocated to the user, with CDN URLs signed for 24 h."""
    q = (
        select(UserLesson, Lesson, Topic)
        .join(Lesson, Lesson.id == UserLesson.lesson_id)
        .join(Topic, Topic.id == UserLesson.topic_id)
        .where(
            UserLesson.user_id == me,
            UserLesson.completed_at.is_(None),
            UserLesson.skipped.is_(False),
            Lesson.status == "ready",
            Lesson.hidden.is_(False),
        )
        .order_by(UserLesson.assigned_at, Lesson.concept_index)
        .limit(limit)
    )
    if topic_id:
        q = q.where(UserLesson.topic_id == topic_id)
    rows = db.execute(q).all()
    now = datetime.now(UTC)
    for ul, _, _ in rows:
        ul.delivered_at = ul.delivered_at or now
    db.commit()
    return [signed_manifest(lesson, topic) for _, lesson, topic in rows]


@router.post("/events", response_model=EventsAccepted)
def post_events(
    body: EventBatch, me: uuid.UUID = Depends(active_user), db: Session = Depends(get_session)
) -> EventsAccepted:
    """Lesson events only, idempotent by event_id. Completed/skipped lessons trigger a stock refill."""
    accepted, refill = 0, set()
    for ev in body.events:
        res = db.execute(
            insert(ProcessedEvent).values(event_id=ev.event_id, user_id=me, at=ev.at).on_conflict_do_nothing()
        )
        if res.rowcount == 0:  # type: ignore[attr-defined]
            accepted += 1  # replay of an event we already have: acknowledge so the client drops it
            continue
        topic_id = generation.apply_event(db, me, ev)
        if topic_id:
            refill.add(topic_id)
        accepted += 1
    plan = generation.Plan()
    for topic_id in refill:
        plan.extend(generation.ensure_stock(db, me, topic_id))
    generation.commit_and_send(db, plan)
    return EventsAccepted(accepted=accepted)


@router.post("/lessons/{lesson_id}/reports", status_code=status.HTTP_202_ACCEPTED)
def report(
    lesson_id: uuid.UUID, body: ReportIn, me: uuid.UUID = Depends(active_user), db: Session = Depends(get_session)
) -> dict:
    lesson = db.get(Lesson, lesson_id)
    if lesson is None:
        raise ApiError(404, "not_found", "Unknown lesson")
    already = db.scalar(
        select(LessonReport.id).where(
            LessonReport.lesson_id == lesson_id, LessonReport.user_id == me, LessonReport.reason == body.reason
        )
    )
    if not already:
        db.add(
            LessonReport(
                lesson_id=lesson_id, user_id=me, reason=body.reason, note=body.note, position_s=body.position_s
            )
        )
        lesson.report_count += 1
        db.flush()
        generation.maybe_hide(db, lesson)
    db.commit()
    return {"status": "received"}
