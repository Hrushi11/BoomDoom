import unittest
from pathlib import Path

from reel_classifier import ReelClassifier, aggregate_frames, label_descriptions


class FakeModels:
    def __init__(self, caption=None, frames=None, speech=None):
        self.caption = caption or {}
        self.frames = frames or []
        self.speech = speech or {}
        self.calls = []

    def text_scores(self, text):
        self.calls.append("text")
        return self.speech if text == "spoken" else self.caption

    def frame_scores(self, video):
        self.calls.append("frames")
        return self.frames

    def transcribe(self, video):
        self.calls.append("audio")
        return "spoken" if self.speech else ""


class ReelClassifierTest(unittest.TestCase):
    def classify(self, models, caption="", audio=True):
        return ReelClassifier(models).classify(caption, Path("fixture.mp4"), audio)

    def test_clear_caption_skips_video_and_audio(self):
        models = FakeModels(caption={"fitness": 0.9, "education": 0.1})
        result = self.classify(models, "#workout")
        self.assertEqual(("fitness", ["caption"]), (result["label"], result["signals"]))
        self.assertEqual(["text"], models.calls)

    def test_ambiguous_caption_uses_sequence(self):
        models = FakeModels(caption={"food": 0.85, "education": 0.84}, frames=[{"food": 0.9}] * 3)
        result = self.classify(models, "Learn to make dosa")
        self.assertEqual("food", result["label"])
        self.assertEqual(["text", "frames"], models.calls)

    def test_talking_head_or_weak_frames_trigger_audio_last(self):
        for frames in [[{"food": 0.3}], [{"talking_head": 0.7, "food": 0.6}]]:
            models = FakeModels(frames=frames, speech={"finance": 1.0})
            result = self.classify(models)
            self.assertEqual(["frames", "audio", "text"], models.calls)
            self.assertEqual(["frames", "speech"], result["signals"])
            self.assertNotEqual("talking_head", result["label"])

    def test_missing_signals_and_audio_disabled(self):
        models = FakeModels()
        result = self.classify(models, audio=False)
        self.assertEqual(("other", []), (result["label"], result["signals"]))
        self.assertEqual(["frames"], models.calls)
        result = self.classify(models)
        self.assertEqual([], result["signals"])

    def test_frame_mean_and_talking_head_peak(self):
        result = aggregate_frames([{"food": 0.2, "talking_head": 0.1}, {"food": 0.8, "talking_head": 0.85}])
        self.assertEqual({"food": 0.5, "talking_head": 0.85}, result)

    def test_flag_and_secondary_labels(self):
        result = self.classify(FakeModels(frames=[{"rage_bait": 0.75, "comedy": 0.65}]))
        self.assertTrue(result["flagged"])
        self.assertEqual(["comedy"], result["secondary"])

    def test_custom_labels_and_reserved_names(self):
        self.assertIn("cricket", label_descriptions(["Cricket"]))
        for names in [["other"], ["talking_head"], ["food"], ["a", "b", "c"]]:
            with self.assertRaises(ValueError):
                label_descriptions(names)
