# BoomDoom — Product Spec

**Tagline:** Turn doom into boom.
**Version:** v0.1 (hackathon draft) · **Date:** 21 Nov 2026 · **Platform:** Android (MVP)
**Hackathon track:** Build Creative — *Personal context, into everyday AI products*

---

## 1. Summary

BoomDoom watches your short-video scrolling (Instagram Reels first, then YouTube Shorts) **privately on your phone**, understands *what* you are watching, and turns a doom-scroll into something useful.

It does three things:

1. **Track:** counts the reels you watch against a daily budget, shows a red progress bar over the feed, and alerts you every *n* reels.
2. **Understand:** classifies what you watched into labels (built-in + your own custom labels), produces insights and summaries, and flags content you have said you never want to waste time on.
3. **Replace:** offers a stickman micro-lesson feed on topics you pick (Psychology, Interview prep, General Knowledge, anything you want to learn), served at the moment a doom-scroll is detected.

**The core loop:** **See → Understand → Replace.** Existing apps either count screen time or offer a separate learning feed. BoomDoom connects the two: it knows what you're watching and swaps in the right lesson at the right moment.

---

## 2. Problem

- Gen Z themselves rank phone and social media dependence among the biggest problems their generation faces (YPulse surveys, 2025–2026).
- Screen-time tools report *how long* ("Instagram: 3h") but not *what*. Three hours of interview tips and three hours of rage-bait look the same.
- Blockers and pause screens (one sec, ScreenZen, Opal) add friction but leave the user with nothing better to do, so they often switch the blocker off.
- Learning feeds (ScrollEd, CogniScroll, ZunoScroll) are good, but they live in a separate app that you have to remember to open while the doom-scroll is happening somewhere else.

**User problem statement:**
> "I don't even know what I spent two hours watching. I want to scroll less, and when I do scroll, I want some of it to actually be worth it."

---

## 3. Target users

| Persona | Description | Main need |
|---|---|---|
| **Student (primary)** | 18–24, college or exam prep, 3–5 h/day on Reels | Swap scroll time for exam, GK or interview content |
| **Early-career professional** | 22–28, job hunting or upskilling | Interview prep in the gaps of the day, less late-night scrolling |
| **Self-aware scroller** | Already tried screen-time limits and gave up | Understand their own feed and avoid specific content types |

---

## 4. Goals and non-goals

**Goals**
- Show users *what* they watch, not only how long.
- Cut down time spent on reels that fall under flagged labels.
- Make the learning feed the easy option at the moment of a doom-scroll.
- Keep all feed analysis **on the phone**. No screenshots or frames leave the device.

**Non-goals (v1)**
- Modifying or injecting into the Instagram app or its servers.
- iOS support (the platform does not allow screen reading or overlays on other apps; see §10).
- Hard blocking or locking apps (users can pair BoomDoom with a blocker).
- Social features, leaderboards, or sharing feed data.

---

## 5. Features

### F1. Feed tracking

**F1.1 Session and reel detection**
- Detect when Instagram Reels (later YouTube Shorts) is in the foreground.
- Count each reel as viewed when the user swipes to a new reel.
- Record per reel: timestamp, app, approximate watch time (time until the next swipe).

**F1.2 Daily reel budget**
- **Default: 300 reels/day.** The user can change it during onboarding or in settings.
- The day resets at **4:00 AM** local time by default (so late-night sessions count toward the day they started), configurable in settings.
- Optional: separate budgets per time window (e.g., stricter after 11 PM).

**F1.3 Red progress bar**
- A thin red line drawn across the top of the screen while a reels app is open.
- Fills from left to right as `reels_watched_today / daily_budget`.
- Colour states: normal red → pulsing at 80% → solid full bar plus a label ("Budget used") at 100%.
- Must not block touches on the feed; it is a passive, non-interactive overlay.

**F1.4 Alert every *n* reels**
- User-configurable *n* (default 15).
- At every *n*th reel, show a short interruption card such as "15 reels · 70% comedy, 20% rage-bait. Keep going, or take a 60-sec Psychology lesson?"
- Actions: **Continue**, **Switch to lesson** (opens F3), **Done for now** (goes to the home screen).
- **Back-off:** two **Continue** taps in a row in a session double the interval for that session; **Snooze for this session** stops check-ins until the session ends.
- After the budget is used: one budget card, then check-ins every 25 reels (configurable, never more often than every 10).

**Acceptance criteria**
- Reel count accuracy ≥ 90% against a manual count over a 100-reel session.
- Progress bar updates within 1 s of a swipe.
- Alert appears within 1 s of the *n*th reel.

---

### F2. Content classification and insights

**F2.1 Labels**
- **Platform labels (built-in):** Comedy, Fitness, Food, Travel, News, Education, Motivation, Finance, Music/Dance, Gaming, Relationships, Fashion/Beauty, Rage-bait/Drama, Ads/Promotions, Other.
- **Custom labels:** the user adds their own (e.g., "Cricket", "Finfluencer tips", "Exam memes"). Custom labels are sent to the classifier as part of the label list.
- The label list is capped (e.g., 25 labels) so on-device classification stays fast and accurate.

