"""SQLAlchemy models mirroring Tech Spec §8.4. Keep in sync with Alembic migrations."""

import uuid
from datetime import datetime

from sqlalchemy import (
    Boolean,
    DateTime,
    Float,
    ForeignKey,
    Index,
    Integer,
    Text,
    UniqueConstraint,
    func,
    text,
)
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


def _uuid() -> Mapped[uuid.UUID]:
    return mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)


def _now() -> Mapped[datetime]:
    return mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)


class AppUser(Base):
    __tablename__ = "app_user"
    __table_args__ = (UniqueConstraint("auth_provider", "auth_subject"),)

    id: Mapped[uuid.UUID] = _uuid()
    install_secret_hash: Mapped[str] = mapped_column(Text, unique=True, nullable=False)
    auth_provider: Mapped[str | None] = mapped_column(Text)
    auth_subject: Mapped[str | None] = mapped_column(Text)
    plan: Mapped[str] = mapped_column(Text, nullable=False, server_default="free")
    fcm_token: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = _now()
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))


class RefreshToken(Base):
    """Opaque, rotated refresh tokens (§8.2). Only the hash is stored."""

    __tablename__ = "refresh_token"

    token_hash: Mapped[str] = mapped_column(Text, primary_key=True)
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("app_user.id", ondelete="CASCADE"), nullable=False)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    revoked: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default=text("false"))


class LessonProfile(Base):
    __tablename__ = "lesson_profile"

    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("app_user.id", ondelete="CASCADE"), primary_key=True)
    language: Mapped[str] = mapped_column(Text, nullable=False, server_default="en")
    voice_id: Mapped[str] = mapped_column(Text, nullable=False, server_default="af_heart")
    length_s: Mapped[int] = mapped_column(Integer, nullable=False, server_default="45")
    version: Mapped[int] = mapped_column(Integer, nullable=False, server_default="1")
    updated_at: Mapped[datetime] = _now()


class Topic(Base):
    __tablename__ = "topic"
    __table_args__ = (UniqueConstraint("user_id", "name_norm"),)

    id: Mapped[uuid.UUID] = _uuid()
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("app_user.id", ondelete="CASCADE"))
    name: Mapped[str] = mapped_column(Text, nullable=False)
    name_norm: Mapped[str] = mapped_column(Text, nullable=False)
    level: Mapped[str] = mapped_column(Text, nullable=False)
    goal: Mapped[str | None] = mapped_column(Text)
    type: Mapped[str] = mapped_column(Text, nullable=False)  # preset|custom|suggested
    status: Mapped[str] = mapped_column(Text, nullable=False, server_default="preparing")
    syllabus_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    created_at: Mapped[datetime] = _now()


class Syllabus(Base):
    __tablename__ = "syllabus"
    __table_args__ = (
        Index(
            "syllabus_shared_key",
            "topic_norm",
            "level",
            "language",
            unique=True,
            postgresql_where=text("owner_user_id is null"),
        ),
    )

    id: Mapped[uuid.UUID] = _uuid()
    topic_norm: Mapped[str] = mapped_column(Text, nullable=False)
    level: Mapped[str] = mapped_column(Text, nullable=False)
    language: Mapped[str] = mapped_column(Text, nullable=False)
    goal_hash: Mapped[str | None] = mapped_column(Text)
    owner_user_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("app_user.id", ondelete="CASCADE"))
    concepts: Mapped[list] = mapped_column(JSONB, nullable=False, server_default="[]")
    model: Mapped[str] = mapped_column(Text, nullable=False)
    # Not in §8.4: lets concurrent users attach to a shared syllabus while it is being built.
    status: Mapped[str] = mapped_column(Text, nullable=False, server_default="building")  # building|ready|failed
    created_at: Mapped[datetime] = _now()


class SyllabusProgress(Base):
    __tablename__ = "syllabus_progress"

    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("app_user.id", ondelete="CASCADE"), primary_key=True)
    syllabus_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("syllabus.id"), primary_key=True)
    next_index: Mapped[int] = mapped_column(Integer, nullable=False, server_default="0")
    weak_concepts: Mapped[list] = mapped_column(JSONB, nullable=False, server_default="[]")


