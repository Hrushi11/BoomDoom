# BoomDoom reel classification — end-to-end decision and test report

**Decision · 21 November 2026.** For live Instagram Reels, classify the accessible caption first. If it is missing or ambiguous, pass the first usable frame from Android MediaProjection directly to SigLIP 2 through an in-memory ImageReader. Use later frames, on-screen text and optional speech to resolve uncertain cases. A cold start can exceed 500 ms on its own, so prepare the models when the user enables content understanding.

**Status.** The team fixture classifier runs end to end on desktop. The standalone Pixel 9 harness runs raw caption and frame inference on the phone. The installed monitoring app counts reels and currently stores `other` for each one. The live path from an Instagram transition through consented capture, inference, Room and Insights is the remaining integration. [Watcher](../android/monitor/src/main/kotlin/app/boomdoom/monitor/watcher/ReelWatcherService.kt) · [Monitor controller](../android/monitor/src/main/kotlin/app/boomdoom/monitor/MonitorController.kt) · [Capture service](../android/monitor/src/main/kotlin/app/boomdoom/monitor/capture/MonitorService.kt)

## What the measurements establish

### Pixel 9, on-device CPU inference

Four intra-op threads, eight built-in categories, one cooking caption and one public flower clip. Each workload ran once after model startup and 20 more times with the model warm. Cold time below is setup plus first inference within the benchmark process. The ImageReader received 448 × 800 RGBA frames from a synthetic producer. Its timing includes producing and acquiring the frame, copying the pixels and classifying it. MP4 timings include frame retrieval and inference. [Android harness](android/ReelBenchmark.java)

| Path | Warm median | Warm p95 | Warm runs under 500 ms | Cold time |
|---|---:|---:|---:|---:|
| Raw caption, including tokenization | 39 ms | 41 ms | 20/20 | 923 ms |
| One in-memory ImageReader frame | 80 ms | 83 ms | 20/20 | 563 ms |
| One frame decoded from a local MP4 | 426 ms | 481 ms | 19/20 | 816 ms |
| Three frames decoded from a local MP4 | 1,972 ms | 2,099 ms | 0/20 | 2,297 ms |

The caption produced **Food** with a score of 0.915. The flower produced **Other**, the expected category for that fixture. The in-memory frame path saved **346 ms at the median** relative to reading one frame from the MP4. These measurements start with fixture input available. Time waiting for Instagram to expose a caption or draw a new reel is still unknown.

### Fetching or recording five seconds

| Experiment | Observed result | Consequence for BoomDoom |
|---|---|---|
| Full download of the 1.13 MB public flower clip | 890 ms for transfer alone, one desktop request | Even a small asset can exhaust the first-label budget before decoding starts. |
| Five-second range preview of the 11.05 MB W3C trailer | 1.09 MB fetched, about 9.9% of the file. Warm transfer plus inference was 782 ms median and 948 ms p95 across five fresh-cache requests. | Range reads save bandwidth when a direct, byte-range-enabled MP4 URL is available. The accessibility watcher receives no such URL. Use this path for team-supplied test media. |
| Proposed MediaProjection → MediaCodec → temporary MP4 | Finishing a five-second live recording requires five seconds of playback before file-based classification. This is an acquisition delay, independent of model speed. | Connect the projection’s virtual display to an ImageReader and process frames as they arrive. This also keeps live feed pixels off disk. |

