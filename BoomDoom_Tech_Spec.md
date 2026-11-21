# BoomDoom — End-to-End Technical Specification

**Version:** 1.0  
**Date:** 21 November 2026  
**Status:** Draft for hackathon build and beta  
**Related documents:** `BoomDoom_Product_Spec.md` (what and why), `UX.md` v1.1 (user stories and screens, referenced as US-xx), `prompts_for_design.md` (visual exploration)

---

## 0. Scope and conventions

This document describes **how** BoomDoom is built end to end: the Android app, on-device machine learning, the lesson backend on AWS, lesson generation with OpenAI and Stickman Studio, infrastructure, security, testing and the hackathon build plan.

- **MVP** = hackathon build. **Beta/v1** = after the hackathon.
- **TBD (spike Sx)** marks values that depend on the phone tests in Product Spec §15.3 (S1–S5).
- All feed analysis runs **on the phone**. The backend only ever sees learning preferences and lesson events. This rule overrides any convenience trade-off in this document.

---

## 1. System overview

```
┌──────────────────────────── Android phone ────────────────────────────┐
│                                                                        │
│  Reels app (third party)                                               │
│      │ accessibility events        │ screen frames      │ media audio  │
│      ▼                             ▼                    ▼              │
│  ReelWatcher (AccessibilityService)  FrameSampler     AudioSnipper     │
│      │                             │ (MediaProjection) (PlaybackCapture)│
│      ▼                             ▼                    ▼              │
│  SessionEngine ──► Classifier pipeline (SigLIP 2, ML Kit OCR,          │
│      │             speech-to-text, EmbeddingGemma, fusion)             │
│      │                             │                                   │
│      ▼                             ▼                                   │
│  OverlayManager (bar, tag,     Room DB (reels, sessions, labels,       │
│  check-in, budget cards)       lessons cache, outbox)                  │
│                                    │                                   │
│  Compose UI: Home · Learn · Insights · You · Lesson player             │
│                                    │                                   │
│  LessonSync (WorkManager) ─────────┼──── HTTPS (JWT) ───────────┐      │
└────────────────────────────────────┼────────────────────────────┼──────┘
                                     │ no feed data, ever         │
┌──────────────────────────── AWS (ap-south-1) ──────────────────┼──────┐
│  CloudFront ◄── S3 (lesson videos, thumbnails, manifests)       │      │
│                                                                 ▼      │
│  ALB ──► API (FastAPI on ECS Fargate) ──► RDS PostgreSQL               │
│                │                                                       │
│                ├──► SQS: script-jobs ──► Script worker (Fargate) ──► OpenAI API
│                └──► SQS: render-jobs ──► Render workers (EC2 Linux,    │
│                                          Stickman Studio fork) ──► S3  │
│  Cognito (optional login, P1) · Secrets Manager · CloudWatch · FCM     │
└────────────────────────────────────────────────────────────────────────┘
```

### 1.1 Main flows

| Flow | Path |
|---|---|
| Track | Accessibility events → SessionEngine → reel count → overlay bar → Room |
| Understand | Frames (+ audio snippet, OCR, captions) → classifier pipeline → label → Room → Insights |
| Intervene | SessionEngine milestone → check-in rules → overlay card → lesson player |
| Learn | App requests feed → API returns ready lessons → app prefetches MP4 + manifest → player |
| Generate | Profile/topic saved → API → script job (OpenAI) → render job (Stickman Studio) → S3 → catalog `ready` → push |

### 1.2 Key decisions (from Product Spec §15)

| Area | Decision |
|---|---|
| Platform | Android only (min SDK 29, target SDK 35) |
| Default budget | 300 reels/day; check-in every 15 reels; reset at 4:00 AM |
| Frame classifier | SigLIP 2 (Apache 2.0). MobileCLIP2 not used (research-only license) |
| Speech | Shruti or lipi-hn for Hindi/Hinglish (spike S4) |
| Text embeddings | EmbeddingGemma (candidate, spike S5) |
| Descriptions/summaries | LFM2-VL-450M via llama.cpp, plus a deterministic template fallback |
| Lesson scripts | OpenAI: `gpt-6.2-sol` (syllabus), `gpt-6.1-terra` (script), `gpt-6-luna` (QA) |
| Lesson rendering | Stickman Studio fork, Cinematic Illustrated mode, one video per lesson, Linux workers |
| Cloud | AWS, region `ap-south-1` (Mumbai) for latency to Indian users |
| Accounts | Anonymous by default; optional login (Cognito: Google, phone OTP) in P1 |

---

## 2. Technology stack

### 2.1 Android app

| Concern | Choice |
|---|---|
| Language | Kotlin 2.x, coroutines + Flow |
| UI | Jetpack Compose, Material 3 (custom BoomDoom theme) |
| Architecture | MVVM with unidirectional data flow; repositories over Room/DataStore/network |
| DI | Hilt |
| Local storage | Room (SQLite) for records; Proto DataStore for settings |
| Background work | WorkManager (sync, prefetch, retraining, cleanup); foreground service for monitoring |
| Networking | Retrofit + OkHttp + kotlinx.serialization |
| Video | AndroidX Media3 ExoPlayer, with on-disk lesson cache |
| On-device ML | ONNX Runtime Mobile (SigLIP 2, EmbeddingGemma, classifier heads); llama.cpp via NDK (LFM2-VL-450M); ML Kit Text Recognition v2 (bundled Latin + Devanagari); speech-to-text runtime per chosen model (spike S4) |
| Charts | Compose Canvas (donut, bars) — no heavy chart library |
| Push | Firebase Cloud Messaging |
| Crash reporting | Firebase Crashlytics (no feed data in logs; see §11) |
| Build | Gradle (Kotlin DSL), version catalogs, R8 minification |

### 2.2 Backend

| Concern | Choice |
|---|---|
| API | Python 3.12, FastAPI, Pydantic v2, Uvicorn |
| ORM / migrations | SQLAlchemy 2.x, Alembic |
| Database | PostgreSQL 16 on Amazon RDS |
| Queues | Amazon SQS (standard queues + dead-letter queues) |
| Workers | Python workers polling SQS (script worker on Fargate; render workers on EC2) |
| LLM | OpenAI Python SDK, Structured Outputs (JSON Schema) |
| Rendering | Stickman Studio fork (Python 3.11+, FFmpeg, Kokoro ONNX) in Docker |
| Storage / CDN | S3 + CloudFront (signed URLs) |
| Auth | Own JWTs for anonymous users; Amazon Cognito for optional login (P1) |
| IaC | Terraform |
| CI/CD | GitHub Actions → ECR → ECS / EC2 Auto Scaling |
| Observability | CloudWatch logs, metrics, alarms; structured JSON logs |

---

## 3. Android app architecture

### 3.1 Gradle modules

```
:app                      Application, navigation graph, Hilt setup
:core:designsystem        Theme, colours, typography, components (cards, chips, buttons)
:core:data                Repositories, Room DB, DataStore, network client
:core:model               Shared data classes (Reel, Session, Label, Lesson, ...)
:core:common              Time/day-boundary utils, dispatchers, result types
:monitor                  ReelWatcher (accessibility), SessionEngine, MonitorService (FGS),
                          FrameSampler, AudioSnipper, capability checks
:overlay                  OverlayManager, budget bar, flag tag, check-in & budget cards
:ml:vision                SigLIP 2 runtime, frame pre-processing, talking-head detector
:ml:text                  EmbeddingGemma runtime, text classifier, OCR wrapper (ML Kit)
:ml:speech                Speech-to-text runtime (Shruti / lipi-hn), audio pre-processing
:ml:fusion                Signal fusion, thresholds, personal heads, corrections
:ml:llm                   llama.cpp JNI bridge, LFM2-VL-450M summaries/descriptions
:feature:onboarding       US-01 → US-07, US-06A
:feature:home             US-09, US-10, US-32
:feature:learn            US-17 → US-23, lesson player, Learned, My Topics
:feature:insights         US-24 → US-26, US-24A
:feature:settings         US-27 → US-29, account link (P1), demo mode (US-34)
```

### 3.2 Layering rules

- `feature:*` modules depend on `core:*` and expose screens + ViewModels only.
- `monitor`, `overlay` and `ml:*` never depend on `feature:*`.
- Only `core:data` talks to the network. **No class in `monitor`, `overlay` or `ml:*` may depend on the network client** (enforced with a Gradle dependency rule / Konsist test). This makes the "feed never leaves the phone" rule structural, not just a convention.

