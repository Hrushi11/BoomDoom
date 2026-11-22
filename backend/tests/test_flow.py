"""End-to-end: profile -> syllabus -> scripts -> renders -> feed -> events -> refill."""

import uuid

from sqlalchemy import func, select, text

from app.db import get_engine, session_scope
from app.models import AppUser, Job, Lesson, Topic
from tests.fakes import FakeLLM, drain, queued, worker

PSYCH = {"name": "Psychology", "level": "beginner", "type": "preset"}


def _count(model, *where):
    with session_scope() as db:
        return db.scalar(select(func.count()).select_from(model).where(*where))


def test_profile_to_feed_and_refill(api):
    out = api.profile(PSYCH)
    topic_id = out["topics"][0]["topic_id"]
    assert out["topics"][0]["status"] == "preparing"
    assert queued("script-jobs") == 1  # the syllabus job

    drain()
    feed = api.get("/v1/feed", params={"topic_id": topic_id}).json()
    assert len(feed) == 5
    lesson = feed[0]
    assert lesson["topic"] == {"id": topic_id, "name": "Psychology"}
    assert lesson["video_url"].startswith("https://cdn.test/lessons/")
    assert [s["id"] for s in lesson["segments"]] == ["s1", "s2", "s3"]
    assert lesson["interactions"][0]["pause_at_s"] == round(45 / 3 + 0.15, 3)  # end of s1 + 0.15 s
    topic = api.get("/v1/topics").json()[0]
    assert (topic["status"], topic["ready_count"]) == ("ready", 5)

    # Watch 3 -> stock 2 < 3 -> refill back to 5
    events = [
        {
            "event_id": str(uuid.uuid4()),
            "type": "lesson_completed",
            "lesson_id": lesson["lesson_id"],
            "at": "2026-11-21T10:00:00Z",
        }
        for lesson in feed[:3]
    ]
    assert api.post("/v1/events", json={"events": events}).json() == {"accepted": 3}
    drain()
    assert len(api.get("/v1/feed").json()) == 5
    assert _count(Lesson) == 8


def test_shared_lessons_are_reused_across_users(make_api):
    a, b = make_api(), make_api()
    a.profile(PSYCH)
    drain()
    scripts_before = _count(Job, Job.stage == "script")
    b.profile(PSYCH)
    drain()
    assert _count(Job, Job.stage == "script") == scripts_before  # nothing new generated
    assert {x["lesson_id"] for x in a.get("/v1/feed").json()} == {x["lesson_id"] for x in b.get("/v1/feed").json()}
    # Reused lessons are already rendered, so no render job ever flips B's topic: it must be ready at once.
    topic = b.get("/v1/topics").json()[0]
    assert (topic["status"], topic["ready_count"]) == ("ready", 5)


def test_goal_or_custom_topic_is_personal(make_api):
    a, b = make_api(), make_api()
    a.profile({**PSYCH, "goal": "understand my procrastination"})
    b.post("/v1/topics", json={"name": "Stoicism", "level": "beginner"})
    drain()
    with session_scope() as db:
        owners = set(db.scalars(select(Lesson.owner_user_id)))
    assert owners == {uuid.UUID(a.user_id), uuid.UUID(b.user_id)}


def test_unsafe_custom_topic_fails_without_lessons(api):
    r = api.post("/v1/topics", json={"name": "Something dodgy", "level": "beginner"})
    drain(worker(llm=FakeLLM(allow=False)))
    assert api.get("/v1/topics").json()[0]["status"] == "failed"
    assert _count(Lesson) == 0
    assert r.json()["status"] == "preparing"


def test_quality_failures_fail_topic_then_retry_works(api):
    api.profile(PSYCH)
    drain(worker(llm=FakeLLM(qa_pass=False)))
    topic = api.get("/v1/topics").json()[0]
    assert topic["status"] == "failed"
    assert api.post(f"/v1/topics/{topic['topic_id']}/retry").json()["status"] == "preparing"
    drain()
    assert api.get("/v1/topics").json()[0]["status"] == "ready"


def test_openai_outage_is_retried(api):
    api.profile(PSYCH)
    llm = FakeLLM(fail_first=2)
    drain(worker(llm=llm))
    assert len(api.get("/v1/feed").json()) == 5
    with session_scope() as db:
        assert db.scalar(select(Job.attempts).where(Job.stage == "syllabus")) == 3


def test_account_deletion_removes_everything(api, tmp_path):
    api.post("/v1/topics", json={"name": "Stoicism", "level": "beginner"})
    drain()
    from pathlib import Path

    from app.config import get_settings

    store = Path(get_settings().local_storage_dir)
    assert any(store.rglob("*.mp4"))
    deletion_id = api.delete(f"/v1/users/{api.user_id}").json()["deletion_id"]
    assert api.get("/v1/topics").status_code == 401  # deleted users are locked out at once
    drain()
    assert api.get(f"/v1/deletions/{deletion_id}").json() == {"status": "done"}
    assert _count(AppUser) == 0 and _count(Topic) == 0 and _count(Lesson) == 0
    assert not any(store.rglob("*.mp4"))


def test_events_reject_feed_data_and_ignore_unknown_lessons(api):
    base = {
        "event_id": str(uuid.uuid4()),
        "type": "lesson_started",
        "lesson_id": str(uuid.uuid4()),
        "at": "2026-11-21T10:00:00Z",
    }
    assert api.post("/v1/events", json={"events": [{**base, "reel_label": "rage_bait"}]}).status_code == 422
    assert api.post("/v1/events", json={"events": [base]}).json() == {"accepted": 1}  # bundled lesson: ignored
    assert api.post("/v1/events", json={"events": [base]}).json() == {"accepted": 1}  # replay: idempotent


def test_worker_shutdown_hands_job_back(api):
    from workers.common import Route, Worker, WorkerShutdown

    api.profile(PSYCH)

    def interrupted(job_id):
        raise WorkerShutdown()

    w = Worker("t", [Route("script-jobs", 120, interrupted)])
    w.poll_once(w.routes[0])
    with session_scope() as db:
        job = db.scalar(select(Job))
        assert (job.status, job.attempts) == ("queued", 0)
    assert queued("script-jobs") == 1


def test_stuck_job_is_requeued(api):
    from app.services import sweeps

    api.profile(PSYCH)
    with get_engine().begin() as conn:
        conn.execute(text("delete from queue_message"))
        conn.execute(text("update job set status='running', attempts=1, updated_at=now() - interval '1 hour'"))
    assert sweeps.sweep_stuck()["requeued"] == 1
    drain()
    assert len(api.get("/v1/feed").json()) == 5


def test_film_lessons_do_not_reuse_other_engines(make_api, monkeypatch):
    # Shared lessons are reused only within one render engine: switching RENDERER must generate new lessons.
    from app.config import get_settings

    a, b = make_api(), make_api()
    monkeypatch.setattr(get_settings(), "renderer", "stickman")
    a.profile(PSYCH)
    drain()
    scripts_before = _count(Job, Job.stage == "script")
    monkeypatch.setattr(get_settings(), "renderer", "film")
    b.profile(PSYCH)
    drain()
    assert _count(Job, Job.stage == "script") > scripts_before  # new lessons generated for the film engine
    assert not {x["lesson_id"] for x in a.get("/v1/feed").json()} & {x["lesson_id"] for x in b.get("/v1/feed").json()}
