# BoomDoom — Complete End-to-End User Stories

**Product:** BoomDoom — Turn Doom Into Boom  
**Platform:** Android  
**Document Type:** User Stories, UX Flows & Acceptance Criteria  
**Version:** 1.1  
**Date:** 21 November 2026  
**Primary Users:** Students, young professionals, and people seeking healthier scrolling habits

### Changes in v1.1
- Check-ins back off when the user keeps continuing, can be snoozed, and have a defined interval after the budget is reached (US-03, US-15, US-16).
- Built-in categories are always tracked; onboarding only adds custom labels and flags (US-04, US-05).
- Shorter onboarding: budget and check-ins on one screen, skippable label screens, difficulty and goal moved later, and a "try a lesson first" step before permissions (US-02 to US-06A).
- Android setup realities added to permissions: restricted settings, screen-capture notification and re-consent, single-app sharing (US-07).
- Aligned with the product spec: optional login (P1), one video per lesson, Hindi/Hinglish speech understanding in the MVP, extra interaction types, feed-based topic suggestions.
- New stories: daily reset time, session rules, going over budget, fixing wrong labels, Learned collection, reporting a mistake, demo mode.
- One welcome line, a confidence threshold for flags, and a second persona.

---

# 1. Product Vision

BoomDoom is an AI-powered digital wellbeing application that helps users understand what they consume on short-video platforms and replace unproductive scrolling with personalized, interactive learning.

Unlike traditional screen-time applications, BoomDoom goes beyond measuring time. It helps users understand their content consumption patterns, recognize undesirable scrolling habits, and discover opportunities to learn.

The experience follows three principles:

**SEE → UNDERSTAND → REPLACE**

- **See:** Track the number of reels consumed and monitor progress against a personal scrolling budget.
- **Understand:** Classify consumed content into meaningful categories and present insights about viewing habits.
- **Replace:** Offer engaging 30–60-second cinematic stickman lessons at moments when the user may benefit from switching activities.

## Core Experience Principles

1. **Effortless:** Users should understand the application without requiring tutorials.
2. **Nonjudgmental:** Encourage improvement without guilt, shame, or excessive restrictions.
3. **Personalized:** Adapt learning recommendations to the user's interests and goals.
4. **Privacy-first:** Keep captured feed content and its analysis on the device.
5. **Engaging:** Use modern interfaces, smooth animations, illustrations, and interactive learning.
6. **User-controlled:** Every intervention should allow users to continue scrolling, start learning, or stop.
7. **Reliable:** If a permission, model, or network connection is unavailable, explain the limitation and preserve usable functionality.

---

# 2. Primary User Persona

## Persona A — The Student

**Name:** Aarav  
**Age:** 22  
**Occupation:** Engineering student  
**Goal:** Prepare for software engineering interviews  
**Current behavior:** Watches Instagram Reels for approximately 2–3 hours daily.

### Current Problem

Aarav often opens Instagram intending to spend just five minutes relaxing. However, he frequently continues watching reels for much longer.

He watches a mixture of comedy, technology, education, fitness, and entertainment content.

After finishing a scrolling session, he struggles to remember what he watched or whether the time was worthwhile.

He has tried traditional app timers but often ignores or disables them because they interrupt him without suggesting anything useful.

### Desired Experience

Aarav wants an application that:

- Helps him understand how many reels he watches.
- Shows which categories consume most of his attention.
- Alerts him when he has watched too many reels.
- Suggests short educational experiences related to his interests.
- Lets him maintain control over his phone usage.
- Does not expose private browsing or viewing data.

### Successful Outcome

After using BoomDoom, Aarav becomes more aware of his scrolling patterns, reduces time spent on unwanted content, and completes short learning sessions during previously unproductive breaks.

## Persona B — The Early-Career Professional

**Name:** Priya  
**Age:** 25  
**Occupation:** Product analyst, planning a job switch in three months  
**Goal:** Prepare for case interviews and improve communication skills  
**Current behavior:** Scrolls Reels late at night (11 PM–1 AM) and during commutes; watches many Hindi and Hinglish talking-head reels about careers and money.

### What Priya Needs
- A budget that doesn't reset in the middle of her late-night session.
- Check-ins that back off when she clearly wants to keep scrolling.
- Interview-prep lessons tied to a specific goal and date.
- Insights that understand Hindi talking-head reels, not just visuals.

Priya is used to test the goal, late-night and Hindi/Hinglish flows.

---

# 3. Application Navigation Structure

BoomDoom should use a simple four-tab bottom navigation system.

| Navigation | Purpose |
|---|---|
| Home | Daily overview, scroll budget, quick learning actions, monitoring status |
| Learn | Personalized short-video learning feed, topics, lesson history |
| Insights | Scrolling analytics, category breakdown, flagged content, learning progress |
| You | Personal preferences, budgets, labels, themes, permissions, privacy, account |

### Global Navigation Requirements

- Bottom navigation remains consistent across primary screens.
- The currently active tab is visually highlighted.
- Full-screen lesson playback temporarily hides the bottom navigation.
- Android Back should return to the appropriate previous screen.
- Changing tabs should preserve relevant screen state.
- Important settings should never be buried more than a few interactions deep.
- The Home screen should be the default destination on a fresh application launch.

---

# 4. Epic A — First-Time User Experience

## US-01: Launch BoomDoom for the First Time

**Priority:** P0  
**Screen:** Splash → Welcome

### User Story

As a first-time user, I want to understand what BoomDoom does within a few seconds of opening it, so that I can confidently decide to continue.

### Detailed User Flow

1. Aarav downloads and installs BoomDoom.
2. He taps the application icon.
3. A branded splash screen appears with the BoomDoom logo.
4. The application transitions to the welcome screen.
5. The welcome screen displays:

   **Headline:** "Turn doom into boom."

   **Subheadline:** "Understand your scrolling habits and turn everyday breaks into moments of learning."

6. Three illustrated concepts appear:
   - See what you watch.
   - Understand your habits.
   - Replace scrolling with learning.
7. Aarav sees a short privacy message: "Your feed stays private on your device."
8. He taps **Get Started**.
9. The application opens the budget configuration screen.

### UI Requirements

- Modern gradient or dark background.
- Minimal branded illustration.
- Large, readable headline.
- One prominent primary CTA.
- Subtle entrance animations.
- No mandatory login before explaining the application.

### Acceptance Criteria

- The welcome screen must load successfully on a fresh installation.
- The user must be able to start onboarding with one tap.
- The app must not begin monitoring or screen capture before consent.
- The screen must display the core value proposition and privacy promise.
- Returning users must not be forced through onboarding again.

---

## US-02: Configure a Daily Reel Budget

**Priority:** P0  
**Screen:** Onboarding — Your Scroll Budget (one screen with US-03)

### User Story

As a user, I want to establish a daily reel budget, so that I can track my scrolling habits against a goal I choose myself.

### Detailed User Flow

1. Aarav moves to the budget configuration screen.
2. The screen asks:

   **"How many reels would you like to watch each day?"**

3. BoomDoom displays a suggested default of **300 reels/day**.
4. A slider allows Aarav to increase or decrease this number.
5. Quick-selection presets appear below the slider.
6. As the value changes, the selected number updates immediately.
7. The screen explains:

   "Your budget helps you stay aware. BoomDoom won't automatically lock Instagram."

8. Aarav selects 150 reels.
9. On the same screen, he chooses how often to get check-ins (US-03).
10. He taps **Continue**.
11. The preferences are saved locally.

### Daily Reset

- The day resets at **4:00 AM local time** by default, not at midnight, so a late-night session counts toward the day it started in.
- The reset time can be changed in Preferences (US-27).

### Alternative Flows

**A. User keeps the default:** The application saves 300 reels.

**B. User returns to edit the value:** The slider displays their previous selection.

**C. User changes the budget later:** The Home progress indicator recalculates using the new limit.

### Acceptance Criteria

- The default budget must be 300.
- The user must be able to modify the budget.
- The chosen value must persist after restarting the application.
- The user must be able to change it from Settings.
- Exceeding the budget must not automatically block Instagram.
- The daily count must reset at the configured reset time (default 4:00 AM), not at midnight.

---

## US-03: Configure Scroll Reminders

