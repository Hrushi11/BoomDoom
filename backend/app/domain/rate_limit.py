"""Per-user token buckets (Tech Spec §8.3). Pure logic; persistence lives in the rate_bucket table."""

from dataclasses import dataclass
from datetime import datetime


@dataclass(frozen=True)
class BucketSpec:
    capacity: int
    refill_period_s: float  # time to refill the full capacity


BUCKETS = {
    "general": BucketSpec(capacity=30, refill_period_s=60),
    "topics": BucketSpec(capacity=10, refill_period_s=86_400),
    "generate": BucketSpec(capacity=20, refill_period_s=86_400),  # free plan
}


def take(spec: BucketSpec, tokens: int, refilled_at: datetime, now: datetime, cost: int = 1) -> tuple[bool, int]:
    """Returns (allowed, remaining_tokens) after continuous refill."""
    elapsed = max(0.0, (now - refilled_at).total_seconds())
    refilled = min(spec.capacity, tokens + int(elapsed * spec.capacity / spec.refill_period_s))
    if refilled < cost:
        return False, refilled
    return True, refilled - cost
