"""Operator queue controls for the POC (registered only when ADMIN_TOKEN is set).

    GET  /v1/admin/queue[?stage=render]          queued + running jobs, queued ones in pick-up order
    POST /v1/admin/jobs/{job_id}/prioritize      move one queued job to the front
    POST /v1/admin/jobs/{job_id}/cancel          cancel one job (?include_running=true to stop a running one)
    POST /v1/admin/queue/prioritize              {user_id?, topic_id?, stage?}: move matching queued jobs to the front
    POST /v1/admin/queue/cancel                  {user_id?, topic_id?, stage?, include_running?}: cancel matching jobs

Every call needs the header `X-Admin-Token: <ADMIN_TOKEN>`.
"""

from __future__ import annotations

import hmac
import uuid

from fastapi import APIRouter, Depends, Header
from pydantic import BaseModel, ConfigDict
from sqlalchemy.orm import Session

from app.config import get_settings
from app.db import get_session
from app.errors import ApiError
from app.services import admin_queue


def require_admin(x_admin_token: str = Header(default="")) -> None:
    expected = get_settings().admin_token
    if not expected or not hmac.compare_digest(x_admin_token.encode(), expected.encode()):
        raise ApiError(401, "unauthorized", "Admin token required")


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
