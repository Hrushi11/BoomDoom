"""Presigned S3 URLs must use the bucket's regional host (regression: HTTP 400 in us-west-2)."""

from urllib.parse import parse_qs, urlparse

from app import cdn
from app.config import get_settings


def test_presigned_url_uses_regional_endpoint(monkeypatch):
    monkeypatch.setenv("STORAGE_BACKEND", "s3")
    monkeypatch.setenv("AWS_REGION", "us-west-2")
    monkeypatch.setenv("LESSONS_BUCKET", "boomdoom-test-bucket")
    monkeypatch.setenv("AWS_ACCESS_KEY_ID", "AKIATEST")
    monkeypatch.setenv("AWS_SECRET_ACCESS_KEY", "secret")
    get_settings.cache_clear()
    cdn._s3.cache_clear()
    try:
        url = urlparse(cdn.sign_url("lessons/abc/v1/lesson_720p.mp4"))
        scope = parse_qs(url.query)["X-Amz-Credential"][0].split("/")
        assert url.netloc == "boomdoom-test-bucket.s3.us-west-2.amazonaws.com"
        assert scope[2] == "us-west-2"
        assert url.path == "/lessons/abc/v1/lesson_720p.mp4"
    finally:
        get_settings.cache_clear()
        cdn._s3.cache_clear()


def test_models_manifest_endpoint(client):
    import json

    from app.storage import get_storage

    assert client.get("/v1/models").json() == {"version": None, "files": []}
    get_storage().put_bytes(
        "models/manifest.json",
        json.dumps(
            {
                "version": "2026.11.21",
                "total_bytes": 3,
                "files": [{"name": "vision.json", "key": "models/2026.11.21/vision.json", "sha256": "ab", "size": 3}],
            }
        ).encode(),
        "application/json",
    )
    body = client.get("/v1/models").json()
    assert body["version"] == "2026.11.21"
    assert body["files"][0]["url"].endswith("/models/2026.11.21/vision.json")
    assert "key" not in body["files"][0]