### 3.3 Navigation

Single-activity app with Compose Navigation. Top-level destinations: `home`, `learn`, `insights`, `you`. Full-screen destinations (bottom bar hidden): `onboarding/*`, `player/{lessonId}`, `topic/add`, `settings/*`. Deep links: `boomdoom://player/{lessonId}` (notifications, overlay "Switch to lesson"), `boomdoom://fix-setup/{capability}` (US-32).

### 3.4 Process and services

| Component | Type | Lifetime |
|---|---|---|
| `ReelWatcherService` | `AccessibilityService` | While enabled by the user in system settings |
| `MonitorService` | Foreground service, type `mediaProjection` | While monitoring is active and capture consent is valid |
| `OverlayManager` | Singleton owned by `MonitorService`/`ReelWatcherService` | While monitoring is active |
| Workers | WorkManager | Scheduled (see §7.4) |

**Manifest permissions:** `BIND_ACCESSIBILITY_SERVICE` (on the service), `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `RECORD_AUDIO` (optional, audio understanding), `POST_NOTIFICATIONS`, `INTERNET`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED` (to show the "Resume monitoring" prompt after reboot).  
*Whether audio playback capture also needs the `microphone` foreground-service type on Android 14+ is to be confirmed in spike S3.*

---

## 4. Monitoring subsystem

### 4.1 ReelWatcher (AccessibilityService)

**Service config (`res/xml/reel_watcher.xml`):**

```xml
<accessibility-service
    android:accessibilityEventTypes="typeViewScrolled|typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:packageNames="com.instagram.android"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100"
    android:description="@string/a11y_description" />
```

**Responsibilities**
1. Detect when the reels app is in the foreground and the **Reels screen** is showing.
2. Detect **reel transitions** (one swipe = one new reel).
3. Read **caption, hashtags and creator name** from the node tree, if exposed (spike S2).
4. Emit events to `SessionEngine` through an in-process `SharedFlow`.

**Reels-screen detection (TBD spike S1):** match a combination of (a) foreground package, (b) presence of the Reels container node identified by view ID / class / content description, (c) full-screen vertical video layout. Selectors live in a **remote-configurable JSON** (`reels_selectors.json`, bundled default + downloaded override) so Instagram UI changes can be handled without an app release.

**Reel transition algorithm**

```
on TYPE_VIEW_SCROLLED in Reels container:
    if now - lastTransitionAt < 400 ms: ignore            // debounce fling/settle events
    candidate = now
    wait up to 600 ms for the scroll to settle
    if FrameSampler is active:
        confirm = frameDiff(previousFrame, currentFrame) > DIFF_THRESHOLD   // new video visible
    else:
        confirm = scroll delta ≥ 0.6 × screen height
    if confirm: emit ReelTransition(at = candidate); lastTransitionAt = candidate
```

- **Back-scroll** (user swipes back to a previous reel) is detected by scroll direction and does **not** increment the count.
- Target accuracy: **≥ 90% on a 100-reel manual test** (US-11). If S1 fails, fall back to frame-difference-only detection.

### 4.2 SessionEngine

Pure Kotlin state machine (unit-testable, no Android dependencies).

```
          ReelsVisible                      ReelsHidden
  IDLE ──────────────────► ACTIVE ───────────────────────► PAUSED
                             ▲  │                            │
                ReelsVisible │  │ DoneForNow / ScreenOff      │ 2 min elapsed /
                (< 2 min)    └──┼────────────────────────────┤ ScreenOff
                                ▼                            ▼
                              ENDED ◄─────────────────────────┘
```

**State it owns:** current session, today's reel count, check-in counter, back-off multiplier, snooze flag, over-budget flag.

**Check-in rules (US-03, US-15, US-16)**

```kotlin
fun nextCheckInAt(s: SessionState, cfg: Settings): Int {
    val base = if (s.overBudget) cfg.overBudgetInterval.coerceAtLeast(10)   // default 25
               else cfg.checkInInterval                                    // default 15
    return s.lastCheckInAtReel + base * s.backoffMultiplier                // 1, 2, 4 ...
}
// Continue tapped twice in a row in this session -> backoffMultiplier *= 2
// Snooze for this session -> no check-ins until ENDED
// Budget crossing -> show budget card once, then use overBudgetInterval
// New session -> backoffMultiplier = 1, snooze = false
```

**Day boundary:** a "day" runs from `resetTime` (default 04:00 local) to the next `resetTime`. `dayKey = (now - resetTime).toLocalDate()`. Records store both the timestamp and the `dayKey`. A timezone change recomputes only future day keys.

### 4.3 OverlayManager

All overlays use `WindowManager` with `TYPE_APPLICATION_OVERLAY` and a `ComposeView` hosted with its own `LifecycleOwner` / `SavedStateRegistryOwner`.

| Overlay | Flags | Size / position | Notes |
|---|---|---|---|
| Budget bar (US-12) | `FLAG_NOT_TOUCHABLE`, `FLAG_NOT_FOCUSABLE`, `FLAG_LAYOUT_IN_SCREEN` | 4 dp tall, full width, just below the status bar (respect display cutout) | Animates within 1 s of a transition; states normal / near (≥ 80%) / over (+N) |
| Flag tag (US-14) | Not touchable, not focusable | Small chip, top-left below the bar | Auto-hides after 2.5 s; shown only if confidence ≥ 0.7 |
| Speech label (US-13A) | Not touchable | Bottom-third chip | Demo moment; auto-hides after 4 s |
| Check-in card (US-15) | Touchable, focusable, dim behind | Centred card | Actions: Switch / Continue / Done / Snooze |
| Budget card (US-16) | Touchable, focusable, dim behind | Centred card | Actions: Lesson / Keep scrolling / Finish |
| In-feed lesson card (P1) | Touchable, full-screen opaque | Full screen | Swipe-up dismiss returns to the reels app |

- Opaque full-screen overlays are used for interactive cards so touches never "pass through" to the app underneath.
- **Switch to lesson** launches `boomdoom://player/{lessonId}` (apps holding the overlay permission may start activities from the background).
- **Done for now** sends the user to the launcher (`Intent.ACTION_MAIN` + `CATEGORY_HOME`) and ends the session.
- **Back to my reels** (US-21) launches the reels app's launch intent; exact reel restoration is not guaranteed.

### 4.4 FrameSampler (MediaProjection)

- `MediaProjectionManager.createScreenCaptureIntent()` from an activity; on consent, start `MonitorService` (foreground, type `mediaProjection`) and create **one** `VirtualDisplay` per `MediaProjection` (Android 14+ requires a new consent for each new projection).
- `ImageReader` at reduced resolution: short side **448 px** (enough for SigLIP input and OCR of large captions), `RGBA_8888`, 2 buffers.
- Sampling: **1 frame every 2 s** while the Reels screen is visible and the session is ACTIVE; additionally **1 frame ~700 ms after each reel transition** (first look at the new reel).
- **Skip unchanged frames:** 16×16 grayscale thumbnail, mean absolute difference < 6 → skip.
- Frames live only in memory, are converted to the model input tensor, and are released immediately. **No frame is written to disk.**
- If the user chose single-app sharing (Android 14+), frames simply stop when another app is in front.
- `MediaProjection.Callback.onStop()` → status `Needs re-consent`, show "Resume monitoring" prompt (US-07, US-32).

### 4.5 AudioSnipper (AudioPlaybackCapture)

- Only when audio understanding is enabled, `RECORD_AUDIO` is granted, and the fusion step requests speech for the current reel (talking-head or low confidence, §5.6).
- `AudioPlaybackCaptureConfiguration` built from the active `MediaProjection`, `addMatchingUsage(USAGE_MEDIA)` (+ `USAGE_UNKNOWN`), `AudioRecord` at **16 kHz mono PCM16**.
- Captures the **first 10–15 s** of the reel into an in-memory ring buffer; capture stops early on reel transition.
- If captured audio is silent for 3 s while the reel is playing, mark `audioCaptureBlocked = true` for the session (Instagram may block capture — spike S3) and fall back to OCR/captions.
- Audio buffers are zeroed and released after transcription.

### 4.6 Capability manager

Single source of truth for the monitoring status shown on Home (US-10, US-32).