class Lesson(Base):
    __tablename__ = "lesson"
    __table_args__ = (
        Index("lesson_ready_shared", "share_key", "status", postgresql_where=text("owner_user_id is null")),
        # One shared lesson per (share_key, concept): concurrent users reuse instead of duplicating.
        Index(
            "lesson_shared_concept",
            "share_key",
            "concept_index",
            unique=True,
            postgresql_where=text("owner_user_id is null"),
        ),
    )

    id: Mapped[uuid.UUID] = _uuid()
    syllabus_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("syllabus.id"))
    owner_user_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("app_user.id", ondelete="CASCADE"))
    concept: Mapped[str] = mapped_column(Text, nullable=False)
    concept_index: Mapped[int] = mapped_column(Integer, nullable=False)
    format: Mapped[str] = mapped_column(Text, nullable=False)
    level: Mapped[str] = mapped_column(Text, nullable=False)
    language: Mapped[str] = mapped_column(Text, nullable=False)
    voice_id: Mapped[str] = mapped_column(Text, nullable=False)
    visual_style: Mapped[str] = mapped_column(Text, nullable=False)
    accent_color: Mapped[str] = mapped_column(Text, nullable=False)
    share_key: Mapped[str | None] = mapped_column(Text)
    script: Mapped[dict | None] = mapped_column(JSONB)
    manifest: Mapped[dict | None] = mapped_column(JSONB)
    duration_s: Mapped[float | None] = mapped_column(Float)
    video_key: Mapped[str | None] = mapped_column(Text)
    thumb_key: Mapped[str | None] = mapped_column(Text)
    status: Mapped[str] = mapped_column(Text, nullable=False)
    report_count: Mapped[int] = mapped_column(Integer, nullable=False, server_default="0")
    hidden: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default=text("false"))
    cost_tokens_in: Mapped[int | None] = mapped_column(Integer)
    cost_tokens_out: Mapped[int | None] = mapped_column(Integer)
    render_seconds: Mapped[float | None] = mapped_column(Float)
    created_at: Mapped[datetime] = _now()
    ready_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))


class UserLesson(Base):
    __tablename__ = "user_lesson"

    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("app_user.id", ondelete="CASCADE"), primary_key=True)
    lesson_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("lesson.id", ondelete="CASCADE"), primary_key=True)
    # Not in §8.4: which of the user's topics this lesson was allocated to (shared lessons have no topic).
    topic_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("topic.id", ondelete="CASCADE"), index=True)
    assigned_at: Mapped[datetime] = _now()
    delivered_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    started_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    completed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    skipped: Mapped[bool] = mapped_column(Boolean, server_default=text("false"))
    liked: Mapped[bool] = mapped_column(Boolean, server_default=text("false"))
    interaction_results: Mapped[list] = mapped_column(JSONB, nullable=False, server_default="[]")
    go_deeper_choice: Mapped[str | None] = mapped_column(Text)


class Job(Base):
    __tablename__ = "job"
    __table_args__ = (Index("job_status_stage", "status", "stage"),)

    id: Mapped[uuid.UUID] = _uuid()
    user_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    # Not in Tech Spec §8.4: lets a script job resolve its topic before a lesson/syllabus row exists.
    topic_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("topic.id", ondelete="CASCADE"))
    lesson_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("lesson.id", ondelete="CASCADE"))
    stage: Mapped[str] = mapped_column(Text, nullable=False)
    status: Mapped[str] = mapped_column(Text, nullable=False)
    attempts: Mapped[int] = mapped_column(Integer, nullable=False, server_default="0")
    error: Mapped[str | None] = mapped_column(Text)
    progress: Mapped[str | None] = mapped_column(Text)  # current step of a running job (shown on /trackVideoGen)
    created_at: Mapped[datetime] = _now()
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )
    finished_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))


class LessonReport(Base):
    __tablename__ = "lesson_report"

    id: Mapped[uuid.UUID] = _uuid()
    lesson_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("lesson.id", ondelete="CASCADE"))
    user_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    reason: Mapped[str] = mapped_column(Text, nullable=False)
    note: Mapped[str | None] = mapped_column(Text)
    position_s: Mapped[float | None] = mapped_column(Float)
    created_at: Mapped[datetime] = _now()


class ProcessedEvent(Base):
    __tablename__ = "processed_event"

    event_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    user_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))


class RateBucket(Base):
    __tablename__ = "rate_bucket"

    user_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    bucket: Mapped[str] = mapped_column(Text, primary_key=True)
    tokens: Mapped[int] = mapped_column(Integer, nullable=False)
    refilled_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)


class DailyCost(Base):
    """OpenAI spend per UTC day, for the daily cost cap (Tech Spec §8.7)."""

    __tablename__ = "daily_cost"

    day: Mapped[str] = mapped_column(Text, primary_key=True)
    usd: Mapped[float] = mapped_column(Float, nullable=False, server_default="0")


class QueueMessage(Base):
    """Postgres-backed queue used when QUEUE_BACKEND=db (local, tests, single-host demo)."""

    __tablename__ = "queue_message"
    __table_args__ = (Index("queue_message_ready", "queue", "visible_at"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    queue: Mapped[str] = mapped_column(Text, nullable=False)
    body: Mapped[str] = mapped_column(Text, nullable=False)
    visible_at: Mapped[datetime] = _now()
    receive_count: Mapped[int] = mapped_column(Integer, nullable=False, server_default="0")
