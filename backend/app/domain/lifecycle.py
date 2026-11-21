"""Lesson/job lifecycle (Tech Spec §8.5).

queued -> scripting -> script_ready -> rendering -> ready
any working stage -> failed (attempts < 3 -> re-queued) | failed_final
ready -> hidden (>= 3 wrong_fact reports from distinct users)
"""

from enum import StrEnum

MAX_ATTEMPTS = 3
HIDE_AFTER_DISTINCT_REPORTS = 3
VISIBILITY_TIMEOUT_S = {"scripting": 120, "rendering": 900}


class LessonStatus(StrEnum):
    QUEUED = "queued"
    SCRIPTING = "scripting"
    SCRIPT_READY = "script_ready"
    RENDERING = "rendering"
    READY = "ready"
    FAILED = "failed"
    FAILED_FINAL = "failed_final"
    HIDDEN = "hidden"


_ALLOWED: dict[LessonStatus, set[LessonStatus]] = {
    LessonStatus.QUEUED: {LessonStatus.SCRIPTING, LessonStatus.FAILED},
    LessonStatus.SCRIPTING: {LessonStatus.SCRIPT_READY, LessonStatus.FAILED, LessonStatus.QUEUED},
    LessonStatus.SCRIPT_READY: {LessonStatus.RENDERING, LessonStatus.FAILED},
    LessonStatus.RENDERING: {LessonStatus.READY, LessonStatus.FAILED, LessonStatus.QUEUED},
    LessonStatus.FAILED: {LessonStatus.QUEUED, LessonStatus.FAILED_FINAL},
    LessonStatus.READY: {LessonStatus.HIDDEN},
    LessonStatus.HIDDEN: {LessonStatus.READY},
    LessonStatus.FAILED_FINAL: set(),
}


class InvalidTransition(ValueError):
    pass


def transition(current: LessonStatus, target: LessonStatus) -> LessonStatus:
    if target not in _ALLOWED[current]:
        raise InvalidTransition(f"{current} -> {target}")
    return target


def on_failure(attempts: int) -> LessonStatus:
    """After a failure, retry with backoff while attempts remain."""
    return LessonStatus.QUEUED if attempts < MAX_ATTEMPTS else LessonStatus.FAILED_FINAL


def is_stuck(stage: str, seconds_in_stage: float) -> bool:
    timeout = VISIBILITY_TIMEOUT_S.get(stage)
    return timeout is not None and seconds_in_stage > 3 * timeout


def topic_status(lesson_statuses: list[LessonStatus]) -> str:
    """Topic is ready once any lesson is ready; failed if the first 3 all ended failed_final."""
    if LessonStatus.READY in lesson_statuses:
        return "ready"
    first = lesson_statuses[:3]
    if len(first) == 3 and all(s == LessonStatus.FAILED_FINAL for s in first):
        return "failed"
    return "preparing"


def should_hide(distinct_wrong_fact_reporters: int) -> bool:
    return distinct_wrong_fact_reporters >= HIDE_AFTER_DISTINCT_REPORTS
