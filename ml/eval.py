"""Measure local reel classification on a hand-labelled JSONL set."""

import argparse
import json
from collections import Counter, defaultdict
from math import ceil
from pathlib import Path

from reel_classifier import (
    THRESHOLDS,
    build_classifier,
    label_descriptions,
    model_arguments,
)


def read_set(path, labels):
    seen = set()
    with path.open(encoding="utf-8") as source:
        for line_no, line in enumerate(source, 1):
            if not line.strip():
                continue
            try:
                row = json.loads(line)
                key, expected = row["id"], row["label"]
                if not isinstance(key, str) or not key.strip() or key in seen:
                    raise ValueError("missing or duplicate reel id")
                if expected not in labels:
                    raise ValueError(f"unknown reference label {expected!r}")
                caption = row.get("caption", "")
                if not isinstance(caption, str):
                    raise TypeError("caption must be text")
                video = path.parent / row["video"] if row.get("video") else None
                if video and not video.is_file():
                    raise ValueError(f"missing local media for {key}")
                seen.add(key)
                yield expected, caption, video
            except (TypeError, ValueError, KeyError) as error:
                raise ValueError(f"{path}:{line_no}: {error}") from error
    if not seen:
        raise ValueError("the evaluation set is empty")


def metrics(records, flagged):
    confusion = defaultdict(Counter)
    times = defaultdict(list)
    flag_hits = flag_count = 0
    correct = 0
    for expected, result in records:
        predicted = result["label"]
        confusion[expected][predicted] += 1
        correct += expected == predicted
        if result["flagged"]:
            flag_count += 1
            flag_hits += expected in flagged
        path = "+".join(result["timing_ms"]) or "no_input"
        times[path].append(result["total_ms"])
    return {
        "reels": len(records),
        "accuracy": correct / len(records),
        "confusion_actual_by_predicted": confusion,
        "recall_by_label": {k: v[k] / v.total() for k, v in confusion.items()},
        "flagged_predictions": flag_count,
        "flag_precision": flag_hits / flag_count if flag_count else None,
        "timing_by_path_including_first_use": {
            path: {
                "n": len(ms),
                "p50_ms": sorted(ms)[ceil(0.50 * len(ms)) - 1],
                "p95_ms": sorted(ms)[ceil(0.95 * len(ms)) - 1],
                "under_500_ms": sum(t < 500 for t in ms),
            }
            for path, ms in times.items()
        },
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--without-captions", action="store_true", help="compare against a run with captions")
    model_arguments(parser)
    args = parser.parse_args()
    classifier = build_classifier(args)
    valid = set(label_descriptions(args.custom_label)) | {"other"}
    records = []
    for expected, caption, video in read_set(args.manifest, valid):
        result = classifier.classify("" if args.without_captions else caption, video, audio=bool(args.speech_model))
        records.append((expected, result))
    report = metrics(records, classifier.flagged)
    # Raising the final abstention threshold needs no rerun of the encoders.
    report["accuracy_by_other_threshold"] = {
        threshold: sum((r["label"] if r["confidence"] >= threshold else "other") == label for label, r in records)
        / len(records)
        for threshold in (THRESHOLDS["other_threshold"], 0.55, 0.65, 0.75)
    }
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
