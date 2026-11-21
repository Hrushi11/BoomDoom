"""Scheduled jobs entrypoint: `python -m app.cron <refill|sweep-stuck>`."""

import json
import sys

from app.config import get_settings
from app.logs import setup_logging
from app.services import sweeps

JOBS = {"refill": sweeps.refill_all, "sweep-stuck": sweeps.sweep_stuck}
SCHEDULE_S = {"sweep-stuck": 300, "refill": 600}


def loop() -> None:
    """Single-host scheduler (POC): run each job on its interval; a failed run never stops the loop."""
    import logging
    import time

    log = logging.getLogger("cron")
    next_run = dict.fromkeys(JOBS, 0.0)
    while True:
        now = time.monotonic()
        for name, fn in JOBS.items():
            if now >= next_run[name]:
                try:
                    log.info(f"{name}: {fn()}")
                except Exception:
                    log.exception(f"{name} failed")
                next_run[name] = now + SCHEDULE_S[name]
        time.sleep(5)


def main(argv: list[str]) -> int:
    if len(argv) != 1 or argv[0] not in (*JOBS, "loop"):
        print(f"usage: python -m app.cron {{{'|'.join(JOBS)}|loop}}", file=sys.stderr)
        return 2
    s = get_settings()
    setup_logging(f"cron-{argv[0]}", s.log_level)
    if argv[0] == "loop":
        loop()
    result = JOBS[argv[0]]()
    print(json.dumps({"job": argv[0], "result": result}, default=str))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