**Priority:** P0  
**Screen:** Onboarding — Your Scroll Budget (same screen as US-02)

### User Story

As a user, I want to decide how frequently BoomDoom reminds me about my scrolling activity, so that interruptions occur at intervals I find useful.

### Detailed User Flow

1. Aarav sees:

   **"When should we check in with you?"**

2. The application displays reminder choices:
   - Every 10 reels.
   - Every 15 reels.
   - Every 25 reels.
   - Every 30 reels.

3. The suggested default is every 15 reels.
4. Aarav selects 15 reels.
5. A preview explains the future reminder:

   "After every 15 reels, you'll see a short summary and an opportunity to learn."

6. Aarav confirms the selection.
7. The application saves the reminder interval.

### Check-in Rules (to avoid nagging)

| Situation | Behavior |
|---|---|
| Normal | A check-in every *n* reels (default 15) |
| User taps **Continue Scrolling** twice in a row in a session | Interval doubles for the rest of that session (15 → 30 → 60) |
| User taps **Snooze for this session** | No more check-ins until the session ends (US-11 session rules) |
| Budget reached | One budget card (US-16), then a check-in every **25 reels** while over budget (user-configurable, never more often than every 10) |
| New session starts | Interval returns to the user's setting |

All of these are adjustable in Preferences → Scrolling Goals.

### Acceptance Criteria

- The default interval must be 15 reels.
- The user must be able to modify the interval.
- Reminders should appear at the configured reel milestones.
- The user must be able to continue scrolling after an intervention.
- A dismissed intervention should not immediately reappear for the same milestone.
- Two consecutive **Continue Scrolling** taps must double the interval for the rest of the session.
- **Snooze for this session** must stop check-ins until the session ends.
- Over-budget check-ins must follow the configured over-budget interval (default 25, minimum 10).

---

## US-04: Add My Own Categories (Optional)

**Priority:** P0  
**Screen:** Onboarding — Anything Specific? (skippable)

### User Story

As a user, I want to add the specific kinds of content I care about, so that my insights reflect my interests without me having to set up everything.

### Why Built-in Categories Are Always Tracked

The classifier needs the full built-in label set to sort reels correctly. If a user could turn off "Comedy", comedy reels would quietly become "Other" and insights would be wrong. So built-in categories are always on; users only **add** custom labels and **flag** labels (US-05).

**MVP built-in categories (8):** Comedy, Fitness, Education, Food, News, Finance, Rage-bait, Ads.
Later releases expand this list (e.g., Gaming, Relationships, Fashion, Travel, Music).

### Detailed User Flow

1. Aarav reaches the screen.
2. The screen displays:

   **"Anything specific you want us to spot?"**

3. The built-in categories are shown as read-only chips with the note: "We always recognize these."
4. He taps **Add Custom Category**.
5. He enters "Cricket".
6. The new category appears as a selected chip.
7. He taps **Continue**, or **Skip** to use only the built-in categories.
8. The application saves his custom labels.

### Additional Behavior

- The classifier supports custom labels without the user training a model.
- **Limits:** up to 2 custom labels in the MVP; up to 25 labels in total in later releases. The limit is explained when reached.

### Acceptance Criteria

- Built-in categories are always active and cannot be deselected.
- Users can create custom categories, up to the current limit.
- Duplicate labels (including duplicates of built-in categories) are prevented.
- The screen can be skipped.
- Custom labels can be edited or removed later.
- If classification is uncertain, the app uses "Other" rather than inventing a confident label.

---

## US-05: Mark Content as Not Worth My Time

**Priority:** P0  
**Screen:** Onboarding — Flagged Categories (skippable)

### User Story

As a user, I want to identify content categories I would prefer to consume less often, so that BoomDoom can help me recognize those patterns.

### Detailed User Flow

1. Aarav sees the question:

   **"Is there anything you'd like to see less of?"**

2. BoomDoom displays all built-in and custom categories.
3. Each category can be marked as **Not Worth My Time**.
4. Aarav marks Rage-bait.
5. The selected category receives a small warning indicator.
6. An explanation appears:

   "We'll highlight this category in your insights and show a small tag on the reel when we spot it."

7. Aarav taps **Save & Continue**, or **Skip**.

### Important UX Rules

- Flagged content should not be described as inherently bad.
- Users should be able to unflag categories.
- Flagging does not change the Instagram algorithm.
- Flagged reels do not count double toward the daily budget by default.

### Acceptance Criteria

- Flagged categories must be saved locally.
- The application must distinguish flagged and unflagged content.
- Users can modify flags at any time.
- Flagged content must be reflected in Insights.
- Classification uncertainty should be communicated appropriately.

---

## US-06: Select Learning Interests

**Priority:** P0  
**Screen:** Onboarding — Learning Topics

### User Story

As a user, I want to choose what I would like to learn, so that BoomDoom can offer meaningful educational alternatives to scrolling.

### Detailed User Flow

1. Aarav reaches the learning preferences screen.
2. The application asks:

   **"What would you love to learn in your free time?"**

3. The screen displays colorful topic cards:

   - Psychology
   - Interview Preparation
   - General Knowledge
   - Personal Finance Basics
   - Science
   - History
   - Communication Skills
   - Technology Explained

4. Aarav selects Psychology, Interview Preparation, and General Knowledge.
5. The selected cards receive a clear visual highlight.
6. He taps **Continue**.
7. All topics start at **Beginner**. A small link, **Fine-tune later**, explains that difficulty and goals can be set from Learn → My Topics.
8. Later, from Learn → My Topics, Aarav sets:
   - Psychology — Beginner.
   - Interview Preparation — Intermediate.
   - General Knowledge — Beginner.
9. He adds a goal there: "Prepare for SDE interviews".
10. He saves the topic settings.

### Alternative Flow

Aarav chooses **Add My Own Topic** and enters "System Design".

The app saves the topic and requests appropriate lessons from the learning backend.

### Acceptance Criteria

- Users can select preset learning topics.
- Users can create custom topics.
- Each topic can have a difficulty level (default Beginner; editable later, not during onboarding).
- Personal learning goals are optional and set outside onboarding.
- Users must be able to update preferences later.
- Lesson generation receives only the required learning profile, not the user's private reel content.

---

## US-06A: Try a Lesson Before Granting Permissions

**Priority:** P0  
**Screen:** Onboarding — First Lesson

### User Story

As a new user, I want to experience a BoomDoom lesson before granting sensitive permissions, so that I understand the value before deciding.

### Detailed User Flow

1. After choosing topics, Aarav sees: **"Try a 45-second lesson first?"**
2. He taps **Play** (or **Skip**).
3. A bundled starter lesson from one of his topics plays, with one interaction card.
4. After the recap card, he sees: **"Want BoomDoom to suggest these when you're scrolling? Let's set it up."**
5. He continues to permissions (US-07).

### Acceptance Criteria

- At least one bundled lesson per preset topic plays without network or permissions.
- The step can be skipped.
- The lesson's interactions and recap work exactly as in US-18 to US-20.
- Completing it counts as the user's first completed lesson.

---

## US-07: Enable Required Android Permissions

**Priority:** P0  
**Screen:** Onboarding — Permissions

### User Story

As a user, I want clear explanations of why BoomDoom needs Android permissions, so that I can make informed decisions about my privacy.

### Detailed User Flow

1. Aarav sees a screen titled:

   **"Let's get BoomDoom ready."**

2. A checklist displays required and optional capabilities.
3. The first card explains Accessibility access for detecting supported scrolling events.
4. Aarav taps **Enable**.
5. The application opens the relevant Android settings.
6. **If the Accessibility toggle is greyed out** (Android 13+ "restricted settings", which applies to apps installed from outside an app store, such as a demo APK), BoomDoom shows an illustrated guide: open BoomDoom's App info → ⋮ menu → **Allow restricted settings** → return and enable Accessibility.
7. Aarav grants permission and returns.
8. The permission card displays a success state.
9. The next card requests permission to display overlays.
10. Aarav grants overlay access.
11. The app explains that screen capture is used to understand visible content locally, and sets expectations before the system dialog:
    - Android shows a **persistent notification** while capture is running ("BoomDoom is understanding your feed on this phone"); it can't be dismissed while monitoring is active.
    - Android may ask for screen-capture consent **again** each time monitoring restarts (e.g., after a reboot or after the app is closed).
    - If the phone offers **sharing a single app**, choose **Instagram**: BoomDoom only needs Instagram, and this keeps other apps private.
