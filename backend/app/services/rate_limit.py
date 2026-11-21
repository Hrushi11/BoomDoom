"""Postgres token buckets per user (Tech Spec §8.3). Redis is not needed at MVP scale."""

import uuid
from datetime import UTC, datetime

from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.orm import Session

from app.config import get_settings
from app.domain.rate_limit import BucketSpec, take
from app.errors import ApiError
from app.models import RateBucket


def specs() -> dict[str, BucketSpec]:
    s = get_settings()
    return {
        "general": BucketSpec(capacity=s.requests_per_minute, refill_period_s=60),
        "topics": BucketSpec(capacity=s.free_topics_per_day, refill_period_s=86_400),
        "generate": BucketSpec(capacity=s.free_lessons_per_day, refill_period_s=86_400),
    }


def consume(db: Session, user_id: uuid.UUID, bucket: str, cost: int = 1) -> None:
    spec = specs()[bucket]
    now = datetime.now(UTC)
    db.execute(
        insert(RateBucket)
        .values(user_id=user_id, bucket=bucket, tokens=spec.capacity, refilled_at=now)
        .on_conflict_do_nothing()
    )
    row = db.scalar(
        select(RateBucket).where(RateBucket.user_id == user_id, RateBucket.bucket == bucket).with_for_update()
    )
    assert row is not None
    ok, row.tokens, row.refilled_at = take(spec, row.tokens, row.refilled_at, now, cost)
    if not ok:
        db.commit()
        raise ApiError(429, "rate_limited", f"Too many requests ({bucket}); try again later")
