import uuid

from fastapi import APIRouter, Depends, status
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app import auth
from app.api.deps import active_user
from app.db import get_session
from app.domain.sharing import normalize_topic
from app.errors import ApiError
from app.models import Lesson, Topic, UserLesson
from app.schemas import LessonProfileIn, LessonProfileOut, TopicCreate, TopicOut, TopicPatch, TopicStatusOut
from app.services import generation, rate_limit

router = APIRouter(tags=["topics"])


def _ready_count(db: Session, user_id: uuid.UUID, topic_id: uuid.UUID) -> int:
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
                Lesson.status == "ready",
                Lesson.hidden.is_(False),
            )
        )
        or 0
    )


def _out(db: Session, t: Topic) -> TopicOut:
    return TopicOut(
        topic_id=t.id,
        name=t.name,
        level=t.level,
        goal=t.goal,
        status=t.status,  # type: ignore[arg-type]
        ready_count=_ready_count(db, t.user_id, t.id),
    )


@router.put("/users/{user_id}/lesson-profile", response_model=LessonProfileOut)
def put_profile(
    user_id: uuid.UUID, body: LessonProfileIn, me: uuid.UUID = Depends(active_user), db: Session = Depends(get_session)
) -> LessonProfileOut:
    """Save lesson inputs and queue the starter batch for each topic."""
    auth.require_self(user_id, me)
    for t in body.topics:
        if t.type != "preset":
            generation.check_topic_safety(t.name, t.goal)
    profile = generation.profile_for(db, me)
    changed = (profile.language, profile.voice_id, profile.length_s) != (body.language, body.voice_id, body.length_s)
    profile.language, profile.voice_id, profile.length_s = body.language, body.voice_id, body.length_s
    if changed:
        profile.version += 1

    plan = generation.Plan()
    out: list[TopicStatusOut] = []
    for t in body.topics:
        norm = normalize_topic(t.name)
        topic = db.scalar(select(Topic).where(Topic.user_id == me, Topic.name_norm == norm))
        if topic is None:
            topic = Topic(user_id=me, name=t.name.strip(), name_norm=norm, level=t.level, goal=t.goal, type=t.type)
            db.add(topic)
        elif (topic.level, topic.goal) != (t.level, t.goal):
            topic.level, topic.goal, topic.syllabus_id, topic.status = t.level, t.goal, None, "preparing"
        db.flush()
        plan.extend(generation.ensure_stock(db, me, topic.id))
        out.append(TopicStatusOut(topic_id=topic.id, status=topic.status))  # type: ignore[arg-type]
    generation.commit_and_send(db, plan)
    return LessonProfileOut(profile_version=profile.version, topics=out)


@router.post("/topics", response_model=TopicStatusOut)
def create_topic(
    body: TopicCreate, me: uuid.UUID = Depends(active_user), db: Session = Depends(get_session)
) -> TopicStatusOut:
    generation.check_topic_safety(body.name, body.goal)
    norm = normalize_topic(body.name)
    if not norm:
        raise ApiError(422, "invalid_topic", "Topic name is empty")
    if db.scalar(select(Topic.id).where(Topic.user_id == me, Topic.name_norm == norm)):
        raise ApiError(409, "duplicate_topic", "You already have this topic")
    rate_limit.consume(db, me, "topics")
    topic = Topic(user_id=me, name=body.name.strip(), name_norm=norm, level=body.level, goal=body.goal, type="custom")
    db.add(topic)
    db.flush()
    generation.commit_and_send(db, generation.ensure_stock(db, me, topic.id))
    return TopicStatusOut(topic_id=topic.id, status="preparing")


@router.get("/topics", response_model=list[TopicOut])
def list_topics(me: uuid.UUID = Depends(active_user), db: Session = Depends(get_session)) -> list[TopicOut]:
    return [_out(db, t) for t in db.scalars(select(Topic).where(Topic.user_id == me).order_by(Topic.created_at))]


def _owned(db: Session, topic_id: uuid.UUID, me: uuid.UUID) -> Topic:
    t = db.get(Topic, topic_id)
    if t is None or t.user_id != me:
        raise ApiError(404, "not_found", "Unknown topic")
    return t


@router.patch("/topics/{topic_id}", response_model=TopicOut)
def patch_topic(
    topic_id: uuid.UUID, body: TopicPatch, me: uuid.UUID = Depends(active_user), db: Session = Depends(get_session)
) -> TopicOut:
    """Level or goal changes pick a new syllabus; already allocated lessons stay in the feed."""
    t = _owned(db, topic_id, me)
    new_goal = (body.goal or None) if body.goal is not None else t.goal
    new_level = body.level or t.level
    if new_goal != t.goal:
        generation.check_topic_safety(t.name, new_goal)
    if (new_level, new_goal) != (t.level, t.goal):
        t.level, t.goal, t.syllabus_id = new_level, new_goal, None
        if t.status == "failed":
            t.status = "preparing"
        db.flush()
        generation.commit_and_send(db, generation.ensure_stock(db, me, t.id))
    return _out(db, t)


@router.post("/topics/{topic_id}/retry", response_model=TopicStatusOut)
def retry_topic(
    topic_id: uuid.UUID, me: uuid.UUID = Depends(active_user), db: Session = Depends(get_session)
) -> TopicStatusOut:
    """US-22: retry a topic whose first lessons all failed."""
    t = _owned(db, topic_id, me)
    if t.status != "failed":
        return TopicStatusOut(topic_id=t.id, status=t.status)  # type: ignore[arg-type]
    t.status, t.syllabus_id = "preparing", None
    db.flush()
    generation.commit_and_send(db, generation.ensure_stock(db, me, t.id))
    return TopicStatusOut(topic_id=t.id, status="preparing")


@router.delete("/topics/{topic_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_topic(topic_id: uuid.UUID, me: uuid.UUID = Depends(active_user), db: Session = Depends(get_session)) -> None:
    db.delete(_owned(db, topic_id, me))
    db.commit()
