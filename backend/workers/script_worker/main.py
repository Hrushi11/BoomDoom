"""Script worker: polls `script-jobs`, builds syllabus + script + QA via OpenAI (Tech Spec §8.7).

Run:  python -m workers.script_worker.main
"""

import json
import logging
import time

from app.config import get_settings
from workers.script_worker.prompts import script_prompt
from workers.script_worker.schema import LESSON_SCRIPT_SCHEMA, WORDS_PER_SECOND, validate_script

log = logging.getLogger("script-worker")
TEMPERATURE = {"syllabus": 0.4, "script": 0.8, "qa": 0.0}


def generate_script(
    client,
    *,
    topic: str,
    level: str,
    goal: str | None,
    language: str,
    fmt: str,
    concept: dict,
    covered: list[str],
    weak: list[str],
    length_s: int,
) -> tuple[dict, dict]:
    """Returns (script, usage). Raises ValueError if the script fails validation after retries."""
    s = get_settings()
    prompt = script_prompt(
        topic=topic,
        level=level,
        goal=goal,
        language=language,
        fmt=fmt,
        title=concept["title"],
        objective=concept["objective"],
        covered=covered,
        weak=weak,
        words=length_s * WORDS_PER_SECOND,
    )
    last_issues: list[str] = []
    for _ in range(3):  # first try + 2 regenerations
        resp = client.chat.completions.create(
            model=s.openai_model_script,
            temperature=TEMPERATURE["script"],
            response_format={"type": "json_schema", "json_schema": LESSON_SCRIPT_SCHEMA},
            messages=[{"role": "user", "content": prompt}],
            timeout=30,
        )
        script = json.loads(resp.choices[0].message.content)
        last_issues = validate_script(script, length_s)
        if not last_issues:
            usage = {"in": resp.usage.prompt_tokens, "out": resp.usage.completion_tokens}
            return script, usage
        log.warning("script failed validation: %s", last_issues)
    raise ValueError(f"script failed validation: {last_issues}")


def run_forever() -> None:  # pragma: no cover - wiring, exercised in the demo env
    logging.basicConfig(level=logging.INFO, format='{"lvl":"%(levelname)s","msg":"%(message)s"}')
    log.info("script worker started (model=%s)", get_settings().openai_model_script)
    # TODO(D): SQS long-poll -> load job -> syllabus (reuse shared) -> generate_script -> QA (gpt-6-luna)
    #          -> store script + token usage -> transition script_ready -> enqueue render-jobs.
    while True:
        time.sleep(20)


if __name__ == "__main__":
    run_forever()
