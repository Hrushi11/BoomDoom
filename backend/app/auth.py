"""Anonymous auth (Tech Spec §8.2): RS256 access JWTs + opaque rotated refresh tokens."""

import hashlib
import secrets
import uuid
from datetime import UTC, datetime, timedelta
from functools import lru_cache

import jwt
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from fastapi import Depends
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.config import get_settings
from app.errors import ApiError

ISSUER = "boomdoom-api"
_bearer = HTTPBearer(auto_error=False)


@lru_cache
def _keys() -> tuple[bytes, bytes]:
    s = get_settings()
    if s.jwt_private_key_pem and s.jwt_public_key_pem:
        return s.jwt_private_key_pem.encode(), s.jwt_public_key_pem.encode()
    if not s.is_local:
        raise RuntimeError("JWT keys must be configured outside local/test environments")
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    priv = key.private_bytes(
        serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()
    )
    pub = key.public_key().public_bytes(serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo)
    return priv, pub


def issue_access_token(user_id: uuid.UUID) -> str:
    now = datetime.now(UTC)
    claims = {
        "iss": ISSUER,
        "sub": str(user_id),
        "scope": "user",
        "iat": now,
        "exp": now + timedelta(seconds=get_settings().access_token_ttl_s),
    }
    return jwt.encode(claims, _keys()[0], algorithm="RS256")


def new_refresh_token() -> tuple[str, str]:
    """Returns (plaintext token for the client, hash to store)."""
    token = secrets.token_urlsafe(48)
    return token, hash_token(token)


def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def decode_access_token(token: str) -> uuid.UUID:
    try:
        claims = jwt.decode(token, _keys()[1], algorithms=["RS256"], issuer=ISSUER)
    except jwt.PyJWTError as e:
        raise ApiError(401, "unauthorized", "Invalid or expired token") from e
    if claims.get("scope") != "user":
        raise ApiError(403, "forbidden", "Wrong token scope")
    return uuid.UUID(claims["sub"])


def current_user_id(creds: HTTPAuthorizationCredentials | None = Depends(_bearer)) -> uuid.UUID:
    if creds is None:
        raise ApiError(401, "unauthorized", "Missing bearer token")
    return decode_access_token(creds.credentials)


def require_self(path_user_id: uuid.UUID, user_id: uuid.UUID) -> None:
    if path_user_id != user_id:
        raise ApiError(403, "forbidden", "Cannot act on another user")
