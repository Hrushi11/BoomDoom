"""Train a linear classification head on frozen SigLIP 2 image features from teacher-labelled reels.

Data: ml/data/reels/*.jpg (phone captures) + labels.jsonl (label_reels.py). The frame is cropped exactly like
the app (FrameClassifier: drop Instagram's header, action column and caption strip).

Evaluation (5-fold, same folds for both): the full phone pipeline, before vs after:
  sponsored marker -> ads; else decisive caption (>= 0.80, 0.15 margin) -> caption label; else fuse frames
  (zero-shot calibrated, or the trained head) with the caption when it is informative (top >= 0.20).

--export writes out/phone/vision.onnx + vision.json ("calibrated": true): encoder -> head softmax over
8 categories + other, plus the zero-shot talking_head sigmoid. The app then uses the probabilities directly.
"""

import argparse
import json
import random
from pathlib import Path

import torch
from PIL import Image
from reel_classifier import HERE, LABELS, THRESHOLDS, LocalModels, calibrate_frame, label_descriptions

CLASSES = [*LABELS, "other"]
CROP = (0.08, 0.78, 0.87)  # top, bottom, right: same as FrameClassifier.CROP_*
W = THRESHOLDS["weights"]
MIN_INFORMATIVE = 0.20


def crop(img):
    w, h = img.size
    return img.crop((0, int(h * CROP[0]), int(w * CROP[2]), int(h * CROP[1]))) if h > w else img


def load(data):
    rows = [json.loads(line) for line in (data / "labels.jsonl").open()]
    return [r for r in rows if (data / f"{r['id']}.jpg").exists() and r["label"] in CLASSES]


@torch.inference_mode()
def featurise(models, data, rows):
    processor, model, text_features, prompts = models.vision
    feats, zeroshot = [], []
    for i in range(0, len(rows), 16):
        images = [crop(Image.open(data / f"{r['id']}.jpg").convert("RGB")) for r in rows[i : i + 16]]
        f = torch.nn.functional.normalize(model.get_image_features(**processor(images=images, return_tensors="pt")), dim=-1)
        logits = (f @ text_features.T) * model.logit_scale.exp() + model.logit_bias
        best = torch.stack([g.amax(dim=1) for g in logits.split([len(v) for v in prompts.values()], dim=1)], dim=1)
        feats.append(f)
        zeroshot += [calibrate_frame(dict(zip(prompts, row))) for row in best.tolist()]
    captions = [models.text_scores(r["caption"]) if r["caption"] not in ("", "(none)") else {} for r in rows]
    return torch.cat(feats), zeroshot, captions


def train(x, y, weight_decay=1e-3, epochs=300):
    head = torch.nn.Linear(x.shape[1], len(CLASSES))
    counts = torch.bincount(y, minlength=len(CLASSES)).float()
    # Square-root balancing: full balancing made the head almost never say "other" (2% vs 15% true).
    class_weight = (counts.sum() / (counts.clamp(min=1) * len(CLASSES))).sqrt().clamp(max=4)
    opt = torch.optim.AdamW(head.parameters(), lr=0.05, weight_decay=weight_decay)
    for _ in range(epochs):
        opt.zero_grad()
        loss = torch.nn.functional.cross_entropy(head(x * 10), y, weight=class_weight)
        loss.backward()
        opt.step()
    return head


def pipeline(sponsored, caption, frames):
    """Mirror of ReelAnalyzer + Fusion (frames: category -> score; talking_head ignored here)."""
    if sponsored:
        return "ads"
    ranked = sorted(caption.values(), reverse=True)
    if ranked and ranked[0] >= THRESHOLDS["caption_exit_threshold"] and ranked[0] - (ranked[1] if len(ranked) > 1 else 0) >= THRESHOLDS["caption_exit_margin"]:
        return max(caption, key=caption.get)
    signals = {"frames": {k: v for k, v in frames.items() if k in LABELS}}
    if ranked and ranked[0] >= MIN_INFORMATIVE:
        signals["caption"] = caption
    total = sum(W[s] for s in signals)
    fused = {lab: sum(W[s] * p.get(lab, 0) for s, p in signals.items()) / total for lab in LABELS}
    best = max(fused, key=fused.get)
    return best if fused[best] >= THRESHOLDS["other_threshold"] else "other"


