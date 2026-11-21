import uuid

from fastapi import APIRouter, Depends, status
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app import auth
from app.db import get_session
from app.domain.sharing import normalize_topic
from app.errors import ApiError
from app.models import Lesson, LessonProfile, Topic
from app.schemas import (
    LessonProfileIn,
    LessonProfileOut,
    TopicCreate,
    TopicOut,
    TopicPatch,
    TopicStatusOut,
)
from app.services import generation

router = APIRouter(tags=["topics"])


def _out(db: Session, t: Topic) -> TopicOut:
    ready = (
        db.scalar(
            select(func.count())
            .select_from(Lesson)
            .where(Lesson.syllabus_id == t.syllabus_id, Lesson.status == "ready", Lesson.hidden.is_(False))
        )
        if t.syllabus_id
        else 0
    )
    return TopicOut(
        topic_id=t.id,
        name=t.name,
        level=t.level,
        goal=t.goal,
        status=t.status,  # type: ignore[arg-type]
        ready_count=ready or 0,
    )


@router.put("/users/{user_id}/lesson-profile", response_model=LessonProfileOut)
def put_profile(
    user_id: uuid.UUID,
    body: LessonProfileIn,
    me: uuid.UUID = Depends(auth.current_user_id),
    db: Session = Depends(get_session),
) -> LessonProfileOut:
    auth.require_self(user_id, me)
    profile = db.get(LessonProfile, me)
    if profile is None:
        profile = LessonProfile(user_id=me, version=1)
        db.add(profile)
    else:
        profile.version += 1
    profile.language, profile.voice_id, profile.length_s = body.language, body.voice_id, body.length_s

    out: list[TopicStatusOut] = []
    for t in body.topics:
        norm = normalize_topic(t.name)
        topic = db.scalar(select(Topic).where(Topic.user_id == me, Topic.name_norm == norm))
        if topic is None:
            topic = Topic(user_id=me, name=t.name, name_norm=norm, level=t.level, goal=t.goal, type=t.type)
            db.add(topic)
        else:
            topic.level, topic.goal = t.level, t.goal
        db.flush()
        out.append(TopicStatusOut(topic_id=topic.id, status=topic.status))  # type: ignore[arg-type]
    db.commit()
    for o in out:
        generation.ensure_stock(db, me, o.topic_id)  # queues the starter batch
    return LessonProfileOut(profile_version=profile.version, topics=out)


@router.post("/topics", response_model=TopicStatusOut)
def create_topic(
    body: TopicCreate, me: uuid.UUID = Depends(auth.current_user_id), db: Session = Depends(get_session)
) -> TopicStatusOut:
    generation.check_topic_safety(body.name, body.goal)
    topic = Topic(
        user_id=me,
        name=body.name,
        name_norm=normalize_topic(body.name),
        level=body.level,
        goal=body.goal,
        type="custom",
    )
    db.add(topic)
    try:
        db.commit()
    except IntegrityError as e:
        db.rollback()
        raise ApiError(409, "duplicate_topic", "You already have this topic") from e
    generation.ensure_stock(db, me, topic.id)
    return TopicStatusOut(topic_id=topic.id, status="preparing")


@router.get("/topics", response_model=list[TopicOut])
def list_topics(me: uuid.UUID = Depends(auth.current_user_id), db: Session = Depends(get_session)) -> list[TopicOut]:
    return [_out(db, t) for t in db.scalars(select(Topic).where(Topic.user_id == me).order_by(Topic.created_at))]


def _owned(db: Session, topic_id: uuid.UUID, me: uuid.UUID) -> Topic:
    t = db.get(Topic, topic_id)
    if t is None or t.user_id != me:
        raise ApiError(404, "not_found", "Unknown topic")
    return t


@router.patch("/topics/{topic_id}", response_model=TopicOut)
def patch_topic(
    topic_id: uuid.UUID,
    body: TopicPatch,
    me: uuid.UUID = Depends(auth.current_user_id),
    db: Session = Depends(get_session),
) -> TopicOut:
    t = _owned(db, topic_id, me)
    if body.level is not None:
        t.level = body.level
    if body.goal is not None:
        t.goal = body.goal or None
    db.commit()
    return _out(db, t)


@router.delete("/topics/{topic_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_topic(
    topic_id: uuid.UUID, me: uuid.UUID = Depends(auth.current_user_id), db: Session = Depends(get_session)
) -> None:
    db.delete(_owned(db, topic_id, me))
    db.commit()
