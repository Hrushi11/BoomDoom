"""Shared worker loop for SQS (or the DB queue).

- Long-polls; one job at a time per process.
- Claims the job row atomically (`queued -> running`), so duplicate or stale messages are no-ops.
- Heartbeat thread extends message visibility while a long job runs.
- SIGTERM (ECS deploy / scale-in, Spot interruption) interrupts the job: it goes back to `queued`
  without spending an attempt and is re-sent immediately, so another worker picks it up.
- Job-level failures are retried with backoff by re-sending a new message; after MAX_ATTEMPTS the job
  is failed_final. Unexpected crashes leave the message un-acked, so SQS redelivers it (DLQ after 3).
"""

from __future__ import annotations

import logging
import signal
import threading
import time
import uuid
from collections.abc import Callable
from dataclasses import dataclass

from sqlalchemy import update

from app import metrics, queues
from app.db import session_scope
from app.domain.lifecycle import MAX_ATTEMPTS
from app.models import Job

log = logging.getLogger("worker")


class WorkerShutdown(BaseException):
    """Raised inside a running job when the process receives SIGTERM."""


class RetryableError(Exception):
    """The job should be retried later (e.g. OpenAI unavailable, cost cap reached)."""

    def __init__(self, message: str, delay_s: int | None = None, count_attempt: bool = True):
        super().__init__(message)
        self.delay_s, self.count_attempt = delay_s, count_attempt


class JobCancelled(Exception):
    """An operator cancelled the job (POST /v1/admin/...); stop without retrying or marking it done."""


def set_progress(job_id: uuid.UUID | str | None, step: str) -> None:
    """Record the current step of a running job (best effort; shown on /trackVideoGen)."""
    if not job_id:
        return
    try:
        _set(uuid.UUID(str(job_id)), progress=step[:120])
    except Exception:
        log.exception("progress update failed")


def check_cancelled(job_id: uuid.UUID | str | None) -> None:
    """Raise JobCancelled if the job was cancelled. Long handlers call this between their stages."""
    if not job_id:
        return
    with session_scope() as db:
        job = db.get(Job, uuid.UUID(str(job_id)))
        if job is not None and job.status == "cancelled":
            raise JobCancelled(str(job_id))


class PermanentError(Exception):
    """The job can never succeed (bad input); fail it now."""


@dataclass
class Route:
    queue: str
    visibility_s: int
    handler: Callable[[uuid.UUID], None]  # called with a claimed job id
    on_final_failure: Callable[[uuid.UUID, str], None] | None = None


def claim(job_id: uuid.UUID) -> bool:
    with session_scope() as db:
        res = db.execute(
            update(Job)
            .where(Job.id == job_id, Job.status == "queued")
            .values(status="running", attempts=Job.attempts + 1, updated_at=queues.utcnow())
        )
        return res.rowcount == 1  # type: ignore[attr-defined]


def _set(job_id: uuid.UUID, **values) -> None:
    with session_scope() as db:
        db.execute(update(Job).where(Job.id == job_id).values(updated_at=queues.utcnow(), **values))


