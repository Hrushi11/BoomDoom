# Vendored: stickman-skill

- Upstream: https://github.com/krishnagoswami52/stickman-skill (commit d3f9321), based on
  https://github.com/michxlai/stickman-skill by michxlai.
- Licences: code and docs MIT (`LICENSE`, `LICENSE.repo`); sound effects in `sfx/` by Kenney (https://www.kenney.nl), CC0;
  the free voice is Kokoro-82M (Apache-2.0). BoomDoom does not use `scripts/tts.py`: narration is spoken by Stickman
  Studio's Kokoro TTS (`vendor/stickman-studio/boomdoom_tts.py`).
- Used by BoomDoom's film worker (`backend/workers/film_worker`): an OpenAI model plans the film and writes `film.js`
  against this engine, then `scripts/build.py` renders, mixes and audits it. The skill was written for Claude driving it
  interactively; BoomDoom drives the same steps automatically.

Unmodified copy of `skills/stickman`. Local changes, if any, are listed below.

| File | Change |
|---|---|
| `engine/core.js` | `W`, `H` come from the page canvas instead of a fixed 1920x1080, so films can be rendered 1080x1920 for the 9:16 lesson player |