**F2.2 Classification**
- Sample screen frames while a reel is playing (e.g., 1 frame per 2 s, skipping frames that haven't changed).
- Use several models together, all on the phone (full details in §7.1 Model strategy):
  - **SigLIP 2** scores every sampled frame against the active label list (works with custom labels without retraining).
  - **Caption text classifier** labels the reel's caption, hashtags and creator name, if the Accessibility API exposes them.
  - **Speech understanding** for talking-head reels (e.g., someone talking in Hindi): capture 10–15 s of the reel's audio, transcribe it on the phone, and label the transcript (full details in §7.3).
  - **On-screen text** (burned-in captions in Hinglish or Devanagari) is read with ML Kit text recognition and labelled by the same text classifier.
  - **LFM2-VL-450M** (small vision-language model) runs only occasionally, producing a short description (people, objects, on-screen text) for summaries and for explaining flagged reels.
- Combine the per-frame and caption results into one label set per reel.
- Low-confidence results are labelled `Other` rather than guessed.

**F2.3 Flagged labels**
- The user marks labels as **"Never worth my time"** (e.g., Rage-bait, Gambling ads, Body-comparison content).
- When a flagged reel is detected:
  - The red bar flashes, and a small chip says something like "Flagged: Rage-bait".
  - Flagged reels count as one reel toward the budget, like any other (no double counting).
  - They are reported separately in insights.

**F2.4 Insights and summaries**
- **Today view:** reels watched vs budget, label breakdown (donut chart), flagged count, longest session, peak hour.
- **Weekly report:** trends by label, time of day, change from last week, and how many minutes were swapped for lessons.
- **AI summary:** a short text summary written by a small on-device text model from the structured records. Example: "You watched 212 reels this week. 48% were comedy, mostly after 11 PM. Rage-bait dropped from 18% to 9%. You switched to lessons 6 times."

**Acceptance criteria**
- Classification adds no visible lag to the reels app.
- Label accuracy ≥ 75% on a hand-labelled test set of 100 reels (built-in labels).
- No frame, screenshot or description ever leaves the device (verified by a network audit).

---

### F3. Stickman learning feed

**F3.1 Topics**
- The user picks the topics they want lessons on: presets (Psychology, Interview prep, General Knowledge, Personal finance basics, Science facts, History, Communication skills, Tech explained), custom topics they type, and opt-in suggestions based on what they doom-scroll (§7.4).
- Each topic has a level and an optional goal ("SDE interviews in Feb").
- Optional: lessons built from the user's own notes or PDFs (later version).

**F3.2 Lesson format and interactivity**
- A vertical, swipeable feed that looks and feels like Reels.
- Each lesson is 30–60 s: animated stickman segments with captions and a voice-over, in one of six formats (explainer, myth vs fact, story scenario, dialogue, guess first, series episode).
- **Interactive:** the app pauses between segments for interaction cards (predict, multiple choice, swipe true/false, reflect, your answer, go deeper) and ends with a recap card saved to a "Learned" collection. Full design in §7.4.

**F3.3 How lessons are made**
Rendering uses **[Stickman Studio](https://github.com/atharv01h/Stickman-Studio)** (open source, MIT license). It turns a topic or script into a narrated, animated stickman video, offline, with no API keys.

- **Mode: Cinematic Illustrated** (decided). Illustrated stickman scenes with animated diagrams, kinetic titles and word-by-word captions; local neural text-to-speech (Kokoro); 1080p MP4 export in **9:16** (the reel format). Test renders on Linux: §7.4 item 9.
- **Pipeline:**
  1. **OpenAI** plans a syllabus for the topic, writes the next lesson as structured JSON (segments, scenes, interactions, recap), and a second OpenAI call checks it for facts, length and safety (§7.4).
  2. A render worker calls Stickman Studio's Cinematic Illustrated pipeline (`CISPipeline.generate_video(params)`) **once per lesson**, with the full narration, a fixed visual style and accent colour, and the voice. This is the same Python class the desktop app uses; there is no documented CLI, so BoomDoom wraps it in its own worker.
  3. Pause points for interactions are taken from the word-level caption timings Stickman Studio writes to `project.json`.
  4. The video is compressed for mobile (e.g., 720p, H.264), stored in object storage, and saved with the lesson manifest (segments, pause times, interactions) as one lesson record.
- **Where it runs:** on the **deployed BoomDoom lesson backend** (§7.2), **not on the phone**. Each user's lesson inputs (topics, level, goal, language, voice, style) go to the backend, which builds that user's lessons, stores them, and serves them whenever the app needs one.
- **Interactions** are drawn by the BoomDoom app as native cards at the pause points, so they stay interactive and work offline.
- **Always ready:** the backend keeps a stock of unwatched lessons for each of the user's topics, and the app prefetches the next few onto the phone. Lessons therefore open instantly when an alert fires, even offline.
- **New custom topics** take minutes to appear the first time (the app shows "your lesson is being made" and notifies when ready); after that they come from stock.
- This avoids full AI video generation, which is slow and costly.

**F3.4 Entry points**
- From the *n*-reel alert ("Switch to lesson").
- From the budget-reached screen.
- When a flagged reel is detected (optional).
- Directly, from the BoomDoom home screen.

**F3.5 In-feed lesson player**
- On Android, lessons play in a full-screen, BoomDoom-branded vertical video overlay over the current Instagram screen. **You → Nudge for learning content** is on by default and shows the check-in or budget card first. Turn it off to play an available lesson directly at those milestones.
- Swipe up, tap Close, or use Android Back to dismiss the lesson and resume the same scrolling session. Lesson playback does not add to the Instagram reel count.
- Nothing is inserted into Instagram itself; this is BoomDoom's own overlay.

**Acceptance criteria**
- Lesson starts within 1 s of being chosen (from the cache).
- Each topic has at least 10 cached lessons ready for a demo.

---

## 6. Key user flows

**Onboarding**
1. Welcome → explain the "See → Understand → Replace" idea and the privacy promise.
2. Grant permissions (Accessibility, display over other apps, screen capture, record audio for reel speech), with a plain-language reason for each.
3. Set the daily reel budget and alert frequency *n*.
4. Pick labels to track, add custom labels, and mark flagged labels.
5. Pick learning topics.

**Daily use**
1. User opens Instagram Reels → the red bar appears at the top.
2. Reels are counted and classified in the background.
3. On the 15th reel → an alert with a mini breakdown → user taps "Switch to lesson".
4. A stickman lesson plays → quiz → "Back to Instagram" or "Another lesson".
5. At night → a summary notification: "Today: 64 reels, 3 lessons, rage-bait down 40%".

---

## 7. Technical architecture (Android app + lesson backend)

```
┌────────────── Android device ───────────────────────────────┐
│                                                             │
│  Accessibility Service ──► foreground app + scroll events   │
│          │                        │                         │
│          ▼                        ▼                         │
│  Reel counter ◄──────── Session manager ───► Overlay layer  │
│          │                        │          (red bar,      │
│          │                        │           alerts,       │
│          ▼                        ▼           lesson card)  │
│  MediaProjection frame sampler (1 frame / 2 s, skips dupes) │
│          │                                                  │
│          ▼                                                  │
│  SigLIP 2: label scores for every frame                     │
│  + caption text classifier (Accessibility text)             │
│  + reel audio (AudioPlaybackCapture) → on-device speech-to- │
│    text (Hindi/Hinglish) → multilingual text classifier     │
│  + LFM2-VL-450M: occasional descriptions                    │
│          │                                                  │
│          ▼                                                  │
│  Local DB (Room/SQLite): reels, labels, sessions, lessons   │
│          │                                                  │
│          ▼                                                  │
│  Insights engine + small on-device text model (summaries)   │
│                                                             │
│  Lesson player (ExoPlayer MP4 + native quiz) ◄─ lesson cache│
└─────────────────────────────────────────────────────────────┘
             ▲ lesson inputs (topics, level,       │ MP4 + quiz (CDN)
             │ goal, voice…) + watch/quiz events   ▼  no feed data, ever
┌──────────── BoomDoom lesson backend (deployed, §7.2) ───────┐
│  API → queue → OpenAI (syllabus, script, QA) → render workers│
│  (Stickman Studio) → object storage + CDN, Postgres catalog │
└─────────────────────────────────────────────────────────────┘
```

**Components**

| Component | Technology | Notes |
|---|---|---|
| App detection, swipe events | Android `AccessibilityService` | Detect the Reels screen and swipe or scroll events; requires a Play Store declaration |
| Overlays | `SYSTEM_ALERT_WINDOW` ("display over other apps") | Red bar is pass-through; alerts and lesson cards are opaque and full-screen |
| Screen frames | `MediaProjection` | User consent each session (Android shows a system prompt); persistent notification required |
| Frame classifier | SigLIP 2 (Apache 2.0) + small trained layer | Matches frames to text labels without retraining; exported to ONNX/TFLite for Android; latency to be measured on a mid-range phone. MobileCLIP2 is research-only (§7.1) |
| Caption / transcript classifier | EmbeddingGemma (308M, 100+ languages; candidate) + trained layer | Labels captions, on-screen text and speech transcripts in Hindi, Hinglish and English |
| Reel audio capture | Android `AudioPlaybackCapture` (Android 10+) | Uses the same screen-capture consent + `RECORD_AUDIO`; only for talking-head reels |
| Speech-to-text | Shruti / lipi-hn (Hinglish, MVP); IndicConformer packs (later) | On-device; first 10–15 s of the reel only |
| On-screen text | ML Kit Text Recognition v2 (Latin + Devanagari) | Reads burned-in captions |
| Scene describer | LFM2-VL-450M (Apache 2.0) | Runs occasionally, not on every frame; reads on-screen text; can be fine-tuned with LoRA |
| Storage | Room / SQLite | All data local; user can export or delete |
| Summaries | Small on-device text model | Works only from structured records, never from pixels |
| Lesson scripts | LLM (cloud or on-device) | Receives only the topic name and difficulty level, never feed data |
| Lesson backend | Deployed service (§7.2): API, queue, script + render workers, Postgres, object storage + CDN | Takes each user's lesson inputs, builds and stores lessons, serves them on demand |
| Lesson rendering | Stickman Studio (MIT), Cinematic Illustrated mode, in Linux render workers | `CISPipeline` called from our worker (forked, with fixes); Kokoro TTS; FFmpeg export to 9:16 MP4 |
| Lesson playback | ExoPlayer (Media3) + native quiz card | Plays cached MP4s instantly; quiz drawn by the app |

**Data model (simplified)**

```
Reel      { id, app, started_at, watch_ms, labels[], confidence, flagged, description }
Session   { id, app, started_at, ended_at, reel_count, flagged_count }
Label     { id, name, type: platform|custom, flagged: bool }
Budget    { date, limit, used, alert_every_n }
Topic     { id, name, type: preset|custom }
Lesson    { id, remote_id, topic_id, manifest_json, local_video_path, downloaded_at, watched_at, interaction_results }   // cache of backend lessons
```

**Example: zero-shot frame labelling (prototype in Python, then export for Android)**

```python
import torch, open_clip
model, _, preprocess = open_clip.create_model_and_transforms("ViT-B-16-SigLIP2", pretrained="webli")
tokenizer = open_clip.get_tokenizer("ViT-B-16-SigLIP2")

labels = ["a comedy skit", "a workout video", "a news clip", "a person ranting angrily at the camera",
          "an educational explainer", "an advertisement", "a cricket match", "something else"]
with torch.no_grad():
    text = model.encode_text(tokenizer(labels)); text /= text.norm(dim=-1, keepdim=True)   # cache once
    img  = model.encode_image(preprocess(frame).unsqueeze(0)); img /= img.norm(dim=-1, keepdim=True)
    scores = torch.sigmoid(model.logit_scale.exp() * img @ text.T + model.logit_bias)   # SigLIP uses sigmoid, not softmax
```
*(Model and checkpoint names to be confirmed against the OpenCLIP docs.)*

---

### 7.1 Model strategy

**Why more than one model.** BoomDoom's main job is sorting every frame into the user's labels quickly. A CLIP-style model does that best, because it matches images against text labels directly and runs in milliseconds. A vision-language model gives richer descriptions but is too slow to run on every frame, so it runs only occasionally. Caption text, if available, is the cheapest signal of all.

**Models considered**

| Model | Speed per frame | Custom labels | Fine-tunable | Reads text | Role in BoomDoom |
|---|---|---|---|---|---|
| **SigLIP 2** (Apache 2.0) | To measure (larger than MobileCLIP2) | ✅ zero-shot | ✅ easy (small trained layer on embeddings) | ❌ | **Classifying every frame** |
| **MobileCLIP2** | 3–15 ms (Apple-reported) | ✅ zero-shot | ✅ | ❌ | **Research-only license; not used** |
| **LFM2-VL-450M** | Slower (generates text) | ✅ via prompt | ✅ LoRA | ✅ (OCRBench 655) | Summaries, explaining flagged reels |
| **Caption text classifier** | < 5 ms | ✅ | ✅ | Reads caption text directly | Cheapest signal; check this first |

**Pipeline**

```
Every sampled frame → SigLIP 2: label scores (+ small layer trained on labelled reels)
Caption text        → small text classifier (if Accessibility exposes it)
Combine both        → final label(s) per reel
Occasionally        → LFM2-VL-450M: short description for summaries and flag explanations
```

**Improving accuracy**
1. **Better label wording:** write labels as short descriptions ("person ranting angrily at camera", not "rage_bait"); require agreement across several frames before accepting a label.
2. **Small trained layer:** a logistic-regression layer on SigLIP 2 image embeddings, trained on 300–500 labelled frames. Can also be trained on LFM2-VL description text.
3. **Per-user personalisation:** a user's label corrections update their own small layer on the phone, so custom labels keep getting better.
4. **Later:** LoRA fine-tune LFM2-VL-450M if descriptions need to be specific to reels.

**Training data plan**
- Screen-record your own reel sessions and sample frames from them.
- Label 300–500 frames across the 8 MVP labels (Label Studio or a spreadsheet).
- Keep 20% aside as a test set. Report "base model vs base model + trained layer" accuracy in the demo.

**Licensing**
- **MobileCLIP2 cannot be used in BoomDoom.** Its weights are released under Apple's research model license, which limits use to "Research Purposes" and states that this "does not include any commercial exploitation, product development or use in any commercial product or service" (`LICENSE_MODELS` in apple/ml-mobileclip). **SigLIP 2 (Apache 2.0) is the frame classifier.** Because the license also excludes product development, don't use MobileCLIP2 anywhere in BoomDoom's work, including internal benchmarks.
- LFM2-VL-450M is Apache 2.0.

**Android deployment**
- Export SigLIP 2's image encoder to ONNX or TFLite and run it with ONNX Runtime Mobile or LiteRT on Android; use the smallest variant that meets the accuracy target, and int8-quantise it.
- Text-label embeddings are computed once whenever the label list changes and cached, so each frame needs only the image encoder.
- **LFM2-VL-450M on Android: llama.cpp**, embedded directly through the NDK. Liquid's docs say upstream llama.cpp supports LFM2-VL projectors and that their LEAP SDK is deprecated, so use llama.cpp, not LEAP. Load the GGUF model plus its `mmproj` projector file. Speed on a mid-range phone still needs measuring.

**Pitch line:** *"SigLIP for fast labelling, a small vision-language model for understanding. All on your phone."*

### 7.2 Lesson backend (deployed)

The backend takes each user's lesson inputs, builds stickman lessons for them with Stickman Studio, stores them, and serves them to the app whenever one is needed.

**Per-user lesson inputs (lesson profile)**

| Input | Example | Used for |
|---|---|---|
| Topics | Psychology, Interview prep, "System design" | What lessons to make |
| Goal (optional) | "SDE placement interviews in Feb" | Tailoring scripts |
| Level | Beginner / Intermediate / Advanced | Script depth |
| Language | English (others where the TTS voice supports them) | Script + voice |
| Voice and style | Kokoro voice ID; Stickman Studio style | Rendering |
| Lesson length | 30 / 45 / 60 s | Script length |
| Avoid list | Lessons already watched, disliked topics | No repeats |

**Architecture**

```
App ──HTTPS──► API (FastAPI) ──► Postgres (users, profiles, lessons, events)
                   │
                   ▼
             Job queue (SQS)
                   │
        ┌──────────┴───────────┐
        ▼                      ▼
  Script worker (OpenAI)  Render workers (Stickman Studio CISPipeline, Linux)
  syllabus → script JSON   lesson → TTS → animation → FFmpeg → 720p 9:16 MP4
  → quality check (§7.4)       │
                               ▼
                    Object storage (S3 / R2) + CDN ──► app downloads MP4 + thumbnail
                               │
                    Push notification (FCM) when a requested lesson is ready
```

**Lesson lifecycle**
1. **Profile saved** (onboarding or edit) → the API queues a starter batch (e.g., 5 lessons per topic).
2. **Script worker** picks the next concept from the user's syllabus, writes the lesson script JSON with OpenAI (segments, scenes, interactions, recap), and runs the quality check (§7.4).
3. **Render worker** renders the lesson as one video with Stickman Studio, reads pause times from its caption timings, compresses the video, and uploads video + thumbnail + manifest to object storage.
4. **Catalog** marks the lesson `ready` in Postgres.
5. **App** fetches the feed, prefetches the next 3–5 lessons per topic over Wi-Fi, and plays them from the phone when needed.
6. **Refill:** when a user's unwatched stock for a topic drops below 3, the backend queues more automatically.
7. **Events** (watched, skipped, quiz result, liked) feed back into the next scripts (e.g., revisit concepts missed in quizzes).

Lesson status: `queued → scripting → rendering → ready` (or `failed`, retried up to 3 times).

**API (v1)**

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/v1/users` | Create an anonymous user; returns user ID + token |
| `POST` | `/v1/users/{id}/link` | Optional login: link the anonymous user to a Google or phone-OTP account, so lessons and progress sync across devices |
| `PUT` | `/v1/users/{id}/lesson-profile` | Save lesson inputs; triggers the starter batch |
| `POST` | `/v1/lessons/generate` | Request lessons for a topic: `{topic, count}`; returns job IDs |
| `GET` | `/v1/jobs/{job_id}` | Job status |
| `GET` | `/v1/feed?topic=&limit=` | Ready, unwatched lessons with signed CDN URLs + quiz JSON |
| `POST` | `/v1/lessons/{id}/events` | Watched / skipped / quiz result / liked |
| `DELETE` | `/v1/users/{id}` | Delete the user and all their backend data |

**Backend data model**

```
User          { id, created_at, plan, auth_provider | null, auth_subject | null }   // null = anonymous
LessonProfile { user_id, topics[{name, level, goal}], language, voice_id, style, length_s, updated_at }
Syllabus      { id, topic, level, goal | null, owner_user_id | null, concepts[] }
SyllabusProgress { user_id, syllabus_id, next_index, weak_concepts[] }
Lesson        { id, owner_user_id | null (shared), syllabus_id, concept, format, topic, level, language,
                voice_id, style, manifest_json (segments + clip_urls + interactions + recap),
                duration_s, thumb_url, status, created_at }
UserLesson    { user_id, lesson_id, delivered_at, watched_at, skipped, liked,
                interaction_results[{interaction_id, answer, correct}], go_deeper_choice }
Job           { id, user_id, lesson_id, stage, attempts, error, created_at, finished_at }
```

**Reuse across users (cost control)**
- Lessons with the same topic, level, language, voice and style can be **shared**: before generating, the backend serves unseen shared lessons that match the user's inputs.
- Lessons built from a personal goal or custom topic stay **personal** (`owner_user_id` set).
- This keeps popular topics (GK, interview basics) cheap while still allowing fully personal lessons.

**Deployment (AWS)**

| Part | AWS service | Notes |
|---|---|---|
| API | ECS on Fargate (FastAPI container) behind an Application Load Balancer | Autoscale on CPU / request count |
| Job queue | SQS (one queue per stage: script, render) | Replaces Redis/Celery; dead-letter queue for failed jobs |
| Script worker | ECS on Fargate | Calls OpenAI; small and stateless |
| Render workers | **Linux** EC2 instances (compute-optimised, CPU only) in an Auto Scaling group, running Stickman Studio in a Docker image | Linux works (tested, §7.4 item 9); scale on SQS queue depth; Spot instances to cut cost |
| Database | RDS for PostgreSQL | Users, profiles, syllabi, lessons, events |
| Video storage | S3 + CloudFront | Signed URLs; lifecycle rules delete personal lessons of deleted users |
| Optional login | Amazon Cognito (Google sign-in, phone OTP) | Anonymous by default; linking an account syncs lessons across devices |
| Secrets | AWS Secrets Manager | OpenAI API key, DB credentials |
| Push | Firebase Cloud Messaging (called from the API) | "Your lesson is ready" |
| Monitoring | CloudWatch | Queue depth, render time per lesson, failure rate |

- **Hackathon version:** one EC2 instance running the API, one render worker and Postgres in Docker Compose, plus an S3 bucket. Move to the full setup after the demo.
- **Render cost basis:** measured ~2× real time on 2 vCPUs (a 24 s lesson rendered in ~50 s), so a 60 s lesson takes roughly 2 minutes of one 2-vCPU worker. Measure again on the chosen instance type.
- **Limits:** cap new lessons per user per day on the free tier; higher on Pro.

### 7.3 Regional language and speech understanding

**The problem.** Many Indian reels are a person talking to the camera in Hindi or Hinglish (finance tips, motivation, rants, relationship advice). Visually they all look the same: "a person talking". The meaning is in the speech, so BoomDoom has to listen to the reel, on the phone.

**Pipeline**

```
Visual classifier says "person talking to camera" (or isn't confident)
        ↓
Capture 10–15 s of the reel's audio (AudioPlaybackCapture)
        ↓
On-device speech-to-text (Hindi / Hinglish) → transcript
        ↓
Multilingual text classifier → labels (finance tips, motivation, rant, comedy…)
        ↓
Combine with visual + caption + on-screen-text labels → final label
        ↓
Audio and transcript deleted right away; only labels are stored
```

**1. Capturing the reel's audio**
- Android's `AudioPlaybackCapture` API (Android 10+) lets an app copy the audio another app is playing. It uses the same screen-capture consent BoomDoom already asks for, plus the `RECORD_AUDIO` permission.
- Apps targeting API 29+ allow capture of media audio by default, but an app can opt out (`android:allowAudioPlaybackCapture="false"`). **Testing whether Instagram's reel audio can be captured is a day-one spike.**
- **Fallback if Instagram blocks it:** read burned-in captions (common on Hindi/Hinglish talking-head reels) with ML Kit Text Recognition v2 (Latin + Devanagari), and use caption text from Accessibility.

**2. On-device speech-to-text**

| Model | Size | Languages | Role |
|---|---|---|---|
| **Shruti** (Udgam Labs) | 29.5 MB | Hindi, English, Hinglish (Devanagari + Latin output) | **MVP default**; weaker on pure Hindi |
| **lipi-hn** | 14.4 MB | English + Hinglish (Roman-script output), streaming | Smallest option / alternative MVP choice |
| **IndicConformer** (AI4Bharat, MIT) | ~131–140 MB per language (int8 ONNX / GGUF packs) | 22 Indian languages | Downloadable language packs after MVP; best accuracy |
| **Indic Whisper** (whisper.cpp, q5_1) | ~181 MiB per language | Hindi, Kannada, Tamil, Telugu, Gujarati | Alternative language packs |

- Only the user's chosen languages are downloaded.
- A perfect transcript isn't needed: topic labels survive 15–20% word errors because the key words ("SIP", "mutual fund", "gym", "breakup") carry the meaning.

**3. Labelling the text**
- One small **multilingual text-embedding model** on the phone handles transcripts, Accessibility captions and on-screen text, in Hindi, Hinglish and English.
- Zero-shot: compare the text embedding with label descriptions, so custom labels work without retraining. A small trained layer on top improves accuracy.
- Label lists stay in English internally; users never see them in another language unless the app UI is localised.

**4. Battery and privacy**
- Audio is captured only for talking-head or low-confidence reels, and only the first 10–15 seconds.
- Audio and transcripts are processed in memory and deleted immediately; only the final labels are stored.
- Nothing is sent to any server, including OpenAI.

**5. Using OpenAI credits (development only)**
OpenAI is never used on users' reels, because that would break the on-device promise. For reel understanding it is used only to build the models (it also writes lesson scripts on the backend, §7.4, which involves no feed data):
- **Auto-labelled data:** the team screen-records reels it watches itself; OpenAI transcribes and labels them to build a Hindi/Hinglish training and test set quickly.
- **Synthetic transcripts:** GPT writes a few hundred realistic Hindi and Hinglish talking-head transcripts per label to train the text classifier.
- **Accuracy check:** compare on-device transcripts with OpenAI transcripts of the same clips to measure the accuracy lost on the phone.

**Demo moment:** a Hindi finance-tips reel plays, and "Finfluencer tips 🚩" appears live with a snippet of the Hinglish transcript.

### 7.4 Lesson content and interactivity

**Goal:** lessons that feel like reels but make the user *think* every 15–20 seconds. OpenAI writes the scripts, Stickman Studio animates them, and the app adds the interactions.

**1. Choosing topics**
- **Presets:** Psychology, Interview prep, General Knowledge, Personal finance basics, Science facts, History, Communication skills, Tech explained.
- **Custom:** anything the user types ("System design", "Stoicism", "Indian polity").
- **Suggestions from the feed (opt-in):** the phone maps what the user doom-scrolls to something worth learning. Only topics the user accepts are sent to the backend, never the feed labels themselves.

| Watches a lot of… | Suggested topic |
|---|---|
| Finfluencer reels | Personal finance basics |
| Cricket reels | Cricket stats & strategy |
| Relationship rants | Psychology of relationships |
| Fitness reels | Exercise science |
| Motivation reels | Habits & productivity |

- Each topic has a **level** (beginner / intermediate / advanced) and an optional **goal** ("SDE interviews in Feb").

**2. Lesson formats** (mixed so the feed doesn't feel repetitive)

| Format | Example | Fits |
|---|---|---|
| **Explainer** | "The Zeigarnik effect in 45 seconds" | Psychology, science |
| **Myth vs Fact** | "Myth: you need ₹1 lakh to start investing" | Finance, health, GK |
| **Story scenario** | A stickman nervously walks into an interview… | Interview prep, soft skills |
| **Dialogue** | Interviewer vs candidate, two voices (Stickman Studio dialogue mode) | Interview prep, debates |
| **Guess first** | "Which country has the most time zones?" → pause → reveal | GK, trivia |
| **Series episode** | "Stoicism, part 3 of 10" | Custom topics, curricula |

**3. Interactions**

Stickman Studio outputs plain MP4 video, so the app adds interactions as native cards **between video segments**. A typical lesson:

```
[Hook 3s] → [Setup 15s] → ❓ Interaction card → [Reveal 10s] → [Takeaway 8s] → 🃏 Recap card
```

| Interaction | What the user does |
|---|---|
| **Predict** | Video pauses: "What do you think happens next?" User taps an answer, then the reveal plays |
| **Multiple choice** | Tap 1 of 3–4 options; instant right/wrong feedback |
| **Swipe true / false** | Swipe right = true, left = false |
| **Reflect** | "Has this happened to you?" Personal reflection only; other users' answers are never shown |
| **Your answer** (interview prep) | "How would you answer this?" User types or thinks, then sees a model answer |
| **Go deeper** | Choose the next sub-topic ("Want to see how to beat this bias?") |
| **Recap card** | One-line takeaway, saved to a "Learned" collection |

**4. Generation pipeline (backend)**

```
Topic + level + goal + language
      ↓
① Curriculum planner (OpenAI)  → 20–30 concept syllabus per topic; tracked per user
      ↓
② Script writer (OpenAI, Structured Outputs) → next concept → lesson script JSON
      ↓
③ Quality checker (OpenAI, cheaper model) → fact-check, length, tone, safety
      ↓
④ Render (Stickman Studio, Cinematic Illustrated) → one video per lesson + pause times
      ↓
⑤ Package → lesson manifest JSON (with pause times) + video + thumbnail → S3 + CloudFront
```

- **① Curriculum planner:** builds an ordered syllabus once per topic + level (+ goal), so lessons progress and never repeat. Shared syllabi for preset topics; personal ones for goals and custom topics.
- **② Script writer:** uses OpenAI Structured Outputs with the JSON schema below, so every script is valid and the pipeline never breaks on malformed output. Narration length is set by word count: Stickman Studio's voice speaks about **3 words per second** (measured), so a 60 s lesson is about 180 words.
- **③ Quality checker:** a second call that rejects or fixes scripts with factual errors, wrong length, more than one main idea, or anything that reads as specific financial, medical or legal advice (adds a short disclaimer where the topic needs one). Failed scripts are regenerated, up to 2 times.
- **④ Render:** the whole narration goes to Stickman Studio's Cinematic Illustrated pipeline **once per lesson**, with a fixed visual style (`B1`, `B2` or `B3`) and a fixed accent colour so every lesson in a topic looks consistent. Stickman Studio writes word-level caption timings to `project.json`; the worker finds the last word of each segment there and records it as a pause time.
- **⑤ Package:** the lesson becomes a manifest (below) plus one video; the app plays it with ExoPlayer, pauses at each pause time, and shows the interaction card.

**5. Lesson script / manifest schema (what OpenAI returns)**

```json
{
  "lesson_id": "psy-012",
  "topic": "Psychology",
  "concept": "Zeigarnik effect",
  "format": "guess_first",
  "level": "beginner",
  "language": "en",
  "duration_s": 50,
  "cis_visual_style": "B1",
  "cis_accent_color": "#2F6F5E",
  "voice_id": "af_heart",
  "segments": [
    {
      "id": "hook",
      "narration": "Ever noticed you can't stop thinking about an unfinished task?",
      "scene": "Stickman lying in bed, thought bubble with a half-done to-do list",
      "duration_s": 5
    },
    {
      "id": "setup",
      "narration": "In the 1920s, a psychologist noticed waiters remembered unpaid orders perfectly, but forgot them once paid.",
      "scene": "Stickman waiter juggling order slips",
      "duration_s": 15
    },
    { "id": "reveal", "narration": "...", "scene": "...", "duration_s": 10 },
    { "id": "takeaway", "narration": "...", "scene": "...", "duration_s": 8 }
  ],
  "interactions": [
    {
      "after_segment": "setup",
      "type": "predict",
      "question": "Why do you think the waiters forgot paid orders?",
      "options": ["Paid orders were boring", "The brain lets go of finished tasks", "They were tired"],
      "answer_index": 1,
      "feedback_correct": "Exactly! Finished tasks stop nagging the brain.",
      "feedback_wrong": "Close. It's about the task being finished."
    }
  ],
  "recap": "Unfinished tasks stick in your mind. Start a task to beat procrastination.",
  "go_deeper": ["How to use this to beat procrastination", "Why cliffhangers work"],
  "disclaimer": null
}
```

After rendering, the backend adds `video_url`, `thumb_url` and a `pause_at_s` value to each segment (from the caption timings); the app receives this manifest from `/v1/feed`.

The `scene` field is a hint, not a command: Stickman Studio picks its illustrations from the narration text itself and has no input for scene descriptions. Keep `scene` in the schema for a future fork that accepts storyboards; until then, write narration with concrete, visual nouns.

Interaction `type` values: `predict`, `mcq`, `true_false`, `reflect`, `your_answer`, `go_deeper`.

**6. Script-writer prompt (sketch)**

> You are a script writer for 30–60 s vertical stickman micro-lessons for Indian Gen Z.
> Topic: {topic}. Level: {level}. Goal: {goal}. Language: {language}.
> Concept: {next concept from syllabus}. Already covered: {list}. Recently answered wrong: {list}.
> Format: {format}.
> Rules: hook in the first 3 seconds; one idea only; at least one interaction every 15–20 s; simple, conversational language; about 3 words per second of target length; concrete, visual nouns in the narration (the animator picks illustrations from it); one-line scene hint per segment; no specific financial, medical or legal advice.
> Return JSON matching the schema.

**7. Personalisation loop**
- **Quiz results:** concepts answered wrong come back as a short "remember this?" card two days later (spaced repetition).
- **Skips:** formats the user often skips are shown less; formats they finish are shown more.
- **Difficulty:** consistent right answers raise the level; wrong answers slow it down.
- **Go deeper choices** reorder the user's personal syllabus.

These signals reach the backend only as lesson events (§7.2), never as feed data.

**8. OpenAI models and cost** (prices from OpenAI's pricing page, 21 Nov 2026; standard tier, per 1M tokens)

| Step | Model | Price (input / output) | Why |
|---|---|---|---|
| ① Curriculum planner | `gpt-6.2-sol` | $2.00 / $10.00 | Runs once per topic + level; quality matters most here |
| ② Script writer | `gpt-6.1-terra` | $1.00 / $5.00 | Good writing at half the price; runs for every lesson |
| ③ Quality checker | `gpt-6-luna` | $0.10 / $0.50 | Cheap yes/no checks with short fixes |

**Estimated OpenAI cost per lesson: about $0.01 (under ₹1).**
- Script: ~1,500 input + ~1,000 output tokens on `gpt-6.1-terra` ≈ $0.0065.
- Quality check: ~2,500 input + ~300 output tokens on `gpt-6-luna` ≈ $0.0004.
- Syllabus: ~1,000 input + ~2,000 output tokens on `gpt-6.2-sol` ≈ $0.022 per topic, spread over ~30 lessons ≈ $0.0007.

These are estimates; log real token counts from the first 50 lessons. Rendering on AWS, not OpenAI, will be the main cost per lesson. Model choice can be swapped per step if quality or prices change.

**9. Stickman Studio render test (21 Nov 2026, Linux, 2 vCPU, no GPU)**

A 74-word lesson (the Zeigarnik effect example) was rendered headless with `CISPipeline`, without the desktop UI.

| Result | Finding |
|---|---|
| Runs on Linux | **Yes**, with two patches (below). No Windows VMs needed |
| Render time | **~50 s for a 24 s video** (~2× real time), with style and voice fixed |
| Output | 1080×1920 H.264 + AAC, ~5–6 MB for 24 s (compress to 720p for the app) |
| Speaking rate | ~3 words per second |
| Consistency | Fixing `cis_visual_style` (`B1`) and `cis_accent_color` gives one consistent look per lesson. Rendering segments separately reset titles and colours and saved no time, so **render one video per lesson** |
| Pause points | `project.json` contains word-level caption timings, which give exact pause times |
| Scene control | Illustrations are chosen from the narration text by Stickman Studio's own planner; some were off-topic (e.g., an unrelated "dopamine" diagram). There is no storyboard input |

Patches and bugs to fix in our fork:
- **NVENC check:** it uses the NVIDIA encoder whenever FFmpeg lists it, even without a GPU, and the render fails. Fix: fall back to `libx264` when no GPU is present.
- **Fonts:** captions only look for Windows fonts (`C:/Windows/Fonts`) and fall back to a tiny default font on Linux. Fix: point the font folder at bundled fonts (e.g., Liberation Sans, Noto).
- **Background music** fails to mix (`AudioMixer.mix_audio()` argument mismatch), so videos have no music; **sound effects** log a type error; **title icons** show as empty boxes (missing glyph font).
- **Off-topic illustrations:** later, add a storyboard input to the fork so the script's `scene` hints drive the illustrations.


---

## 8. Privacy and safety

- **On-device first:** frames, reel audio and transcripts are processed in memory and dropped right away; they are never stored or uploaded.
- Only structured labels and short descriptions are saved, locally.
- The lesson backend receives only lesson inputs (topics, level, goal, language, voice, style) and lesson events (watched, skipped, quiz result). It never receives frames, labels, descriptions or anything else about the user's feed.
- Users are identified by an anonymous account ID by default. Login (Google or phone OTP) is optional and only used to sync lessons and progress across devices. Deleting the account deletes their lesson profile, events and personal lessons on the backend.
- Settings include "Delete all my data" and a pause toggle.
- Clear, plain-language permission screens; no hidden tracking.
- Built to follow India's DPDP Act: explicit consent, a stated purpose, and easy deletion.

---

## 9. Competitive landscape

| Capability | Blockers (one sec, ScreenZen, Opal) | Learning feeds (ScrollEd, CogniScroll, ZunoScroll) | Content analysers (AlgoMirror, DoomLess AI) | Instagram "Your Algorithm" | **BoomDoom** |
|---|---|---|---|---|---|
| Time / reel tracking | ✅ | ❌ | ✅ | ❌ | ✅ |
| Knows *what* was watched | ❌ | ❌ | ✅ partial | ✅ (to keep you scrolling) | ✅ |
| Custom labels | ❌ | ❌ | ❌ | Topics only | ✅ |
| On-device, private analysis | Partial | n/a | Unclear / desktop | ❌ | ✅ |
| Learning feed | ❌ | ✅ | ❌ | ❌ | ✅ |
| Lesson offered at the doom-scroll moment | ❌ | ❌ | ❌ | ❌ | ✅ |

**Positioning:** BoomDoom is the only one that closes the loop. It sees what you watch, privately, and replaces it at the right moment.

---

## 10. Risks and constraints

| Risk | Impact | Mitigation |
|---|---|---|
| Play Store scrutiny of Accessibility API use | Rejection | Narrow, declared purpose (digital wellbeing); prominent disclosure; sideload APK for the hackathon |
| MediaProjection consent prompt every session | Friction | Explain it in onboarding; optional mode that uses Accessibility events only (counting without classifying) |
| Battery and heat | Uninstalls | Sample sparsely, skip unchanged frames, use the NPU, pause when the battery is low |
| Hindi/regional talking-head reels | Visuals alone can't tell the topic | On-device speech-to-text + multilingual text classifier (§7.3); burned-in captions via ML Kit; label `Other` when unsure |
| Instagram blocks audio capture (`allowAudioPlaybackCapture=false`) | No speech signal | Test on day one; fall back to burned-in captions (ML Kit) and Accessibility caption text |
| Speech-to-text cost on battery | Drain, heat | Only talking-head/low-confidence reels; first 10–15 s; small models (Shruti 29.5 MB / lipi-hn 14.4 MB) |
| Weak accuracy on pure Hindi or other Indian languages | Mislabels | Downloadable IndicConformer packs per language; user corrections train the text classifier |
| LFM2-VL too slow or heavy on some phones | Laggy summaries | Run it only on the charger or in batches at night; summaries can work from labels alone |
| Stickman Studio is a small project with no documented CLI and some bugs (NVENC check, Windows-only fonts, music mixing) | Render failures or poor output | Pin a known-good commit in our own fork; fix the bugs listed in §7.4 item 9; call `CISPipeline` from our own worker. Linux rendering already tested |
| Stickman Studio picks illustrations from narration, sometimes off-topic | Confusing visuals | Write concrete narration; quality-check thumbnails; later add a storyboard input to the fork |
| New custom-topic lessons can't be rendered instantly | Users wait for new topics | Keep stock per topic; share lessons across users with the same topic + level; show "your lesson is being made" and notify when ready |
| Render cost grows with users | Server bills | Share lessons across users where inputs match; daily per-user generation cap on the free tier; autoscale workers on queue depth |
| AI-written lessons contain factual errors | Users learn wrong things; trust drops | OpenAI quality-check pass; curated syllabi for preset topics; "Report a mistake" button on every lesson |
| Backend down or slow | No new lessons | App plays cached and bundled starter lessons; lessons are prefetched ahead of need |
| Lesson MP4 sizes | Storage and data use | Compress to 720p; cache only the next few lessons per topic |
| MobileCLIP2's research-only license | Can't ship it | Decided: SigLIP 2 (Apache 2.0); MobileCLIP2 not used at all |
| Instagram may not expose captions to Accessibility | Lose the cheapest signal | Vision-only classification still works; test early |
| Classification errors | Lost trust | Show confidence; let users correct labels (corrections improve their custom labels) |
| Instagram UI changes break detection | Tracking stops | Use visual detection as a fallback; remote config for screen selectors |
| iOS cannot support this | Smaller market | Android-first; iOS later with a reduced version (Screen Time shield + learning app) |
| Instagram's Terms of Service | Legal | Never modify Instagram or scrape its servers; only read the user's own screen with their consent |

---

## 11. Success metrics

| Metric | Target (8 weeks after launch) |
|---|---|
| Daily reels watched per active user | −30% from week-1 baseline |
| Share of flagged-label reels | −40% |
| Alert → lesson conversion | ≥ 20% of alerts |
| Lessons completed per user per week | ≥ 5 |
| Week-4 retention | ≥ 35% |
| User-reported "time well spent" (1–5) | ≥ 4.0 |

---

## 12. Hackathon MVP scope

**Build**
- Android app tracking Instagram Reels only.
- Reel counter, daily budget, red progress bar, alert every *n* reels.
- On-device classification (SigLIP 2 + small trained layer, LFM2-VL-450M for descriptions) with 6–8 built-in labels + 1–2 custom labels; 1 flagged label.
- Accuracy slide: base model vs. base model + trained layer on our test set.
- Hindi/Hinglish speech understanding for talking-head reels (Shruti or lipi-hn + multilingual text classifier), with ML Kit on-screen text as fallback.
- Today view: donut chart, counts, a one-paragraph AI summary.
- Lesson backend, minimal version: FastAPI + a single render worker (Stickman Studio) + local or S3-compatible storage, deployed on one machine.
- Stickman feed with 3 topics (Psychology, Interview prep, GK) × 10 interactive lessons pre-built by the backend pipeline (OpenAI → Stickman Studio), plus one live custom topic generated during the demo.
- Interactions in the MVP: predict, multiple choice, swipe true/false, and the recap card.
- P1 (if time allows): an in-feed lesson card overlay after a swipe.

**Skip**
- YouTube Shorts, iOS, weekly reports, accounts, cloud sync, lessons from the user's own documents.

**2-minute demo script**
1. **(0:00–0:20) Problem:** "Gen Z calls phone dependence its biggest problem. Screen-time apps say 3 hours; they never say *of what*."
2. **(0:20–0:50) Track + Understand:** Open Instagram Reels. The red bar fills as you swipe. Labels appear live (Comedy, Rage-bait 🚩, Cricket). A Hindi finance-tips reel shows "Finfluencer tips 🚩" with a snippet of its Hinglish transcript.
3. **(0:50–1:15) Alert:** On the 10th reel, "10 reels: 60% comedy, 2 flagged. Switch to a 60-sec Psychology lesson?"
4. **(1:15–1:40) Replace:** A stickman sets up the "Zeigarnik effect"; the video pauses for a "predict" card; the user taps an answer; the reveal plays; a recap card is saved.
5. **(1:40–2:00) Insights + privacy:** The Today view and AI summary, then the line "Not one frame left this phone. Every model runs on-device."

---

## 13. Monetisation (later)

- **Freemium:** free tracking + 3 topics; **Pro** (₹99–149/month) for unlimited custom labels and topics, weekly reports, and lessons from your own notes.
- **Exam and interview packs:** paid curated topic packs (UPSC GK, placement interview prep).
- **Campus / institution plans:** colleges sponsor BoomDoom for students.
- Never sell or share feed data.

---

## 14. Roadmap

| Phase | Scope |
|---|---|
| **v0.1 Hackathon** | MVP scope in §12 |
| **v0.5 Beta** | YouTube Shorts, weekly report, label corrections, more topics, battery tuning; backend autoscaling and lesson sharing across users |
| **v1.0 Launch** | Play Store release, Pro tier, lessons from the user's own notes/PDFs, streaks |
| **v1.x** | Time-of-day budgets, accountability buddy (shares totals only), reduced iOS version |

---

## 15. Decisions and open questions

*Updated 21 Nov 2026 with the team's answers, research, and a Stickman Studio test render.*

### 15.1 Decided

| # | Question | Decision |
|---|---|---|
| 1 | Default daily budget | **300 reels/day** (user can change it) |
| 2 | Flagged reels count double? | **No**, they count as one reel |
| 3 | Who writes lesson scripts? | **OpenAI** (team credits) |
| 4 | Stickman Studio mode | **Cinematic Illustrated**, fixed visual style + accent colour per topic |
| 5 | In-feed lesson card | **P1**, built after the MVP if time allows |
| 11 | Where render workers run | **AWS**, on Linux EC2 workers (Linux rendering tested, §7.4 item 9) |
| 12 | Accounts | **Anonymous by default, optional login** (Cognito: Google or phone OTP) to sync across devices |
| 18 | Show other users' poll answers? | **No.** "Poll" became a personal "Reflect" card |

### 15.2 Answered by research and testing

| # | Question | Answer |
|---|---|---|
| 8 | MobileCLIP2 or SigLIP 2? | **SigLIP 2.** MobileCLIP2's weights are research-only: the license excludes commercial use and product development, so it can't be used in BoomDoom at all (§7.1). |
| 9 | LFM2-VL-450M runtime on Android | **llama.cpp**, embedded via the NDK (upstream supports LFM2-VL; Liquid's LEAP SDK is deprecated). Speed still needs a phone test (S5). |
| 10 | Render time for a lesson | **~2× real time on 2 vCPUs, no GPU** (24 s video in ~50 s), so ~2 min for a 60 s lesson. Re-measure on the chosen EC2 type. |
| 15 | Multilingual text-embedding model | **Candidate: EmbeddingGemma** (308M parameters, 100+ languages, under 200 MB RAM when quantised). If too slow on a mid-range phone, try a smaller multilingual model (e.g., multilingual-e5-small). Speed test in S5. |
| 16 | Segments vs one video | **One video per lesson.** Pause times come from Stickman Studio's word-level caption timings in `project.json`. Separate segments reset titles and colours and saved no time. |
| 17 | OpenAI models and cost | Syllabus `gpt-6.2-sol`, scripts `gpt-6.1-terra`, quality check `gpt-6-luna`. **~$0.01 (under ₹1) per lesson** in tokens (§7.4 item 8). |

### 15.3 Still open: phone spikes (need a real Android phone)

Run S1–S3 on day one: they decide whether tracking, captions and speech work at all.

**S1. Reel boundaries from Accessibility events (Q6)**
1. Build a minimal `AccessibilityService` that logs scroll and content-change events for `com.instagram.android`.
2. Swipe through 100 reels while counting by hand.
3. **Pass:** ≥ 90% count accuracy (F1 acceptance criterion). **If it fails:** detect reel changes from screen frames (big frame difference after a swipe).

**S2. Captions, hashtags and creator names via Accessibility (Q7)**
1. With a Reel open, run `adb shell uiautomator dump` and read the XML (or log `getRootInActiveWindow()` from the service).
2. Look for the caption text, hashtags and username in `text` / `content-desc` fields.
3. Repeat on 10 reels, including Hindi captions. **If missing:** rely on ML Kit on-screen text and speech.

**S3. Reel audio capture (Q13)**
1. Minimal app: MediaProjection consent + `AudioPlaybackCaptureConfiguration` with `USAGE_MEDIA`, plus `RECORD_AUDIO`.
2. Play reels in Instagram and check whether the captured audio is non-silent and clear.
3. **If silent:** Instagram blocks capture; use burned-in captions (ML Kit) and Accessibility captions only.

**S4. Shruti vs lipi-hn (Q14)** (after S3 passes)
1. Capture 50 Hinglish talking-head reels (15 s each).
2. Make reference transcripts with OpenAI `gpt-transcribe` (~$0.0045/min, about ₹3 for all 50) and hand-check 10.
3. Compare word error rate, **topic-label accuracy** (what matters), speed and RAM on the phone. Pick the winner.

**S5. On-phone speed (Q9, Q15, SigLIP 2)**
1. On a mid-range Android phone, benchmark: SigLIP 2 image encoder (int8), EmbeddingGemma vs a smaller multilingual model, and LFM2-VL-450M via llama.cpp.
2. Record milliseconds per call, RAM, and battery drain over 10 minutes of reel scrolling.
3. **Targets:** frame labelling fast enough for 1 frame / 2 s without lag; text embedding < 100 ms per caption; LFM2-VL only in the background.