12. When monitoring begins, Android's system screen-capture consent dialog is displayed.
13. Optional audio understanding (for talking-head reels, US-13A) and notification permissions are explained separately.
14. Aarav completes setup and enters Home.

### Permission States

- Not requested.
- Enabled.
- Denied.
- Revoked.
- Temporarily unavailable.

### Alternative Flow — Permission Denied

If Aarav declines screen capture, BoomDoom should explain that content classification will be unavailable while offering supported reel-count tracking where possible.

The Learn tab must remain usable without monitoring permissions.

### Acceptance Criteria

- Every permission must have a plain-language explanation.
- Android system dialogs must be used where required.
- Permission denial must not crash the application.
- The application must detect permission revocation.
- Users must be able to reopen permission settings.
- No content capture begins without applicable system consent.
- The restricted-settings guide appears whenever the Accessibility toggle can't be enabled.
- The persistent capture notification and possible re-consent are explained before the first system dialog.
- When screen-capture consent is needed again, BoomDoom shows a one-tap **Resume monitoring** prompt instead of re-running onboarding.

**Technical dependency:** Instagram reel detection, caption availability, and audio capture must be validated on supported Android versions and devices before those capabilities are treated as guaranteed.

---

## US-08: Use BoomDoom Without Mandatory Login

**Priority:** P0 for anonymous use; P1 for optional sign-in  
**Screen:** Account Setup / You

### User Story

As a privacy-conscious user, I want to start using BoomDoom without creating a traditional account, so that onboarding remains fast and my information stays limited.

### Detailed User Flow

1. Aarav finishes onboarding.
2. BoomDoom automatically creates an anonymous local profile and, if required for lesson delivery, an anonymous backend identity.
3. He enters the Home dashboard.
4. He can begin using the application without entering an email or password.
5. Optional sign-in (P1): from You → Account, Aarav can link his anonymous profile to **Google** or a **phone number (OTP)**, so lessons, topics and learning progress sync to a new phone. Feed data and local insights never leave the device and are not synced.

### Acceptance Criteria

- Mandatory email registration is not required for MVP onboarding.
- Anonymous users can access core features.
- Local settings survive application restarts.
- Authentication is not a prerequisite for local scrolling insights.
- Backend deletion must be supported for the anonymous lesson profile.
- (P1) Linking an account must keep the user's existing lessons and progress, and signing out must not delete local data.

---

# 5. Epic B — Home Dashboard and Daily Monitoring

## US-09: View My Daily Dashboard

**Priority:** P0  
**Screen:** Home

### User Story

As a user, I want to see my daily scrolling and learning progress immediately after opening BoomDoom, so that I can understand my current habits without navigating through multiple screens.

### Detailed User Flow

1. Aarav opens BoomDoom.
2. The app navigates to Home.
3. The top section displays a friendly greeting.
4. A large scrolling-budget card displays:

   **124 / 150 reels watched**

5. A visual progress bar represents the amount of budget consumed.
6. Supporting statistics show:
   - Reels watched today.
   - Flagged reels.
   - Lessons completed.
   - Learning minutes.

7. The dashboard displays a recommended lesson.
8. Aarav can tap **Start Learning**.
9. A Today Insight card summarizes his dominant content categories.
10. Bottom navigation provides access to Learn, Insights, and You.

### UI Requirements

- Dark mode as the primary design.
- Rounded statistic cards.
- Large, readable progress numbers.
- Clear contrast between budget and learning metrics.
- Minimal clutter.
- Useful empty states for first-time users.

### Acceptance Criteria

- Home must show the current daily budget.
- The displayed data must come from actual local records.
- The progress indicator must be mathematically consistent with the budget.
- Statistics must refresh when relevant local records change.
- A primary learning action must be visible without extensive scrolling.
- Users without monitoring data should see an onboarding or empty state, not fabricated statistics.

---

## US-10: Understand Whether Monitoring Is Active

**Priority:** P0  
**Screen:** Home / Persistent Status

### User Story

As a user, I want to know whether BoomDoom is actively monitoring my scrolling, so that I always understand when monitoring is operating.

### Detailed User Flow

1. Aarav opens the Home dashboard.
2. A status indicator displays:

   **Monitoring Active**

3. A nearby control allows him to pause monitoring.
4. Aarav taps **Pause**.
5. The status changes to:

   **Monitoring Paused**

6. BoomDoom stops active monitoring and removes its overlays.
7. Aarav can resume when he chooses.

### Alternative Flow

If a required permission has been revoked, the status becomes **Setup Required** or **Limited Monitoring**.

A repair action directs the user to the appropriate permission screen.

### Acceptance Criteria

- Monitoring status must be visible.
- Pausing must stop affected capture and overlay behavior.
- Resuming must respect Android permission requirements.
- The app must not display an inaccurate Active status.
- Paused state must persist appropriately through lifecycle changes.

---

## US-11: Automatically Detect an Instagram Reels Session

**Priority:** P0  
**Screen:** Instagram Reels / Background Monitoring

### User Story

As a user, I want BoomDoom to recognize when I start watching Instagram Reels, so that tracking happens automatically without requiring me to open BoomDoom first.

### Detailed User Flow

1. Aarav minimizes BoomDoom.
2. He opens Instagram.
3. He navigates to Reels.
4. BoomDoom detects the supported Reels screen through its permitted monitoring mechanisms.
5. A new scrolling session begins.
6. The application records the session start time.
7. Aarav watches the first reel.
8. When he swipes to another reel, BoomDoom detects a reel transition.
9. The reel counter advances.
10. The process continues during the session.
11. When Aarav exits Reels, the session follows the session rules below.

### Session Rules

- A session **starts** when the Reels screen is detected.
- Leaving Reels for **less than 2 minutes** (e.g., replying to a message) **pauses** the session; returning continues it.
- Leaving Reels for **2 minutes or more**, locking the phone, or tapping **Done for Now** **ends** the session.
- Snoozes and check-in back-off (US-03) reset when a session ends.

### Data Recorded

- Session start and end timestamps.
- Supported application identifier.
- Number of detected reels.
- Approximate watch duration per reel.
- Locally generated category labels, where analysis is available.

### Acceptance Criteria

- The app must distinguish supported Reels activity from unrelated screens as reliably as feasible.
- Reel count accuracy target: at least 90% against a 100-reel manual test.
- The app must avoid double-counting duplicate transition events.
- Session records must be stored locally.
- Sessions must start, pause and end according to the session rules.
- Unsupported or ambiguous detection must not silently produce misleading counts.

---

## US-12: View a Live Reel Budget Progress Bar

**Priority:** P0  
**Screen:** Instagram Overlay

### User Story

As a user, I want a subtle progress bar while watching Reels, so that I can remain aware of my daily budget without significantly interrupting Instagram.

### Detailed User Flow

1. Aarav starts watching Instagram Reels.
2. A thin progress bar appears at the top of the screen.
3. The bar initially reflects today's existing reel count.
4. Every newly detected reel increases the filled portion.
5. The bar progressively fills as Aarav approaches his budget.
6. At approximately 80% usage, the bar becomes more visually prominent.
7. At 100%, it reaches full width and can briefly indicate that the budget has been used.
8. **Over budget:** the bar stays full and changes to a solid "over" style, with a small counter at the edge showing the extra reels (e.g., **+30**). Home shows **180 / 150 reels (+30 over)**.
9. The bar remains non-interactive and does not interfere with scrolling gestures.

### Example

If Aarav has watched 75 reels against a budget of 150, the bar displays 50% progress.

### Acceptance Criteria

- The indicator must reflect the active daily budget.
- It should update within one second of a detected reel transition.
- It must not block taps or swipes in Instagram.
- It must disappear when monitoring is paused or the supported context is exited.
- It must not cover important system controls unnecessarily.
- A fallback state must exist if overlay permission is missing.
- Over-budget state must show the number of extra reels, both on the bar and on Home.

---

# 6. Epic C — Content Understanding and Intervention

## US-13: Automatically Categorize Watched Reels

