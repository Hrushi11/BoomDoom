"""Job queue abstraction. SQS in AWS; an in-process list locally so the API runs without AWS.

Messages carry only {job_id} (Tech Spec §8.5).
"""

import json
import uuid
from collections import defaultdict

from app.config import get_settings

_local: dict[str, list[dict]] = defaultdict(list)


def _url(queue: str) -> str:
    s = get_settings()
    return {
        "script-jobs": s.sqs_script_jobs_url,
        "render-jobs": s.sqs_render_jobs_url,
        "deletion-jobs": s.sqs_deletion_jobs_url,
    }[queue]


def enqueue(queue: str, job_id: uuid.UUID) -> None:
    body = {"job_id": str(job_id)}
    url = _url(queue)
    if not url:
        _local[queue].append(body)
        return
    import boto3

    boto3.client("sqs", region_name=get_settings().aws_region).send_message(QueueUrl=url, MessageBody=json.dumps(body))


def drain_local(queue: str) -> list[dict]:
    items, _local[queue] = _local[queue], []
    return items
