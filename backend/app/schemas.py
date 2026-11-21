"""Request/response models for API v1 (Tech Spec §8.3).

Every request model forbids unknown fields. This is part of the privacy guarantee: the backend
must never accept reel, label or feed data, so a client that tries to send extra fields gets a 422.
"""

import uuid
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

Level = Literal["beginner", "intermediate", "advanced"]
TopicType = Literal["preset", "custom", "suggested"]
TopicStatus = Literal["preparing", "ready", "failed"]


class Strict(BaseModel):
    model_config = ConfigDict(extra="forbid")


# ---- auth ----
class RegisterRequest(Strict):
    install_secret_hash: str = Field(min_length=32, max_length=128)
    app_version: str = Field(max_length=32)
    # BCP 47 tags can carry extensions (Pixel: "en-US-u-mu-celsius"); a 16-char cap blocked registration.
    locale: str = Field(max_length=35)


class RefreshRequest(Strict):
    refresh_token: str


class TokenPair(BaseModel):
    user_id: uuid.UUID
    access_token: str
    refresh_token: str


# ---- profile & topics ----
class TopicIn(Strict):
    name: str = Field(min_length=1, max_length=80)
    level: Level
    goal: str | None = Field(default=None, max_length=200)
    type: TopicType = "custom"


class LessonProfileIn(Strict):
    language: str = Field(default="en", max_length=16)
    voice_id: str = Field(default="af_heart", max_length=32)
    length_s: int = Field(default=45, ge=30, le=60)
    topics: list[TopicIn] = Field(max_length=10)


class TopicCreate(Strict):
    name: str = Field(min_length=1, max_length=80)
    level: Level
    goal: str | None = Field(default=None, max_length=200)


class TopicPatch(Strict):
    level: Level | None = None
    goal: str | None = Field(default=None, max_length=200)


class TopicOut(BaseModel):
    topic_id: uuid.UUID
    name: str
    level: Level
    goal: str | None
    status: TopicStatus
    ready_count: int = 0


class TopicStatusOut(BaseModel):
    topic_id: uuid.UUID
    status: TopicStatus


class LessonProfileOut(BaseModel):
    profile_version: int
    topics: list[TopicStatusOut]


# ---- generation ----
class GenerateRequest(Strict):
    topic_id: uuid.UUID
    count: int = Field(default=1, ge=1, le=5)


class GenerateResponse(BaseModel):
    job_ids: list[uuid.UUID]


class JobOut(BaseModel):
    status: str
    stage: str
    error: str | None = None


# ---- events (lesson events only — no feed data, ever) ----
EventType = Literal[
    "lesson_started",
    "lesson_completed",
    "lesson_skipped",
    "interaction_answered",
    "go_deeper_chosen",
    "lesson_liked",
]


class InteractionAnsweredData(Strict):
    interaction_id: str
    answer: int | str
    correct: bool | None = None


class GoDeeperData(Strict):
    option: str


class Event(Strict):
    event_id: uuid.UUID
    type: EventType
    lesson_id: uuid.UUID
    at: datetime
    data: InteractionAnsweredData | GoDeeperData | None = None

    @model_validator(mode="after")
    def _data_matches_type(self) -> "Event":
        expected = {"interaction_answered": InteractionAnsweredData, "go_deeper_chosen": GoDeeperData}.get(self.type)
        if expected is None and self.data is not None:
            raise ValueError(f"{self.type} takes no data")
        if expected is not None and not isinstance(self.data, expected):
            raise ValueError(f"{self.type} requires {expected.__name__}")
        return self


class EventBatch(Strict):
    events: list[Event] = Field(max_length=50)


class EventsAccepted(BaseModel):
    accepted: int


class ReportIn(Strict):
    reason: Literal["wrong_fact", "inappropriate", "audio_video", "other"]
    note: str | None = Field(default=None, max_length=500)
    position_s: float = Field(ge=0)


class PushTokenIn(Strict):
    fcm_token: str = Field(max_length=4096)


class ErrorBody(BaseModel):
    code: str
    message: str


class ErrorResponse(BaseModel):
    error: ErrorBody


class LinkRequest(Strict):
    cognito_id_token: str


class CognitoSignIn(Strict):
    cognito_id_token: str
    install_secret_hash: str = Field(min_length=32, max_length=128)
