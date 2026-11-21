import uuid
from datetime import UTC, datetime, timedelta

from fastapi import APIRouter, Depends, status
from sqlalchemy import func, select, update
from sqlalchemy.orm import Session

from app import auth, queues
from app.api.deps import active_user
from app.config import get_settings
from app.db import get_session
from app.errors import ApiError
from app.models import AppUser, Job, Lesson, RefreshToken, Syllabus, SyllabusProgress, Topic, UserLesson
from app.schemas import CognitoSignIn, LinkRequest, PushTokenIn, RefreshRequest, RegisterRequest, TokenPair

router = APIRouter(tags=["users"])


def _issue(db: Session, user_id: uuid.UUID) -> TokenPair:
    token, token_hash = auth.new_refresh_token()
    expires = datetime.now(UTC) + timedelta(days=get_settings().refresh_token_ttl_days)
    db.add(RefreshToken(token_hash=token_hash, user_id=user_id, expires_at=expires))
    db.commit()
    return TokenPair(user_id=user_id, access_token=auth.issue_access_token(user_id), refresh_token=token)


@router.post("/users", response_model=TokenPair)
def register(body: RegisterRequest, db: Session = Depends(get_session)) -> TokenPair:
    """First launch creates the user; re-registering with the same install secret recovers it."""
    user = db.scalar(select(AppUser).where(AppUser.install_secret_hash == body.install_secret_hash))
    if user is None:
        user = AppUser(install_secret_hash=body.install_secret_hash)
        db.add(user)
        db.flush()
    if user.deleted_at is not None:
        raise ApiError(410, "deleted", "This account was deleted")
    return _issue(db, user.id)


@router.post("/auth/refresh", response_model=TokenPair)
def refresh(body: RefreshRequest, db: Session = Depends(get_session)) -> TokenPair:
    row = db.scalar(
        select(RefreshToken).where(RefreshToken.token_hash == auth.hash_token(body.refresh_token)).with_for_update()
    )
    if row is None or row.expires_at < datetime.now(UTC):
        raise ApiError(401, "invalid_refresh", "Refresh token is invalid; re-register with the install secret")
    if row.revoked:
        # Reuse of a rotated token: assume theft and revoke the whole family for this user.
        db.execute(update(RefreshToken).where(RefreshToken.user_id == row.user_id).values(revoked=True))
        db.commit()
        raise ApiError(401, "invalid_refresh", "Refresh token was already used")
    user = db.get(AppUser, row.user_id)
    if user is None or user.deleted_at is not None:
        raise ApiError(401, "invalid_refresh", "Account not found")
    row.revoked = True
    return _issue(db, row.user_id)


@router.put("/users/{user_id}/push-token", status_code=status.HTTP_204_NO_CONTENT)
def put_push_token(
    user_id: uuid.UUID, body: PushTokenIn, me: uuid.UUID = Depends(active_user), db: Session = Depends(get_session)
) -> None:
    auth.require_self(user_id, me)
    db.execute(update(AppUser).where(AppUser.id == me).values(fcm_token=body.fcm_token))
    db.commit()


@router.delete("/users/{user_id}", status_code=status.HTTP_202_ACCEPTED)
def delete_user(user_id: uuid.UUID, me: uuid.UUID = Depends(active_user), db: Session = Depends(get_session)) -> dict:
    """Tech Spec §8.10: mark deleted, revoke tokens, enqueue the deletion job."""
    auth.require_self(user_id, me)
    user = db.get(AppUser, me)
    assert user is not None
    user.deleted_at = datetime.now(UTC)
    user.fcm_token = None
    db.execute(update(RefreshToken).where(RefreshToken.user_id == me).values(revoked=True))
    job = Job(user_id=me, stage="deletion", status="queued")
    db.add(job)
    db.commit()
    queues.enqueue("deletion-jobs", job.id)
    return {"deletion_id": str(job.id)}


@router.get("/deletions/{deletion_id}")
def deletion_status(
    deletion_id: uuid.UUID, me: uuid.UUID = Depends(auth.current_user_id), db: Session = Depends(get_session)
) -> dict:
    """Works with the (still valid) access token after the user row is gone."""
    job = db.get(Job, deletion_id)
    if job is None or job.user_id != me or job.stage != "deletion":
        raise ApiError(404, "not_found", "Unknown deletion")
    return {"status": "done" if job.status == "done" else "pending"}


