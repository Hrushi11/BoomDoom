"""API tests that don't need Postgres. DB-backed tests run in CI with a Postgres service container."""

import uuid

from fastapi.testclient import TestClient

from app.auth import decode_access_token, issue_access_token
from app.main import app

client = TestClient(app)


def test_health_and_config():
    assert client.get("/healthz").json() == {"ok": True}
    assert "feature_flags" in client.get("/v1/config").json()


def test_jwt_roundtrip():
    uid = uuid.uuid4()
    assert decode_access_token(issue_access_token(uid)) == uid


def test_requires_auth():
    r = client.get("/v1/topics")
    assert r.status_code == 401
    assert r.json()["error"]["code"] == "unauthorized"


def test_events_reject_feed_data():
    """Privacy guarantee: unknown fields (e.g. reel labels) are rejected before touching the DB."""
    token = issue_access_token(uuid.uuid4())
    body = {
        "events": [
            {
                "event_id": str(uuid.uuid4()),
                "type": "lesson_started",
                "lesson_id": str(uuid.uuid4()),
                "at": "2026-11-21T10:00:00Z",
                "reel_label": "rage_bait",
            }
        ]
    }
    r = client.post("/v1/events", json=body, headers={"Authorization": f"Bearer {token}"})
    assert r.status_code == 422
