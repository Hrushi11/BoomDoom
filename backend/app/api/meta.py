import logging

from fastapi import APIRouter
from fastapi.responses import JSONResponse

from app import auth, db
from app.config import get_settings

router = APIRouter(tags=["meta"])
log = logging.getLogger(__name__)


@router.get("/healthz")
def healthz() -> dict:
    """Liveness: the process is up. Used by the container health check."""
    return {"ok": True}


@router.get("/readyz")
def readyz() -> JSONResponse:
    """Readiness: the database is reachable. Used by the ALB target group."""
    try:
        ok = db.ping()
    except Exception:
        log.exception("readiness check failed")
        ok = False
    return JSONResponse({"ok": ok}, status_code=200 if ok else 503)


@router.get("/.well-known/jwks.json")
def jwks() -> dict:
    return auth.jwks()


@router.get("/v1/config")
def remote_config() -> dict:
    """Remote config pointers (Tech Spec Appendix C). The app always ships bundled defaults."""
    base = get_settings().cdn_base_url.rstrip("/")
    return {
        "selectors_version": "2026.11.1",
        "thresholds_version": "2026.11.1",
        "selectors_url": f"{base}/config/reels_selectors.json",
        "thresholds_url": f"{base}/config/ml_thresholds.json",
        "model_manifest_url": f"{base}/config/models_manifest.json",
        "feature_flags": {
            "in_feed_lesson_card": False,
            "captions_toggle": False,
            "topic_suggestions": False,
            "optional_login": bool(get_settings().cognito_user_pool_id),
        },
    }