# ---- optional login (P1) ----


def _progress(db: Session, user_id: uuid.UUID) -> int:
    return (
        db.scalar(
            select(func.count())
            .select_from(UserLesson)
            .where(UserLesson.user_id == user_id, UserLesson.completed_at.is_not(None))
        )
        or 0
    )


def _merge(db: Session, keep: AppUser, drop: AppUser) -> None:
    """Move the dropped user's topics and lesson history to the kept user, then delete the dropped user."""
    from app.services.deletion import delete_user_data

    kept_topics = {t.name_norm: t.id for t in db.scalars(select(Topic).where(Topic.user_id == keep.id))}
    topic_map: dict[uuid.UUID, uuid.UUID] = {}
    for t in db.scalars(select(Topic).where(Topic.user_id == drop.id)):
        if t.name_norm in kept_topics:
            topic_map[t.id] = kept_topics[t.name_norm]  # duplicate topic: history goes to the kept one
        else:
            t.user_id = keep.id
            topic_map[t.id] = t.id
    # Personal lessons/syllabi/progress move with the user instead of being deleted.
    db.execute(update(Lesson).where(Lesson.owner_user_id == drop.id).values(owner_user_id=keep.id))
    db.execute(update(Syllabus).where(Syllabus.owner_user_id == drop.id).values(owner_user_id=keep.id))
    kept_progress = set(db.scalars(select(SyllabusProgress.syllabus_id).where(SyllabusProgress.user_id == keep.id)))
    for sp in db.scalars(select(SyllabusProgress).where(SyllabusProgress.user_id == drop.id)):
        if sp.syllabus_id not in kept_progress:
            db.add(
                SyllabusProgress(
                    user_id=keep.id,
                    syllabus_id=sp.syllabus_id,
                    next_index=sp.next_index,
                    weak_concepts=sp.weak_concepts,
                )
            )
    db.flush()
    kept_lessons = set(db.scalars(select(UserLesson.lesson_id).where(UserLesson.user_id == keep.id)))
    for ul in db.scalars(select(UserLesson).where(UserLesson.user_id == drop.id)):
        if ul.lesson_id not in kept_lessons:
            cols = {c: getattr(ul, c) for c in ul.__table__.columns.keys() if c not in ("user_id", "topic_id")}
            db.add(UserLesson(**cols, user_id=keep.id, topic_id=topic_map.get(ul.topic_id) if ul.topic_id else None))
    db.flush()
    delete_user_data(db, drop.id)


@router.post("/users/{user_id}/link")
def link_account(
    user_id: uuid.UUID, body: LinkRequest, me: uuid.UUID = Depends(active_user), db: Session = Depends(get_session)
) -> dict:
    auth.require_self(user_id, me)
    provider, subject = auth.verify_cognito_id_token(body.cognito_id_token)
    user = db.get(AppUser, me)
    assert user is not None
    other = db.scalar(select(AppUser).where(AppUser.auth_provider == provider, AppUser.auth_subject == subject))
    if other is not None and other.id != me:
        keep, drop = (other, user) if _progress(db, other.id) >= _progress(db, me) else (user, other)
        _merge(db, keep, drop)
        keep.auth_provider, keep.auth_subject = provider, subject
        db.commit()
        return {"linked": True, "user_id": str(keep.id)}
    user.auth_provider, user.auth_subject = provider, subject
    db.commit()
    return {"linked": True, "user_id": str(me)}


@router.post("/auth/cognito", response_model=TokenPair)
def cognito_sign_in(body: CognitoSignIn, db: Session = Depends(get_session)) -> TokenPair:
    """Sign in on a new phone with a linked identity."""
    provider, subject = auth.verify_cognito_id_token(body.cognito_id_token)
    user = db.scalar(
        select(AppUser).where(
            AppUser.auth_provider == provider, AppUser.auth_subject == subject, AppUser.deleted_at.is_(None)
        )
    )
    if user is None:
        raise ApiError(404, "not_linked", "No BoomDoom account is linked to this sign-in")
    return _issue(db, user.id)
