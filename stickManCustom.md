# StickMan Custom: OpenAI-directed stickman films

The POC image now defaults to an OpenAI-directed film for each new lesson. The existing script and render workers
deliver it through the lesson feed. The integration is local as of 22 November 2026.

## Lesson integration

1. The script worker targets 30 seconds (90 words, validated within ±15%) when `RENDERER=film`. It keeps the lesson
   schema, profile language, format, quiz and QA pass. The prompt describes the four sets and available characters,
   asks the narrator to speak to the viewer and gives the final third a new visual beat.
2. The render worker passes the topic name, concept and ordered segment narration to `FilmRenderer`. The director
   splits at sentence and segment boundaries and speaks the approved words. It tries up to 1.15× speed before
   extending the picture to fit the voice, its 0.5-second lead-in and its 1.2-second tail.
3. Each caption uses measured sentence boundaries on the picture clock. Word boundaries within a sentence are
   estimated by word length. The final word ends at the measured sentence end, so the existing pause calculation
   places a quiz 0.15 seconds after its segment.
4. The render worker compresses the portrait master to 720×1280, generates the thumbnail and stores the MP4,
   manifest and project JSON before marking the lesson ready. Existing lessons keep their stored media.
5. `RENDERER=stickman` selects the previous Stickman Studio renderer and profile-based script length.
   Local tests use `placeholder`. Render queue visibility is 60 minutes for film and 15 minutes for the other engines.
   The stuck-job sweeper uses three visibility windows before recovering a running job.

## Why

Our lessons rendered with Stickman Studio's Cinematic Illustrated engine looked plain: floating icons, label cards and
stock scenes ("Subatomic scale & quantum entanglement" in an interview lesson). The look we want is closer to the
Stickman Studio demos: lit, furnished scenes, characters who act, film-style cameras. Neither the Cinematic Illustrated
engine nor the Moving Stickman engine reproduces those demos (see the comparison videos in
`~/Downloads/boomdoom-stickman-comparison`).

