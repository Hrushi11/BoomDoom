"""Job queues (Tech Spec §8.5). Messages carry only {"job_id"}.

- QUEUE_BACKEND=sqs: Amazon SQS standard queues with DLQs (max receive 3, configured in Terraform).
- QUEUE_BACKEND=db:  a Postgres table with SKIP LOCKED, so local/demo runs need no AWS.

Both expose the same SQS-like semantics: receive hides a message for `visibility_s`, delete acks it,
`extend` keeps it hidden while a long render runs, and un-acked messages reappear.
"""

from __future__ import annotations

import json
import threading
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from functools import lru_cache
from typing import Protocol

from sqlalchemy import text

from app.config import get_settings

QUEUES = ("script-jobs", "render-jobs", "deletion-jobs")
STOP = threading.Event()  # set on SIGTERM so idle DB-queue polls return immediately
MAX_DELAY_S = 900  # SQS limit


@dataclass
class Message:
    job_id: uuid.UUID
    receipt: str
    receive_count: int


class Queue(Protocol):
    def send(self, queue: str, job_id: uuid.UUID, delay_s: int = 0) -> None: ...
    def receive(self, queue: str, visibility_s: int, wait_s: int = 20, max_messages: int = 1) -> list[Message]: ...
    def delete(self, queue: str, receipt: str) -> None: ...
    def extend(self, queue: str, receipt: str, visibility_s: int) -> None: ...


class SqsQueue:
    def __init__(self) -> None:
        import boto3

        s = get_settings()
        self.client = boto3.client("sqs", region_name=s.aws_region, endpoint_url=s.aws_endpoint_url or None)
        self.urls = {
            "script-jobs": s.sqs_script_jobs_url,
            "render-jobs": s.sqs_render_jobs_url,
            "deletion-jobs": s.sqs_deletion_jobs_url,
        }

    def send(self, queue: str, job_id: uuid.UUID, delay_s: int = 0) -> None:
        self.client.send_message(
            QueueUrl=self.urls[queue],
            MessageBody=json.dumps({"job_id": str(job_id)}),
            DelaySeconds=min(max(delay_s, 0), MAX_DELAY_S),
        )

    def receive(self, queue: str, visibility_s: int, wait_s: int = 20, max_messages: int = 1) -> list[Message]:
        resp = self.client.receive_message(
            QueueUrl=self.urls[queue],
            MaxNumberOfMessages=max_messages,
            WaitTimeSeconds=wait_s,
            VisibilityTimeout=visibility_s,
            MessageSystemAttributeNames=["ApproximateReceiveCount"],
        )
        out = []
        for m in resp.get("Messages", []):
            try:
                job_id = uuid.UUID(json.loads(m["Body"])["job_id"])
            except (ValueError, KeyError, TypeError):
                self.delete(queue, m["ReceiptHandle"])  # poison message; nothing to do with it
                continue
            out.append(Message(job_id, m["ReceiptHandle"], int(m["Attributes"]["ApproximateReceiveCount"])))
        return out

    def delete(self, queue: str, receipt: str) -> None:
        self.client.delete_message(QueueUrl=self.urls[queue], ReceiptHandle=receipt)

    def extend(self, queue: str, receipt: str, visibility_s: int) -> None:
        self.client.change_message_visibility(
            QueueUrl=self.urls[queue], ReceiptHandle=receipt, VisibilityTimeout=visibility_s
        )


class DbQueue:
    def send(self, queue: str, job_id: uuid.UUID, delay_s: int = 0) -> None:
        from app.db import get_engine

        with get_engine().begin() as conn:
            conn.execute(
                text(
                    "insert into queue_message (queue, body, visible_at) "
                    "values (:q, :b, now() + make_interval(secs => :d))"
                ),
                {"q": queue, "b": json.dumps({"job_id": str(job_id)}), "d": delay_s},
            )

    def receive(self, queue: str, visibility_s: int, wait_s: int = 20, max_messages: int = 1) -> list[Message]:
        import time

        from app.db import get_engine

        deadline = time.monotonic() + wait_s
        while True:
            with get_engine().begin() as conn:
                rows = conn.execute(
                    text(
                        """
                        update queue_message set visible_at = now() + make_interval(secs => :v),
                               receive_count = receive_count + 1
                        where id in (select id from queue_message where queue = :q and visible_at <= now()
                                     order by id limit :n for update skip locked)
                        returning id, body, receive_count
                        """
                    ),
                    {"q": queue, "v": visibility_s, "n": max_messages},
                ).all()
            if rows or STOP.is_set() or time.monotonic() >= deadline:
                return [Message(uuid.UUID(json.loads(r.body)["job_id"]), str(r.id), r.receive_count) for r in rows]
            STOP.wait(min(1.0, max(0.0, deadline - time.monotonic())))

    def delete(self, queue: str, receipt: str) -> None:
        from app.db import get_engine

        with get_engine().begin() as conn:
            conn.execute(text("delete from queue_message where id = :id"), {"id": int(receipt)})

    def extend(self, queue: str, receipt: str, visibility_s: int) -> None:
        from app.db import get_engine

        with get_engine().begin() as conn:
            conn.execute(
                text("update queue_message set visible_at = now() + make_interval(secs => :v) where id = :id"),
                {"v": visibility_s, "id": int(receipt)},
            )


@lru_cache
def get_queue() -> Queue:
    return SqsQueue() if get_settings().queue_backend == "sqs" else DbQueue()


def enqueue(queue: str, job_id: uuid.UUID, delay_s: int = 0) -> None:
    get_queue().send(queue, job_id, delay_s)


def backoff_delay(attempt: int, base_s: int = 30) -> int:
    """Exponential backoff for re-queued jobs, capped at the SQS max delay."""
    return min(base_s * 2 ** max(attempt - 1, 0), MAX_DELAY_S)


def utcnow() -> datetime:
    return datetime.now(UTC)


def ago(seconds: float) -> datetime:
    return utcnow() - timedelta(seconds=seconds)