class Worker:
    def __init__(self, name: str, routes: list[Route], wait_s: int = 20):
        self.name, self.routes, self.wait_s = name, routes, wait_s
        self.stopping = False
        self.in_job = False

    # ---- signals ----
    def _on_sigterm(self, signum, frame) -> None:  # noqa: ARG002
        log.info("shutdown requested")
        self.stopping = True
        queues.STOP.set()
        if self.in_job:
            raise WorkerShutdown()

    def install_signals(self) -> None:
        signal.signal(signal.SIGTERM, self._on_sigterm)
        signal.signal(signal.SIGINT, self._on_sigterm)

    # ---- loop ----
    def run_forever(self) -> None:
        self.install_signals()
        log.info("worker started", extra={"stage": self.name})
        wait = self.wait_s if len(self.routes) == 1 else max(1, self.wait_s // len(self.routes))
        while not self.stopping:
            for route in self.routes:
                if self.stopping:
                    break
                self.poll_once(route, wait_s=wait)
        log.info("worker stopped", extra={"stage": self.name})

    def poll_once(self, route: Route, wait_s: int = 0) -> int:
        q = queues.get_queue()
        handled = 0
        for msg in q.receive(route.queue, route.visibility_s, wait_s=wait_s):
            self.process(route, msg)
            handled += 1
        return handled

    def process(self, route: Route, msg: queues.Message) -> None:
        q = queues.get_queue()
        if not claim(msg.job_id):
            q.delete(route.queue, msg.receipt)  # duplicate, stale, or already done
            return
        stop_beat = threading.Event()
        beat = threading.Thread(target=self._heartbeat, args=(route, msg, stop_beat), daemon=True)
        beat.start()
        start = time.monotonic()
        self.in_job = True
        try:
            route.handler(msg.job_id)
            self.in_job = False
            check_cancelled(msg.job_id)  # cancelled while it ran: keep it cancelled, not done
            _set(msg.job_id, status="done", finished_at=queues.utcnow(), error=None)
            metrics.emit("job_succeeded", 1, stage=route.queue)
        except JobCancelled:
            self.in_job = False
            log.info("job cancelled by operator", extra={"job_id": str(msg.job_id)})
        except WorkerShutdown:
            self.in_job = False
            _set(msg.job_id, status="queued", attempts=Job.attempts - 1)
            queues.enqueue(route.queue, msg.job_id)
            log.info("job handed back on shutdown", extra={"job_id": str(msg.job_id)})
        except RetryableError as e:
            self.in_job = False
            self._retry(route, msg.job_id, str(e), e.delay_s, e.count_attempt)
        except PermanentError as e:
            self.in_job = False
            self._fail_final(route, msg.job_id, str(e))
        except Exception as e:
            self.in_job = False
            log.exception("job failed", extra={"job_id": str(msg.job_id)})
            self._retry(route, msg.job_id, f"{type(e).__name__}: {e}"[:500], None, True)
        finally:
            self.in_job = False
            stop_beat.set()
            metrics.emit("job_seconds", round(time.monotonic() - start, 2), "Seconds", stage=route.queue)
        q.delete(route.queue, msg.receipt)

    def _retry(self, route: Route, job_id: uuid.UUID, error: str, delay_s: int | None, count: bool) -> None:
        with session_scope() as db:
            job = db.get(Job, job_id)
            attempts = job.attempts if job else MAX_ATTEMPTS
        if count and attempts >= MAX_ATTEMPTS:
            self._fail_final(route, job_id, error)
            return
        _set(job_id, status="queued", error=error, **({} if count else {"attempts": Job.attempts - 1}))
        queues.enqueue(route.queue, job_id, delay_s if delay_s is not None else queues.backoff_delay(attempts))
        metrics.emit("job_retried", 1, stage=route.queue)
        log.warning("job retry scheduled", extra={"job_id": str(job_id), "status": error[:200]})

    def _fail_final(self, route: Route, job_id: uuid.UUID, error: str) -> None:
        _set(job_id, status="failed_final", error=error[:500], finished_at=queues.utcnow())
        if route.on_final_failure:
            route.on_final_failure(job_id, error)
        metrics.emit("job_failed_final", 1, stage=route.queue)
        log.error("job failed permanently", extra={"job_id": str(job_id), "status": error[:200]})

    def _heartbeat(self, route: Route, msg: queues.Message, stop: threading.Event) -> None:
        interval = max(5, route.visibility_s // 3)
        while not stop.wait(interval):
            try:
                queues.get_queue().extend(route.queue, msg.receipt, route.visibility_s)
                _set(msg.job_id)  # touch updated_at so the stuck-job sweeper leaves it alone
            except Exception:
                log.exception("heartbeat failed")
