"""OpenAI access with cost accounting, a daily cost cap and a circuit breaker (Tech Spec §8.7)."""

from __future__ import annotations

import json
import logging
import time
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Protocol

from sqlalchemy.dialects.postgresql import insert

from app import metrics
from app.config import DEFAULT_PRICES, get_settings
from app.db import session_scope
from app.models import DailyCost
from workers.common import RetryableError

log = logging.getLogger(__name__)


@dataclass
class Usage:
    model: str
    input_tokens: int
    output_tokens: int

    @property
    def usd(self) -> float:
        prices = json.loads(get_settings().openai_prices_json) if get_settings().openai_prices_json else DEFAULT_PRICES
        p_in, p_out = prices.get(self.model, [0.0, 0.0])
        return (self.input_tokens * p_in + self.output_tokens * p_out) / 1_000_000


class LLM(Protocol):
    def json(self, *, model: str, prompt: str, schema: dict, temperature: float) -> tuple[dict, Usage]: ...


def today() -> str:
    return datetime.now(UTC).strftime("%Y-%m-%d")


def spent_today() -> float:
    with session_scope() as db:
        row = db.get(DailyCost, today())
        return row.usd if row else 0.0


def record(usage: Usage) -> None:
    with session_scope() as db:
        db.execute(
            insert(DailyCost)
            .values(day=today(), usd=usage.usd)
            .on_conflict_do_update(index_elements=[DailyCost.day], set_={"usd": DailyCost.usd + usage.usd})
        )
    metrics.emit("openai_cost_usd", usage.usd, "None", model=usage.model)
    metrics.emit("openai_tokens", usage.input_tokens + usage.output_tokens, model=usage.model)


class CircuitBreaker:
    """After `threshold` consecutive failures, refuse calls for `cooldown_s` (queue effectively paused)."""

    def __init__(self, threshold: int = 10, cooldown_s: int = 300):
        self.threshold, self.cooldown_s = threshold, cooldown_s
        self.failures, self.open_until = 0, 0.0

    def check(self) -> None:
        if time.monotonic() < self.open_until:
            raise RetryableError("openai circuit open", delay_s=self.cooldown_s, count_attempt=False)

    def success(self) -> None:
        self.failures = 0

    def failure(self) -> None:
        self.failures += 1
        if self.failures >= self.threshold:
            self.open_until = time.monotonic() + self.cooldown_s
            self.failures = 0
            log.error("openai circuit opened")
            metrics.emit("openai_circuit_open", 1)


class OpenAILLM:
    def __init__(self) -> None:
        from openai import OpenAI

        s = get_settings()
        if not s.openai_api_key:
            raise RuntimeError("OPENAI_API_KEY is not set")
        # SDK retries 429/5xx with exponential backoff + jitter.
        self.client = OpenAI(api_key=s.openai_api_key, timeout=s.openai_timeout_s, max_retries=3)
        self.breaker = CircuitBreaker()

    def json(self, *, model: str, prompt: str, schema: dict, temperature: float) -> tuple[dict, Usage]:
        import openai

        s = get_settings()
        if spent_today() >= s.openai_daily_cost_cap_usd:
            metrics.emit("openai_cost_cap_hit", 1)
            raise RetryableError("daily OpenAI cost cap reached", delay_s=900, count_attempt=False)
        self.breaker.check()
        try:
            # The gpt-6 models only accept the default temperature, so the per-step values from Tech Spec §8.7
            # are not sent (the parameter stays in the interface in case a future model supports it again).
            resp = self.client.chat.completions.create(
                model=model,
                response_format={"type": "json_schema", "json_schema": schema},
                messages=[{"role": "user", "content": prompt}],
            )
        except (
            openai.APIConnectionError,
            openai.RateLimitError,
            openai.APITimeoutError,
            openai.InternalServerError,
        ) as e:
            self.breaker.failure()
            raise RetryableError(f"openai unavailable: {type(e).__name__}") from e
        self.breaker.success()
        usage = Usage(
            model, resp.usage.prompt_tokens if resp.usage else 0, resp.usage.completion_tokens if resp.usage else 0
        )
        record(usage)
        choice = resp.choices[0]
        if getattr(choice.message, "refusal", None):
            raise ValueError("model refused")
        return json.loads(choice.message.content or "{}"), usage