| Capability | Check | Missing → status |
|---|---|---|
| Accessibility | `AccessibilityManager.getEnabledAccessibilityServiceList` contains ours | Setup required |
| Overlay | `Settings.canDrawOverlays()` | Limited (no bar/cards; counting continues) |
| Screen capture | Active `MediaProjection` | Limited (counting only, no categories) |
| Audio | `RECORD_AUDIO` + not blocked | Limited speech (OCR fallback) |
| Notifications | `POST_NOTIFICATIONS` | No impact on monitoring |

Status values: `ACTIVE`, `PAUSED`, `LIMITED(reasons)`, `SETUP_REQUIRED(reasons)`, `NEEDS_RECONSENT`. Re-evaluated on app resume, service connect/disconnect, and every 60 s while monitoring.

**Restricted settings (US-07):** if the Accessibility toggle cannot be enabled (detected when the user returns without enabling it twice), show the illustrated guide and deep-link to `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for BoomDoom.

### 4.7 Battery and thermal policy

| Condition | Behaviour |
|---|---|
| Battery saver on, or battery < 20% | Frame sampling every 4 s, no speech, no LFM2-VL |
| Battery < 10% | Counting only (no classification) |
| `PowerManager.thermalStatus ≥ THERMAL_STATUS_MODERATE` | Halve sampling; skip speech |
| `≥ THERMAL_STATUS_SEVERE` | Counting only |
| Charging + idle | Allowed: LFM2-VL descriptions, personal-head retraining, prefetch |

Budget: monitoring should add **≤ 5% battery per hour of scrolling** on a mid-range phone (measured in spike S5).

---

## 5. On-device ML pipeline

### 5.1 Pipeline per reel

```
ReelTransition ─► frames (1–8 per reel) ─► SigLIP 2 image embeddings ─┐
                 Accessibility caption/hashtags ─► EmbeddingGemma ────┤
                 OCR (ML Kit) on 1–2 frames ─► EmbeddingGemma ────────┤─► Fusion ─► label(s), confidence
   if talking-head or low confidence: audio 10–15 s ─► STT ─► text ──┘
```

The pipeline runs on a single background dispatcher with a **work queue of max 2 reels**; if the user swipes faster than analysis, older reels are finalised with whatever signals they have (frames-only).

### 5.2 Label set

- **Built-in (MVP, always active):** Comedy, Fitness, Education, Food, News, Finance, Rage-bait, Ads, plus implicit **Other**.
- **Custom:** up to 2 in MVP (25 total later).
- Each label has a list of **text prompts** used for zero-shot scoring, e.g.

```json
{
  "rage_bait": ["a person shouting angrily at the camera", "a heated argument video",
                "an outrage reaction video"],
  "finance":   ["a person explaining stock market investing", "a chart of share prices",
                "a video about saving money and mutual funds"],
  "talking_head": ["a person talking directly to the camera"]
}
```

- Custom label prompts are generated **on the phone** from the user's label text with fixed templates (`"a short video about {x}"`, `"a reel showing {x}"`). No server call.
- `talking_head` is an internal helper label (not shown to users) used to trigger speech.

### 5.3 Frame classifier — SigLIP 2

- **Model:** smallest SigLIP 2 variant that meets the accuracy target on the 100-reel test set (start with the base/16 at 224 px; evaluate smaller if available). Image encoder and text encoder both exported to ONNX, **int8 dynamic quantisation**.
- **Text embeddings** for all label prompts are computed once when the label set changes and cached in Room (`label_embedding`), so per-frame cost is the image encoder only.
- **Scoring:** SigLIP uses a sigmoid: `p(label) = σ(scale · ⟨img, txt⟩ + bias)`, max over the label's prompts.
- **Per-reel aggregation:** mean of per-frame probabilities; talking-head = max over frames.
- **Trained head (MVP+):** logistic regression on the 768-d (variant-dependent) image embedding, trained offline on 300–500 labelled frames for built-in labels; shipped as a small weights file (`head_v{n}.bin`). Final frame score = `0.5 × zero-shot + 0.5 × head` (weights tuned on the test set).
- Latency target: **≤ 150 ms per frame** on a mid-range phone (spike S5); if exceeded, drop to a smaller variant or sample less often.

### 5.4 Text understanding

- **Sources:** Accessibility caption + hashtags + creator name (if exposed), OCR text, speech transcript.
- **OCR:** ML Kit Text Recognition v2, bundled `DevanagariTextRecognizerOptions` (covers Latin + Devanagari). Run on the post-transition frame and one mid-reel frame; keep lines with height ≥ 2.5% of frame (captions, not UI text).
- **Embeddings:** EmbeddingGemma (int8, ONNX) — candidate; fallback a smaller multilingual model if > 100 ms per text (spike S5). Text is truncated to 256 tokens.
- **Scoring:** cosine similarity between the text embedding and each label's prompt embeddings (same multilingual model) + a per-label logistic head trained on synthetic Hindi/Hinglish/English examples (§9.6). Output: probability per label.

### 5.5 Speech-to-text

| Candidate | Size | Notes |
|---|---|---|
| Shruti (Udgam Labs) | 29.5 MB | Hindi, English, Hinglish; Devanagari + Latin output |
| lipi-hn | 14.4 MB | English + Hinglish; Roman-script output; streaming |

- Choose in **spike S4** (50 Hinglish reels; measure topic-label accuracy, WER, speed, RAM). Integrate via the runtime the chosen model ships for (C/C++ via JNI).
- Input: 16 kHz mono PCM, first 10–15 s. Output: transcript text (never stored).
- Later: IndicConformer per-language packs (~131–140 MB each) as downloadable language packs.

### 5.6 Fusion

```
signals = { frames: P_f, caption: P_c?, ocr: P_o?, speech: P_s? }      // each: label -> prob
weights = { frames: 0.45, caption: 0.25, ocr: 0.15, speech: 0.35 }    // renormalised over present signals
if P_f[talking_head] ≥ 0.6: weights.frames = 0.2                     // visuals uninformative
P[label] = Σ w_i · P_i[label] / Σ w_i
best = argmax P
if P[best] < 0.45 → label = Other
flaggedTag shown only if best is flagged AND P[best] ≥ 0.70           // US-14
needSpeech = audioEnabled && (P_f[talking_head] ≥ 0.6 || P[best] < 0.55)
```

- A reel may carry **one primary label** plus up to 2 secondary labels with P ≥ 0.6 (custom labels such as "Cricket" can co-exist with "Comedy").
- All thresholds live in a remote-config JSON (`ml_thresholds.json`) and are tuned on the test sets.

### 5.7 Personalisation from corrections (US-24A)

- Each correction stores `(reel_id, old_label, new_label, frame_embedding_mean, text_embedding?)` locally.
- A **personal bias head** (per-label offsets + small logistic layer over the embedding) is retrained on the phone by WorkManager when ≥ 10 new corrections exist, only while charging and idle. Training data never leaves the device.

### 5.8 Descriptions and summaries — LFM2-VL-450M

- Runtime: llama.cpp built for Android (NDK, arm64-v8a), GGUF model + `mmproj` projector, Q4/Q8 quantisation chosen by size/quality.
- **Uses:**
  1. Short reel description for Insights lists and flagged explanations (US-24A): run on **one** frame per reel, in batches while charging/idle; until then the list shows label + time only.
  2. Daily AI summary (US-25): text-only prompt over **structured stats** (counts, top labels, peak hour, lessons); output ≤ 60 words.
- **Guardrails:** prompt forbids inventing causes, emotions or intentions; output is checked to contain only numbers present in the input; on any failure, use the deterministic template:
  `"You watched {n} reels today. {top} made up {pct}% of recognised content. Your longest session was {m} minutes around {hour}. You completed {k} lessons."`

### 5.9 Model delivery

| Model | Approx. size (int8/quantised) | Delivery |
|---|---|---|
| SigLIP 2 image + text encoders | TBD (measure after export) | Bundled (needed day one) |
| Classifier heads | < 1 MB | Bundled + remote updates |
| ML Kit OCR (Latin + Devanagari) | ~4 MB per script (bundled variant) | Bundled |
| EmbeddingGemma | ~200 MB RAM when quantised (per Google) | Downloaded on first Wi-Fi after onboarding |
| Shruti / lipi-hn | 29.5 MB / 14.4 MB | Bundled (chosen one only) |
| LFM2-VL-450M GGUF + mmproj | TBD | Downloaded on Wi-Fi, optional |

- Downloads come from CloudFront (`/models/{name}/{version}/`), verified with SHA-256 from a signed `models_manifest.json`.
- App works without the downloaded models (frames-only classification, template summaries).

### 5.10 Performance budgets

| Operation | Target (mid-range phone) |
|---|---|
| Reel transition → bar update | ≤ 1 s |
| Milestone → check-in card | ≤ 1 s |
| SigLIP 2 frame inference | ≤ 150 ms |
| Text embedding | ≤ 100 ms |
| Speech-to-text (15 s audio) | ≤ 3 s, off the UI thread |
| Reel finalised (all signals) | ≤ 5 s after transition |
| App RAM while monitoring (excluding LFM2-VL) | ≤ 350 MB |
| Cached lesson start | ≤ 1 s |

---

## 6. Local data (on the phone)

### 6.1 Room schema

```kotlin
@Entity data class SessionEntity(
    @PrimaryKey val id: String, val app: String,             // "com.instagram.android"
    val startedAt: Long, val endedAt: Long?, val dayKey: String,
    val reelCount: Int, val flaggedCount: Int, val endReason: String?)   // timeout|done|screen_off

