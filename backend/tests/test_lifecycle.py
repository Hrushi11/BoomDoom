import pytest

from app.domain.lifecycle import (
    InvalidTransition,
    is_stuck,
    on_failure,
    should_hide,
    topic_status,
    transition,
)
from app.domain.lifecycle import (
    LessonStatus as S,
)


def test_happy_path():
    s = S.QUEUED
    for nxt in (S.SCRIPTING, S.SCRIPT_READY, S.RENDERING, S.READY):
        s = transition(s, nxt)
    assert s == S.READY


def test_cannot_skip_stages():
    with pytest.raises(InvalidTransition):
        transition(S.QUEUED, S.READY)


def test_retry_then_final():
    assert on_failure(1) == S.QUEUED
    assert on_failure(3) == S.FAILED_FINAL


def test_stuck_after_three_timeouts():
    assert not is_stuck("rendering", 900 * 3)
    assert is_stuck("rendering", 900 * 3 + 1)
    assert not is_stuck("queued", 10_000)


def test_topic_status():
    assert topic_status([S.QUEUED, S.READY]) == "ready"
    assert topic_status([S.FAILED_FINAL] * 3) == "failed"
    assert topic_status([S.FAILED_FINAL, S.FAILED_FINAL]) == "preparing"


def test_hide_after_three_reporters():
    assert not should_hide(2) and should_hide(3)
