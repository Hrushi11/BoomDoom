"""Script worker jobs (Tech Spec §8.7): `syllabus` and `script` stages from the script-jobs queue.

DB transactions are never held open across OpenAI calls: read inputs, close, call the model, then
write results in a fresh transaction.
"""

from __future__ import annotations

import json
import logging
import uuid
from dataclasses import dataclass

from sqlalchemy import case, func, select

from app.config import get_settings
from app.db import session_scope
from app.domain.lifecycle import LessonStatus
from app.domain.sharing import FORMAT_ROTATION
from app.models import Job, Lesson, Syllabus, SyllabusProgress, Topic, UserLesson
from app.services import generation
from workers.common import PermanentError
from workers.script_worker.llm import LLM, Usage
from workers.script_worker.prompts import qa_prompt, safety_prompt, script_prompt, syllabus_prompt
from workers.script_worker.schema import (
    LESSON_SCRIPT_SCHEMA,
    QA_SCHEMA,
    SAFETY_SCHEMA,
    SYLLABUS_SCHEMA,
    WORDS_PER_SECOND,
    validate_script,
    validate_syllabus,
)

log = logging.getLogger(__name__)
TEMPERATURE = {"syllabus": 0.4, "script": 0.8, "qa": 0.0}
MAX_SCRIPT_TRIES = 3  # first try + 2 regenerations
HIGH_SKIP_RATE, MIN_SAMPLES = 0.6, 3


def handle(job_id: uuid.UUID, llm: LLM) -> None:
    with session_scope() as db:
        job = db.get(Job, job_id)
        if job is None:
            return
        stage = job.stage
    if stage == "syllabus":
        build_syllabus(job_id, llm)
    elif stage == "script":
        write_script(job_id, llm)
    else:
        raise PermanentError(f"unexpected stage {stage} on script-jobs")


# ------------------------------------------------------------------ syllabus


def build_syllabus(job_id: uuid.UUID, llm: LLM) -> None:
    with session_scope() as db:
        job = db.get(Job, job_id)
        topic = db.get(Topic, job.topic_id) if job and job.topic_id else None
        if topic is None or topic.syllabus_id is None:
            return  # topic deleted meanwhile
        syl = db.get(Syllabus, topic.syllabus_id)
        assert syl is not None
        if syl.status == "ready":
            _allocate_waiting(syl.id)
            return
        personal = syl.owner_user_id is not None
        inputs = {
            "topic": topic.name,
            "level": syl.level,
            "goal": topic.goal if personal else None,
            "custom": topic.type != "preset",
            "syllabus_id": syl.id,
        }

    if inputs["custom"] or inputs["goal"]:
        verdict, _ = llm.json(
            model=get_settings().openai_model_qa,
            schema=SAFETY_SCHEMA,
            temperature=0,
            prompt=safety_prompt(inputs["topic"], inputs["goal"]),
        )
        if not verdict.get("allowed", False):
            log.info("topic rejected by safety check", extra={"job_id": str(job_id)})
            _fail_syllabus(inputs["syllabus_id"], "topic_not_allowed")
            return

    data, _ = llm.json(
        model=get_settings().openai_model_syllabus,
        schema=SYLLABUS_SCHEMA,
        temperature=TEMPERATURE["syllabus"],
        prompt=syllabus_prompt(inputs["topic"], inputs["level"], inputs["goal"], get_settings().syllabus_size),
    )
    concepts = validate_syllabus(data)
    with session_scope() as db:
        syl = db.get(Syllabus, inputs["syllabus_id"])
        if syl is None:
            return
        syl.concepts, syl.status, syl.model = concepts, "ready", get_settings().openai_model_syllabus
    _allocate_waiting(inputs["syllabus_id"])


def _allocate_waiting(syllabus_id: uuid.UUID) -> None:
    """Every topic waiting on this syllabus gets its starter batch."""
    with session_scope() as db:
        waiting = db.execute(select(Topic.id, Topic.user_id).where(Topic.syllabus_id == syllabus_id)).all()
    for topic_id, user_id in waiting:
        with session_scope() as db:
            plan = generation.ensure_stock(db, user_id, topic_id)
            db.commit()
        plan.send()


def _fail_syllabus(syllabus_id: uuid.UUID, reason: str) -> None:
    with session_scope() as db:
        syl = db.get(Syllabus, syllabus_id)
        if syl is None:
            return
        syl.status = "failed"
        for topic in db.scalars(select(Topic).where(Topic.syllabus_id == syllabus_id)):
            topic.status = "failed"
        log.warning("syllabus failed", extra={"status": reason})


def on_syllabus_or_script_failure(job_id: uuid.UUID, error: str) -> None:
    with session_scope() as db:
        job = db.get(Job, job_id)
        if job is None:
            return
        if job.stage == "syllabus" and job.topic_id:
            topic = db.get(Topic, job.topic_id)
            syl_id = topic.syllabus_id if topic else None
        else:
            syl_id = None
            if job.lesson_id:
                fail_lesson(db, job.lesson_id)
    if syl_id:
        _fail_syllabus(syl_id, error)


def fail_lesson(db, lesson_id: uuid.UUID) -> None:
    lesson = db.get(Lesson, lesson_id)
    if lesson is None:
        return
    lesson.status = LessonStatus.FAILED_FINAL.value
    for topic in generation.topics_for_lesson(db, lesson.id):
        generation.refresh_topic_status(db, topic)


