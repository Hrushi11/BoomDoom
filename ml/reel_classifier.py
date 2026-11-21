"""Classify a local reel, trying its caption, sampled frames and optional speech in order."""

import argparse
import json
from contextlib import nullcontext
from functools import cached_property
from pathlib import Path
from time import perf_counter

HERE = Path(__file__).resolve().parent
THRESHOLDS = json.loads((HERE.parent / "config/ml_thresholds.json").read_text())

# Each hypothesis describes a topic for NLI. The visual prompts describe things a frame can show.
LABELS = {
    "comedy": ("comedy or jokes", ["a stand-up comedian on stage", "a funny meme or comedy skit"]),
    "fitness": ("exercise or fitness", ["a person lifting weights in a gym", "a workout demonstration"]),
    "education": ("an educational lesson", ["a teacher explaining a diagram", "an educational science demonstration"]),
    "food": ("food or cooking", ["a person preparing a recipe", "a close-up photo of a meal"]),
    "news": ("a news report", ["a television news broadcast", "a reporter covering a current event"]),
    "finance": ("finance or investing", ["a stock market chart", "a video about saving money or mutual funds"]),
    "rage_bait": ("outrage or deliberately provoking anger", ["a heated argument", "an angry outrage reaction video"]),
    "ads": (
        "advertising or promoting a product",
        ["an advertisement offering a discount", "a sponsored product promotion"],
    ),
}

# "None of these": everyday reel content that is not one of the categories. Without it, selfies, pets and
# scenery drifted into rage_bait/news on Reels-styled frames (ml/README.md, Android POC notes).
BACKGROUND = [
    "a selfie video", "a vlog of someone's day", "friends hanging out", "a pet or an animal",
    "a landscape or scenery", "a dance video", "people at a party or event", "a cartoon or animation",
    "a meme with text", "a screenshot of an app", "a close-up of a face",
]
# Frame groups that compete in the softmax but never become a category score. Rage-bait is about tone,
# which one frame cannot show, so frames never vote for it. Same rule as Android FrameClassifier.calibrate.
FRAME_HIDDEN = {"talking_head", "background", "rage_bait"}
FRAME_GATE_LOGIT = -9.0


def calibrate_frame(logits):
    """Group logits -> category scores: softmax over every group times a gate on the best logit."""
    import math

    top = max(logits.values())
    weights = {k: math.exp(v - top) for k, v in logits.items()}
    total = sum(weights.values())
    gate = 1 / (1 + math.exp(-(top - FRAME_GATE_LOGIT)))
    scores = {k: w / total * gate for k, w in weights.items() if k not in FRAME_HIDDEN}
    if "talking_head" in logits:
        scores["talking_head"] = 1 / (1 + math.exp(-logits["talking_head"]))
    return scores


def label_descriptions(custom):
    labels = LABELS.copy()
    if len(custom) > 2:
        raise ValueError("the MVP supports up to two custom labels")
    for name in custom:
        key = name.strip().lower()
        if not key or key in labels or key in {"other", "talking_head", "background"}:
            raise ValueError(f"invalid or duplicate custom label: {name!r}")
        labels[key] = (key, [f"a short video about {key}", f"a reel showing {key}"])
    return labels


def aggregate_frames(rows):
    if not rows:
        return {}
    return {
        label: max(row[label] for row in rows)
        if label == "talking_head"
        else sum(row[label] for row in rows) / len(rows)
        for label in rows[0]
    }


def fuse(signals):
    weights = THRESHOLDS["weights"].copy()
    if signals.get("frames", {}).get("talking_head", 0) >= THRESHOLDS["talking_head_threshold"]:
        weights["frames"] = THRESHOLDS["talking_head_frame_weight"]
    total = sum(weights[source] for source in signals)
    labels = dict.fromkeys(label for scores in signals.values() for label in scores if label != "talking_head")
    return {
        label: sum(weights[source] * scores.get(label, 0) for source, scores in signals.items()) / total
        for label in labels
    }