The preview decoder may request the index at the end of an MP4. Five seconds limits **playback time inspected**. Bytes transferred depend on encoding, container layout and server range support. [Range reader](preview.py) · [Android MediaProjection guide](https://developer.android.com/media/grow/media-projection)

### Desktop routing, speech and accuracy

Each timing row below covers 11 warm repetitions of one fixture. The speech fixture used a synthetic English recipe narration and blank frames. Audio was already available locally. The 66 ms includes the visual check, speech recognition and transcript classification. A live user must first play the audio.

| Input and exercised route | Result | Warm median | Warm p95 |
|---|---|---:|---:|
| Explicit cooking caption | Food from the caption | 8 ms | 13 ms |
| Inconclusive cooking caption paired with unrelated flower footage | Other after caption and frame fusion | 234 ms | 235 ms |
| Flower video without a caption | Other from frames | 222 ms | 225 ms |
| Blank video with recipe narration | Food from speech | 66 ms | 66 ms |

The labelled smoke set returned **3/3** on recheck, covering two Food fixtures and one Other fixture. It produced zero flags, so flag precision is undefined. All **12 ML tests** passed, covering routing, thresholds, custom labels, range reads, metrics and the five-second media boundary. The Android debug build, monitor tests, JVM fusion tests and feed-isolation check also passed during the benchmark work. The smoke fixtures establish working signal paths. Reel accuracy, including Hinglish and rage-bait precision, requires independent labels. [Classifier](reel_classifier.py) · [Evaluation](eval.py)

### Model decision

| Component | Decision and evidence |
|---|---|
| Caption and transcript | Start with the tested multilingual MiniLM NLI checkpoint. It scores a text hypothesis for each category. Its Android export uses a 139.6 MB model and a 5.1 MB SentencePiece tokenizer graph. Full dynamic int8 quantization changed the cooking fixture to Other on the Pixel. Quantizing the embedding tables and retaining floating-point transformer operations restored Food. The product spec’s EmbeddingGemma candidate has no comparable measurement in this report. |
| Vision | Use SigLIP 2 base/16 at 224 px with cached label-prompt embeddings and quantized matrix operations. The tested fixed-label graph is 95.8 MB. The app needs a way to refresh prompt embeddings when a user changes a custom label. |
| Speech and OCR | Shruti’s Hindi/English int8 ONNX model worked on the desktop narration. Its phone latency and Hinglish accuracy remain open. ML Kit Latin/Devanagari OCR is a specified fallback for on-screen text and still needs integration and timing. |
| MobileCLIP2-S0 and S2 | Apple reports image-encoder latency of 1.5 ms and 3.6 ms respectively, with ImageNet zero-shot top-1 accuracy of 71.5% and 77.2%. These are publisher figures. Apple’s public weights license excludes product development, and BoomDoom’s spec explicitly excludes internal MobileCLIP2 benchmarks. Neither variant was run on the Pixel. |

Sources for the model comparison and export are listed here. [MiniLM](https://huggingface.co/MoritzLaurer/multilingual-MiniLMv2-L6-mnli-xnli) · [SigLIP 2](https://huggingface.co/google/siglip2-base-patch16-224) · [Shruti](https://huggingface.co/udgamlabs/Shruti-ONNX) · [ONNX export](export_onnx.py) · [Apple’s benchmarks](https://github.com/apple/ml-mobileclip#evaluation) · [Apple’s weights license](https://github.com/apple/ml-mobileclip/blob/main/LICENSE_MODELS) · [Product spec §7.1](../BoomDoom_Product_Spec.md)

## Recommended live solution

1. **Start with consent and a reel identity.** When content understanding is enabled, request MediaProjection consent from the activity, start the projection foreground service and create one virtual display backed by an RGBA ImageReader. Warm the classifier off the UI thread. On each accepted Instagram reel transition, assign a stable reel ID and record the monotonic start time. Reel counting and the budget bar continue independently of inference. Without capture consent, retain the spec’s counting-only mode.
2. **Try the caption immediately.** Read only the visible reel’s caption and hashtags if Instagram exposes them. Return a first category when the top MiniLM score reaches 0.80 and leads the runner-up by at least 0.15. An absent caption should incur no polling delay before visual analysis. The watcher currently inspects layout only, and its controller discards `CaptionRead`.
3. **Use the first usable frame.** Acquire the latest frame belonging to the new reel, scale it to 224 × 224, score the visual prompts and release the image. Reject stale, blank and near-duplicate frames. If uncertainty remains, sample changed frames near seconds 2 and 4 and read large on-screen captions with OCR where available. The tech spec’s suggested 700 ms first-frame delay already exceeds the first-label target. Use that timing for a retry if the transition frame is stale. Verify frame ownership during real swipes.
4. **Escalate to speech selectively.** With audio understanding enabled, playback-capture permission granted and a supported audio stream, capture 16 kHz mono audio for the remainder of the opening five-second window. Trigger speech when the talking-head score reaches 0.60 or the best fused score is below 0.55. Start capture when the trigger fires. Transcribe in memory, classify the transcript, then release the buffers. Blocked or silent playback leaves the available caption, frame and OCR evidence usable. Five seconds is the requested trial window. The original spec proposes 10–15 seconds, so measure how much topic accuracy the shorter window costs on Hinglish reels.
5. **Fuse and finalize exactly once.** Average frame scores across samples and take the maximum talking-head score. Combine only available signals, with weights of 0.45 for frames, 0.25 for captions, 0.15 for OCR and 0.35 for speech. Reduce frame weight to 0.20 for a talking head and renormalize. A best score below 0.45 yields Other. Keep up to two secondary labels at 0.60 or above. Show a flag only for a user-flagged primary category at 0.70 or above. These are starting thresholds for calibration. [Threshold defaults](../config/ml_thresholds.json) · [Android fusion](../android/ml/fusion/src/main/kotlin/app/boomdoom/ml/fusion/Fusion.kt)
6. **Deliver the result to the correct reel.** Pass the same reel ID through every callback. Finalize on swipe, exit or the five-second deadline using the evidence then available. If speech is still running at the deadline, use the completed signals. Bound pending work to two reels and discard stale callbacks. Persist primary and secondary labels, score, signal mask and flag in Room. Update reel, session and daily aggregates transactionally so a refinement or retry cannot count a second reel. Insights reads those local records. Pause capture on exit, lock, lesson playback or consent revocation. Apply the spec’s thermal and low-battery limits. [Tracking repository](../android/core/data/src/main/kotlin/app/boomdoom/core/data/repo/TrackingRepository.kt) · [Tech spec §§4–6](../BoomDoom_Tech_Spec.md)

The live outputs are Comedy, Fitness, Education, Food, News, Finance, Rage-bait and Ads, plus Other and up to two custom labels. The internal talking-head signal only controls routing. Raw feed frames, captions, audio and transcripts stay in memory. Production traffic for model delivery or lessons must carry no feed content.

## Android POC implementation notes (21 Nov 2026)

- **Tokenizer:** the caption model is tokenized in Kotlin (`android/ml/text/.../UnigramTokenizer.kt`) instead of the
  `onnxruntime-extensions` graph, because extensions 0.13.0 ships 4 KB-aligned native libraries that fail Android 15's
  16 KB page check. It matches Hugging Face on 249/249 reference texts (3,607 tokens), including Devanagari, romanized
  Hinglish and emoji. `export_onnx.py --stage caption` now writes `caption_vocab.bin` in place of `tokenizer.onnx`;
  ONNX Runtime is 1.23.2 (16 KB-aligned).
- **Frame score calibration:** SigLIP's per-label sigmoid is near zero even for an obvious match (clear food
  photos: 0.009 and 0.001), so it can never pass the fusion thresholds. Both the app (`FrameClassifier.calibrate`)
  and `reel_classifier.calibrate_frame` recover the group logits, take a softmax over all of them, and multiply by
  a soft gate centred at a top logit of −9.
- **Background group and the rage-bait rule (after the first Pixel 9 run):**
  - The first Pixel run classified 11 real reels: 10 came out Other at low confidence, and 1 came out News at 0.47.
  - On Reels-styled frames, selfies, pets and scenery had no category of their own, so they drifted into rage-bait
    (0.4–0.7).
  - The vision graph now has a `background` group (11 "none of these" prompts in `BACKGROUND`).
  - Frames never vote rage-bait, because a single frame can't show tone. Rage-bait stays in the softmax only as a
    sink, and comes from captions, and later from speech.
  - The app classifies only the video area. It cuts away 8% at the top, 22% at the bottom and 13% on the right,
    where Instagram draws its buttons and text. Without the crop, a clear food frame scored 0.61 instead of 0.93.
  - Measured on 12 Reels-styled frames with the int8 model:
    - food 0.90 and a rally speech News 0.84;
    - selfies, a pet, scenery and a cartoon → Other;
    - two group-of-people shots still read as News (0.63–0.71).
  - Re-tune on the labelled 100-reel set.
- **Verified on the emulator against the AWS backend:**
  - Decisive caption: food 0.915 from the caption alone, the same as the Pixel benchmark.
  - Weak caption ("…paneer tikka recipe…" scores food 0.544) over a non-food screen: Other 0.195. This matches the
    Python reference (0.194).
  - No caption, food photo on screen: food 0.653 from the frame alone.
- **Delivery:** `ml/publish_models.py` uploads the five files with SHA-256 to `models/<version>/`. The backend serves them
  at `GET /v1/models`, and the app downloads them on Wi-Fi and verifies the hashes.

## Proving the live implementation

| Milestone | Evidence required |
|---|---|
| Watcher → first label | On consented team devices, measure from the **detected reel transition** to the first category, including input wait. Record input-ready and inference-complete timestamps to locate delays. Report warm p50/p95, cold starts, the fraction of all reels receiving a recognized label within 500 ms, and accuracy of those early labels. Include abstentions and unavailable captions in the denominator. |
| First label → final record → Insights | Follow a caption-only reel, ambiguous reel, talking head and unsupported reel through Room and Insights. Verify one count per accepted transition on rapid swipes, correct flags, no late label on a later reel, and cleanup when the screen locks or sharing ends. Measure finalization against the spec’s five-second target separately from first-label latency. |
| General and Hinglish accuracy | Freeze prompts and thresholds on a separate development set. Evaluate the same 100 hand-labelled reels with and without captions. Target at least 75 correct primary labels. On the separate 50-reel Hinglish set, compare frames/captions with the added speech signal. Report per-label recall, confusion, Other rate and flag precision with sample counts. Both held-out sets are pending. |
| Device and privacy checks | Run low-end, mid-range and flagship devices. Check the spec’s ≤150 ms frame budget, ≤350 MB monitoring RAM and ≤5% added battery drain per hour. Exercise denied/revoked consent, blocked audio and battery saver. Verify storage and network traffic during a real session as well as the existing dependency isolation check. |

The first integration milestone is **one real reel producing one correct local row visible in Insights**. Start with a decisive caption, repeat with a captionless reel using ImageReader, then enable OCR and speech as their device tests pass. [UX acceptance flow US-13](../UX.md) · [Tech spec validation matrix](../BoomDoom_Tech_Spec.md)

## Run locally

Run these commands from the repository root with Python 3.11. Model downloads are a one-time setup. Reel inference uses local model directories. Test media belongs under the git-ignored `ml/data/` directory.

```bash
python3.11 -m venv ml/.venv
source ml/.venv/bin/activate
pip install -r ml/requirements.txt
hf download MoritzLaurer/multilingual-MiniLMv2-L6-mnli-xnli --revision 0a71e92a985b6e1ad1828cf67ce9c459639c1dca --include '*.json' 'model.safetensors' 'sentencepiece.bpe.model' --exclude 'onnx/*' --local-dir ml/out/caption-nli
hf download google/siglip2-base-patch16-224 --revision 75de2d55ec2d0b4efc50b3e9ad70dba96a7b2fa2 --include '*.json' 'model.safetensors' 'tokenizer.model' --local-dir ml/out/siglip2-base-patch16-224
hf download udgamlabs/Shruti-ONNX --revision a464a502cc732e4335313869e1ccf393ff8ba56b --include 'hi/onnx-int8/*' --local-dir ml/out/shruti-hi

python ml/reel_classifier.py --caption "This is a video about cooking a meal." --repeat 5
python ml/reel_classifier.py ml/data/reel.mp4 --custom-label Cricket --speech-model ml/out/shruti-hi/hi/onnx-int8
python -m unittest discover -s ml -p 'test_*.py'
```

### Test a partial download

```bash
python ml/reel_classifier.py --media-url https://media.w3.org/2010/05/bunny/trailer.mp4 --repeat 6
```

Supply a direct HTTPS MP4 URL that accepts byte-range requests. The benchmark fetches 256 KiB blocks into memory as the decoder requests them. A file with its index at the end can require a range from the tail. A decisive caption fetches zero media bytes. The JSON output includes transferred bytes, total resource size, fetch time and end-to-end classification time. Fresh range caches are used on every repeat. Network time is already included in the total. An Instagram share link points to a webpage. The Android watcher receives no downloadable media URL.

## Measure on Android

The standalone harness runs raw caption tokenization, eight-label inference, and three visual workloads on an arm64 Android device. One uses an actual 448 × 800 RGBA ImageReader driven by a synthetic producer. Two others decode one or three frames from a local MP4. Every visual sample in the timing harness goes through the encoder, so duplicate-frame filtering cannot artificially lower the result. It prints model startup separately. Run 1 includes first-use overhead. Later runs reuse the loaded model. The harness stages and removes its own files under a unique ADB temporary directory.

```bash
python ml/export_onnx.py --stage caption
python ml/export_onnx.py --stage vision
curl -fLo ml/out/onnxruntime-android-1.23.2.aar https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.23.2/onnxruntime-android-1.23.2.aar
curl -fLo ml/out/onnxruntime-extensions-android-0.13.0.aar https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime-extensions-android/0.13.0/onnxruntime-extensions-android-0.13.0.aar
mkdir -p ml/data/smoke
curl -fLo ml/data/smoke/flower.mp4 https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4
# Set ANDROID_HOME and replace DEVICE with the serial from adb devices.
ml/android/benchmark.sh DEVICE ml/out/onnxruntime-android-1.23.2.aar ml/out/onnxruntime-extensions-android-0.13.0.aar ml/out/phone ml/data/smoke/flower.mp4 21
```

The harness uses Android SDK platform 35, build tools 34.0.0, a JDK and the activated Python environment. The export compares local runtime scores with PyTorch on a fixture. The per-run JSONL from the reported Pixel session is available in this workspace at `ml/out/phone/pixel9-results.jsonl`. Redirect the benchmark command’s standard output to a new file to retain a repeat run.

## Measure accuracy

Use a JSONL manifest with one hand-labelled record per reel. Paths are relative to the manifest. Ground truth is a built-in or configured custom label, or `other`. Use separate manifests for the 100-reel general set and the 50-reel Hinglish set.

```json
{"id":"team-001","video":"clips/001.mp4","caption":"A recipe for masala dosa","label":"food"}
```

```bash
python ml/eval.py ml/data/held-out.jsonl --speech-model ml/out/shruti-hi/hi/onnx-int8
python ml/eval.py ml/data/held-out.jsonl --speech-model ml/out/shruti-hi/hi/onnx-int8 --without-captions
```

Compare accuracy and latency on the same reels to measure the contribution of captions. The report includes per-label recall, a confusion matrix, flag precision, abstention-threshold sweeps and p50/p95 by path. A small smoke run checks wiring. The product target is 75% accuracy on the held-out 100 reels.

The offline labelling, synthetic-data generation and trained-head scripts are still scaffolds.
