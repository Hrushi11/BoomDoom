import uuid
from datetime import UTC, datetime, timedelta

from fastapi import APIRouter, Depends, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app import auth, queues
from app.config import get_settings
from app.db import get_session
from app.errors import ApiError
from app.models import AppUser, Job, RefreshToken
from app.schemas import PushTokenIn, RefreshRequest, RegisterRequest, TokenPair

router = APIRouter(tags=["users"])


def _issue(db: Session, user_id: uuid.UUID) -> TokenPair:
    token, token_hash = auth.new_refresh_token()
    expires = datetime.now(UTC) + timedelta(days=get_settings().refresh_token_ttl_days)
    db.add(RefreshToken(token_hash=token_hash, user_id=user_id, expires_at=expires))
    db.commit()
    return TokenPair(user_id=user_id, access_token=auth.issue_access_token(user_id), refresh_token=token)


@router.post("/users", response_model=TokenPair)
def register(body: RegisterRequest, db: Session = Depends(get_session)) -> TokenPair:
    user = db.scalar(select(AppUser).where(AppUser.install_secret_hash == body.install_secret_hash))
    if user is None:  # first launch; re-registering with the same secret recovers the account
        user = AppUser(install_secret_hash=body.install_secret_hash)
        db.add(user)
        db.flush()
    if user.deleted_at is not None:
        raise ApiError(410, "deleted", "This account was deleted")
    return _issue(db, user.id)


@router.post("/auth/refresh", response_model=TokenPair)
def refresh(body: RefreshRequest, db: Session = Depends(get_session)) -> TokenPair:
    row = db.get(RefreshToken, auth.hash_token(body.refresh_token))
    if row is None or row.revoked or row.expires_at < datetime.now(UTC):
        raise ApiError(401, "invalid_refresh", "Refresh token is invalid; re-register with the install secret")
    row.revoked = True  # rotation
    return _issue(db, row.user_id)


@router.put("/users/{user_id}/push-token", status_code=status.HTTP_204_NO_CONTENT)
def put_push_token(
    user_id: uuid.UUID,
    body: PushTokenIn,
    me: uuid.UUID = Depends(auth.current_user_id),
    db: Session = Depends(get_session),
) -> None:
    auth.require_self(user_id, me)
    user = db.get(AppUser, me)
    assert user is not None
    user.fcm_token = body.fcm_token
    db.commit()


@router.delete("/users/{user_id}", status_code=status.HTTP_202_ACCEPTED)
def delete_user(
    user_id: uuid.UUID, me: uuid.UUID = Depends(auth.current_user_id), db: Session = Depends(get_session)
) -> dict:
    """§8.10: mark deleted, revoke tokens, enqueue the deletion job."""
    auth.require_self(user_id, me)
    user = db.get(AppUser, me)
    assert user is not None
    user.deleted_at = datetime.now(UTC)
    for t in db.scalars(select(RefreshToken).where(RefreshToken.user_id == me)):
        t.revoked = True
    job = Job(user_id=me, stage="deletion", status="queued")
    db.add(job)
    db.commit()
    queues.enqueue("deletion-jobs", job.id)
    return {"deletion_id": str(job.id)}


@router.get("/deletions/{deletion_id}")
def deletion_status(
    deletion_id: uuid.UUID, me: uuid.UUID = Depends(auth.current_user_id), db: Session = Depends(get_session)
) -> dict:
    job = db.get(Job, deletion_id)
    if job is None or job.user_id != me or job.stage != "deletion":
        raise ApiError(404, "not_found", "Unknown deletion")
    return {"status": job.status}


@router.post("/users/{user_id}/link")
def link_account(user_id: uuid.UUID, me: uuid.UUID = Depends(auth.current_user_id)) -> dict:
    """Optional login via Cognito — P1."""
    auth.require_self(user_id, me)
    raise ApiError(501, "not_implemented", "Account linking ships in P1")