@Entity(indices = [Index("dayKey"), Index("primaryLabel")])
data class ReelEntity(
    @PrimaryKey val id: String, val sessionId: String, val dayKey: String,
    val startedAt: Long, val watchMs: Long,
    val primaryLabel: String, val confidence: Float, val secondaryLabels: String, // JSON array
    val signals: Int,                       // bitmask: frames|caption|ocr|speech
    val flagged: Boolean, val description: String?,            // from LFM2-VL, optional
    val correctedLabel: String?, val demo: Boolean = false)

@Entity data class LabelEntity(
    @PrimaryKey val key: String, val displayName: String,
    val type: String,                        // builtin|custom|internal
    val flagged: Boolean, val promptsJson: String, val createdAt: Long)

@Entity(primaryKeys = ["labelKey", "modelVersion", "promptIndex"])
data class LabelEmbeddingEntity(val labelKey: String, val modelVersion: String,
    val promptIndex: Int, val vector: ByteArray)            // float16 packed

@Entity data class CorrectionEntity(@PrimaryKey val reelId: String,
    val oldLabel: String, val newLabel: String, val embedding: ByteArray, val at: Long)

@Entity(primaryKeys = ["dayKey"])
data class DailyAggregateEntity(val dayKey: String, val reels: Int, val budget: Int,
    val flagged: Int, val lessons: Int, val learningSec: Int,
    val labelCountsJson: String, val longestSessionSec: Int, val peakHour: Int?)

@Entity data class TopicEntity(@PrimaryKey val id: String, val name: String,
    val level: String, val goal: String?, val type: String,  // preset|custom|suggested
    val status: String)                                       // preparing|ready|failed

@Entity data class LessonCacheEntity(@PrimaryKey val lessonId: String, val topicId: String,
    val manifestJson: String, val videoPath: String?, val thumbPath: String?,
    val downloadedAt: Long?, val bundled: Boolean, val watchedAt: Long?)

@Entity data class LearnedRecapEntity(@PrimaryKey val lessonId: String, val topicId: String,
    val concept: String, val recap: String, val savedAt: Long)

@Entity data class OutboxEventEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, val payloadJson: String, val createdAt: Long, val attempts: Int = 0)
```

**Settings (Proto DataStore):** budget, check-in interval, back-off on/off, over-budget interval, reset time, flags for audio understanding / topic suggestions / monitoring paused, theme, reduced motion, onboarding step, anonymous user ID + tokens (tokens in `EncryptedSharedPreferences`), demo mode.

### 6.2 Retention

| Data | Kept |
|---|---|
| `ReelEntity` | 90 days, then deleted (aggregates remain) |
| `SessionEntity` | 90 days |
| `DailyAggregateEntity` | Until the user deletes data |
| Corrections | Until the user deletes data |
| Frames, audio, transcripts, OCR text, captions | **Never stored** (memory only) |

A nightly `CleanupWorker` enforces retention. "Delete tracking history" (US-29) clears sessions, reels, aggregates and corrections in one transaction and resets the personal head.

### 6.3 Insights computation

- `DailyAggregateEntity` is updated incrementally on every finalised reel (no full scans on Home).
- Category share = count of reels with `coalesce(correctedLabel, primaryLabel)` / reels with a label ≠ Other; "Other" is shown separately (US-24).
- Peak hour and longest session require ≥ 2 sessions, otherwise hidden (US-24 "insufficient data").

---

## 7. Learning on the phone

### 7.1 Lesson manifest (from the backend)

```json
{
  "lesson_id": "les_8f2c…",
  "topic": {"id": "top_psych", "name": "Psychology"},
  "concept": "Zeigarnik effect",
  "format": "guess_first",
  "level": "beginner",
  "language": "en",
  "duration_s": 48.2,
  "video_url": "https://cdn…/lessons/les_8f2c/v1/lesson_720p.mp4?Expires=…&Signature=…",
  "thumb_url": "https://cdn…/lessons/les_8f2c/v1/thumb.jpg?…",
  "segments": [
    {"id": "hook",  "end_s": 3.1},
    {"id": "setup", "end_s": 18.4},
    {"id": "reveal","end_s": 31.0},
    {"id": "takeaway","end_s": 48.2}
  ],
  "interactions": [
    {"id": "i1", "after_segment": "setup", "pause_at_s": 18.4, "type": "predict",
     "question": "Why did the waiters forget paid orders?",
     "options": ["Paid orders were boring", "The brain lets go of finished tasks", "They were tired"],
     "answer_index": 1,
     "feedback_correct": "Exactly! Finished tasks stop nagging the brain.",
     "feedback_wrong": "Close. It's about the task being finished."}
  ],
  "recap": "Unfinished tasks stick in your mind. Start a task to beat procrastination.",
  "go_deeper": ["How to use this to beat procrastination"],
  "disclaimer": null,
  "version": 1
}
```

### 7.2 Lesson player

- Media3 `ExoPlayer` with `CacheDataSource` over a `SimpleCache` (LRU, 500 MB cap) **and** explicit download of the next lessons (§7.4) so playback is offline-safe.
- **Pausing at interactions:** for each interaction, `player.createMessage { pauseAndShow(interaction) }.setPosition(pause_at_s * 1000).setLooper(mainLooper).setDeleteAfterDelivery(true).send()`. Seeking backwards re-arms messages for positions after the new position.
- Interaction cards and recap are native Compose overlays above the `PlayerSurface`; the video stays paused until **Continue**.
- Captions: burned into the video by Stickman Studio (always visible). The "captions on/off" control (US-18) is P1 and requires a separate WebVTT track generated from `project.json` (§9.5).
- Segmented progress bar from `segments[].end_s`.
- **Swipe true/false (US-19):** a draggable card with tap-button alternatives; TalkBack announces options.
- Completion is recorded once (idempotent by `lesson_id`), creates a `LearnedRecapEntity`, and enqueues `lesson_completed` in the outbox.
- **Report a mistake:** pauses playback; sends `POST /v1/lessons/{id}/reports` (via outbox if offline).

### 7.3 Bundled starter lessons

- 1 lesson per preset topic (8) + the 30 hackathon lessons (3 topics × 10) shipped in `assets/lessons/` (manifest + 720p MP4). Used for US-06A (try a lesson first), offline demo, and as a fallback when the cache is empty.

### 7.4 Sync and prefetch (WorkManager)

| Worker | Trigger | Constraints | Job |
|---|---|---|---|
| `OutboxFlushWorker` | Outbox insert; periodic 15 min | Network connected | Send lesson events/reports in batches of 50; exponential backoff |
| `FeedSyncWorker` | App start; push received; periodic 6 h | Network connected | `GET /v1/feed` per topic; upsert `LessonCacheEntity` |
| `PrefetchWorker` | After FeedSync | Unmetered network (Wi-Fi) | Download until 5 unwatched lessons per topic are on disk |
| `ModelDownloadWorker` | After onboarding | Unmetered, battery not low | EmbeddingGemma, LFM2-VL |
| `PersonalHeadWorker` | ≥ 10 new corrections | Charging, idle | Retrain personal head (§5.7) |
| `DescribeReelsWorker` | Nightly | Charging, idle | LFM2-VL descriptions for today's reels |
| `CleanupWorker` | Daily | — | Retention, cache trimming |

On metered networks, the app still streams a lesson when the user taps it (with a small "uses mobile data" note) but never prefetches.

---

## 8. Backend

### 8.1 Services

| Service | Runtime | Responsibility |
|---|---|---|
| `api` | FastAPI on ECS Fargate (2 tasks min) | Auth, profiles, topics, feed, events, reports, account deletion, job orchestration |
| `script-worker` | Python on Fargate (1–5 tasks) | Syllabus, script, quality check via OpenAI |
| `render-worker` | Python + Stickman Studio fork on EC2 Linux (Auto Scaling) | Render, extract pause times, compress, upload |
| `scheduler` | EventBridge Scheduler → API internal endpoints | Stock refill sweep (every 10 min), stuck-job sweeper (every 5 min), deletion jobs |

### 8.2 Authentication

**Anonymous (MVP):**
1. On first launch the app generates a random `install_secret` (32 bytes, stored in `EncryptedSharedPreferences`).
2. `POST /v1/users` with `{install_secret_hash}` → backend creates `user` and returns `{user_id, access_token (JWT, 1 h), refresh_token (opaque, 90 days, rotated)}`.
3. `POST /v1/auth/refresh` with the refresh token → new pair. Lost refresh token → re-register with the install secret.

JWT: RS256, issued by the API, `sub = user_id`, `scope = user`, keys in Secrets Manager, JWKS at `/.well-known/jwks.json`.

**Optional login (P1):** Cognito user pool with Google and phone-OTP sign-in. `POST /v1/users/{id}/link` with the Cognito ID token → backend verifies it, stores `auth_provider` + `auth_subject`, and merges if that identity already owns another user (keeps the record with more progress; lessons and events are re-parented). Sign-in on a new phone → `POST /v1/auth/cognito` returns tokens for the linked user.

### 8.3 API (v1)

All endpoints JSON over HTTPS, `Authorization: Bearer <JWT>` except registration. Errors use `{"error": {"code": "...", "message": "..."}}`.

| Method | Path | Body / query | Response |
|---|---|---|---|
| POST | `/v1/users` | `{install_secret_hash, app_version, locale}` | `{user_id, access_token, refresh_token}` |
| POST | `/v1/auth/refresh` | `{refresh_token}` | new token pair |
| PUT | `/v1/users/{id}/lesson-profile` | `{language, voice_id, length_s, topics:[{name, level, goal?, type}]}` | `{profile_version, topics:[{topic_id, status}]}` — queues starter batch |
| POST | `/v1/topics` | `{name, level, goal?}` | `{topic_id, status:"preparing"}` (409 if duplicate) |
| PATCH | `/v1/topics/{topic_id}` | `{level?, goal?}` | updated topic |
| DELETE | `/v1/topics/{topic_id}` | — | 204 |
| GET | `/v1/topics` | — | `[{topic_id, name, level, goal, status, ready_count}]` |
| POST | `/v1/lessons/generate` | `{topic_id, count ≤ 5}` | `{job_ids:[…]}` |
| GET | `/v1/jobs/{job_id}` | — | `{status, stage, error?}` |
| GET | `/v1/feed` | `?topic_id=&limit=10` | `[LessonManifest]` (ready, unwatched, signed URLs valid 24 h) |
| POST | `/v1/events` | `{events:[{type, lesson_id, at, data}]}` (batch ≤ 50, idempotent `event_id`) | `{accepted}` |
| POST | `/v1/lessons/{id}/reports` | `{reason, note?, position_s}` | 202 |
| POST | `/v1/users/{id}/link` (P1) | `{cognito_id_token}` | `{linked:true}` |
| DELETE | `/v1/users/{id}` | — | 202 + `{deletion_id}`; status via `GET /v1/deletions/{id}` |
| PUT | `/v1/users/{id}/push-token` | `{fcm_token}` | 204 |
| GET | `/v1/config` | — | remote config: selectors version, thresholds version, model manifest URL, feature flags |

**Event types:** `lesson_started`, `lesson_completed`, `lesson_skipped`, `interaction_answered {interaction_id, answer, correct}`, `go_deeper_chosen {option}`, `lesson_liked`. There is **no** event type that carries reel, label or feed data, and the request schema rejects unknown fields.

**Rate limits (per user):** 30 req/min general; `POST /v1/topics` 10/day; `lessons/generate` 20 lessons/day (free) — enforced with a Postgres token bucket table (Redis not needed at MVP scale).

### 8.4 Database (PostgreSQL)

```sql
create table app_user (
  id uuid primary key, install_secret_hash text unique not null,
  auth_provider text, auth_subject text, plan text not null default 'free',
  created_at timestamptz not null default now(), deleted_at timestamptz,
  unique (auth_provider, auth_subject));