class ReelClassifier:
    def __init__(self, models, flagged=("rage_bait",)):
        self.models = models
        self.flagged = set(flagged)

    def classify(self, caption, video, audio=False):
        start = perf_counter()
        signals = {}
        timings = {}
        if caption.strip():
            signals["caption"] = self.models.text_scores(caption.strip())
            timings["caption"] = (perf_counter() - start) * 1000
        ranked = sorted(signals.get("caption", {}).values(), reverse=True)
        decisive = (
            ranked
            and ranked[0] >= THRESHOLDS["caption_exit_threshold"]
            and ranked[0] - max(ranked[1:], default=0) >= THRESHOLDS["caption_exit_margin"]
        )
        if video is not None and not decisive:
            before = perf_counter()
            frames = aggregate_frames(self.models.frame_scores(video))
            timings["frames"] = (perf_counter() - before) * 1000
            if frames:
                signals["frames"] = frames
            talking_head = frames.get("talking_head", 0) >= THRESHOLDS["talking_head_threshold"]
            if audio and (talking_head or max(fuse(signals).values(), default=0) < THRESHOLDS["speech_trigger"]):
                before = perf_counter()
                transcript = self.models.transcribe(video)
                if transcript.strip():
                    signals["speech"] = self.models.text_scores(transcript)
                timings["audio"] = (perf_counter() - before) * 1000

        scores = fuse(signals)
        ranked_labels = sorted(scores, key=scores.get, reverse=True)
        best = ranked_labels[0] if ranked_labels else "other"
        confidence = scores.get(best, 0)
        label = best if confidence >= THRESHOLDS["other_threshold"] else "other"
        return {
            "label": label,
            "confidence": confidence,
            "secondary": [k for k in ranked_labels[1:] if scores[k] >= THRESHOLDS["secondary_threshold"]][:2],
            "flagged": label in self.flagged and label != "other" and confidence >= THRESHOLDS["flag_threshold"],
            "signals": list(signals),
            "timing_ms": timings,
            "total_ms": (perf_counter() - start) * 1000,
        }


def open_media(video):
    import av

    if isinstance(video, Path):
        return av.open(str(video), options={"protocol_whitelist": "file"})
    video.seek(0)
    return av.open(video, format="mov", options={"enable_drefs": "0", "protocol_whitelist": "file"})


