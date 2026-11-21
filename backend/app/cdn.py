"""CloudFront signed URLs (Tech Spec §9.2: key group + canned policy). Plain URLs in local mode."""

from __future__ import annotations

import base64
import json
from datetime import UTC, datetime, timedelta
from functools import lru_cache

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

from app.config import get_settings


def _cf_b64(data: bytes) -> str:
    return base64.b64encode(data).decode().replace("+", "-").replace("=", "_").replace("/", "~")


@lru_cache
def _private_key():
    return serialization.load_pem_private_key(get_settings().cloudfront_private_key_pem.encode(), password=None)


@lru_cache
def _s3():
    import boto3
    from botocore.config import Config

    s = get_settings()
    return boto3.client(
        "s3", region_name=s.aws_region, endpoint_url=s.aws_endpoint_url or None, config=Config(signature_version="s3v4")
    )


def sign_url(key: str, ttl_s: int | None = None, now: datetime | None = None) -> str:
    """CloudFront signed URL if configured; else an S3 presigned URL (POC); else a plain local URL."""
    s = get_settings()
    key = key.lstrip("/")
    if not (s.cloudfront_key_pair_id and s.cloudfront_private_key_pem):
        if s.storage_backend == "s3":
            return _s3().generate_presigned_url(
                "get_object",
                Params={"Bucket": s.lessons_bucket, "Key": key},
                ExpiresIn=min(ttl_s or s.signed_url_ttl_s, 7 * 86_400),
            )
        return f"{s.cdn_base_url.rstrip('/')}/{key}"
    url = f"{s.cdn_base_url.rstrip('/')}/{key}"
    expires = int(((now or datetime.now(UTC)) + timedelta(seconds=ttl_s or s.signed_url_ttl_s)).timestamp())
    policy = json.dumps(
        {"Statement": [{"Resource": url, "Condition": {"DateLessThan": {"AWS:EpochTime": expires}}}]},
        separators=(",", ":"),
    ).encode()
    # CloudFront requires RSA-SHA1 for signed URLs.
    signature = _private_key().sign(policy, padding.PKCS1v15(), hashes.SHA1())  # noqa: S303
    return f"{url}?Expires={expires}&Signature={_cf_b64(signature)}&Key-Pair-Id={s.cloudfront_key_pair_id}"