def report(name, truth, pred):
    acc = sum(t == p for t, p in zip(truth, pred)) / len(truth)
    f1s = []
    for c in CLASSES:
        tp = sum(t == c and p == c for t, p in zip(truth, pred))
        fp = sum(t != c and p == c for t, p in zip(truth, pred))
        fn = sum(t == c and p != c for t, p in zip(truth, pred))
        if tp + fn:
            f1s.append(2 * tp / max(2 * tp + fp + fn, 1))
    recognised = [(t, p) for t, p in zip(truth, pred) if t != "other"]
    rec = sum(t == p for t, p in recognised) / max(len(recognised), 1)
    print(f"{name:<28} accuracy {acc:.0%}   macro-F1 {sum(f1s) / len(f1s):.2f}   recall on real categories {rec:.0%}   predicted Other {pred.count('other') / len(pred):.0%}")


class VisionHead(torch.nn.Module):
    def __init__(self, models, head):
        super().__init__()
        _, model, text_features, prompts = models.vision
        self.encoder = model.vision_model
        self.head = head
        names = list(prompts)
        start = sum(len(prompts[k]) for k in names[: names.index("talking_head")])
        self.register_buffer("talking", text_features[start : start + len(prompts["talking_head"])].T.contiguous())
        self.register_buffer("scale", model.logit_scale.exp().detach())
        self.register_buffer("bias", model.logit_bias.detach())

    def forward(self, pixels):
        f = torch.nn.functional.normalize(self.encoder(pixel_values=pixels).pooler_output, dim=-1)
        probs = self.head(f * 10).softmax(dim=-1)
        talking = ((f @ self.talking) * self.scale + self.bias).sigmoid().amax(dim=1, keepdim=True)
        return torch.cat([probs, talking], dim=1)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--data", type=Path, default=HERE / "data/reels")
    parser.add_argument("--export", action="store_true")
    parser.add_argument("--folds", type=int, default=5)
    args = parser.parse_args()
    torch.manual_seed(0)
    random.seed(0)

    rows = load(args.data)
    dist = {c: sum(r["label"] == c for r in rows) for c in CLASSES}
    print(f"{len(rows)} labelled reels:", ", ".join(f"{c} {n}" for c, n in sorted(dist.items(), key=lambda kv: -kv[1]) if n))
    models = LocalModels(label_descriptions([]), HERE / "out/caption-nli", HERE / "out/siglip2-base-patch16-224", None)
    x, zeroshot, captions = featurise(models, args.data, rows)
    y = torch.tensor([CLASSES.index(r["label"]) for r in rows])
    truth = [r["label"] for r in rows]

    order = list(range(len(rows)))
    random.shuffle(order)
    head_frames = [None] * len(rows)
    for k in range(args.folds):
        test = order[k :: args.folds]
        train_idx = [i for i in order if i not in set(test)]
        head = train(x[train_idx], y[train_idx])
        with torch.no_grad():
            probs = head(x[test] * 10).softmax(dim=-1)
        for i, p in zip(test, probs.tolist()):
            head_frames[i] = dict(zip(CLASSES, p))

    report("before (zero-shot, shipped)", truth, [pipeline(r["sponsored"], c, z) for r, c, z in zip(rows, captions, zeroshot)])
    report("after (trained head)", truth, [pipeline(r["sponsored"], c, h) for r, c, h in zip(rows, captions, head_frames)])
    report("trained head, frame only", truth, [max(h, key=h.get) for h in head_frames])

    if args.export:
        from export_onnx import export

        head = train(x, y)
        out = HERE / "out/phone"
        pixels = torch.linspace(-1, 1, 3 * 224 * 224).reshape(1, 3, 224, 224)
        export(VisionHead(models, head).eval(), (pixels,), ["pixels"], out / "vision.onnx", ["MatMul", "Gemm"])
        meta = {"labels": [*CLASSES, "talking_head"], "calibrated": True, "trained_on": len(rows),
                "other_threshold": THRESHOLDS["other_threshold"]}
        (out / "vision.json").write_text(json.dumps(meta))
        print("exported", out / "vision.onnx")


if __name__ == "__main__":
    main()