class LocalModels:
    def __init__(self, labels, text_dir, vision_dir, speech_dir):
        self.labels = labels
        self.text_dir = text_dir
        self.vision_dir = vision_dir
        self.speech_dir = speech_dir

    @cached_property
    def text(self):
        from transformers import AutoModelForSequenceClassification, AutoTokenizer

        tokenizer = AutoTokenizer.from_pretrained(self.text_dir, local_files_only=True)
        model = AutoModelForSequenceClassification.from_pretrained(self.text_dir, local_files_only=True).eval()
        return tokenizer, model

    def text_scores(self, text):
        import torch

        tokenizer, model = self.text
        hypotheses = [f"This video is about {topic}." for topic, _ in self.labels.values()]
        inputs = tokenizer(
            [text[:4096]] * len(hypotheses),
            hypotheses,
            padding=True,
            truncation="only_first",
            max_length=256,
            return_tensors="pt",
        )
        with torch.inference_mode():
            probs = model(**inputs).logits.softmax(dim=-1)[:, model.config.label2id["entailment"]]
        return dict(zip(self.labels, probs.tolist(), strict=True))

    @cached_property
    def vision(self):
        import torch
        from transformers import AutoModel, AutoProcessor

        model = AutoModel.from_pretrained(self.vision_dir, local_files_only=True).eval()
        processor = AutoProcessor.from_pretrained(self.vision_dir, local_files_only=True, use_fast=False)
        prompts = {key: phrases for key, (_, phrases) in self.labels.items()}
        prompts["talking_head"] = ["a person talking directly to the camera"]
        prompts["background"] = BACKGROUND
        texts = [s for phrases in prompts.values() for s in phrases]
        inputs = processor(text=texts, padding="max_length", truncation=True, max_length=64, return_tensors="pt")
        with torch.inference_mode():
            features = torch.nn.functional.normalize(model.get_text_features(**inputs), dim=-1)
        return processor, model, features, prompts

    def frame_scores(self, video):
        import av
        import numpy as np
        import torch

        images = []
        previous = None
        sample_slot = 0
        # Decode locally and retain only the first frame at 0, 2 and 4 seconds.
        with open_media(video) as container:
            if not container.streams.video:
                return []
            origin = container.start_time / av.time_base if container.start_time is not None else None
            for frame in container.decode(video=0):
                if origin is None:
                    origin = frame.time
                elapsed = frame.time - origin if frame.time is not None else None
                if (elapsed is None and sample_slot > 0) or (elapsed is not None and elapsed >= 5):
                    break
                if elapsed is not None and elapsed < 2 * sample_slot:
                    continue
                sample_slot += 1
                image = frame.to_image()
                thumb = np.asarray(image.convert("L").resize((16, 16)), dtype=np.int16)
                # Blank leaders carry no visual evidence. Repeated frames should get only one vote.
                if np.ptp(thumb) >= 6 and (previous is None or np.abs(thumb - previous).mean() >= 6):
                    images.append(image)
                    previous = thumb
                if sample_slot == 3:
                    break
        if not images:
            return []
        processor, model, text_features, prompts = self.vision
        inputs = processor(images=images, return_tensors="pt")
        with torch.inference_mode():
            features = torch.nn.functional.normalize(model.get_image_features(**inputs), dim=-1)
            logits = (features @ text_features.T) * model.logit_scale.exp() + model.logit_bias
            groups = logits.split([len(v) for v in prompts.values()], dim=1)
            best = torch.stack([group.amax(dim=1) for group in groups], dim=1)
        return [calibrate_frame(dict(zip(prompts, row, strict=True))) for row in best.tolist()]

    @cached_property
    def speech(self):
        from shruti_onnx import Shruti

        return Shruti(str(self.speech_dir))

    def transcribe(self, video):
        import av
        import numpy as np

        chunks = []
        samples = 0
        with open_media(video) as container:
            if not container.streams.audio:
                return ""
            resampler = av.AudioResampler(format="flt", layout="mono", rate=16000)
            origin = container.start_time / av.time_base if container.start_time is not None else None
            for frame in container.decode(audio=0):
                if origin is None:
                    origin = frame.time
                elapsed = frame.time - origin if frame.time is not None else samples / 16000
                if elapsed >= 5:
                    break
                for chunk in resampler.resample(frame):
                    take = min(chunk.samples, 80000 - samples, round((5 - elapsed) * 16000))
                    if take > 0:
                        chunks.append(chunk.to_ndarray().ravel()[:take])
                        samples += take
                if samples >= 80000:
                    break
        if not chunks:
            return ""
        waveform = np.concatenate(chunks)[:80000]
        if np.max(np.abs(waveform)) < 0.001:
            return ""
        return self.speech.transcribe(waveform, beams=1)["text"]


def model_arguments(parser):
    parser.add_argument("--text-model", type=Path, default=HERE / "out/caption-nli")
    parser.add_argument("--vision-model", type=Path, default=HERE / "out/siglip2-base-patch16-224")
    parser.add_argument("--speech-model", type=Path, help="enable audio fallback with this local Shruti directory")
    parser.add_argument("--custom-label", action="append", default=[])
    parser.add_argument("--flag", action="append", help="flag this label (default: rage_bait)")


def build_classifier(args):
    import torch

    torch.set_num_threads(4)
    labels = label_descriptions(args.custom_label)
    return ReelClassifier(
        LocalModels(labels, args.text_model, args.vision_model, args.speech_model),
        args.flag if args.flag is not None else ["rage_bait"],
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    media = parser.add_mutually_exclusive_group()
    media.add_argument("video", type=Path, nargs="?")
    media.add_argument("--media-url", help="direct range-enabled HTTPS media URL for a team test clip")
    parser.add_argument("--caption", default="")
    parser.add_argument("--repeat", type=int, default=1, help="reuse the model instances across timed runs")
    model_arguments(parser)
    args = parser.parse_args()
    if args.repeat < 1 or (not args.video and not args.media_url and not args.caption.strip()):
        parser.error("provide a caption or local media file, and a positive repeat count")
    if args.video and not args.video.is_file():
        parser.error("video must be a local file")
    classifier = build_classifier(args)
    from preview import RangePreview

    for run in range(1, args.repeat + 1):
        source = RangePreview(args.media_url) if args.media_url else nullcontext(args.video)
        with source as video:
            result = classifier.classify(args.caption, video, audio=bool(args.speech_model))
            if args.media_url:
                result.update(bytes_fetched=video.bytes_fetched, fetch_ms=video.fetch_ms, media_bytes=video.size)
            print(json.dumps({"run": run, **result}), flush=True)


if __name__ == "__main__":
    main()
