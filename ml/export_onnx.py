"""Build the local int8 vision and caption models used by the Android timing harness."""

import argparse
import json
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
import torch
from onnxruntime.quantization import QuantType, quantize_dynamic
from reel_classifier import HERE, THRESHOLDS, LocalModels, label_descriptions


class VisionScores(torch.nn.Module):
    def __init__(self, models):
        super().__init__()
        _, model, text_features, prompts = models.vision
        self.encoder = model.vision_model
        self.register_buffer("text_features", text_features.T.contiguous())
        self.register_buffer("scale", model.logit_scale.exp().detach())
        self.register_buffer("bias", model.logit_bias.detach())
        self.sizes = [len(phrases) for phrases in prompts.values()]

    def forward(self, pixels):
        features = torch.nn.functional.normalize(self.encoder(pixel_values=pixels).pooler_output, dim=-1)
        probs = ((features @ self.text_features) * self.scale + self.bias).sigmoid()
        return torch.stack([group.amax(dim=1) for group in probs.split(self.sizes, dim=1)], dim=1)


class CaptionScores(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model
        self.entailment = model.config.label2id["entailment"]

    def forward(self, input_ids, attention_mask):
        logits = self.model(input_ids=input_ids, attention_mask=attention_mask).logits
        return logits.softmax(dim=-1)[:, self.entailment]


def export(module, inputs, names, output, quantized_ops, dynamic=None):
    module.eval()
    temp = output.with_suffix(".fp32.onnx")
    with torch.inference_mode():
        expected = module(*inputs).numpy()
        torch.onnx.export(
            module,
            inputs,
            temp,
            input_names=names,
            output_names=["scores"],
            dynamic_axes=dynamic,
            dynamo=False,
            opset_version=17,
        )
    quantize_dynamic(temp, output, weight_type=QuantType.QInt8, op_types_to_quantize=quantized_ops)
    options = ort.SessionOptions()
    options.intra_op_num_threads = 4
    session = ort.InferenceSession(str(output), options, providers=["CPUExecutionProvider"])
    (actual,) = session.run(None, {name: value.numpy() for name, value in zip(names, inputs, strict=True)})
    if actual.shape != expected.shape or not np.isfinite(actual).all():
        raise ValueError(f"invalid exported scores in {output}")
    print(
        f"{output.name}: {output.stat().st_size:,} bytes; fixture max score delta {np.max(np.abs(actual - expected)):.5f}"
    )
    temp.unlink()


def export_vocab(text_dir, output):
    """Unigram vocabulary for the app's Kotlin tokenizer (replaces the onnxruntime-extensions graph, whose
    native libraries are not 16 KB page-aligned). Format, little-endian:
    int32 count, then per piece (in token-id order): float32 score, uint16 byte length, UTF-8 bytes."""
    import struct

    model = json.loads((text_dir / "tokenizer.json").read_text())["model"]
    if model["type"] != "Unigram":
        raise ValueError("expected a Unigram tokenizer")
    with open(output, "wb") as f:
        f.write(struct.pack("<i", len(model["vocab"])))
        for piece, score in model["vocab"]:
            data = piece.encode()
            f.write(struct.pack("<fH", score, len(data)) + data)
    print(f"{output.name}: {output.stat().st_size:,} bytes, {len(model['vocab'])} pieces")


def export_tokenizer(text_dir, output):
    """SentencePiece with XLM-R's Fairseq ID mapping, returning plain tokens for one caption."""
    from onnx import TensorProto as T
    from onnx import helper

    constants = [
        helper.make_tensor("nbest", T.INT64, [1], [0]),
        helper.make_tensor("alpha", T.FLOAT, [1], [0]),
        helper.make_tensor("off", T.BOOL, [1], [False]),
        helper.make_tensor("on", T.BOOL, [1], [True]),
    ]
    tokenize = helper.make_node(
        "SentencepieceTokenizer",
        ["text", "nbest", "alpha", "off", "off", "off", "on"],
        ["ids", "rows", "offsets"],
        domain="ai.onnx.contrib",
        model=(text_dir / "sentencepiece.bpe.model").read_bytes(),
    )
    graph = helper.make_graph(
        [tokenize],
        "caption_tokenizer",
        [helper.make_tensor_value_info("text", T.STRING, [1])],
        [helper.make_tensor_value_info("ids", T.INT32, [None])],
        initializer=constants,
    )
    model = helper.make_model(
        graph, opset_imports=[helper.make_opsetid("", 17), helper.make_opsetid("ai.onnx.contrib", 1)], ir_version=8
    )
    onnx.save(model, output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stage", choices=("caption", "vision"), required=True)
    parser.add_argument("--output", type=Path, default=HERE / "out/phone")
    parser.add_argument("--vision-model", type=Path, default=HERE / "out/siglip2-base-patch16-224")
    parser.add_argument("--text-model", type=Path, default=HERE / "out/caption-nli")
    parser.add_argument("--custom-label", action="append", default=[])
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    torch.set_num_threads(4)
    labels = label_descriptions(args.custom_label)
    models = LocalModels(labels, args.text_model, args.vision_model, None)
    metadata = {"labels": list(labels), "other_threshold": THRESHOLDS["other_threshold"]}
    if args.stage == "vision":
        model = VisionScores(models)
        metadata["labels"] += ["talking_head", "background"]  # graph groups; calibration happens in the app
        pixels = torch.linspace(-1, 1, 3 * 224 * 224).reshape(1, 3, 224, 224)
        export(model, (pixels,), ["pixels"], args.output / "vision.onnx", ["MatMul", "Gemm"])
    else:
        tokenizer, model = models.text
        caption = "This is a video about cooking a meal."
        hypotheses = [f"This video is about {topic}." for topic, _ in labels.values()]
        inputs = tokenizer([caption] * len(labels), hypotheses, padding=True, return_tensors="pt")
        names = ["input_ids", "attention_mask"]
        metadata["hypothesis_ids"] = tokenizer(hypotheses, add_special_tokens=False)["input_ids"]
        metadata["caption"] = caption
        export_vocab(args.text_model, args.output / "caption_vocab.bin")
        export(
            CaptionScores(model),
            tuple(inputs[name] for name in names),
            names,
            args.output / "caption.onnx",
            ["Gather"],
            dynamic={name: {0: "labels", 1: "tokens"} for name in names} | {"scores": {0: "labels"}},
        )
    (args.output / f"{args.stage}.json").write_text(json.dumps(metadata))


if __name__ == "__main__":
    main()