create table lesson_profile (
  user_id uuid primary key references app_user(id) on delete cascade,
  language text not null default 'en', voice_id text not null default 'af_heart',
  length_s int not null default 45, version int not null default 1,
  updated_at timestamptz not null default now());

create table topic (
  id uuid primary key, user_id uuid references app_user(id) on delete cascade,
  name text not null, name_norm text not null, level text not null,
  goal text, type text not null, status text not null default 'preparing',
  syllabus_id uuid, created_at timestamptz not null default now(),
  unique (user_id, name_norm));

create table syllabus (
  id uuid primary key, topic_norm text not null, level text not null, language text not null,
  goal_hash text, owner_user_id uuid references app_user(id) on delete cascade,  -- null = shared
  concepts jsonb not null, model text not null, created_at timestamptz not null default now());
create unique index syllabus_shared_key on syllabus(topic_norm, level, language) where owner_user_id is null;

create table syllabus_progress (
  user_id uuid references app_user(id) on delete cascade, syllabus_id uuid references syllabus(id),
  next_index int not null default 0, weak_concepts jsonb not null default '[]',
  primary key (user_id, syllabus_id));

create table lesson (
  id uuid primary key, syllabus_id uuid references syllabus(id),
  owner_user_id uuid references app_user(id) on delete cascade,      -- null = shared
  concept text not null, concept_index int not null, format text not null,
  level text not null, language text not null, voice_id text not null,
  visual_style text not null, accent_color text not null,
  share_key text,                                                     -- hash for reuse, null if personal
  script jsonb, manifest jsonb, duration_s real,
  video_key text, thumb_key text, status text not null,               -- see 8.5
  report_count int not null default 0, hidden boolean not null default false,
  cost_tokens_in int, cost_tokens_out int, render_seconds real,
  created_at timestamptz not null default now(), ready_at timestamptz);
create index lesson_ready_shared on lesson(share_key, status) where owner_user_id is null;

create table user_lesson (
  user_id uuid references app_user(id) on delete cascade, lesson_id uuid references lesson(id),
  delivered_at timestamptz, started_at timestamptz, completed_at timestamptz,
  skipped boolean default false, liked boolean default false,
  interaction_results jsonb not null default '[]', go_deeper_choice text,
  primary key (user_id, lesson_id));

create table job (
  id uuid primary key, user_id uuid, lesson_id uuid references lesson(id) on delete cascade,
  stage text not null, status text not null, attempts int not null default 0,
  error text, created_at timestamptz not null default now(), updated_at timestamptz not null default now());

create table lesson_report (
  id uuid primary key, lesson_id uuid references lesson(id), user_id uuid,
  reason text not null, note text, position_s real, created_at timestamptz not null default now());

create table processed_event (event_id uuid primary key, user_id uuid, at timestamptz);
create table rate_bucket (user_id uuid, bucket text, tokens int, refilled_at timestamptz,
  primary key (user_id, bucket));
```

### 8.5 Lesson lifecycle

```
queued ─► scripting ─► script_ready ─► rendering ─► ready
   │          │              │              │
   └──────────┴──── failed (attempts < 3 → re-queued with backoff; else failed_final)
