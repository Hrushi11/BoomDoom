import tempfile
import unittest
import wave
from pathlib import Path

import av
import numpy as np
from reel_classifier import LABELS, LocalModels


class PreviewWindowTest(unittest.TestCase):
    def test_a_late_visual_and_late_audio_are_outside_the_preview(self):
        models = LocalModels(LABELS, None, None, None)
        with tempfile.TemporaryDirectory() as folder:
            video = Path(folder) / "late.mp4"
            with av.open(str(video), "w") as out:
                stream = out.add_stream("mpeg4", rate=1)
                stream.width = stream.height = 64
                for second in range(7):
                    pixels = np.zeros((64, 64, 3), dtype=np.uint8)
                    if second >= 5:
                        pixels[:, 32:] = 255
                    for packet in stream.encode(av.VideoFrame.from_ndarray(pixels, format="rgb24")):
                        out.mux(packet)
                for packet in stream.encode():
                    out.mux(packet)
            self.assertEqual([], models.frame_scores(video))

            audio = Path(folder) / "late.wav"
            samples = np.zeros(16000 * 7, dtype="<i2")
            samples[16000 * 5 :] = 8000
            with wave.open(str(audio), "wb") as out:
                out.setparams((1, 2, 16000, 0, "NONE", ""))
                out.writeframes(samples.tobytes())
            self.assertEqual("", models.transcribe(audio))
