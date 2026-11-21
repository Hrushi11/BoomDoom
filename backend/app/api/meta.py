import json
import logging
from functools import lru_cache
from pathlib import Path

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse

from app.errors import ApiError

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


MODELS_MANIFEST_KEY = "models/manifest.json"


@router.get("/v1/models")
def models() -> dict:
    """On-device classification models (Tech Spec §5.9): files with SHA-256, size and a signed download URL.

    The manifest is published by ml/publish_models.py. Model files are not user data and contain no feed data.
    """
    from app.cdn import sign_url
    from app.storage import get_storage

    raw = get_storage().get_bytes(MODELS_MANIFEST_KEY)
    if raw is None:
        return {"version": None, "files": []}
    manifest = json.loads(raw)
    for f in manifest["files"]:
        f["url"] = sign_url(f.pop("key"))
    return manifest


# Remote config files served by the API itself (repo `config/`, copied into the image as /srv/config). The links
# used to point at cdn_base_url, which is http://localhost:8000/static outside local dev: unreachable from phones.
REMOTE_CONFIG = ("reels_selectors", "ml_thresholds", "feature_flags")


def _config_dir() -> Path:
    here = Path(__file__).resolve()
    for root in (here.parents[2], here.parents[3]):  # /srv (image) or the repo root (local dev)
        if (root / "config" / "ml_thresholds.json").is_file():
            return root / "config"
    raise RuntimeError("config/ directory not found")


@lru_cache
def _config_file(name: str) -> dict:
    return json.loads((_config_dir() / f"{name}.json").read_text())


@router.get("/v1/config")
def remote_config(request: Request) -> dict:
    """Remote config pointers (Tech Spec Appendix C). The app always ships bundled defaults.

    Links are absolute and built from the address the client used (Caddy forwards host and scheme), so they work
    on AWS (https://<ip>.sslip.io) and on the emulator (http://10.0.2.2:8000) alike.
    """
    base = str(request.base_url).rstrip("/")
    flags = {k: v for k, v in _config_file("feature_flags").items() if k != "version"}
    flags["optional_login"] = bool(get_settings().cognito_user_pool_id)
    return {
        "selectors_version": _config_file("reels_selectors")["version"],
        "thresholds_version": _config_file("ml_thresholds")["version"],
        "selectors_url": f"{base}/v1/config/reels_selectors.json",
        "thresholds_url": f"{base}/v1/config/ml_thresholds.json",
        "model_manifest_url": f"{base}/v1/models",
        "feature_flags": flags,
    }


@router.get("/v1/config/{name}.json")
def remote_config_file(name: str) -> dict:
    if name not in REMOTE_CONFIG:
        raise ApiError(404, "not_found", "Unknown config file")
    return _config_file(name)