ready ─► hidden (≥ 3 "wrong_fact" reports from distinct users, pending review)
```

- The `job` row tracks each stage; SQS messages carry only `{job_id}`.
- **Visibility timeout:** script 120 s, render 900 s. Workers extend visibility while working.
- **Stuck-job sweeper:** jobs in `scripting`/`rendering` for > 3× their timeout are reset to `queued` (or failed after 3 attempts).
- Topic `status` = `ready` once ≥ 1 lesson is ready; `failed` if the first 3 lessons all end `failed_final` (app shows Retry, US-22).

### 8.6 Stock, refill and sharing

- **Target stock:** 5 unwatched ready lessons per user per topic (starter batch on profile save; refill when < 3).
- **Sharing (cost control):**
  - `share_key = sha256(topic_norm | level | language | voice_id | visual_style | format_mix_version)` for **preset topics without a goal**.
  - Before generating, the API looks for ready shared lessons with the same `share_key` that the user hasn't received (`user_lesson`), in syllabus order. Only the shortfall is generated.
  - Lessons for custom topics or topics with a goal are personal (`owner_user_id` set) and never shared.
- **Syllabus reuse:** shared syllabi per `(topic_norm, level, language)`; personal syllabi when a goal is present.

### 8.7 Script worker (OpenAI)

**Models and settings**

| Step | Model | Notes |
|---|---|---|
| Syllabus | `gpt-6.2-sol` | Once per syllabus; 20–30 concepts, ordered, each with a one-line objective |
| Script | `gpt-6.1-terra` | Per lesson; Structured Outputs with the lesson schema (Appendix A) |
| Quality check | `gpt-6-luna` | Returns `{pass, issues[], fixed_script?}` |
| Topic safety | `gpt-6-luna` (or the moderation endpoint) | Custom topic names/goals checked before anything is generated |

Model IDs live in config (`OPENAI_MODEL_SYLLABUS`, …) so they can be swapped without code changes. Use Structured Outputs with a strict JSON Schema (Appendix A). Temperature: syllabus 0.4, script 0.8, QA 0.

**Script generation inputs:** topic, level, goal, language, target length (`length_s × 3 words/s`), concept + objective, already-covered concepts (last 20), user's weak concepts (from `syllabus_progress.weak_concepts`), chosen format (rotation: explainer → guess_first → myth_fact → story → dialogue, skipping formats with high skip rates for that user).

**Quality check rubric (all must pass):**
1. Factually correct for a general audience; no fabricated statistics, names or dates.
2. Exactly one main idea; hook in the first sentence.
3. Narration word count within ±15% of target.
4. At least one interaction; each interaction has exactly one correct answer that is supported by the narration.
5. No specific financial, medical or legal advice; adds the standard disclaimer for finance/health topics.
6. Narration uses concrete, visual nouns (Stickman Studio picks illustrations from the text).
7. Language and register match the profile (e.g., simple English; Hinglish if requested).

Failed scripts: use `fixed_script` if provided and re-check once; otherwise regenerate (max 2), then `failed_final`.

**Cost logging:** store `usage.input_tokens` / `output_tokens` per lesson (`lesson.cost_tokens_*`); CloudWatch metric `openai_cost_usd` computed from a price table in config. Alarm if daily cost > configured cap. Expected ≈ $0.01 per lesson (Product Spec §7.4 item 8).

**Resilience:** OpenAI calls with 30 s timeout, 3 retries with exponential backoff + jitter on 429/5xx; circuit breaker pauses the script queue for 5 min after 10 consecutive failures.

### 8.8 Render worker (Stickman Studio)

**Container image** (`boomdoom-render`): Python 3.11, FFmpeg (libx264), Stickman Studio fork pinned to a commit, Kokoro ONNX model files (`kokoro-v1.0.int8.onnx`, `voices-v1.0.bin`), fonts (Liberation Sans, Noto Sans, Noto Sans Devanagari). No GUI dependencies (PySide6 not installed).

**Fork patches (required, found in the 21 Nov test render):**

| Patch | Problem | Fix |
|---|---|---|
| NVENC detection | Picks `h264_nvenc` whenever FFmpeg lists it, even without a GPU → render fails | Probe with a 1-frame test encode; fall back to `libx264` |
| Fonts | Captions only search `C:/Windows/Fonts` → tiny default font on Linux | Configurable font directory; bundle fonts in the image |
| Music mix | `AudioMixer.mix_audio()` called with unsupported `sfx_path` → no music | Fix call signature |
| SFX | Type error comparing `str` and `int` | Fix parsing |
| Title glyphs | Icon glyph renders as an empty box | Bundle a font with the glyph or remove the icon |
| Storyboard input (later) | Illustrations chosen only from narration text | Add optional per-scene hints from the script's `scene` fields |

**Render job**

```python
params = {
  "topic": lesson.concept, "script": narration_text, "script_mode": "use_as_is",
  "duration": target_s, "voice_id": profile.voice_id, "format": "9:16",
  "cis_visual_style": lesson.visual_style,        # B1 | B2 | B3, fixed per topic
  "cis_accent_color": lesson.accent_color,        # fixed per topic
  "cis_illustration_mode": "pil",                 # no diffusion backgrounds (CPU workers)
}
mp4 = CISPipeline(llm_manager=None, tts_manager=tts, output_dir=tmp, temp_dir=tmp).generate_video(params)
```

**Pause-time extraction:** read `project.json` → `captions[].words[]` (word, start, end). Walk the narration words in order; for each segment, find the timestamp of its **last word**; `pause_at_s = end + 0.15`. If alignment confidence is low (token mismatch > 10%), fall back to proportional timing by word count and log a warning.

**Compression:** `ffmpeg -i in.mp4 -vf scale=720:1280 -c:v libx264 -preset veryfast -crf 26 -c:a aac -b:a 96k -movflags +faststart lesson_720p.mp4`; thumbnail at 25% of duration (`-ss`, 1 frame, 360 px wide JPEG).

**Upload layout (S3):**

```
s3://boomdoom-lessons-{env}/lessons/{lesson_id}/v{version}/lesson_720p.mp4
                                                      /thumb.jpg
                                                      /manifest.json
                                                      /project.json      (internal, not served)
