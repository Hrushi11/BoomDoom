"""Firebase Cloud Messaging HTTP v1 (Tech Spec §8.9). Data messages only; never feed data."""

from __future__ import annotations

import json
import logging
import threading
from functools import lru_cache

import httpx

from app.config import get_settings

log = logging.getLogger(__name__)
_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
_lock = threading.Lock()


@lru_cache
def _credentials():
    from google.oauth2 import service_account

    info = json.loads(get_settings().fcm_service_account_json)
    return service_account.Credentials.from_service_account_info(info, scopes=[_SCOPE]), info["project_id"]


def _token() -> tuple[str, str]:
    import google.auth.transport.requests

    creds, project = _credentials()
    with _lock:
        if not creds.valid:
            creds.refresh(google.auth.transport.requests.Request())
    return creds.token, project


def send_data(fcm_token: str | None, data: dict[str, str]) -> bool:
    """Best effort; returns False if push is disabled or delivery failed. Never raises."""
    if not fcm_token or not get_settings().fcm_service_account_json:
        return False
    try:
        token, project = _token()
        r = httpx.post(
            f"https://fcm.googleapis.com/v1/projects/{project}/messages:send",
            headers={"Authorization": f"Bearer {token}"},
            json={"message": {"token": fcm_token, "data": data, "android": {"priority": "normal"}}},
            timeout=10,
        )
        if r.status_code == 404:  # unregistered token
            log.info("fcm token unregistered")
        return r.is_success
    except Exception:
        log.exception("fcm send failed")
        return False


def lessons_ready(fcm_token: str | None, topic_id: str) -> bool:
    return send_data(fcm_token, {"type": "lessons_ready", "topic_id": topic_id})