**Priority:** P0  
**Screen:** Background Analysis / Insights

### User Story

As a user, I want BoomDoom to identify the general type of content I watch, so that I can understand what consumes my attention.

### Detailed User Flow

1. Aarav opens a reel.
2. With the appropriate Android consent, BoomDoom samples permitted content frames.
3. On-device models analyze available visual information.
4. Captions and on-screen text contribute when accessible.
5. Supported audio understanding may contribute when the platform permits capture.
6. The application combines available signals.
7. A final label is assigned to the reel.
8. The label and relevant structured metadata are stored locally.
9. Transient frame and audio data are discarded according to the privacy design.
10. The category becomes available in Insights.

### Example

Aarav watches a gym workout reel.

BoomDoom assigns the category **Fitness**.

If he watches a software interview explainer, BoomDoom may assign **Education** or a supported custom label.

### Alternative Flow — Insufficient Confidence

If there is insufficient evidence, the application assigns **Other**.

### Acceptance Criteria

- Classification must not introduce visible lag in Instagram.
- MVP classification accuracy target: at least 75% on a manually labeled set of 100 reels.
- Content classification must occur locally.
- Raw frames, audio, and transcripts must not be uploaded.
- Low-confidence classification must use an appropriate fallback.
- Classification results must be available for local insights.

---

## US-13A: Understand Hindi and Hinglish Talking-Head Reels

**Priority:** P0 (demo moment)  
**Screen:** Background Analysis / Instagram Overlay / Insights

### User Story

As a user who watches many Hindi and Hinglish reels of people talking to the camera, I want BoomDoom to understand what they're about, so that my insights aren't just "person talking".

### Detailed User Flow

