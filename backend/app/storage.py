"""Object storage: S3 on AWS, local directory otherwise (Tech Spec §8.8 upload layout)."""

from __future__ import annotations

import shutil
from functools import lru_cache
from pathlib import Path
from typing import Protocol

from app.config import get_settings


class Storage(Protocol):
    def put_file(self, key: str, path: Path, content_type: str) -> None: ...
    def put_bytes(self, key: str, data: bytes, content_type: str) -> None: ...
    def delete_prefix(self, prefix: str) -> int: ...


class S3Storage:
    def __init__(self) -> None:
        import boto3

        s = get_settings()
        self.bucket = s.lessons_bucket
        self.client = boto3.client("s3", region_name=s.aws_region, endpoint_url=s.aws_endpoint_url or None)

    def put_file(self, key: str, path: Path, content_type: str) -> None:
        self.client.upload_file(
            str(path),
            self.bucket,
            key,
            ExtraArgs={"ContentType": content_type, "CacheControl": "public, max-age=31536000, immutable"},
        )

    def put_bytes(self, key: str, data: bytes, content_type: str) -> None:
        self.client.put_object(Bucket=self.bucket, Key=key, Body=data, ContentType=content_type)

    def delete_prefix(self, prefix: str) -> int:
        deleted = 0
        paginator = self.client.get_paginator("list_object_versions")
        for page in paginator.paginate(Bucket=self.bucket, Prefix=prefix):
            objs = [
                {"Key": v["Key"], "VersionId": v["VersionId"]}
                for v in page.get("Versions", []) + page.get("DeleteMarkers", [])
            ]
            for i in range(0, len(objs), 1000):
                self.client.delete_objects(Bucket=self.bucket, Delete={"Objects": objs[i : i + 1000], "Quiet": True})
                deleted += len(objs[i : i + 1000])
        return deleted


class LocalStorage:
    def __init__(self) -> None:
        self.root = Path(get_settings().local_storage_dir)

    def _path(self, key: str) -> Path:
        p = (self.root / key).resolve()
        if not str(p).startswith(str(self.root.resolve())):
            raise ValueError("key escapes storage root")
        p.parent.mkdir(parents=True, exist_ok=True)
        return p

    def put_file(self, key: str, path: Path, content_type: str) -> None:
        shutil.copyfile(path, self._path(key))

    def put_bytes(self, key: str, data: bytes, content_type: str) -> None:
        self._path(key).write_bytes(data)

    def delete_prefix(self, prefix: str) -> int:
        target = self.root / prefix
        if not target.exists():
            return 0
        n = sum(1 for f in target.rglob("*") if f.is_file())
        shutil.rmtree(target)
        return n


@lru_cache
def get_storage() -> Storage:
    return S3Storage() if get_settings().storage_backend == "s3" else LocalStorage()


def lesson_prefix(lesson_id: object, version: int = 1) -> str:
    return f"lessons/{lesson_id}/v{version}/"
