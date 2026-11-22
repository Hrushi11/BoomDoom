"""Operator queue controls for the POC (registered only when ADMIN_PASSWORD or ADMIN_TOKEN is set).

    GET  /v1/admin/queue[?stage=render]          queued + running jobs, queued ones in pick-up order
    GET  /v1/admin/history[?limit=300]           finished renders with signed S3 links
    GET  /trackVideoGen                          the same, as a live page (sign in with ADMIN_PASSWORD)
    POST /v1/admin/jobs/{job_id}/prioritize      move one queued job to the front
    POST /v1/admin/jobs/{job_id}/cancel          cancel one job (?include_running=true to stop a running one)
    POST /v1/admin/queue/prioritize              {user_id?, topic_id?, stage?}: move matching queued jobs to the front
    POST /v1/admin/queue/cancel                  {user_id?, topic_id?, stage?, include_running?}: cancel matching jobs

Sign in once on /trackVideoGen with ADMIN_PASSWORD: a session cookie (30 days, renewed on every request) does
the rest. Scripts can send `X-Admin-Token: <ADMIN_TOKEN>` instead.
"""

from __future__ import annotations

import hashlib
import hmac
import time
import uuid

from fastapi import APIRouter, Depends, Header, Request, Response
from pydantic import BaseModel, ConfigDict
from sqlalchemy.orm import Session

from app.config import get_settings
from app.db import get_session
from app.errors import ApiError
from app.services import admin_queue

COOKIE = "boomdoom_admin"
SESSION_S = 30 * 86400  # sliding: renewed on every admin request, so an open tracker never logs out


def enabled() -> bool:
    s = get_settings()
    return bool(s.admin_password or s.admin_token)


def _secret() -> bytes:
    s = get_settings()
    return hashlib.sha256(f"boomdoom-admin|{s.admin_password}|{s.admin_token}|{s.jwt_key_id}".encode()).digest()


def _session_value(expires: int) -> str:
    sig = hmac.new(_secret(), str(expires).encode(), hashlib.sha256).hexdigest()
    return f"{expires}.{sig}"


def _session_ok(value: str) -> bool:
    try:
        expires, _ = value.split(".", 1)
        return int(expires) > time.time() and hmac.compare_digest(value, _session_value(int(expires)))
    except ValueError:
        return False


def _set_session(response: Response, request: Request) -> None:
    response.set_cookie(
        COOKIE,
        _session_value(int(time.time()) + SESSION_S),
        max_age=SESSION_S,
        httponly=True,
        secure=request.url.scheme == "https",
        samesite="strict",
        path="/",
    )


def require_admin(request: Request, response: Response, x_admin_token: str = Header(default="")) -> None:
    """A session cookie from /v1/admin/login (the tracker page), or X-Admin-Token (scripts, curl)."""
    s = get_settings()
    if s.admin_token and x_admin_token and hmac.compare_digest(x_admin_token.encode(), s.admin_token.encode()):
        return
    if _session_ok(request.cookies.get(COOKIE, "")):
        _set_session(response, request)  # slide the expiry
        return
    raise ApiError(401, "unauthorized", "Log in at /trackVideoGen")


class Login(BaseModel):
    model_config = ConfigDict(extra="forbid")
    password: str


auth_router = APIRouter(prefix="/admin", tags=["admin"])


@auth_router.post("/login")
def login(body: Login, request: Request, response: Response) -> dict:
    s = get_settings()
    expected = s.admin_password or s.admin_token
    if not expected or not hmac.compare_digest(body.password.encode(), expected.encode()):
        time.sleep(1)  # slow down guessing
        raise ApiError(401, "unauthorized", "Wrong password")
    _set_session(response, request)
    return {"ok": True}


@auth_router.post("/logout")
def logout(response: Response) -> dict:
    response.delete_cookie(COOKIE, path="/")
    return {"ok": True}


router = APIRouter(prefix="/admin", tags=["admin"], dependencies=[Depends(require_admin)])


class Selector(BaseModel):
    model_config = ConfigDict(extra="forbid")
    user_id: uuid.UUID | None = None
    topic_id: uuid.UUID | None = None
    stage: str | None = None
    include_running: bool = False


def _need_filter(sel: Selector) -> None:
    if not (sel.user_id or sel.topic_id or sel.stage):
        raise ApiError(422, "invalid_request", "Give user_id, topic_id or stage (use stage to act on a whole queue)")


@router.get("/queue")
def queue(stage: str | None = None, db: Session = Depends(get_session)) -> dict:
    return admin_queue.overview(db, stage=stage)


@router.get("/history")
def history(limit: int = 300, db: Session = Depends(get_session)) -> list[dict]:
    """Finished renders, newest first, with signed S3 links (video, thumbnail, manifest, project.json)."""
    return admin_queue.history(db, limit=min(max(limit, 1), 1000))


@router.post("/jobs/{job_id}/prioritize")
def prioritize_job(job_id: uuid.UUID, db: Session = Depends(get_session)) -> dict:
    n = admin_queue.prioritize(db, job_id=job_id)
    db.commit()
    if not n:
        raise ApiError(404, "not_found", "No queued job with that id")
    return {"prioritized": n}


@router.post("/jobs/{job_id}/cancel")
def cancel_job(job_id: uuid.UUID, include_running: bool = False, db: Session = Depends(get_session)) -> dict:
    out = admin_queue.cancel(db, job_id=job_id, include_running=include_running)
    db.commit()
    if not out["cancelled"]:
        raise ApiError(404, "not_found", "No matching job (running jobs need include_running=true)")
    return out


@router.post("/queue/prioritize")
def prioritize_many(sel: Selector, db: Session = Depends(get_session)) -> dict:
    _need_filter(sel)
    n = admin_queue.prioritize(db, user_id=sel.user_id, topic_id=sel.topic_id, stage=sel.stage)
    db.commit()
    return {"prioritized": n}


@router.post("/queue/cancel")
def cancel_many(sel: Selector, db: Session = Depends(get_session)) -> dict:
    _need_filter(sel)
    out = admin_queue.cancel(
        db, user_id=sel.user_id, topic_id=sel.topic_id, stage=sel.stage, include_running=sel.include_running
    )
    db.commit()
    return out