1. Priya watches a Hindi reel of a creator giving stock tips.
2. The visual classifier sees a person talking to the camera and isn't confident.
3. If audio understanding is enabled (US-07), BoomDoom captures the first 10–15 seconds of the reel's audio.
4. On-device speech-to-text produces a Hinglish transcript.
5. A multilingual text classifier labels it **Finance** (and **Finfluencer tips**, if that's one of her custom labels).
6. If the label is flagged, the reel shows the small tag (US-14).
7. Audio and transcript are deleted immediately; only the label is stored.
8. In Insights, the reel appears under Finance.

### Alternative Flows

- **Audio understanding off, or Instagram blocks audio capture:** BoomDoom reads burned-in captions on screen (Latin and Devanagari) and caption text where available; otherwise the reel is labelled **Other**.
- **Language not supported yet** (e.g., Tamil): the reel is labelled from visuals and captions only.

### Acceptance Criteria

- Audio is captured only for talking-head or low-confidence reels, and only the first 10–15 seconds.
- Audio and transcripts are never stored or uploaded.
- Hindi and Hinglish speech are supported in the MVP.
- If audio capture is unavailable, the app falls back to on-screen text without errors.
- Audio understanding can be turned off in Privacy & Data.

---

## US-14: Recognize Flagged Content

**Priority:** P0  
**Screen:** Instagram Overlay / Insights

### User Story

As a user, I want to recognize when I am watching content from categories I have flagged, so that I can make more intentional decisions.

### Detailed User Flow

1. Aarav watches several reels.
2. A reel is classified as Rage-bait.
3. Rage-bait is one of his flagged categories.
4. BoomDoom displays a small temporary indicator:

   **Flagged: Rage-bait**

5. The budget bar may briefly emphasize the event.
6. Aarav can continue scrolling.
7. The flagged reel is added to his local flagged-content statistics.
8. Later, the event contributes to his Insights breakdown.

### Important Rules

- Flagged content does not automatically block the feed.
- Flagged content does not count double by default.
- The user can unflag categories at any time.
- The interface should not suggest classification is certain when it is not.

### Acceptance Criteria

- Flagged categories must be matched against locally classified reels.
- An optional indicator must be brief and non-obstructive.
- Flagged counts must be reflected in analytics.
- Unknown classifications must not trigger confident warning labels.
- The flagged tag appears only when the label's confidence is at or above a set threshold (start at 0.7 and tune it on the 100-reel test set); below it, the reel is counted as **Other** and no tag is shown.
- Users must retain control over flagged preferences.

---

## US-15: Receive a Scrolling Check-In

**Priority:** P0  
**Screen:** Intervention Overlay

### User Story

As a user, I want a useful reminder after a configurable number of reels, so that I can understand my recent viewing habits and choose whether to continue.

### Detailed User Flow

1. Aarav watches Instagram Reels.
2. BoomDoom counts each detected reel.
3. The configured reminder interval is 15.
4. Aarav reaches his 15th reel.
5. An intervention card appears.
6. The card displays:

   **"15 reels already 👀"**

   **"Here's what you've been watching."**

7. A small content breakdown summarizes available classifications.
8. The application suggests:

   **"Swap 60 seconds for a Psychology lesson?"**

9. Three actions appear:
   - **Switch to Lesson**
   - **Continue Scrolling**
   - **Done for Now**

   A smaller text link, **Snooze for this session**, appears below them.

### User Action A — Switch to Lesson

1. Aarav taps **Switch to Lesson**.
2. The intervention closes.
3. BoomDoom opens its lesson player.
4. A cached lesson starts.
5. The user enters the learning experience.

### User Action B — Continue Scrolling

1. Aarav taps **Continue Scrolling**.
2. The intervention disappears.
3. He returns to the current Instagram context.
4. Reel monitoring continues.
5. The next reminder appears at the next milestone. If this is his second **Continue Scrolling** in a row this session, the interval doubles (US-03).

### User Action D — Snooze for This Session

1. Aarav taps **Snooze for this session**.
2. The card closes and no more check-ins appear until the session ends.
3. The budget bar keeps updating.

### User Action C — Done for Now

1. Aarav taps **Done for Now**.
2. The current scrolling session is concluded.
3. The user is taken to an appropriate destination, such as the Android Home screen.
4. The session summary is saved locally.

### Acceptance Criteria

- The intervention appears at the configured reel milestone when monitoring and overlays are available.
- Target appearance latency: within one second of detecting the milestone reel.
- The card must provide all three actions and the **Snooze for this session** link.
- Check-in timing must follow the back-off and snooze rules in US-03.
- The user must never be forced into a lesson.
- The intervention must not falsely present unavailable category analysis.
- Repeated dismissal must not cause an immediate duplicate intervention.

---

## US-16: Reach the Daily Reel Budget

**Priority:** P0  
**Screen:** Budget Reached Overlay

### User Story

As a user, I want a clear notification when I reach my daily reel budget, so that I can make an intentional choice about continuing.

### Detailed User Flow

1. Aarav's daily budget is 150 reels.
2. He watches his 150th reel.
3. The budget bar becomes fully filled.
4. A supportive card appears:

   **"You've reached today's scroll budget."**

5. The card explains how much of the budget was used.
6. A suggested learning option appears.
7. Aarav can:
   - Start a lesson.
   - Continue scrolling.
   - Finish the session.

8. If Aarav continues, the app resumes monitoring.
9. While over budget, check-ins appear every 25 reels by default (user-configurable, never more often than every 10), and the bar shows the extra reels (US-12).

### Acceptance Criteria

- Budget-reached state must be calculated from actual usage.
- The app must not automatically lock Instagram.
- The user must be able to dismiss or act on the intervention.
- Over-budget usage must remain visible in analytics.
- The app must handle budget changes while over the limit.

---

# 7. Epic D — Personalized Learning Experience

## US-17: Open the Learn Feed

**Priority:** P0  
**Screen:** Learn

### User Story

As a user, I want to access short educational videos based on my interests, so that learning feels as approachable as scrolling through social media.

### Detailed User Flow

1. Aarav taps the Learn tab or selects **Switch to Lesson** from a scroll intervention.
2. BoomDoom opens the learning experience.
3. His selected topics are available.
4. The application displays an unwatched, relevant lesson.
5. The lesson includes:
   - Topic name.
   - Concept title.
   - Difficulty level.
   - Expected duration.
   - Cinematic illustrated stickman visuals.

6. Aarav starts or resumes playback.
7. He can swipe to navigate to another available lesson, where appropriate.
8. The application tracks lesson completion and learning interactions.

### UI Expectations

- Portrait-oriented video experience.
- Immersive 9:16 presentation.
- Minimal overlays.
- Captions and audio controls.
- Clear topic identity.
- Smooth transitions between video segments and interactive cards.

### Acceptance Criteria

- Lessons should match selected interests where possible.
- Cached lessons should start within one second of selection.
- The user must be able to leave the player easily.
- The feed must not require external Instagram data.
- Unavailable lessons must show a clear alternative or loading state.

---

## US-18: Watch a Cinematic Stickman Lesson

**Priority:** P0  
**Screen:** Full-Screen Lesson Player

### User Story

As a user, I want educational concepts explained through entertaining illustrated stories, so that I can learn without feeling like I am attending a traditional lecture.

### Example Lesson

**Topic:** Psychology  
**Concept:** Zeigarnik Effect  
**Duration:** Approximately 45 seconds  
**Difficulty:** Beginner

### Detailed User Flow

1. Aarav opens the lesson.
2. The player displays an illustrated stickman character.
3. The narrator introduces a question:

   **"Ever wondered why unfinished work keeps popping into your mind?"**

4. An animated scenario appears.
5. The character demonstrates a familiar everyday situation.
6. Captions synchronize with the narration.
7. Progress segments indicate where Aarav is in the lesson.
8. After the setup, playback pauses at an interactive question.
9. Aarav answers.
10. The lesson continues with an explanation.
11. The final segment presents a practical takeaway.
12. A recap card appears.

### Player Controls

- Pause and resume.
- Captions on/off.
- Sound on/off.
- Exit lesson.
- Report a mistake.
- Navigate to another lesson where appropriate.

### Acceptance Criteria

- Video and captions must remain synchronized.
- Playback must transition to interaction cards at the correct points.
- The user must be able to exit at any moment.
- A cached lesson should play without requiring network connectivity.
- A completed lesson must be recorded once rather than repeatedly for duplicate events.

### Alternative Flow — Report a Mistake

1. Aarav thinks a fact in the lesson is wrong and taps **Report a mistake**.
2. Playback pauses and a bottom sheet opens with options: **Wrong fact**, **Confusing**, **Audio/video problem**, **Inappropriate**, plus an optional text note.
3. He submits; the report is sent to the backend with the lesson ID and the current time in the video (no feed data).
4. He sees: "Thanks, we'll review this lesson." Playback resumes, or he can skip the lesson.
5. A lesson with several "wrong fact" reports is hidden from all users until it's reviewed.

---

## US-19: Answer an Interactive Learning Question

**Priority:** P0  
**Screen:** Lesson Interaction Card

### User Story

As a user, I want lessons to pause and ask me questions, so that I can actively engage with what I am learning.

### Detailed User Flow

1. Aarav watches the setup segment.
2. Playback reaches an interaction point.
3. The player pauses.
4. A multiple-choice card appears.

   **Question:** "Why do unfinished tasks remain in our minds?"

5. Three possible answers appear.
6. Aarav selects an answer.
7. The selected option receives visual feedback.
8. The application explains whether the answer is correct.
9. Aarav taps **Continue**.
10. The lesson resumes.

### Other Supported Interaction Types

- Predict what happens next.
- Multiple choice.
- Swipe true/false (always with tap buttons as an alternative).
- Reflect: a personal "Has this happened to you?" question (P1); other users' answers are never shown.
- Your answer: think or type an answer, then see a model answer (P1; useful for interview prep).
- Go deeper: choose the next sub-topic (P1).

The MVP should prioritize predict, multiple-choice, and true/false interactions.

### Acceptance Criteria

- The video must pause at the intended interaction point.
- Answers must receive immediate feedback.
- Incorrect answers must receive a helpful explanation.
- The answer must be recorded with the lesson event.
- Network failures must not prevent locally cached interaction cards from functioning.
- The user must not be trapped on an interaction screen.

---

## US-20: Complete a Lesson and Save the Takeaway

**Priority:** P0  
**Screen:** Lesson Completion / Recap

### User Story

As a user, I want a concise takeaway when I finish a lesson, so that I can remember what I learned.

### Detailed User Flow

1. Aarav finishes the final video segment.
2. An illustrated recap card appears.
3. The card displays the concept title.
4. A one- or two-sentence takeaway summarizes the lesson.
5. The application saves the recap to a **Learned** collection.
6. A subtle completion animation appears.
7. Aarav sees:
   - **Another Lesson**
   - **Back to Instagram**
   - **Done for Now**

8. He chooses what to do next.

### Acceptance Criteria

- Completed lessons must be tracked accurately.
- The takeaway must be available in the Learned collection.
- The user must be able to choose the next action.
- The lesson should not automatically start an endless sequence without clear stopping opportunities.
- Recaps must remain accessible when the original lesson is cached locally.

---

## US-20A: Browse My Learned Collection

**Priority:** P0  
**Screen:** Learn → Learned

### User Story

As a user, I want to revisit the takeaways I've saved, so that I can refresh what I learned without rewatching lessons.

### Detailed User Flow

1. Aarav opens Learn and taps **Learned**.
2. Recap cards appear newest first, grouped by topic, with the concept title and takeaway.
3. He filters by topic.
4. He taps a card to see the full takeaway and a **Rewatch lesson** button (if the lesson is still cached or available).
5. He can remove a card from the collection.

### Acceptance Criteria

- Every completed lesson's recap appears in Learned.
- Learned works offline.
- Filtering by topic works.
- **Rewatch lesson** is shown only when the lesson can be played.
- An empty state explains: "Finish a lesson to save your first takeaway."

---

## US-21: Return to Instagram After Learning

**Priority:** P0  
**Screen:** Lesson Completion / Application Switching

### User Story

As a user, I want to return to my previous activity after completing a lesson, so that learning feels like a natural break rather than a disruption.

### Detailed User Flow

1. Aarav opens a lesson from an Instagram scroll intervention.
2. He completes the lesson.
3. He taps **Back to Instagram**.
4. BoomDoom attempts to return him to the previous Instagram context using permitted Android navigation behavior.
5. Where restoring the exact screen is unavailable, BoomDoom provides a clear path to reopen Instagram.
6. Monitoring continues only when the supported conditions and required permissions are active.

### Acceptance Criteria

- Returning to Instagram must be straightforward.
- BoomDoom must not falsely guarantee restoration of an exact reel.
- The completed lesson must remain recorded.
- The application must not create an extra or duplicated reel count merely because of app switching.
- Android Back must behave predictably.

---

## US-22: Create a Custom Learning Topic

**Priority:** P0 for the MVP demo  
**Screen:** Learn → My Topics → Add Topic

### User Story

As a user, I want to request lessons about any subject I am interested in, so that BoomDoom becomes useful beyond its predefined topics.

### Detailed User Flow

1. Aarav opens Learn.
2. He taps **My Topics**.
3. He selects **Add Custom Topic**.
4. A text field appears.
5. He enters "System Design".
6. He selects Intermediate difficulty.
7. He optionally adds a goal: "Prepare for backend engineering interviews."
8. He taps **Create Topic**.
9. BoomDoom sends the required learning preferences to its backend.
10. A new topic card appears.
11. The card displays an appropriate preparation status.
12. Once lessons are ready, Aarav can start watching them.

### UI Requirements

Show meaningful states:

- Preparing lessons.
- Lessons ready.
- Could not generate lessons.
- Retry generation.

### Acceptance Criteria

- Users must be able to submit a valid custom topic.
- The app must not promise instant generation when lessons do not exist.
- Lesson generation failures must be recoverable.
- Users must be able to continue using other topics.
- The backend must not receive private feed frames or reel classifications as part of the request.

---

## US-22A: Get Topic Suggestions From My Feed (Opt-in)

**Priority:** P1  
**Screen:** Learn → Suggested for You

### User Story

As a user, I want BoomDoom to suggest learning topics based on what I scroll, so that lessons connect to my existing interests.

### Detailed User Flow

1. After a few days of tracking, Learn shows a card: **"Suggested for you, based on your scrolling (stays on your phone)."**
2. Suggestions are worked out on the phone from his local category counts, e.g.:
   - Lots of Finance/finfluencer reels → **Personal Finance Basics**.
   - Lots of Cricket reels → **Cricket Stats & Strategy**.
   - Lots of Fitness reels → **Exercise Science**.
3. Aarav taps **Add** on one suggestion, or **Not interested**.
4. Only the accepted topic name is sent to the backend; the categories that led to it are not.

### Acceptance Criteria

- Suggestions are off until the user opts in (one-time prompt or Preferences toggle).
- Suggestions are computed on the device.
- Only accepted topic names reach the backend.
- Dismissed suggestions don't reappear for at least 30 days.

---

## US-23: Receive Newly Prepared Lessons

**Priority:** P0 for lesson availability; notifications are optional  
**Screen:** Learn / Notification

### User Story

As a user, I want new lessons to become available automatically after requesting a topic, so that I do not have to repeatedly check whether generation has finished.

### Detailed User Flow

1. Aarav creates a new topic.
2. The backend prepares the lesson syllabus and scripts.
3. Render workers produce the illustrated lesson video (one video per lesson, with pause points for interactions).
4. Completed lessons become available through the lesson catalog.
5. The application fetches ready lessons.
6. Eligible lessons are cached on the device.
7. The topic card changes to **Ready to Learn**.
8. If notifications are enabled, BoomDoom can notify Aarav that his lessons are ready.
9. Aarav taps the topic and begins learning.

### Acceptance Criteria

- The app must accurately represent lesson preparation states.
- Ready lessons must be discoverable without restarting the app.
- Cached lessons should be available offline.
- Failed jobs must not appear as ready.
- Users must have a way to retry or choose another lesson.

---

# 8. Epic E — Insights and Behavioral Awareness

## US-24: View Today's Scrolling Insights

**Priority:** P0  
**Screen:** Insights → Today

### User Story

As a user, I want a simple visual breakdown of what I watched today, so that I can recognize my scrolling habits.

### Detailed User Flow

1. Aarav opens BoomDoom.
2. He taps **Insights**.
3. The Today view appears.
4. A summary section displays:
   - Total reels watched.
   - Reels remaining or budget exceeded.
   - Flagged reels.
   - Longest session.
   - Peak scrolling hour, where available.

5. A donut chart displays the breakdown of recognized categories.
6. Aarav can tap a category to see its count and share.
7. The application displays a short AI-generated summary based on local structured records.
8. Aarav identifies that comedy makes up a large percentage of his feed.
9. He considers adjusting his learning or flagging preferences.

### Example Insight

**"You watched 124 reels today. Comedy accounted for 48% of recognized content. Your longest scrolling session occurred in the evening."**

### Acceptance Criteria

- All insight metrics must derive from stored local usage records.
- Charts must correctly represent category counts.
- Unknown labels must be handled explicitly.
- Metrics with insufficient data should be hidden or marked unavailable.
- Insights must remain accessible without internet connectivity when relevant local records exist.
- The UI must not imply a measurement is exact when it is estimated.

---

## US-24A: Fix a Wrong Label

**Priority:** P0 (simple version)  
**Screen:** Insights → Category → Reel list

### User Story

As a user, I want to correct a reel that was put in the wrong category, so that my insights stay accurate and BoomDoom gets better for me.

### Detailed User Flow

1. In Insights, Aarav taps **Comedy** and sees today's reels in that category (time, duration, a short description; no screenshots are stored).
2. One entry looks wrong. He taps **Wrong label?**
3. He picks the correct category from the list (or **Other**).
4. The reel moves to the new category and charts update.
5. The correction is saved on the phone and used to improve his personal classifier.

### Acceptance Criteria

- Any reel in Insights can be relabelled in two taps.
- Charts and counts update immediately.
- Corrections stay on the device.
- Corrections can be undone.

---

## US-25: Read My Personalized AI Summary

**Priority:** P0  
**Screen:** Insights → AI Summary

### User Story

As a user, I want BoomDoom to summarize my scrolling behavior in plain language, so that I can understand my habits without interpreting complex graphs.

### Detailed User Flow

1. Aarav opens Insights.
2. A card titled **Your Daily Summary** appears.
3. A local summary model processes structured usage statistics.
4. BoomDoom produces a short observation.

   Example:

   "You spent most of your scrolling sessions watching comedy and technology content. You also completed three learning sessions today."

5. The summary highlights meaningful changes where historical data exists.
6. Aarav can open the underlying charts to understand the figures.

### Acceptance Criteria

- The summary must use actual measured statistics.
- It must not invent causes, intentions, emotions, or habits.
- It must use supportive, neutral language.
- It must not require uploading feed data to an external model.
- If local summary generation fails, a deterministic statistical summary should be shown.

---

## US-26: Review Weekly Progress

**Priority:** P1 — Post-MVP  
**Screen:** Insights → Weekly

### User Story

As a user, I want to compare my scrolling habits across several days, so that I can understand whether I am improving.

### Detailed User Flow

1. Aarav opens Insights.
2. He selects the 7 Days tab.
3. The application displays a trend chart of daily reel counts.
4. A category breakdown shows which content types increased or decreased.
5. The number of lessons completed is displayed.
6. Changes in flagged-content consumption are summarized.
7. Learning minutes are compared with earlier periods.
8. Aarav reviews the findings.

### Acceptance Criteria

- Weekly metrics must use consistent aggregation rules.
- Incomplete days must be handled appropriately.
- Comparisons must only appear when previous-period data exists.
- The interface must distinguish measured observations from recommendations.
- This feature should not block delivery of the hackathon MVP.

---

# 9. Epic F — Personalization and Settings

## US-27: Update My Preferences

**Priority:** P0  
**Screen:** You → Preferences

### User Story

As a user, I want to change my scrolling budget, reminders, content labels, and learning topics whenever I want, so that BoomDoom adapts as my goals change.

### Detailed User Flow

1. Aarav opens the You tab.
2. He selects **Preferences**.
3. Settings are grouped under:
   - Scrolling Goals.
   - Content Preferences.
   - Learning Preferences.
   - Appearance.
   - Privacy and Permissions.

4. Aarav changes his daily budget from 150 to 100, and reviews **Scrolling Goals**: check-in interval, back-off on/off, over-budget interval, and daily reset time (default 4:00 AM).
5. He saves the setting.
6. Home immediately reflects the new budget.
7. He removes a content flag.
8. Future classification-related warnings use the updated preference.
9. He adds another learning topic.
10. The learning catalog reflects the new topic.

### Acceptance Criteria

- Settings must reflect the current saved values.
- Changes must persist across app restarts.
- Updated limits must not erase historical reel counts.
- Preference updates must not require reinstalling the app.
- Settings should be reachable through clear navigation.

---

## US-28: Customize the Application Appearance

**Priority:** P1 for theme switching; P0 for basic accessibility  
**Screen:** You → Appearance

### User Story

As a user, I want BoomDoom to match my visual preferences, so that the application feels comfortable and modern.

### Detailed User Flow

1. Aarav opens You.
2. He selects Appearance.
3. He sees three options:
   - Dark.
   - Light.
   - Follow System.

4. He chooses Dark.
5. The app updates its appearance.
6. Rounded cards, charts, icons, and typography retain consistent visual hierarchy.
7. Aarav enables reduced motion if desired.

### Acceptance Criteria

- Theme changes must not affect stored data.
- Text and important controls must remain readable.
- Colors must preserve semantic meaning.
- Reduced-motion preferences must be respected.
- Touch targets and screen-reader labels must remain usable in all themes.

---

## US-29: Manage Privacy and Delete My Data

**Priority:** P0  
**Screen:** You → Privacy & Data

### User Story

As a user, I want control over the personal information BoomDoom stores, so that I can trust the application.

### Detailed User Flow

1. Aarav opens You.
2. He selects **Privacy & Data**.
3. BoomDoom explains which information stays on his phone.
4. It separately explains the learning-profile data sent to the lesson backend.
5. Available controls include:
   - Pause Monitoring.
   - Audio Understanding on/off (US-13A).
   - Feed-based Topic Suggestions on/off (US-22A).
   - Review Permissions.
   - Delete Local Tracking Data.
   - Delete Account and Associated Backend Data.
   - Export Local Records, where supported.

6. Aarav chooses to delete his tracking history.
7. A confirmation dialog explains what will be removed.
8. Aarav confirms.
9. Local tracking records are deleted.
10. Home and Insights return to their appropriate empty states.

### Acceptance Criteria

- The application must clearly differentiate local data and backend data.
- Data deletion must require confirmation.
- Deleted local history must no longer appear in analytics.
- Backend account deletion must request removal of the associated lesson profile and personal lesson records.
- Failed backend deletion must not be falsely reported as successful.
- The user must be able to understand and manage monitoring permissions.

---

# 10. Epic G — Reliability, Notifications, and Accessibility

## US-30: Continue Using BoomDoom Without Internet

**Priority:** P0  
**Screen:** Across the Application

### User Story

As a user, I want the core experience to remain useful without an internet connection, so that BoomDoom works during travel or network interruptions.

### Detailed User Flow

1. Aarav opens BoomDoom while offline.
2. Home displays previously stored tracking information.
3. Local reel counting remains available where the required permissions and services remain functional.
4. Local classification operates where the necessary models are installed.
5. Aarav opens Learn.
6. Cached lessons remain playable.
7. Interaction cards work locally.
8. New custom-topic generation is unavailable.
9. The application communicates that new lessons require connectivity.

### Acceptance Criteria

- Local records must remain accessible offline.
- Cached videos and native interactions must continue working.
- No-network states must be clear and recoverable.
- The application must not delete valid progress because of network failure.
- Unavailable backend actions must not result in infinite loading indicators.

---

## US-31: Receive Helpful Notifications

**Priority:** P1 for daily summaries; optional for generation completion  
**Screen:** Android Notifications

### User Story

As a user, I want useful notifications about my learning and progress, so that I can stay informed without feeling overwhelmed.

### Example Notifications

**Lesson Ready:**  
"Your System Design lessons are ready. Start with a 45-second introduction."

**Daily Summary:**  
"Today: 92 reels watched and 3 lessons completed. See your breakdown."

### Acceptance Criteria

- Notification permission must be optional.
- Users must be able to disable notification categories.
- Notifications must not expose sensitive feed details unnecessarily.
- Repeated notifications must be rate-limited.
- Notification taps must open the relevant application destination.

---

## US-32: Recover From Missing Permissions or Monitoring Errors

**Priority:** P0  
**Screen:** Home / Status / Help

### User Story

As a user, I want clear recovery instructions when monitoring stops working, so that I can restore functionality without technical knowledge.

### Detailed User Flow

1. Aarav previously enabled monitoring.
2. Android later revokes a required permission or stops a capture session.
3. BoomDoom detects that a capability is unavailable.
4. The Home status changes from Active to Limited or Setup Required.
5. A card explains the affected feature.
6. Aarav taps **Fix Setup**.
7. The app guides him to the required Android screen or consent prompt.
8. The app checks whether the capability has been restored.
9. The monitoring status updates accordingly.

### Acceptance Criteria

- The application must not silently display Active while monitoring is unavailable.
- Recovery steps must match the missing capability.
- The user must not be forced to repeat unrelated onboarding steps.
- Learning must remain accessible independently.
- Failed recovery must display an actionable explanation rather than a blank state.

---

## US-33: Use BoomDoom With Accessibility Preferences

**Priority:** P0  
**Screen:** Entire Application

### User Story

As a user, I want BoomDoom to support accessible navigation, readable content, and alternative ways to interact with lessons, so that I can use the app comfortably.

### Expected Behavior

- Support Android text scaling.
- Ensure readable contrast in dark and light themes.
- Provide TalkBack labels for buttons, charts, and settings.
- Use adequately sized touch targets.
- Provide captions for narrated content.
- Support reduced motion.
- Ensure quizzes can be answered through taps rather than requiring swipe gestures.
- Avoid relying on color alone for warning and success states.

### Acceptance Criteria

- Primary controls must be reachable through accessible navigation.
- Information must remain understandable without color.
- Captions must be available for supported lessons.
- Screen-reader users must be able to identify the current tab and action.
- Essential tasks must not depend exclusively on complex gestures.

---

## US-34: Demo Mode for the Hackathon

**Priority:** P0 (hackathon only; hidden in release builds)  
**Screen:** You → About (tap the version number 7 times)

### User Story

As the team presenting BoomDoom, we want to show the full journey in under two minutes, so that judges see tracking, a check-in, a lesson and insights without waiting.

### Behavior

- Check-in interval set to **3 reels**.
- Insights pre-filled with a realistic sample day, clearly marked **Demo data** in the UI.
- One flagged category and one Hindi finance reel example ready for US-13A.
- Lessons for the demo topics bundled in the app (no network needed).
- A **Reset demo** button restores the starting state.

### Acceptance Criteria

- Demo mode can be switched on and off without reinstalling.
- Demo data is always labelled as demo data.
- Demo mode is unavailable in release builds.

---

# 11. Complete Day-in-the-Life User Scenario

This section combines the individual stories into one realistic end-to-end experience.

## Scenario: Aarav's First Day With BoomDoom

### 9:00 AM — First Launch

Aarav downloads BoomDoom after realizing he spends too much time watching Instagram Reels.

He opens the application.

The welcome screen introduces the idea of turning scrolling time into opportunities for learning.

The design feels familiar: modern typography, dark background, colorful category chips, and clear navigation.

Aarav taps **Get Started**.

On one screen, he sets a daily budget of 150 reels and check-ins every 15 reels.

He adds "Cricket" as a custom category and marks Rage-bait as content he wants to watch less often.

He selects three learning topics: Psychology, Interview Preparation, and General Knowledge.

He tries a 45-second Psychology lesson before setting anything else up, and likes it.

He enables the Android permissions (following the in-app guide when the Accessibility toggle is greyed out) and enters the dashboard.

The Home screen shows:

**0 / 150 reels watched**

**1 lesson completed**

**Monitoring Active**, once the required monitoring capability is running.

He closes BoomDoom and continues his morning.

### 11:30 AM — First Scrolling Session

During a short break, Aarav opens Instagram Reels.

BoomDoom detects the supported Reels screen.

A thin red progress indicator appears at the top.

Aarav watches comedy videos, a technology explainer, and a few fitness reels.

He does not need to interact with BoomDoom.

Every detected reel transition contributes to his daily count.

Where permission and device capabilities allow, the app classifies the content locally.

### 11:40 AM — First Intervention

Aarav reaches 15 reels.

A short intervention appears:

**"15 reels already 👀"**

**"Most of your recent reels were comedy and entertainment."**

**"Want to try a 45-second Psychology lesson?"**

Aarav notices three actions: Switch to Lesson, Continue Scrolling, and Done for Now.

He chooses **Switch to Lesson**.

### 11:41 AM — First Learning Experience

BoomDoom opens a cinematic illustrated stickman lesson.

The topic is the Zeigarnik Effect.

An animated character struggles to stop thinking about an unfinished task.

The narrator explains the scenario.

The video pauses and asks Aarav to predict why unfinished tasks remain mentally active.

He chooses an answer.

The application gives immediate feedback and continues the explanation.

The lesson finishes with a recap:

**"Unfinished tasks can remain mentally active and easier to recall."**

Aarav's second lesson is saved to Learned.

He chooses **Done for Now**.

### 4:00 PM — Second Scrolling Session

Aarav opens Instagram again.

The daily reel count continues from his previous usage.

He watches several categories, including a few reels classified as Rage-bait.

Because he flagged this category, BoomDoom briefly displays an indicator.

It does not block the reel.

Aarav continues scrolling until his next reminder.

This time, he selects **Continue Scrolling**.

BoomDoom respects his decision and closes the intervention.

### 8:00 PM — Reviewing the Day

Aarav opens BoomDoom directly.

His dashboard displays illustrative totals such as:

- 92 reels watched.
- 3 learning sessions completed.
- 8 flagged reels.
- Approximately 2 minutes of learning.

He opens Insights.

A donut chart displays the recognized categories.

He learns that comedy accounted for a large share of his viewing.

The daily summary presents an observation based on his recorded activity.

Aarav then opens Learn and watches another lesson about interview communication.

### 10:00 PM — Adjusting His Goals

Aarav opens You → Preferences.

He lowers tomorrow's daily budget to 120 reels.

He adds System Design as a learning topic.

BoomDoom submits the request to prepare lessons.

While new lessons are being generated, Aarav can still access the existing Psychology, Interview Preparation, and General Knowledge lessons.

When his System Design lessons become available, the topic displays a ready state.

### End-of-Day Outcome

Aarav has not been forced to stop using Instagram.

Instead, he has gained visibility into his habits and discovered a practical way to turn a portion of his scrolling time into learning.

That is BoomDoom's central user experience.

---

# 12. Important Application States

| Feature | State | Expected UX |
|---|---|---|
| Monitoring | Active | Show active status and supported tracking behavior |
| Monitoring | Paused | Stop capture and overlays |
| Monitoring | Permission missing | Explain issue and offer recovery |
| Budget | Under limit | Show normal progress |
| Budget | Near limit | Increase progress emphasis |
| Budget | Reached | Show supportive intervention |
| Budget | Over | Full bar in "over" style with +N counter; check-ins every 25 reels |
| Check-ins | Backed off | Interval doubled after two Continue taps in a session |
| Check-ins | Snoozed | No check-ins until the session ends |
| Monitoring | Needs re-consent | One-tap "Resume monitoring" prompt |
| Classification | Successful | Save category locally |
| Classification | Uncertain | Use Other |
| Lesson | Cached | Open immediately |
| Lesson | Generating | Show preparation state |
| Lesson | Ready | Allow playback |
| Lesson | Failed | Explain and offer alternative |
| Network | Offline | Use local features and cached lessons |
| Analytics | No data | Show helpful empty state |
| Account | Anonymous | Allow core features without mandatory login |
| Account | Linked (P1) | Sync lessons and progress; feed data stays local |
| Demo mode | On | Short interval, labelled demo data |

---

# 13. End-to-End Acceptance Scenarios

These scenarios should be used during QA and user acceptance testing.

## Scenario A — Successful Onboarding

**Given** a user installs BoomDoom for the first time,  
**When** the user selects a budget and check-in frequency, optionally adds labels and flags, picks learning topics, optionally tries a lesson, and completes applicable permission setup,  
**Then** the user reaches Home with their saved settings and an accurate monitoring status.

## Scenario B — Reel Counting

**Given** monitoring is active and Instagram Reels detection is available,  
**When** the user swipes through supported reels,  
**Then** the application updates the reel count without duplicate counting and refreshes the progress indicator.

## Scenario C — Reminder Trigger

**Given** the user has configured reminders every 15 reels,  
**When** the 15th reel is detected,  
**Then** BoomDoom displays an intervention containing a recent usage summary and actions to Learn, Continue, or Finish.

## Scenario D — Switch to Learning

**Given** a cached lesson is ready,  
**When** the user selects Switch to Lesson,  
**Then** the lesson opens promptly with working playback, captions, and interactive cards.

## Scenario E — Complete a Quiz

**Given** a lesson reaches an interaction point,  
**When** the user selects an answer,  
**Then** the app displays relevant feedback, records the interaction, and allows the user to continue.

## Scenario F — Reach Daily Budget

**Given** the user has a budget of 150 reels,  
**When** the 150th reel is detected,  
**Then** the app displays a budget-reached message and preserves the user's ability to continue or leave.

## Scenario G — Permission Revoked

**Given** a required permission was previously enabled,  
**When** Android revokes it,  
**Then** BoomDoom updates monitoring status, stops dependent functionality, and displays a recovery action.

## Scenario H — Offline Learning

**Given** a lesson is cached locally,  
**When** network connectivity becomes unavailable,  
**Then** the user can still play that lesson and complete its locally available interactions.

## Scenario I — Data Deletion

**Given** the user has stored tracking history,  
**When** the user confirms local-history deletion,  
**Then** the local records are removed and associated insight views return to empty states.

---

# 14. UX Copy and Interaction Guidelines

BoomDoom should consistently use supportive and positive language.

| Context | Recommended Copy | Avoid |
|---|---|---|
| Welcome | "Turn doom into boom." | "Stop wasting your life." |
| Reminder | "You've watched 15 reels. Want a quick learning break?" | "You're addicted to scrolling." |
| Budget reached | "You've reached today's budget. What would you like to do?" | "You failed your limit." |
| Lesson completion | "Nice! You learned something new." | "You should learn more instead of scrolling." |
| Incorrect answer | "Not quite. Here's why." | "Wrong answer!" |
| Offline | "You're offline. Your saved lessons are ready." | "Network error 503." |
| Permission missing | "Enable access to resume tracking." | "App malfunction." |
| No analytics | "Your insights will appear after your first session." | "No data found." |

### Navigation Guidelines

- Use one primary CTA per screen.
- Keep labels understandable without technical knowledge.
- Use illustrations only when they improve comprehension.
- Make dismissal controls obvious.
- Prefer bottom sheets for small configuration tasks.
- Use full-screen destinations for important flows such as lesson playback.
- Preserve user progress when navigating away and returning.
- Avoid unnecessary confirmation dialogs for reversible preference changes.

---

# 15. MVP Prioritization

## P0 — Required for Hackathon

| Module | Required Experience |
|---|---|
| Onboarding | Welcome, budget + check-ins (one screen), optional custom labels and flags, learning interests, try-a-lesson |
| Permissions | Explain and request monitoring capabilities, including the restricted-settings guide |
| Home | Daily budget, monitoring status, basic statistics |
| Tracking | Instagram Reels session and reel counting |
| Overlay | Passive red budget indicator |
| Classification | On-device category recognition with supported fallback; simple "Wrong label?" fix |
| Speech | Hindi/Hinglish talking-head reels (US-13A) |
| Flagging | Identify flagged categories |
| Intervention | Periodic check-in with three user actions, snooze and back-off |
| Learning | Cached cinematic stickman micro-lessons |
| Interactions | Predict, multiple choice, true/false |
| Recap | Lesson completion, saved takeaway, Learned collection, report a mistake |
| Custom topics | Request at least one generated topic in the demo |
| Insights | Today statistics, category breakdown, basic summary |
| Settings | Budget, labels, topics, pause monitoring |
| Privacy | Local analysis, deletion controls |
| Recovery | Permission errors, offline behavior, unavailable lessons |
| Demo | Hidden demo mode (US-34) |

## P1 — After Core MVP

- Weekly insights and historical comparisons.
- Full light/dark/system theme switching.
- More advanced learning personalization.
- Learning-history review improvements.
- Optional daily summary notifications.
- In-feed lesson overlays inside supported Android contexts.
- Optional login (Google or phone OTP) and cross-device sync of lessons and progress.
- Reflect, Your answer and Go deeper interactions.
- Feed-based topic suggestions (opt-in).
- More sophisticated category correction workflows.
- Indian languages beyond Hindi and Hinglish (e.g., Tamil, Telugu).

## Later Releases

- YouTube Shorts support.
- Lessons generated from personal notes and PDFs.
- Time-of-day scrolling budgets.
- Optional streaks and achievements.
- Additional learning formats.

---

# 16. Final Product Experience Requirements

The end-to-end BoomDoom experience is successful when a first-time user can:

1. Understand the application's purpose without a tutorial.
2. Complete setup without mandatory account creation.
3. Configure a personal reel budget and reminder frequency.
4. Enable monitoring through clearly explained permissions.
5. Continue watching Instagram normally with an unobtrusive progress indicator.
6. Receive relevant and nonjudgmental scrolling check-ins.
7. Understand the categories of content they have watched.
8. Switch to an educational lesson with minimal effort.
9. Complete a short animated lesson and interactive question.
10. Save or review what they learned.
11. Return to their previous activity or finish their session.
12. Review meaningful scrolling and learning insights.
13. Modify preferences without unnecessary friction.
14. Pause monitoring or delete their data whenever they choose.

## Definition of Done

A user story should be considered complete only when:

- Its happy path works end to end.
- Relevant failure and recovery paths are implemented.
- The UI matches the established design system.
- Navigation and Android Back behavior are predictable.
- Loading, empty, permission, and offline states are handled.
- Privacy constraints are preserved.
- Accessibility requirements are met.
- Acceptance criteria are covered by testing.
- The feature has been validated on the target Android devices.

**Final UX objective:** BoomDoom should never feel like an app that fights the user for control of their phone. It should feel like an intelligent, private, supportive companion that makes purposeful scrolling and learning easier.