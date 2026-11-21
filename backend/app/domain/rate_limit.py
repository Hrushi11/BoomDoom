"""Token-bucket maths (Tech Spec §8.3). Pure logic; persistence lives in the rate_bucket table."""

from dataclasses import dataclass
from datetime import datetime, timedelta


@dataclass(frozen=True)
class BucketSpec:
    capacity: int
    refill_period_s: float  # time to refill the full capacity

    @property
    def seconds_per_token(self) -> float:
        return self.refill_period_s / self.capacity


def take(
    spec: BucketSpec, tokens: int, refilled_at: datetime, now: datetime, cost: int = 1
) -> tuple[bool, int, datetime]:
    """Returns (allowed, tokens_left, new_refilled_at).

    The refill clock only advances by the time "spent" on whole tokens, so fractional progress
    is never lost between frequent requests.
    """
    elapsed = max(0.0, (now - refilled_at).total_seconds())
    earned = int(elapsed / spec.seconds_per_token)
    if tokens + earned >= spec.capacity:
        tokens, refilled_at = spec.capacity, now
    elif earned:
        tokens += earned
        refilled_at = refilled_at + timedelta(seconds=earned * spec.seconds_per_token)
    if tokens < cost:
        return False, tokens, refilled_at
    return True, tokens - cost, refilled_at