The [stickman-skill](https://github.com/krishnagoswami52/stickman-skill) does produce that look, but it is built for
Claude driving it interactively in a chat. StickMan Custom runs the same steps automatically, with **OpenAI for every
model step** and **Stickman Studio's Kokoro voice** for narration.

## The pipeline

The render adapter calls `make_film(..., narration=sentences)` in `backend/workers/film_worker/director.py`.
Standalone calls can omit `narration` to have the director write and revise a script of its own.

| Step | What happens | Tool |
|---|---|---|
| 1. Script | Standalone mode writes 7–9 spoken sentences (~88 words), a hook moment with characters, one idea, one takeaway. Written for what the engine can draw (café, street, room, symbolic void). | OpenAI `gpt-6.2-sol` |
| 2. Voice | Each sentence spoken separately with Kokoro (`af_heart`), joined with 0.28 s pauses into `vo.wav`, with exact start/end times per sentence. | Stickman Studio TTS (`vendor/stickman-studio/boomdoom_tts.py`) |
| 3. Fit to 30 s | Speech must fit 30 − 0.5 s (voice start) − 1.2 s (tail). Lesson mode keeps the words and extends only after trying 1.15×. Standalone mode can request a shorter or longer script. | director |
| 4. Plan + code | Beat sheet, one fixed "world" (positions of every character and prop), 8-11 shots cut to the sentence times, the complete `film.js`, and the sound block of `audio_mix.py`. The prompt includes the skill's style bible, production notes, film craft, sound notes, **the full engine source** and the starter film, so the model writes against real functions. | OpenAI `gpt-6.2-sol` |
| 5. Load check | The film is loaded in headless Chromium; page errors, render exceptions, wrong `FILM.dur` or bad cuts go back to the model to fix (up to 3 tries). | `film_worker/probe.py` (Playwright) |
| 6. Stills review | A contact sheet of up to nine shot midpoints, including the opening and ending, is sent to the model **with vision**, checked against the skill's checklist plus our rules (narrated action visible, two characters interacting, vertical framing, nothing in the caption zone). Up to 2 fix rounds. | OpenAI vision |
| 7. Build + audit | Every shot rendered (30 fps), voice + effects + soft music mixed, then the skill's frame audit (hand reach, contacts, elbows, foot skate, pops, frozen windows, determinism, loudness). Audit issues get one fix round, kept only if they reduce the issue count. If the model's sound block crashes, a safe mix (room bed + music + voice) is used. | `vendor/stickman-skill/scripts/build.py` |
| 8. Compose | Captions per sentence (bottom fifth) and the title for the first ~3 s burned into the 1080×1920 film. | `film_worker/compose.py` (ffmpeg) |

### Output

- `film_9x16.mp4`: 1080×1920, normally 30.0 s, H.264 + AAC, captions and title burned in (what the lesson player would show)
- `film/film.mp4`: the raw film from the engine
- `plan.json` (beats, world, shots), `stills_*.jpg` (what the reviewer saw), `voice/timings.json`

## Decisions

- **Thirty-second target.** Fixed lesson narration determines the minimum duration after voice fitting.
- **Native 9:16.** The engine originally rendered only 1920×1080. A one-line patch makes it read its frame size from
  the page canvas (`vendor/stickman-skill/engine/core.js`), and the director sets the canvas to 1080×1920. Frames are
  composed for vertical from the start, instead of shrinking a landscape film into a phone frame (tried first: the
  film filled only about a third of the screen).
- **Model selection.** The script worker uses the configured lesson script and QA models. The film director uses `openai_model_film`
  (default `gpt-6.2-sol`). Spend goes through the same daily cost cap and spend log as the lesson pipeline.
- **Stickman Studio's voice, sentence by sentence.** Speaking each sentence separately gives exact timings without
  forced alignment; cuts and captions land on sentence boundaries.
- **Two characters, visible action.** A first test with one character read as someone standing around a café while the
  captions told the story; the prompts now require interaction, and the review fails "nothing happens" stills.

## Files

| Path | Purpose |
|---|---|
| `backend/workers/film_worker/director.py` | The pipeline (`make_film`), 30 s fitting, load/review/audit loops, `Tools` (paths to the skill, Stickman Studio and its Python) |
| `backend/workers/film_worker/prompts.py` | Script, film, load-fix, stills-critique and audit-fix prompts and JSON schemas |
| `backend/workers/film_worker/llm.py` | `FilmLLM`: OpenAI JSON calls with optional images, long timeout, cost cap and spend logging |
| `backend/workers/film_worker/probe.py` | Loads a film in headless Chromium: errors, duration, cuts, stills contact sheet (runs in Stickman Studio's virtualenv) |
| `backend/workers/film_worker/compose.py` | Captions and title for portrait films; blurred-background layout for landscape films |
| `vendor/stickman-skill/` | The skill's engine, scripts, sound effects and references (MIT; sound effects CC0 by Kenney). Patches listed in its `VENDORED.md` |
| `vendor/stickman-studio/boomdoom_tts.py` | Kokoro narration, one sentence at a time, with timings |
| `backend/workers/render_worker/renderers.py` | Engine selection, sentence splitting and word timings |
| `backend/workers/script_worker/pipeline.py` | Engine-specific script length, validation and QA |
| `backend/tests/test_film_renderer.py` | Renderer contract, narration fitting and script prompt tests |
| `deploy/poc/smoke_film.py` | Credential-free image smoke test using the vendored film example |
| `backend/app/config.py` | Runtime settings |

### Settings

| Setting | Default | Meaning |
|---|---|---|
| `RENDERER` | `film` in the POC, `placeholder` in backend settings | Lesson engine |
| `STICKMAN_SKILL_DIR` | `/opt/stickman-skill` in the POC | Path to `vendor/stickman-skill`; empty means films are unavailable |
| `STICKMAN_MODELS_DIR` | empty | Kokoro voice files (default `<stickman studio>/models`) |
| `OPENAI_MODEL_FILM` | `gpt-6.2-sol` | Model for every film step |
| `FILM_OPENAI_TIMEOUT_S` | 300 | Writing `film.js` is several thousand tokens |
| `FILM_VOICE_ID` | `af_heart` | Kokoro voice |
| `RENDER_TMP_DIR` | `/tmp/boomdoom-render` | Per-job work folders, cleaned after the lesson render |
| `FILM_RENDER_JOBS` | 0 | Parallel Chromium renderers (0 = the skill's default) |

### Dependencies

Stickman Studio's virtualenv also needs `playwright`, `scipy`, `numpy` and `pillow`, plus Chromium
(`python -m playwright install chromium`). `ffmpeg` is taken from next to that Python, else from `PATH`.

## Run it locally

From the repository root, with Kokoro models and the Studio virtualenv in the adjacent Stickman Studio checkout:

```bash
../Stickman-Studio/.venv/bin/pip install playwright scipy
../Stickman-Studio/.venv/bin/python -m playwright install chromium
export RENDERER=film
export STICKMAN_STUDIO_DIR="$PWD/vendor/stickman-studio"
export STICKMAN_SKILL_DIR="$PWD/vendor/stickman-skill"
export STICKMAN_PYTHON="$PWD/../Stickman-Studio/.venv/bin/python"
export STICKMAN_MODELS_DIR="$PWD/../Stickman-Studio/models"
cd backend
mkdir -p /tmp/boomdoom-film-smoke
.venv/bin/python ../deploy/poc/smoke_film.py /tmp/boomdoom-film-smoke
```

The smoke test runs Kokoro, Chromium, the frame audit and ffmpeg with a fixed director response. For a live lesson,
set the OpenAI key in `backend/.env` and start `.venv/bin/python scripts/run_local.py` from the same shell. Create
a topic in the app to send a lesson through the script and render queues.

## POC deployment

The root Docker build context includes both vendored projects. The POC image installs Playwright and SciPy in the
Studio virtualenv and makes its Chromium binaries available to the runtime user. Changes under either vendor directory
trigger the deploy workflow. The workflow runs the Stickman and film smoke tests inside the built image before pushing.

Terraform writes `var.renderer` to the host's SSM environment. An existing host with `RENDERER=stickman` keeps using
that value until its SSM environment is updated. Applying the updated Terraform default selects film unless the
deployment explicitly sets a different renderer.

## Test results so far

| Run | Topic | Result |
|---|---|---|
| 1 | Psychology: Sensation vs perception (16:9, before the portrait switch) | Ran end to end in ~6 min: script 7 s, voice 3 s, plan + code ~2 min (code loaded on the first try), 2 review rounds, render + mix ~1 min on an M-series Mac. 30.00 s, 1920×1080. **Looked right** (lit café, rain, depth blur, smooth motion) **but taught weakly**: one character standing around, 19 s of speech in a 30 s film, and the 16:9 film filled only about a third of the vertical frame. These three findings drove the portrait switch, the 23.5 s minimum and the two-character rule. |
| 2 | Personal Finance: Take-Home Pay | Did not start: OpenAI unreachable (DNS failure on the test Mac). Not a pipeline result. To rerun. |
| 3 | System Design: Caching (9:16, with all fixes) | **Worked.** 7.6 min end to end on an M-series Mac (script 9 s, voice 3 s, plan + code 2.3 min, 2 review rounds 3.9 min, render + mix 1 min, captions 13 s). 30.00 s, 1080×1920, 25.1 s of speech, 11 shots, **0 audit issues**, 92.8k input / 30.1k output tokens (about $0.49 at gpt-6.2-sol prices). The model turned caching into a visible metaphor: cups labelled DATA, a far table labelled SOURCE (the slow fetch), a near table labelled CACHE (the hit), two characters handing cups over. Both review rounds made real fixes (framing, crops, a cup drawn over a hoodie, a stiff hand). Weak spots: the narration describes the animation ("The hero walks to a far table…") instead of talking to the viewer, and the last ~8 s repeat the same two-shot with little new action. |

The standalone prototype outputs above are in `~/Downloads/boomdoom-film-test`.

### Queued lesson smoke test, 22 November 2026

With an isolated local PostgreSQL database and a seeded System Design syllabus, a 45-second profile requested a
caching lesson. The live script and QA workers produced 93 words across three segments. Kokoro spoke those exact
words in 25.557 seconds. The director planned 11 shots and ran two visual reviews. The render job took 9 min 56 s,
including manual inspection of generated code during this run. The frame audit reported zero issues.

The lesson appeared in `GET /v1/feed`. The stored H.264/AAC MP4 measured 30.00 seconds at 720×1280 and 30 fps.
The thumbnail measured 360×640. All 93 project caption words matched the approved script. Segment two ended at
13.263 s and both the stored manifest and feed placed the quiz at 13.413 s. A long caption was cut off in the
first encode. Long sentences now advance through timed three-line pages. Replaying the same approved script and
recorded director responses through both queues produced the corrected MP4 with the same caption and quiz timings.
Its MP4, thumbnail and JSON files are in `backend/.data/film-smoke/10a50f8b-834b-404b-b78f-cbd69a4549dd/` (ignored by Git).

One review mistook an empty contact-sheet cell for a black shot. Review sampling now includes the final shot,
and nine portrait stills fill a 3×3 grid. Ruff lint and format checks and all 48 backend tests passed.
The fixed-response image smoke also ran locally with real media tools. Docker was unavailable on the test host,
so the Linux image build and in-container smoke tests still need to run in CI.

## Known limits

- **Execution privileges.** The director inserts the model's sound block into `audio_mix.py`. The child Python process
  inherits the worker environment, including credentials available to that worker. The offline image smoke test uses
  a trusted fixed mix. A production deployment needs to isolate generated code from worker credentials and storage.

- **What the engine can draw.** Four sets (café, street, room, symbolic void) and six characters. Abstract topics
  (system design, finance numbers) must be told through people and metaphors in those places. If that is not good
  enough, new sets or props (an office, a server room, a phone, a chart) are the next investment; the skill's rule is
  to add sets in the same flat style, not restyle existing ones.
- **No phone prop.** Many of our scripts involve phones; the model has to draw one itself or avoid it.
- **Review is a model looking at stills,** not a person watching the film. It catches broken frames and empty shots,
  not every awkward moment; sound is measured, never heard.
- **Render cost.** About 1 min of rendering per 30 s film on an M-series Mac; the skill quotes ~4 min on 2 cores. On the
  POC server (4 vCPU, no GPU) expect several minutes per film, one film at a time per worker.
- **Model cost.** Roughly 4-7 large calls per film (the system prompt carries ~30 KB of engine source and docs); token
  usage is returned in `FilmResult.tokens` for measuring.

## Remaining validation

The historical finance and psychology runs above still need fresh portrait examples for cross-topic visual review.

## Credits

- Engine and method: [stickman-skill](https://github.com/krishnagoswami52/stickman-skill) (MIT), based on
  [michxlai/stickman-skill](https://github.com/michxlai/stickman-skill).
- Sound effects: Kenney ([kenney.nl](https://www.kenney.nl)), CC0.
- Voice: Kokoro-82M (Apache-2.0) through Stickman Studio.