# ------------------------------------------------------------------ script


@dataclass
class ScriptInputs:
    lesson_id: uuid.UUID
    topic: str
    level: str
    goal: str | None
    language: str
    fmt: str
    concept: dict
    covered: list[str]
    weak: list[str]
    length_s: int


def _high_skip_formats(db, user_id: uuid.UUID) -> set[str]:
    rows = db.execute(
        select(Lesson.format, func.count(), func.sum(case((UserLesson.skipped.is_(True), 1), else_=0)))
        .join(UserLesson, UserLesson.lesson_id == Lesson.id)
        .where(UserLesson.user_id == user_id, (UserLesson.completed_at.is_not(None)) | UserLesson.skipped)
        .group_by(Lesson.format)
    ).all()
    return {f for f, n, skipped in rows if n >= MIN_SAMPLES and (skipped or 0) / n >= HIGH_SKIP_RATE}


def _load_inputs(job_id: uuid.UUID) -> ScriptInputs | None:
    with session_scope() as db:
        job = db.get(Job, job_id)
        lesson = db.get(Lesson, job.lesson_id) if job and job.lesson_id else None
        if lesson is None:
            return None
        if lesson.status not in (LessonStatus.QUEUED.value, LessonStatus.SCRIPTING.value, LessonStatus.FAILED.value):
            return None  # already scripted (duplicate job)
        syl = db.get(Syllabus, lesson.syllabus_id)
        topic = db.get(Topic, job.topic_id) if job.topic_id else None
        if syl is None or topic is None:
            raise PermanentError("syllabus or topic missing")
        profile = generation.profile_for(db, job.user_id)
        personal = lesson.owner_user_id is not None
        weak: list[str] = []
        if personal:
            progress = db.get(SyllabusProgress, (job.user_id, syl.id))
            weak = list(progress.weak_concepts) if progress else []
            skip = _high_skip_formats(db, job.user_id)
            if lesson.format in skip:
                candidates = [f for f in FORMAT_ROTATION if f not in skip] or FORMAT_ROTATION
                lesson.format = candidates[lesson.concept_index % len(candidates)]
        lesson.status = LessonStatus.SCRIPTING.value
        concepts = syl.concepts
        return ScriptInputs(
            lesson_id=lesson.id,
            topic=topic.name,
            level=lesson.level,
            goal=topic.goal if personal else None,
            language=lesson.language,
            fmt=lesson.format,
            concept=concepts[lesson.concept_index],
            covered=[c["title"] for c in concepts[max(0, lesson.concept_index - 20) : lesson.concept_index]],
            weak=[w for w in weak if w != lesson.concept][:5],
            length_s=profile.length_s,
        )


def generate_reviewed_script(llm: LLM, inp: ScriptInputs) -> tuple[dict, list[Usage]]:
    """Write -> validate -> quality check, with one fix attempt and up to 2 regenerations."""
    s = get_settings()
    usages: list[Usage] = []
    prompt = script_prompt(
        topic=inp.topic,
        level=inp.level,
        goal=inp.goal,
        language=inp.language,
        fmt=inp.fmt,
        title=inp.concept["title"],
        objective=inp.concept["objective"],
        covered=inp.covered,
        weak=inp.weak,
        words=inp.length_s * WORDS_PER_SECOND,
    )
    last: list[str] = []
    for _ in range(MAX_SCRIPT_TRIES):
        script, u = llm.json(
            model=s.openai_model_script, prompt=prompt, schema=LESSON_SCRIPT_SCHEMA, temperature=TEMPERATURE["script"]
        )
        usages.append(u)
        last = validate_script(script, inp.length_s)
        if last:
            continue
        candidate, fixed_once = script, False
        while True:
            verdict, u = llm.json(
                model=s.openai_model_qa,
                prompt=qa_prompt(json.dumps(candidate)),
                schema=QA_SCHEMA,
                temperature=TEMPERATURE["qa"],
            )
            usages.append(u)
            if verdict["pass"]:
                return candidate, usages
            last = verdict["issues"] or ["quality check failed"]
            fixed = verdict.get("fixed_script")
            if fixed and not fixed_once and not validate_script(fixed, inp.length_s):
                candidate, fixed_once = fixed, True
                continue  # re-check the fixed script once
            break
    raise PermanentError(f"script failed quality checks: {'; '.join(last)[:300]}")


def write_script(job_id: uuid.UUID, llm: LLM) -> None:
    inp = _load_inputs(job_id)
    if inp is None:
        return
    script, usages = generate_reviewed_script(llm, inp)
    plan = generation.Plan()
    with session_scope() as db:
        job = db.get(Job, job_id)
        lesson = db.get(Lesson, inp.lesson_id)
        if lesson is None or job is None:
            return
        lesson.script = script
        lesson.format = script["format"]
        lesson.cost_tokens_in = (lesson.cost_tokens_in or 0) + sum(u.input_tokens for u in usages)
        lesson.cost_tokens_out = (lesson.cost_tokens_out or 0) + sum(u.output_tokens for u in usages)
        lesson.status = LessonStatus.SCRIPT_READY.value
        render = Job(user_id=job.user_id, topic_id=job.topic_id, lesson_id=lesson.id, stage="render", status="queued")
        db.add(render)
        db.flush()
        plan.add("render-jobs", render.id)
    plan.send()
