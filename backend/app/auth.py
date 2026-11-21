"""Anonymous auth (Tech Spec §8.2): RS256 access JWTs + opaque rotated refresh tokens."""

import hashlib
import json
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
    return jwt.encode(claims, _keys()[0], algorithm="RS256", headers={"kid": get_settings().jwt_key_id})


def new_refresh_token() -> tuple[str, str]:
    """Returns (plaintext token for the client, hash to store)."""
    token = secrets.token_urlsafe(48)
    return token, hash_token(token)


def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def decode_access_token(token: str) -> uuid.UUID:
    try:
        claims = jwt.decode(token, _keys()[1], algorithms=["RS256"], issuer=ISSUER, options={"require": ["exp", "sub"]})
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


def jwks() -> dict:
    """Public key set (Tech Spec §8.2) so other services can verify our tokens."""
    from jwt.algorithms import RSAAlgorithm

    pub = serialization.load_pem_public_key(_keys()[1])
    jwk = json.loads(RSAAlgorithm.to_jwk(pub))
    jwk.update({"kid": get_settings().jwt_key_id, "use": "sig", "alg": "RS256"})
    return {"keys": [jwk]}


@lru_cache
def _cognito_jwks_client() -> jwt.PyJWKClient:
    s = get_settings()
    url = f"https://cognito-idp.{s.aws_region}.amazonaws.com/{s.cognito_user_pool_id}/.well-known/jwks.json"
    return jwt.PyJWKClient(url, cache_keys=True, lifespan=3600)


def verify_cognito_id_token(token: str) -> tuple[str, str]:
    """Returns (provider, subject) for a Cognito ID token. Optional login is P1."""
    s = get_settings()
    if not (s.cognito_user_pool_id and s.cognito_app_client_id):
        raise ApiError(501, "login_disabled", "Optional login is not enabled")
    try:
        key = _cognito_jwks_client().get_signing_key_from_jwt(token).key
        claims = jwt.decode(
            token,
            key,
            algorithms=["RS256"],
            audience=s.cognito_app_client_id,
            issuer=f"https://cognito-idp.{s.aws_region}.amazonaws.com/{s.cognito_user_pool_id}",
        )
    except jwt.PyJWTError as e:
        raise ApiError(401, "invalid_identity", "Could not verify the sign-in token") from e
    if claims.get("token_use") != "id":
        raise ApiError(401, "invalid_identity", "Expected an ID token")
    identities = claims.get("identities") or []
    provider = identities[0].get("providerName", "cognito").lower() if identities else "phone"
    return provider, claims["sub"]
