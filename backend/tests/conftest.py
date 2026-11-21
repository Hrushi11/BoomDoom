"""Test harness: real PostgreSQL (pgserver, embedded), real migrations, DB queue, local storage."""

import os
import shutil
import tempfile
import uuid
from pathlib import Path

import pytest

_TMP = Path(tempfile.mkdtemp(prefix="boomdoom-test-"))
os.environ.update(
    {
        "BOOMDOOM_ENV": "test",
        "RENDERER": "placeholder",
        "QUEUE_BACKEND": "db",
        "STORAGE_BACKEND": "local",
        "LOCAL_STORAGE_DIR": str(_TMP / "storage"),
        "RENDER_TMP_DIR": str(_TMP / "render"),
        "REQUESTS_PER_MINUTE": "10000",
        "CDN_BASE_URL": "https://cdn.test",
        "FCM_SERVICE_ACCOUNT_JSON": "",
        "OPENAI_API_KEY": "",
    }
)


import logging  # noqa: E402

logging.raiseExceptions = False  # pgserver logs during interpreter shutdown, after pytest closed stdout
logging.getLogger("pgserver").setLevel(logging.WARNING)


def _start_postgres() -> str:
    url = os.environ.get("TEST_DATABASE_URL")  # CI provides a Postgres service container
    if url:
        return url
    import pgserver

    srv = pgserver.get_server(_TMP / "pg", cleanup_mode="stop")
    srv.psql("create database boomdoom_test;")
    host = srv.get_uri().split("host=")[1]
    return f"postgresql+psycopg://postgres@/boomdoom_test?host={host}"


os.environ["DATABASE_URL"] = _start_postgres()

from alembic.config import Config  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402
from sqlalchemy import text  # noqa: E402

from alembic import command  # noqa: E402
from app import queues, storage  # noqa: E402
from app.config import get_settings  # noqa: E402
from app.db import Base, get_engine  # noqa: E402


@pytest.fixture(scope="session", autouse=True)
def _migrated():
    cfg = Config(str(Path(__file__).parent.parent / "alembic.ini"))
    cfg.set_main_option("script_location", str(Path(__file__).parent.parent / "alembic"))
    command.upgrade(cfg, "head")
    yield


@pytest.fixture(autouse=True)
def _clean():
    get_settings.cache_clear()
    queues.get_queue.cache_clear()
    storage.get_storage.cache_clear()
    with get_engine().begin() as conn:
        names = ", ".join(t.name for t in Base.metadata.sorted_tables)
        conn.execute(text(f"truncate {names} restart identity cascade"))
    shutil.rmtree(_TMP / "storage", ignore_errors=True)
    yield


@pytest.fixture
def client():
    from app.main import app

    with TestClient(app) as c:
        yield c


class Api:
    """Small helper around TestClient for a registered user."""

    def __init__(self, client: TestClient):
        self.c = client
        r = client.post(
            "/v1/users", json={"install_secret_hash": uuid.uuid4().hex * 2, "app_version": "1", "locale": "en-IN"}
        )
        assert r.status_code == 200, r.text
        self.tokens = r.json()
        self.user_id = self.tokens["user_id"]

    @property
    def h(self) -> dict:
        return {"Authorization": f"Bearer {self.tokens['access_token']}"}

    def get(self, path, **kw):
        return self.c.get(path, headers=self.h, **kw)

    def post(self, path, **kw):
        return self.c.post(path, headers=self.h, **kw)

    def put(self, path, **kw):
        return self.c.put(path, headers=self.h, **kw)

    def patch(self, path, **kw):
        return self.c.patch(path, headers=self.h, **kw)

    def delete(self, path, **kw):
        return self.c.delete(path, headers=self.h, **kw)

    def profile(self, *topics: dict, length_s: int = 45):
        r = self.put(
            f"/v1/users/{self.user_id}/lesson-profile",
            json={"language": "en", "voice_id": "af_heart", "length_s": length_s, "topics": list(topics)},
        )
        assert r.status_code == 200, r.text
        return r.json()


@pytest.fixture
def api(client):
    return Api(client)


@pytest.fixture
def make_api(client):
    return lambda: Api(client)
