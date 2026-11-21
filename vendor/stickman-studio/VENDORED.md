# Vendored: Stickman Studio

- Upstream: https://github.com/atharv01h/Stickman-Studio (MIT, see LICENSE)
- Base commit: `94165c0c0e66b38bbd10a7c7873dc04f3275a46d`
- Used by BoomDoom's render worker through `boomdoom_entry.py` (headless Cinematic Illustrated pipeline + Kokoro voice).

## BoomDoom patches (Tech Spec §8.8)

| File | Change |
|---|---|
| `boomdoom_entry.py` | New headless entry point: params JSON in, `{video, project}` JSON out |
| `src/captions/renderer.py` | Captions fall back to bundled Noto Sans when Windows fonts are missing |
| `src/engine/cis_pipeline.py` | Real 1-frame NVENC probe in the Python render path; fixed SFX track argument order |
| `src/audio/mixer.py` | `mix_audio` accepts a pre-mixed `sfx_path` (call-site mismatch) |
| `src/engine/cis_scene_illustrator.py` | Strip symbol glyphs (✦, 💭) the fonts can't draw |

Not vendored: `.venv/` and `models/` (the Docker image installs dependencies and downloads the Kokoro model).
Local dev can keep using the sibling checkout at `../Stickman-Studio` (see backend/README.md).
