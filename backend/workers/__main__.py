"""Worker entrypoint.

python -m workers script    # script-jobs (syllabus + script) and deletion-jobs   [Fargate]
python -m workers render    # render-jobs                                        [EC2 ASG]
"""

import sys

from app.config import get_settings
from app.logs import setup_logging
from workers.common import Route, Worker


def build(kind: str) -> Worker:
    if kind == "script":
        from app.db import session_scope
        from app.services.deletion import process_deletion_job
        from workers.script_worker import pipeline
        from workers.script_worker.llm import OpenAILLM

        llm = OpenAILLM()

        def deletion(job_id):
            with session_scope() as db:
                process_deletion_job(db, job_id)

        return Worker(
            "script",
            [
                Route("script-jobs", 120, lambda j: pipeline.handle(j, llm), pipeline.on_syllabus_or_script_failure),
                Route("deletion-jobs", 300, deletion),
            ],
        )
    if kind == "render":
        from workers.render_worker import pipeline
        from workers.render_worker.media import Media
        from workers.render_worker.renderers import get_renderer

        renderer, media = get_renderer(), Media()
        return Worker(
            "render",
            # A film (plan, code, two vision reviews, render, audit) takes ~8-20 min; Stickman Studio ~2-5 min.
            [
                Route(
                    "render-jobs",
                    get_settings().render_visibility_s,
                    lambda j: pipeline.handle(j, renderer, media),
                    pipeline.on_render_failure,
                )
            ],
        )
    raise SystemExit(f"unknown worker kind: {kind} (expected script|render)")


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    s = get_settings()
    setup_logging(f"{sys.argv[1]}-worker", s.log_level)
    missing = [m for m in s.validate_for_cloud() if not m.startswith("CLOUDFRONT")]
    if missing:
        raise SystemExit(f"missing configuration: {', '.join(missing)}")
    build(sys.argv[1]).run_forever()


if __name__ == "__main__":
    main()
