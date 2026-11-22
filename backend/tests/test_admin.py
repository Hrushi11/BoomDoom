"""Operator queue controls (/v1/admin): auth, pick-up order, prioritise, cancel queued and running jobs."""

import uuid

import pytest
from fastapi.testclient import TestClient

from app import queues
from app.config import get_settings
from app.db import session_scope
from app.models import Job
from workers.common import JobCancelled, Route, Worker, check_cancelled

TOKEN = "test-admin-token"
H = {"X-Admin-Token": TOKEN}


@pytest.fixture
def admin(monkeypatch):
    monkeypatch.setattr(get_settings(), "admin_token", TOKEN)
    from app.main import create_app

    with TestClient(create_app()) as c:
        yield c


def _topic(name):
    return {"name": name, "level": "beginner", "type": "custom"}


def test_admin_needs_a_token(admin, monkeypatch):
    assert admin.get("/v1/admin/queue").status_code == 401
    assert admin.get("/v1/admin/queue", headers={"X-Admin-Token": "wrong"}).status_code == 401
    assert admin.get("/v1/admin/queue", headers=H).status_code == 200

    monkeypatch.setattr(get_settings(), "admin_token", "")
    from app.main import create_app

    with TestClient(create_app()) as plain:  # no ADMIN_TOKEN: the routes do not exist at all
        assert plain.get("/v1/admin/queue", headers=H).status_code == 404


def test_prioritize_moves_a_user_to_the_front(admin, make_api):
    a, b = make_api(), make_api()
    a.profile(_topic("Topic Alpha"))
    b.profile(_topic("Topic Beta"))
    jobs = admin.get("/v1/admin/queue", headers=H).json()["jobs"]
    assert [j["user_id"] for j in jobs] == [a.user_id, b.user_id]  # first in, first out

    r = admin.post("/v1/admin/queue/prioritize", headers=H, json={"user_id": b.user_id})
    assert r.json() == {"prioritized": 1}
    jobs = admin.get("/v1/admin/queue", headers=H).json()["jobs"]
    assert [(j["user_id"], j["position"]) for j in jobs] == [(b.user_id, 0), (a.user_id, 1)]
    msg = queues.get_queue().receive("script-jobs", 30, wait_s=0)[0]  # the worker really takes B's job next
    assert str(msg.job_id) == jobs[0]["job_id"]


def test_cancel_queued_jobs_removes_them(admin, make_api):
    a, b = make_api(), make_api()
    a.profile(_topic("Topic Alpha"))
    b.profile(_topic("Topic Beta"))
    assert admin.post("/v1/admin/queue/cancel", headers=H, json={}).status_code == 422  # no filter: refuse
    r = admin.post("/v1/admin/queue/cancel", headers=H, json={"user_id": a.user_id})
    assert r.json() == {"cancelled": 1, "running_stopping": 0}
    jobs = admin.get("/v1/admin/queue", headers=H).json()["jobs"]
    assert [j["user_id"] for j in jobs] == [b.user_id]
    msgs = queues.get_queue().receive("script-jobs", 30, wait_s=0, max_messages=10)
    assert [str(m.job_id) for m in msgs] == [jobs[0]["job_id"]]


def test_running_job_cancelled_midway_stays_cancelled(admin, make_api):
    a = make_api()
    a.profile(_topic("Topic Alpha"))
    job_id = uuid.UUID(admin.get("/v1/admin/queue", headers=H).json()["jobs"][0]["job_id"])

    def handler(jid):
        # Running now: an operator cancels it, and the handler hits its next checkpoint.
        r = admin.post(f"/v1/admin/jobs/{jid}/cancel", headers=H)
        assert r.status_code == 404  # running jobs need include_running
        r = admin.post(f"/v1/admin/jobs/{jid}/cancel?include_running=true", headers=H)
        assert r.json() == {"cancelled": 1, "running_stopping": 1}
        with pytest.raises(JobCancelled):
            check_cancelled(jid)
        check_cancelled(jid)  # the real handler stops here

    w = Worker("test", [Route("script-jobs", 30, handler)])
    w.poll_once(w.routes[0])
    with session_scope() as db:
        assert db.get(Job, job_id).status == "cancelled"
    assert queues.get_queue().receive("script-jobs", 30, wait_s=0) == []


@pytest.fixture
def tracker(monkeypatch):
    monkeypatch.setattr(get_settings(), "admin_token", "")
    monkeypatch.setattr(get_settings(), "admin_password", "open sesame")
    from app.main import create_app

    with TestClient(create_app(), base_url="https://testserver") as c:
        yield c


def test_tracker_password_login_keeps_a_sliding_session(tracker):
    page = tracker.get("/trackVideoGen")
    assert page.status_code == 200 and "video generation" in page.text
    assert tracker.get("/v1/admin/queue").status_code == 401  # not signed in
    assert tracker.post("/v1/admin/login", json={"password": "nope"}).status_code == 401
    r = tracker.post("/v1/admin/login", json={"password": "open sesame"})
    assert r.status_code == 200
    cookie = r.headers["set-cookie"]
    assert "HttpOnly" in cookie and "Secure" in cookie and "samesite=strict" in cookie.lower()
    q = tracker.get("/v1/admin/queue")  # the cookie alone is enough, and it is renewed
    assert q.status_code == 200 and "boomdoom_admin=" in q.headers.get("set-cookie", "")
    tracker.post("/v1/admin/logout")
    assert tracker.get("/v1/admin/queue").status_code == 401


def test_history_lists_finished_films_with_s3_links(admin, make_api):
    from tests.fakes import drain

    make_api().profile(_topic("Topic Alpha"))
    drain()
    rows = admin.get("/v1/admin/history", headers=H).json()
    assert rows and all(r["status"] == "done" for r in rows)
    links = rows[0]["links"]
    assert links["s3_key"].startswith("lessons/") and links["s3_key"].endswith("lesson_720p.mp4")
    assert all(links[k] for k in ("video", "thumb", "manifest", "project"))