s3://boomdoom-assets-{env}/models/{name}/{version}/…  (on-device model downloads)
s3://boomdoom-assets-{env}/config/{name}.json         (remote config)
```

**Measured performance:** ~2× real time on 2 vCPUs (24 s video in ~50 s). One worker = one render at a time per 2 vCPUs. Re-measure on the chosen instance family; autoscale on `ApproximateNumberOfMessagesVisible` of `render-jobs` (target ≤ 5 messages per worker).

### 8.9 Push notifications

- App registers its FCM token via `PUT /v1/users/{id}/push-token` (token stored on `app_user`).
- API sends a data message `{type:"lessons_ready", topic_id}` when a topic goes `preparing → ready`, and optionally a daily summary trigger (P1; the summary itself is computed on the phone).
- Notification text never includes feed data (US-31).

### 8.10 Account deletion

`DELETE /v1/users/{id}` → mark `deleted_at`, revoke tokens, enqueue deletion: delete `user_lesson`, personal `lesson` rows and their S3 objects, personal `syllabus`, `topic`, `lesson_profile`, reports' `user_id` (set null), then the `app_user` row. Target completion < 24 h. The app shows success only when `GET /v1/deletions/{id}` reports `done` (US-29).

---

## 9. Infrastructure (AWS)

### 9.1 Environments

| Env | Purpose | Shape |
|---|---|---|
| `demo` | Hackathon | One EC2 instance (Docker Compose: API, script worker, render worker, Postgres) + S3 bucket + CloudFront. Seeded with the 30 demo lessons |
| `dev` | Development | Full topology at minimum size (1 API task, 1 script task, 1 render instance, `db.t4g.micro`) |
| `prod` | Beta / v1 | Full topology, Multi-AZ RDS, autoscaling |

### 9.2 Topology (prod)

| Resource | Configuration |
|---|---|
| VPC | 2 AZs in `ap-south-1`; public subnets (ALB, NAT), private subnets (ECS, EC2, RDS) |
| ALB | HTTPS only (ACM certificate), `api.boomdoom.app` (domain TBD) |
| ECS cluster | Fargate services: `api` (min 2, CPU target 60%), `script-worker` (1–5, scale on `script-jobs` depth) |
| Render workers | EC2 Auto Scaling group, Linux, compute-optimised 2–8 vCPU instances, mixed On-Demand base (1) + Spot; scale on `render-jobs` depth; user data pulls `boomdoom-render` from ECR |
| RDS | PostgreSQL 16, Multi-AZ, automated backups 7 days, encryption at rest (KMS) |
| SQS | `script-jobs`, `render-jobs`, `deletion-jobs` + DLQs (max receive 3) |
| S3 | `boomdoom-lessons-{env}` (private, versioning, lifecycle: delete old versions after 30 days), `boomdoom-assets-{env}` |
| CloudFront | Origin access control to S3; **signed URLs** (key group) for lessons; public-read path for model files and config (checksummed) |
| Cognito (P1) | User pool with Google IdP + phone OTP (SMS) |
| Secrets Manager | OpenAI API key, JWT signing keys, DB credentials, FCM service account |
| EventBridge Scheduler | Refill sweep, stuck-job sweeper, deletion processor |
| CloudWatch | Logs (30-day retention), metrics, alarms, dashboard |
| ECR | `boomdoom-api`, `boomdoom-worker`, `boomdoom-render` |

### 9.3 IAM (least privilege)

| Role | Allowed |
|---|---|
| `api-task` | RDS connect; SQS send (script/render/deletion); S3 read manifests; CloudFront sign (via key in Secrets Manager); Secrets read (JWT, DB, FCM) |
| `script-worker-task` | SQS receive/delete `script-jobs`, send `render-jobs`; RDS; Secrets read (OpenAI, DB) |
| `render-instance` | SQS receive/delete `render-jobs`; S3 put/get `lessons/*`; RDS; ECR pull; Secrets read (DB) |
| CI deploy role (OIDC from GitHub) | ECR push; ECS update service; ASG instance refresh |

### 9.4 Terraform layout

```
infra/
  modules/  network, ecs-service, render-asg, rds, sqs, s3-cdn, cognito, secrets, alarms
  envs/     demo/, dev/, prod/   (each: main.tf, variables.tf, terraform.tfvars, backend.tf → S3 state + DynamoDB lock)
```

### 9.5 CI/CD (GitHub Actions)

| Pipeline | Steps |
|---|---|
| Android | Lint, unit tests, Konsist architecture rules (no network in `monitor`/`overlay`/`ml`), build release AAB + demo APK, upload artifacts; tagged builds to internal testing track |
| Backend | Ruff, mypy, pytest (with Postgres service container), Alembic migration check, Docker build, push to ECR, deploy to `dev` automatically, `prod` on tag with manual approval |
| Render | Build `boomdoom-render`, run **golden render test** (render the Zeigarnik lesson; assert duration ±10%, 1080×1920 → 720p output, pause times extracted, caption font size above threshold), push to ECR, ASG instance refresh |
| Infra | `terraform fmt/validate/plan` on PR; `apply` on merge for `dev`, manual for `prod` |

WebVTT captions (for the P1 captions toggle) are produced by the render worker from `project.json` and uploaded as `captions.vtt` once that feature is enabled.

### 9.6 Offline ML tooling (team-only, not shipped)

| Tool | Purpose |
|---|---|
| `ml/label_frames.py` | Builds the labelled frame set from team screen recordings; OpenAI transcribes and suggests labels; humans confirm |
| `ml/synth_text.py` | GPT generates Hindi/Hinglish/English caption and transcript examples per label for the text head |
| `ml/train_heads.py` | Trains logistic heads for SigLIP 2 and EmbeddingGemma embeddings; exports weights |
| `ml/export_onnx.py` | Exports and int8-quantises SigLIP 2 and EmbeddingGemma |
| `ml/eval.py` | Runs the 100-reel and 50-Hinglish-reel test sets; reports accuracy per label, confusion matrix, threshold sweeps |

Recordings used for training are the team's own reel sessions, stored in a private team bucket and deleted after the hackathon unless the team decides otherwise. No user data is ever used.

---

## 10. Security and privacy

### 10.1 Data classification

| Data | Where | Leaves the phone? |
|---|---|---|
| Screen frames, audio, transcripts, OCR text, captions | Phone memory only | **Never** |
| Reel records, labels, sessions, insights, corrections | Phone (Room) | **Never** |
| Settings | Phone (DataStore) | Never |
| Learning profile (topics, levels, goals, language, voice) | Phone + backend | Yes — needed to make lessons |
| Lesson events (started, completed, answers) | Phone + backend | Yes — needed for personalisation |
| Lesson reports | Backend | Yes |
| Accepted feed-based topic suggestions (P1) | Backend (topic name only) | Yes, topic name only |
| Crash reports | Crashlytics | Yes, scrubbed (no feed data, no labels, no free text) |

### 10.2 Controls

- **Structural guarantee:** `monitor`, `overlay` and `ml:*` modules cannot reach the network (§3.2); verified in CI.
- **API schema allow-lists:** request models reject unknown fields; there is no endpoint that accepts reel or label data.
- **Transport:** TLS 1.2+ only; HSTS on the API domain.
- **At rest:** Android file-based encryption (default) for Room; tokens in `EncryptedSharedPreferences`; RDS and S3 encrypted with KMS.
- **Signed lesson URLs** (24 h) so lesson videos aren't publicly listable.
- **Custom topics** are checked for safety before generation (sexual content, violence, self-harm, hate, minors); unsafe topics are rejected with a friendly message.
- **Lesson reports:** ≥ 3 "wrong fact" reports from distinct users hide a lesson pending review.
- **Logging:** structured logs exclude goals and free-text notes by default (logged only as lengths/hashes).
- **Secrets:** never in the app; the app talks only to BoomDoom's API, never directly to OpenAI.

### 10.3 Compliance (India DPDP Act)

- Clear consent screens with purpose for each permission and for backend data (US-07, US-29).
- Data minimisation as above; purpose limited to lesson delivery.
- Rights: access/export (local export; backend export on request P1), correction (edit topics), deletion (local + backend, §8.10).
- Users under 18: the app is intended for 18+ in the MVP; age confirmation at onboarding. *(Confirm legal requirements for minors before any wider launch.)*
- Privacy policy and in-app "What leaves this phone" screen (US-29) must match §10.1 exactly.

### 10.4 Platform policy

- Play Store: Accessibility API use must be declared with a clear, user-facing purpose; prominent disclosure before enabling. Hackathon builds are sideloaded (hence the restricted-settings guide, US-07).
- BoomDoom never modifies, automates or scrapes Instagram; it reads only what is on the user's own screen with consent, and never performs actions in the other app.

---

## 11. Observability and analytics

### 11.1 Backend metrics and alarms

| Metric | Alarm |
|---|---|
| API p95 latency | > 500 ms for 10 min |
| API 5xx rate | > 2% for 5 min |
| `script-jobs` / `render-jobs` oldest message age | > 15 min / > 30 min |
| DLQ messages | > 0 |
| Lesson failure rate (failed_final / created) | > 10% per hour |
| Render seconds per lesson (p50, p95) | p95 > 4× video length |
| OpenAI cost per day | > configured cap |
| RDS CPU / free storage | > 80% / < 10 GB |

### 11.2 App telemetry (opt-in, privacy-preserving)

- **Allowed:** app version, Android version, device model class, crash/ANR, monitoring capability status counts, model latency percentiles, battery impact buckets, onboarding step completion, lesson playback errors.
- **Never:** labels, categories, reel counts per category, captions, transcripts, descriptions, goals, topic names.
- Off by default; one toggle in Privacy & Data.

---

## 12. Testing strategy

| Level | What | Tools |
|---|---|---|
| Unit (app) | SessionEngine state machine, check-in rules, day boundary, fusion, insights math | JUnit, Turbine, kotlinx-coroutines-test |
| Unit (backend) | Sharing logic, refill, lifecycle transitions, pause-time extraction, schema validation | pytest |
| Contract | API request/response schemas shared via OpenAPI; app models generated or checked against it | OpenAPI + schemathesis |
| Instrumented (app) | Onboarding, permissions flows (with fakes), lesson player pauses, offline playback | Compose UI tests, Espresso |
| Accessibility service | Replay recorded accessibility event logs from real sessions against `ReelWatcher` | Custom replay harness (logs from spike S1) |
| ML evaluation | 100-reel labelled set (≥ 75% accuracy), 50 Hinglish reels (topic accuracy), flag precision at threshold 0.7 | `ml/eval.py` |
| Render | Golden render test per commit (§9.5) | pytest in CI |
| Load | 500 concurrent users syncing feed + events; 200 lessons queued | k6 |
| Device matrix | 1 low-end (4 GB RAM), 1 mid-range (6–8 GB), 1 flagship; Android 12, 13, 14, 15 | Manual + Firebase Test Lab for UI tests |
| Battery | 30-minute scrolling session per device class; ≤ 5%/hour added drain | Android Battery Historian |

**Definition of done** follows UX.md §16 plus: no new network dependency in `monitor`/`overlay`/`ml`, telemetry contains no feed data, and the story's acceptance criteria have automated tests where feasible.

---

## 13. Service levels (beta)

| Area | Target |
|---|---|
| API availability | 99.5% monthly |
| Feed request p95 | < 400 ms |
| Preset-topic lesson availability | ≥ 5 ready lessons per topic at all times |
| New custom topic: first lesson ready | p50 < 5 min, p95 < 15 min |
| Reel count accuracy | ≥ 90% (100-reel test) |
| Classification accuracy | ≥ 75% (100-reel test) |
| Crash-free sessions | ≥ 99.5% |

---

## 14. Hackathon build plan

### 14.1 Team split (suggested for 4 people)

| Person | Owns |
|---|---|
| A — Android core | Accessibility service, SessionEngine, overlays, capability manager, onboarding permissions |
| B — Android UI | Compose design system, Home, Learn, player + interactions, Insights, settings, demo mode |
| C — ML | Spikes S2–S5 support, SigLIP 2 export + heads, OCR, speech, EmbeddingGemma, fusion, eval |
| D — Backend | API, Postgres, OpenAI pipeline, Stickman Studio fork + render worker, AWS `demo` env, 30 demo lessons |

### 14.2 Timeline

| Phase | Work | Exit criteria |
|---|---|---|
| **Day 0 (first hours)** | Spikes S1 (reel counting), S2 (captions), S3 (audio capture); render-worker Docker build; OpenAI syllabus + script prompts | Go/no-go for each signal; first rendered lesson from an OpenAI script |
| **Day 1** | SessionEngine + bar + check-in card; Room; Home; SigLIP 2 on device (zero-shot); backend API + DB + workers on `demo`; generate 30 lessons | Count reels and show the bar live; a lesson plays from the feed |
| **Day 2** | Fusion with OCR + speech (Shruti/lipi-hn); Insights; lesson player pauses + cards + recap; onboarding incl. try-a-lesson; custom topic end to end | Full loop: scroll → check-in → lesson → insights |
| **Day 3 (final)** | Heads trained on labelled frames; thresholds tuned; demo mode; polish; battery check; rehearse the 2-minute demo | Demo script runs 3 times in a row without failure |

### 14.3 Demo mode (US-34) implementation

- Build flavour `demo` + hidden toggle (7 taps on version).
- Check-in interval 3; seeded `DailyAggregateEntity`/`ReelEntity` rows with `demo = true` (shown with a "Demo data" banner); bundled lessons; a prepared Hindi finance reel scenario for US-13A.
- **Reset demo** clears `demo = true` rows and restores settings.

### 14.4 Fallbacks if a spike fails

| Spike fails | Fallback for the demo |
|---|---|
| S1 reel counting via accessibility | Frame-difference counting (screen capture required) |
| S2 captions not exposed | OCR + frames only |
| S3 audio capture blocked | OCR of burned-in Hindi/Hinglish captions for the talking-head demo |
| S5 SigLIP 2 too slow | Sample every 4 s; smaller variant; classify only the post-transition frame |
| Render too slow | Pre-render all demo lessons; custom topic demo shows "Preparing" then a pre-made lesson |

---

## 15. Risks and open items

| Risk | Mitigation | Owner |
|---|---|---|
| Instagram UI changes break detection | Remote-config selectors; frame-diff fallback | A |
| Instagram blocks audio capture | OCR fallback (US-13A) | C |
| On-device models too slow/heavy on low-end phones | Battery/thermal policy, smaller variants, downloads on Wi-Fi only | C |
| Stickman Studio bugs / off-topic illustrations | Fork with fixes; concrete-noun narration; storyboard input later | D |
| AI lesson errors | QA pass, reports, hiding | D |
| Play Store policy on Accessibility | Narrow declared purpose, prominent disclosure | All |
| OpenAI cost or model changes | Config-driven models, cost alarm, shared lessons | D |

Open spikes: Product Spec §15.3 (S1–S5).

---

## Appendix A — Lesson script JSON Schema (OpenAI Structured Outputs)

```json
{
  "name": "lesson_script",
  "strict": true,
  "schema": {
    "type": "object",
    "additionalProperties": false,
    "required": ["concept", "format", "level", "language", "segments", "interactions", "recap", "go_deeper", "disclaimer"],
    "properties": {
      "concept":  {"type": "string"},
      "format":   {"type": "string", "enum": ["explainer", "myth_fact", "story", "dialogue", "guess_first", "series"]},
      "level":    {"type": "string", "enum": ["beginner", "intermediate", "advanced"]},
      "language": {"type": "string"},
      "segments": {
        "type": "array",
        "items": {
          "type": "object", "additionalProperties": false,
          "required": ["id", "narration", "scene", "speaker"],
          "properties": {
            "id":        {"type": "string"},
            "narration": {"type": "string"},
            "scene":     {"type": "string"},
            "speaker":   {"type": "string", "enum": ["narrator", "speaker1", "speaker2"]}
          }
        }
      },
      "interactions": {
        "type": "array",
        "items": {
          "type": "object", "additionalProperties": false,
          "required": ["id", "after_segment", "type", "question", "options", "answer_index", "feedback_correct", "feedback_wrong"],
          "properties": {
            "id":               {"type": "string"},
            "after_segment":    {"type": "string"},
            "type":             {"type": "string", "enum": ["predict", "mcq", "true_false", "reflect", "your_answer", "go_deeper"]},
            "question":         {"type": "string"},
            "options":          {"type": "array", "items": {"type": "string"}},
            "answer_index":     {"type": ["integer", "null"]},
            "feedback_correct": {"type": "string"},
            "feedback_wrong":   {"type": "string"}
          }
        }
      },
      "recap":      {"type": "string"},
      "go_deeper":  {"type": "array", "items": {"type": "string"}},
      "disclaimer": {"type": ["string", "null"]}
    }
  }
}
```

Counts (3–6 segments, 1–3 interactions, ≤ 4 options, ≤ 3 go-deeper items) are enforced by backend validation after generation, since strict schemas may not support every size keyword.

The backend adds `lesson_id`, `topic`, `duration_s`, `video_url`, `thumb_url`, `segments[].end_s`, `interactions[].pause_at_s` and `version` after rendering (manifest, §7.1).

## Appendix B — Prompts (initial versions)

**Syllabus (`gpt-6.2-sol`)**
> Create an ordered syllabus of {n=25} micro-concepts for "{topic}" at {level} level for Indian Gen Z learners{goal ? ", aimed at: " + goal}. Each concept must be teachable in a 30–60 second lesson with one main idea. Order from foundational to advanced. Avoid overlap. Return JSON: `{concepts:[{title, objective}]}`.

**Script (`gpt-6.1-terra`)**
> You write scripts for 30–60 s vertical stickman micro-lessons for Indian Gen Z.
> Topic: {topic}. Level: {level}. Goal: {goal}. Language: {language}. Format: {format}.
> Concept: {title} — objective: {objective}. Already covered: {covered}. Revisit if natural: {weak}.
> Rules: about {words} words of narration in total (~3 words per second); hook in the first sentence; one idea only; at least one interaction every 15–20 s; simple, conversational language; concrete, visual nouns (the animator picks illustrations from them); one-line scene hint per segment; no specific financial, medical or legal advice; add a one-line disclaimer for finance/health topics.
> Return JSON matching the schema.

**Quality check (`gpt-6-luna`)**
> Check this lesson script against the rubric: {rubric items 1–7 from §8.7}. Return JSON `{pass: boolean, issues: [string], fixed_script: <schema or null>}`. Fix only small issues; set `fixed_script` to null for factual problems.

**Daily summary (on device, LFM2-VL-450M, text only)**
> Write one or two neutral, supportive sentences (max 60 words) summarising these scrolling stats. Use only these facts and numbers; do not guess reasons, feelings or intentions. Stats: {json}.

## Appendix C — Remote config files

| File | Contents |
|---|---|
| `reels_selectors.json` | Reels screen and scroll-container selectors per Instagram version range |
| `ml_thresholds.json` | Fusion weights, Other threshold (0.45), flag threshold (0.70), talking-head threshold (0.60), speech trigger (0.55) |
| `models_manifest.json` | Model names, versions, URLs, SHA-256, sizes, min app version |
| `feature_flags.json` | In-feed lesson card, captions toggle, topic suggestions, optional login |

All config files are versioned, checksummed and cached; the app always ships bundled defaults.

## Appendix D — Glossary

| Term | Meaning |
|---|---|
| Check-in | The overlay card shown every *n* reels (US-15) |
| Budget | Daily reel limit (default 300) |
| Day key | Date of the "day" under the 4:00 AM reset rule |
| Fusion | Combining frame, caption, OCR and speech signals into one label |
| Head | Small trained classifier on top of model embeddings |
| Share key | Hash that lets identical preset-topic lessons be reused across users |
| Spike | Short technical test that decides feasibility (S1–S5) |
| Stock | Ready, unwatched lessons kept per user per topic |
