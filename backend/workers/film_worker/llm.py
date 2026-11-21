"""OpenAI calls for the film director: JSON out, optional images in (the stills critique).

Same guards as the lesson pipeline (daily cost cap, spend recorded), but a long timeout: writing film.js is several
thousand tokens of code.
"""

from __future__ import annotations

import base64
import json
import logging
from pathlib import Path

from app.config import get_settings
from workers.common import RetryableError
from workers.script_worker.llm import Usage, record, spent_today

log = logging.getLogger(__name__)


class FilmLLM:
    def __init__(self, record_spend: bool = True) -> None:
        from openai import OpenAI

        s = get_settings()
        if not s.openai_api_key:
            raise RuntimeError("OPENAI_API_KEY is not set")
        self.client = OpenAI(api_key=s.openai_api_key, timeout=s.film_openai_timeout_s, max_retries=2)
        self.model = s.openai_model_film
        self.record_spend = record_spend  # off for local runs without a database
        self.usage = [0, 0]

    def json(self, *, prompt: str, schema: dict, system: str = "", images: list[Path] = ()) -> dict:
        import openai

        s = get_settings()
        if self.record_spend and spent_today() >= s.openai_daily_cost_cap_usd:
            raise RetryableError("daily OpenAI cost cap reached", delay_s=900, count_attempt=False)
        content: list[dict] = [{"type": "text", "text": prompt}]
        for img in images:
            b64 = base64.b64encode(Path(img).read_bytes()).decode()
            content.append(
                {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64}", "detail": "high"}}
            )
        messages = ([{"role": "system", "content": system}] if system else []) + [{"role": "user", "content": content}]
        try:
            resp = self.client.chat.completions.create(
                model=self.model, messages=messages, response_format={"type": "json_schema", "json_schema": schema}
            )
        except (
            openai.APIConnectionError,
            openai.RateLimitError,
            openai.APITimeoutError,
            openai.InternalServerError,
        ) as e:
            raise RetryableError(f"openai unavailable: {type(e).__name__}") from e
        u = resp.usage
        if u:
            self.usage = [self.usage[0] + u.prompt_tokens, self.usage[1] + u.completion_tokens]
            if self.record_spend:
                record(Usage(self.model, u.prompt_tokens, u.completion_tokens))
        return json.loads(resp.choices[0].message.content)
