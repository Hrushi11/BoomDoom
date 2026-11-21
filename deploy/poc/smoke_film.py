"""Exercise the image's film renderer as the runtime user, using the vendored example as a fixed model response."""

import json
import sys
from pathlib import Path

from workers.film_worker.director import SAFE_SOUND
from workers.film_worker.prompts import FILM_SCHEMA
from workers.render_worker.pause_times import caption_words, interaction_pauses, segment_end_times
from workers.render_worker.renderers import FilmRenderer, get_renderer

SEGMENTS = [
    {
        "id": "s1",
        "narration": "Have you ever been so busy watching your cup that you missed a friend arriving? "
        "You can look straight at a scene and still miss something in it.",
    },
    {
        "id": "s2",
        "narration": "Your eyes take in more than you can follow at once. "
        "When you focus on the cup, the door can slip out of your attention.",
    },
    {
        "id": "s3",
        "narration": "Now try watching the door instead. What else did you miss this time? "
        "When a detail matters, pause and choose where to look.",
    },
]


class ExampleDirector:
    usage = [0, 0]

    def __init__(self, template):
        self.code = template.read_text() + "\nFILM.dur = 30; FILM.cuts[FILM.cuts.length - 1] = 900;\n"

    def json(self, *, schema, **kwargs):
        if schema == FILM_SCHEMA:
            return {"beats": [], "world": "café", "shots": [], "film_js": self.code, "sound_block": SAFE_SOUND}
        return {"ok": True, "issues": [], "film_js": ""}


def main(workdir):
    renderer = get_renderer()
    assert isinstance(renderer, FilmRenderer), renderer.name
    template = renderer.tools.skill_dir / "templates" / "film_2d.js"
    renderer.llm_factory = lambda: ExampleDirector(template)
    result = renderer.render({"topic": "Psychology", "concept": "Selective attention", "segments": SEGMENTS}, workdir)
    words = caption_words(result.project)
    ends, fallback = segment_end_times(SEGMENTS, words, result.project["duration"])
    assert [word["word"] for word in words] == " ".join(s["narration"] for s in SEGMENTS).split()
    assert not fallback and 0 < ends["s1"] < ends["s2"] < ends["s3"] < result.project["duration"]
    assert result.video.stat().st_size > 0
    (workdir / "project.json").write_text(json.dumps(result.project, indent=2))
    print(result.video, interaction_pauses([{"after_segment": "s1"}], ends))


if __name__ == "__main__":
    main(Path(sys.argv[1]).resolve())
