from fastapi import APIRouter

from app.config import get_settings

router = APIRouter(tags=["meta"])


@router.get("/healthz")
def healthz() -> dict:
    return {"ok": True}


@router.get("/v1/config")
def remote_config() -> dict:
    """Remote config pointers (Tech Spec Appendix C). The app always ships bundled defaults."""
    base = get_settings().cdn_base_url
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
            "optional_login": False,
        },
    }
