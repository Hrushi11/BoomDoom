import uuid

from fastapi import Depends
from sqlalchemy.orm import Session

from app import auth
from app.db import get_session
from app.errors import ApiError
from app.models import AppUser
from app.services import rate_limit


def active_user(user_id: uuid.UUID = Depends(auth.current_user_id), db: Session = Depends(get_session)) -> uuid.UUID:
    """Authenticated, existing, not-deleted user, within the general rate limit (30 req/min)."""
    user = db.get(AppUser, user_id)
    if user is None or user.deleted_at is not None:
        raise ApiError(401, "unauthorized", "Account not found")
    rate_limit.consume(db, user_id, "general")
    db.commit()
    return user_id
