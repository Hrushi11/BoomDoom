"""Render worker: polls `render-jobs`, renders with the Stickman Studio fork, uploads to S3 (§8.8).

Runs inside the `boomdoom-render` image (see Dockerfile). Run:  python -m workers.render_worker.main
"""

import logging
import time

log = logging.getLogger("render-worker")


def render_params(lesson, narration_text: str, target_s: int, voice_id: str) -> dict:
    return {
        "topic": lesson.concept,
        "script": narration_text,
        "script_mode": "use_as_is",
        "duration": target_s,
        "voice_id": voice_id,
        "format": "9:16",
        "cis_visual_style": lesson.visual_style,  # B1 | B2 | B3, fixed per topic
        "cis_accent_color": lesson.accent_color,  # fixed per topic
        "cis_illustration_mode": "pil",  # no diffusion backgrounds on CPU workers
    }


def run_forever() -> None:  # pragma: no cover
    logging.basicConfig(level=logging.INFO)
    log.info("render worker started")
    # TODO(D): SQS long-poll (extend visibility while rendering) -> CISPipeline(...).generate_video(params)
    #          -> pause_times.segment_end_times -> ffmpeg compress + thumb -> S3 upload -> manifest -> ready.
    while True:
        time.sleep(20)


if __name__ == "__main__":
    run_forever()
