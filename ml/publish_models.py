"""Publish the on-device classification models where the backend serves them (GET /v1/models).

    python ml/publish_models.py --local backend/.data/storage            # local run_local.py backend
    python ml/publish_models.py --bucket <lessons_bucket> --region us-west-2   # AWS (credentials in env)

Uploads ml/out/phone/{caption.onnx, caption.json, caption_vocab.bin, vision.onnx, vision.json} under
models/<version>/ and writes models/manifest.json with each file's SHA-256 and size. The app downloads on
Wi-Fi and verifies the hashes (Tech Spec §5.9). The old tokenizer.onnx is not published: tokenization now runs
in Kotlin because onnxruntime-extensions' native libraries are not 16 KB page-aligned.
"""

import argparse
import hashlib
import json
import shutil
from datetime import UTC, datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
FILES = ["caption.onnx", "caption.json", "caption_vocab.bin", "vision.onnx", "vision.json"]


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--source", type=Path, default=HERE / "out/phone")
    ap.add_argument("--version", default=datetime.now(UTC).strftime("%Y.%m.%d-%H%M"))
    dest = ap.add_mutually_exclusive_group(required=True)
    dest.add_argument("--local", type=Path, help="Local storage root of the backend (LOCAL_STORAGE_DIR)")
    dest.add_argument("--bucket", help="S3 bucket (the backend's LESSONS_BUCKET)")
    ap.add_argument("--region", default="us-west-2")
    args = ap.parse_args()

    files = []
    for name in FILES:
        path = args.source / name
        if not path.exists():
            raise SystemExit(f"missing {path}; run ml/export_onnx.py --stage caption/vision first")
        files.append({"name": name, "key": f"models/{args.version}/{name}", "sha256": sha256(path),
                      "size": path.stat().st_size})
    manifest = {"version": args.version, "total_bytes": sum(f["size"] for f in files), "files": files}

    if args.local:
        for f in files:
            target = args.local / f["key"]
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(args.source / f["name"], target)
        (args.local / "models/manifest.json").write_text(json.dumps(manifest, indent=2))
    else:
        import boto3

        s3 = boto3.client("s3", region_name=args.region)
        for f in files:
            s3.upload_file(str(args.source / f["name"]), args.bucket, f["key"])
            print("uploaded", f["key"])
        s3.put_object(Bucket=args.bucket, Key="models/manifest.json", Body=json.dumps(manifest, indent=2).encode(),
                      ContentType="application/json")
    print(f"published models {args.version}: {manifest['total_bytes'] / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
