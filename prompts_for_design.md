# BoomDoom — AI Image Prompts for Design Exploration

**Purpose:** generate mockup images of every BoomDoom screen with an AI image tool, to explore the look and feel before building.  
**Source:** screens and states from `UX.md` v1.1 (story numbers in brackets).  
**Date:** 21 November 2026

---

## 1. How to use this file

1. **Paste the Style Block (§2) at the start of every prompt.** Image tools don't remember earlier prompts, so each prompt must carry the full style.
2. **Generate the anchor screens first:** Home (H-1), Check-in card (O-4) and Lesson player (L-2). Pick the best result and use it as a **style/image reference** for all other screens, so the app looks like one product.
3. **Keep on-screen text short.** Image models often garble long text. Every prompt lists the exact words to show, in quotes; if text comes out wrong, fix it in Figma afterwards. Tools that are good at text in images give better results here.
4. **Portrait phone format:** use aspect ratio **9:19.5** (or 9:16 if your tool doesn't support it).
5. **Don't show real brands.** BoomDoom works over Instagram, but mockups must not show Instagram's logo or UI. Prompts describe "a generic short-video feed app" instead.
6. **Treat results as mood and layout ideas**, not final UI. Rebuild the chosen direction properly in Figma with real components.

---

## 2. Style Block (paste before every prompt)

```
High-fidelity mobile app UI mockup for an Android app called "BoomDoom", a digital-wellbeing and micro-learning app for Indian Gen Z. Single phone screen, front view, portrait 9:19.5, flat UI (no hands, no 3D phone at an angle unless asked), crisp vector rendering, Dribbble-quality.
Design system: dark mode. Background near-black #0E0F13; cards #171922 and #1F2230 with 24px rounded corners and subtle 1px borders #2A2D3A; soft glow on key elements. Primary accent "boom" coral-orange #FF6B3D; learning accent teal #2EC4B6; budget/progress red #FF3B3B; warning amber #FFB020; success green #34C759. Text: white #F5F5F7 for headings, grey #A1A1AA for secondary text.
Typography: modern geometric sans-serif, bold oversized numbers, clear hierarchy, generous spacing, one primary button per screen (pill-shaped, coral #FF6B3D, dark text).
Illustration style: hand-drawn stickman characters with dark ink lines on warm cream paper cards (#FAF8F3) with small teal accents, playful and minimal.
Tone: friendly, supportive, non-judgmental, premium. Android status bar at top, gesture bar at bottom.
Avoid: real brand logos (no Instagram, no TikTok), clutter, stock photos of people, neon overload, garbled text.
```

**Light theme variant** (for Appearance tests, A-3): replace the colours with background #F7F7F5, cards #FFFFFF with borders #E6E6E3, headings #111216, secondary text #5B5F6B; keep the same accents.

---

## 3. Brand

### B-1 App icon
```
[Style Block] App icon for "BoomDoom" on a plain dark background, shown large and centred, plus small versions at 3 sizes below it. Concept: a stickman character bursting out of a vertical phone-shaped scroll, with a small coral-orange spark explosion ("boom"). Rounded-square Android adaptive icon, bold simple shapes, coral #FF6B3D and teal #2EC4B6 on near-black, readable at small sizes. No text.
```

### B-2 Splash screen
```
[Style Block] Splash screen. Centred app icon (stickman bursting out of a phone-shaped scroll with a coral spark), the wordmark "BoomDoom" in bold below it, and the tagline "Turn doom into boom." in small grey text. Near-black background with a faint radial coral glow behind the icon. Nothing else on screen.
```

---

## 4. Onboarding

### O-1 Welcome [US-01]
```
[Style Block] Onboarding welcome screen. Top: large headline "Turn doom into boom." Subheadline in grey: "Understand your scrolling habits and turn everyday breaks into learning." Middle: three small cream paper cards in a row with stickman illustrations and one-line captions: "See what you watch", "Understand your habits", "Replace scrolling with learning". Small lock icon with text "Your feed stays private on your device." Bottom: one coral pill button "Get Started". Subtle dark gradient background.
```

### O-2 Your Scroll Budget (budget + check-ins on one screen) [US-02, US-03]
```
[Style Block] Onboarding settings screen titled "Your scroll budget". Section 1: question "How many reels a day?" with a huge number "300" and a horizontal slider with a red #FF3B3B track; below it preset chips "100", "150", "200", "300". Section 2: question "Check in with me every…" with four selectable pill chips "10", "15", "25", "30 reels", where "15" is selected in coral. Small grey note: "BoomDoom never locks your apps." Bottom: coral pill button "Continue". Progress dots at the top showing step 2 of 6.
```

### O-3 Anything specific? (custom labels) [US-04]
```
[Style Block] Onboarding screen titled "Anything specific you want us to spot?". A group of 8 read-only grey chips with small icons: "Comedy", "Fitness", "Education", "Food", "News", "Finance", "Rage-bait", "Ads", with a caption "We always recognize these." Below, a dashed-outline chip "+ Add your own" and one custom chip "Cricket" highlighted in teal. Bottom: coral button "Continue" and a text link "Skip".
```

### O-4 See less of… (flags) [US-05]
```
[Style Block] Onboarding screen titled "Anything you'd like to see less of?". A vertical list of category rows (Comedy, Fitness, Education, Food, News, Finance, Rage-bait, Ads, Cricket), each with a toggle labelled "Not worth my time". "Rage-bait" is toggled on and shows a small amber flag icon. Info card in grey: "We'll show a small tag on the reel and highlight it in your insights." Bottom: coral button "Save & Continue" and text link "Skip".
```

### O-5 What do you want to learn? [US-06]
```
[Style Block] Onboarding screen titled "What would you love to learn?". A 2-column grid of 8 colourful topic cards, each with a small stickman illustration on cream paper and a label: "Psychology", "Interview Prep", "General Knowledge", "Personal Finance", "Science", "History", "Communication", "Tech Explained". Three cards (Psychology, Interview Prep, General Knowledge) are selected with a teal outline and checkmark. A dashed card "+ Add my own topic". Small link "Fine-tune difficulty later". Bottom: coral button "Continue".
```

### O-6 Try a lesson first [US-06A]
```
[Style Block] Onboarding screen with a large preview card of a stickman lesson (cream paper card, stickman lying in bed with a thought bubble showing a half-done to-do list), a teal topic tag "Psychology" and a duration tag "45 sec". Headline above: "Try a 45-second lesson first?" Bottom: coral button "Play" and text link "Skip for now".
```

### O-7 Permissions checklist [US-07]
```
[Style Block] Onboarding screen titled "Let's get BoomDoom ready." A vertical checklist of 4 cards, each with an icon, a title, a one-line plain-language reason and a status: 1) "Accessibility" – "Count reels as you scroll" – green check "Enabled"; 2) "Display over apps" – "Show your budget bar" – green check "Enabled"; 3) "Screen understanding" – "Recognise content, only on this phone" – coral button "Enable"; 4) "Audio understanding (optional)" – "Understand Hindi talking reels" – grey button "Maybe later". Small lock note at the bottom: "Nothing from your feed leaves this phone."
```

### O-8 Restricted settings guide [US-07]
```
[Style Block] Help bottom sheet over a dimmed onboarding screen. Title: "Toggle greyed out?" Three numbered steps, each with a tiny simplified illustration of an Android settings screen (generic, no brand): "1. Open BoomDoom's App info", "2. Tap ⋮ then 'Allow restricted settings'", "3. Come back and turn on Accessibility". Bottom: coral button "Open App info".
```

### O-9 Screen-capture explainer [US-07]
```
[Style Block] Onboarding explainer screen titled "Before Android asks…". A mockup of an Android notification card reading "BoomDoom is understanding your feed on this phone". Three short bullet rows with icons: "This notification stays while monitoring is on", "Android may ask again after a restart", "If offered, choose 'A single app' → your reels app". Bottom: coral button "Got it, continue".
```

---

## 5. Home

### H-1 Home dashboard (anchor screen) [US-09, US-10]
```
[Style Block] Home dashboard. Top: greeting "Hey Hrushi 👋" and a small green status pill "● Monitoring active" with a "Pause" text button. Hero card: huge number "124 / 150" with the label "reels today", a thick red progress bar at 83% with an amber glow near the end. Row of 3 small stat cards: "8 flagged" (amber flag icon), "2 lessons" (teal book icon), "4 min learning" (teal clock icon). A wide cream-paper lesson card with a stickman illustration: tag "Psychology · 45 sec", title "The Zeigarnik effect", teal button "Start learning". Below, a "Today's insight" card with a mini donut chart and text "Comedy was 48% of your feed." Bottom navigation bar with 4 tabs: Home (active, coral), Learn, Insights, You.
```

### H-2 Home – over budget [US-12, US-16]
```
[Style Block] Same Home dashboard layout as the anchor, but the hero card shows "180 / 150" with a small coral badge "+30 over", and the progress bar is full with a solid darker red "over" style. Supportive line below: "Over today's budget — want a quick learning break?" with a teal button "Start a lesson". Bottom navigation with Home active.
```

### H-3 Home – setup needed [US-10, US-32]
```
[Style Block] Home dashboard with the status pill in amber: "● Limited monitoring". A prominent amber-outlined card near the top: icon of a broken link, title "Screen understanding is off", text "Categories won't update until it's back.", coral button "Fix setup". The rest of the dashboard (budget card, lessons) is shown normally below. Bottom navigation with Home active.
```

### H-4 Home – first day empty state [US-09]
```
[Style Block] Home dashboard for a brand-new user. Hero card shows "0 / 150 reels today" with an empty red bar. A friendly cream-paper illustration of a stickman waving next to a phone, text "Your insights will appear after your first scroll session." Lesson card "Psychology · 45 sec" with teal "Start learning" button. Status pill "● Monitoring active". Bottom navigation with Home active.
```

---

## 6. Overlays on top of the reels app

> All overlay prompts show a **generic** vertical short-video app underneath (blurred, no logos, no real UI), so the BoomDoom layer is the focus.

### V-1 Budget bar while scrolling [US-12]
```
[Style Block] Phone screen showing a generic full-screen vertical short video (a blurred, out-of-focus scene of a person cooking, no logos, no app UI). At the very top, just below the status bar, a thin 4px red progress bar filled to 60%, with a tiny pill on its right end reading "90 / 150". Nothing else from BoomDoom on screen. Emphasise how subtle and non-intrusive the bar is.
```

### V-2 Budget bar at 80% and over budget [US-12]
```
[Style Block] Two phone screens side by side, each showing a blurred generic vertical video with no logos. Left: the thin top progress bar at 85%, pulsing amber-red glow, pill "128 / 150". Right: bar full in solid dark red with a small coral counter "+30" at the right edge. Caption under each phone in small grey text: "Near limit" and "Over budget".
```

### V-3 Flagged tag [US-14]
```
[Style Block] Phone screen with a blurred generic vertical video of a person shouting at the camera (no logos, no app UI). Thin red budget bar at the top. In the upper-left area, a small rounded amber chip with a flag icon: "Flagged: Rage-bait". It looks temporary and light, not alarming.
```

### V-4 Check-in card (anchor screen) [US-15]
```
[Style Block] Phone screen with a blurred, dimmed generic vertical video behind (no logos). Centred floating card (#171922, 24px corners, soft shadow). Title: "15 reels already 👀". Subtitle: "Here's what you've been watching." A small horizontal stacked bar with 3 coloured segments and a legend: "Comedy 60%", "Tech 25%", "Fitness 15%". A cream-paper mini lesson preview with a stickman and text "Swap 60 seconds for a Psychology lesson?". Three buttons stacked: coral "Switch to lesson", outlined "Continue scrolling", text button "Done for now". Small grey link at the bottom: "Snooze for this session".
```

### V-5 Budget reached card [US-16]
```
[Style Block] Phone screen with a blurred, dimmed generic vertical video behind (no logos). Centred card with a small celebratory-but-calm stickman illustration on cream paper. Title: "You've reached today's scroll budget." Subtitle: "150 of 150 reels. What would you like to do?" Buttons: coral "Start a lesson", outlined "Keep scrolling", text button "Finish for today". Full red bar visible at the top of the screen.
```

### V-6 Hindi talking-head reel understood (demo moment) [US-13A]
```
[Style Block] Phone screen with a blurred generic vertical video of a young man talking to the camera in front of a stock-market chart background (no logos, no app UI). Top: thin red budget bar. Bottom third: a small translucent BoomDoom card with a microphone-wave icon, label chip "Finance" in teal and an amber chip "🚩 Finfluencer tips", and a one-line italic transcript snippet "…SIP mein har mahine invest karo…". Tiny grey note: "Understood on this phone".
```

---

## 7. Learn

### L-1 Learn tab [US-17, US-22]
```
[Style Block] Learn tab screen. Top: title "Learn" and horizontal topic chips "All", "Psychology", "Interview Prep", "GK", "System Design" (the last one has a small spinning icon and "Preparing"). Large featured cream-paper lesson card with stickman illustration: "Up next · Psychology · Beginner · 45 sec", title "The Zeigarnik effect", teal play button. Below, a horizontal row of smaller lesson cards. A section "Learned" with 3 small recap chips. Bottom navigation with Learn active (teal).
```

### L-2 Lesson player (anchor screen) [US-18]
```
[Style Block] Full-screen vertical lesson player, no bottom navigation. The video area is an illustrated scene on warm cream paper: a stickman waiter juggling order slips, simple hand-drawn ink lines, teal accent shapes, a small clock. Bold word-by-word caption near the bottom: "waiters remembered UNPAID orders" with one word highlighted in yellow. Top: segmented progress bar with 4 segments (first two filled), a close "X" and topic tag "Psychology". Right edge: vertical icon buttons for captions, sound, and "report". Clean, immersive, reel-like.
```

### L-3 Interaction – predict / multiple choice [US-19]
```
[Style Block] Full-screen lesson player paused: the cream-paper stickman scene is blurred in the background. A card slides up from the bottom: small label "Predict", question "Why did the waiters forget paid orders?", three large answer buttons stacked: "Paid orders were boring", "The brain lets go of finished tasks", "They were tired". The second answer is selected with a teal border and checkmark, and a green feedback strip reads "Exactly! Finished tasks stop nagging the brain." Coral button "Continue".
```

### L-4 Interaction – swipe true/false [US-19, US-33]
```
[Style Block] Full-screen lesson interaction. A large swipeable cream-paper card in the centre with a stickman illustration and the statement "Unfinished tasks are easier to remember." Left side hint "← False" in red, right side hint "True →" in green, and below the card two large tap buttons "False" and "True" for accessibility. Small label "True or false?" at the top.
```

### L-5 Recap card [US-20]
```
[Style Block] Lesson completion screen. Centre: a cream-paper recap card with a small happy stickman holding a checklist, label "Saved to Learned", concept title "The Zeigarnik effect", takeaway text "Unfinished tasks stick in your mind. Start for 2 minutes to beat procrastination." Subtle confetti in coral and teal. Below: three buttons — teal "Another lesson", outlined "Back to my reels", text button "Done for now".
```

### L-6 Learned collection [US-20A]
```
[Style Block] Screen titled "Learned" with topic filter chips "All", "Psychology", "Interview Prep", "GK". A vertical list of recap cards (cream paper, small stickman icon, concept title, one-line takeaway, date). One card expanded showing a teal "Rewatch lesson" button. Bottom navigation with Learn active.
```

### L-7 My Topics and add custom topic [US-22, US-23]
```
[Style Block] Screen titled "My topics". List of topic rows with difficulty pills: "Psychology – Beginner", "Interview Prep – Intermediate", "General Knowledge – Beginner", and "System Design – Intermediate" with an amber status "Preparing lessons…" and a progress shimmer. A bottom sheet is open: "Add a topic", text field with "System Design", difficulty selector "Beginner / Intermediate / Advanced" (Intermediate selected), optional field "Goal: Prepare for backend interviews", coral button "Create topic".
```

### L-8 Suggested for you (P1) [US-22A]
```
[Style Block] Card in the Learn tab titled "Suggested for you" with a small lock note "Based on your scrolling · stays on your phone". Three suggestion rows, each with a small icon and reason: "Personal Finance Basics — you watch a lot of finance reels", "Cricket Stats & Strategy — you love cricket reels", "Exercise Science — fitness is in your top 3". Each row has a teal "Add" button and an "x" to dismiss.
```

### L-9 Report a mistake sheet [US-18]
```
[Style Block] Lesson player paused and dimmed, with a bottom sheet titled "Report a mistake". Four selectable options as rows with radio buttons: "Wrong fact", "Confusing", "Audio/video problem", "Inappropriate". A text field "Add a note (optional)". Coral button "Send report". Small grey text: "We'll review this lesson."
```

---

## 8. Insights

### I-1 Insights – Today [US-24, US-25]
```
[Style Block] Insights tab, "Today" selected in a segmented control "Today | 7 days". Summary row of 4 stat tiles: "124 reels", "26 left", "8 flagged", "Longest 32 min". A large donut chart with coloured segments and a legend: Comedy 48%, Tech 18%, Fitness 12%, Finance 10%, Rage-bait 7%, Other 5%. A card "Your daily summary" with a small sparkle icon and text "Most of your scrolling was comedy, mostly after 10 PM. You also finished 2 lessons." Bottom navigation with Insights active.
```

### I-2 Category detail and "Wrong label?" [US-24A]
```
[Style Block] Screen titled "Comedy · 59 reels today". A list of reel entries, each with time, duration and a short text description (no thumbnails): "9:42 PM · 18s · Two friends prank a roommate", "9:43 PM · 25s · Person reviews a phone", etc. Each row has a small text button "Wrong label?". A bottom sheet is open for the second row: "Move to…" with category chips, "Tech" selected in teal, and a coral "Save" button.
```

### I-3 Insights – 7 days (P1) [US-26]
```
[Style Block] Insights tab with "7 days" selected. A bar chart of daily reels for Mon–Sun with a dashed budget line at 150, the last 3 bars lower than the first 4. A "What changed" card: "Rage-bait ↓ 40%", "Lessons ↑ 3", "Learning time 18 min". Bottom navigation with Insights active.
```

### I-4 Insights – empty state [US-24]
```
[Style Block] Insights tab for a new user. Centred cream-paper illustration of a stickman holding an empty chart with a question mark. Text: "Your insights will appear after your first session." Teal button "Try a lesson meanwhile". Bottom navigation with Insights active.
```

---

## 9. You (settings)

### Y-1 You tab [US-27]
```
[Style Block] "You" tab. Top: a small avatar circle with a stickman and the label "Anonymous · on this phone", with a small link "Link account (optional)". Grouped settings list with icons: "Scrolling goals", "Content & flags", "Learning preferences", "Appearance", "Privacy & data", "Permissions", "About". Bottom navigation with You active.
```

### Y-2 Scrolling goals [US-27, US-03]
```
[Style Block] Settings screen "Scrolling goals". Rows: "Daily budget — 150 reels" with a slider, "Check in every — 15 reels" with chips, toggle "Ease off when I keep scrolling" (on), "Over-budget check-ins — every 25 reels" with a stepper, "New day starts at — 4:00 AM" with a time picker chip. Small helper texts in grey under each row.
```

### Y-3 Appearance [US-28]
```
Use the light-theme variant of the Style Block for the right phone. Two phone screens side by side showing the same "Appearance" settings screen: options "Dark", "Light", "Follow system" as radio cards with tiny previews, and a toggle "Reduce motion". Left phone in dark theme, right phone in light theme, both with identical layout.
```

### Y-4 Privacy & data [US-29]
```
[Style Block] Settings screen "Privacy & data". Two info cards at the top: "On this phone" (lock icon) — "Reels, labels, insights, audio and transcripts never leave your phone." and "Sent to BoomDoom" (cloud icon) — "Only your learning topics and lesson activity." Then toggles: "Pause monitoring", "Audio understanding", "Topic suggestions from my feed". Then buttons: outlined "Export my data", red-outlined "Delete tracking history", red text button "Delete account".
```

### Y-5 Delete confirmation [US-29]
```
[Style Block] Dialog over a dimmed settings screen. Title: "Delete tracking history?" Text: "This removes your reel counts, categories and insights from this phone. Lessons you've learned stay." Buttons: outlined "Cancel" and red "Delete".
```

### Y-6 Link account (P1) [US-08]
```
[Style Block] Screen "Keep your lessons on any phone". A cream-paper illustration of a stickman carrying lesson cards between two phones. Text: "Link an account to sync lessons and progress. Your feed data always stays on this phone." Two large buttons: "Continue with Google" (white, generic G-shaped icon placeholder, no official logo) and "Continue with phone number". Text link "Not now".
```

---

## 10. System states and extras

### S-1 Offline [US-30]
```
[Style Block] Learn tab with a slim grey banner at the top: "You're offline. Your saved lessons are ready." Lesson cards marked with a small "Downloaded" check are bright; one card for a new topic is greyed out with the label "Needs internet". Bottom navigation with Learn active.
```

### S-2 Notifications [US-31]
```
[Style Block] Android lock screen with a calm dark wallpaper and two notification cards from BoomDoom (small app icon): 1) "Your System Design lessons are ready — start with a 45-second intro." 2) "Today: 92 reels and 3 lessons. See your breakdown." Clean, readable, no other apps' notifications.
```

### S-3 Resume monitoring prompt [US-07, US-32]
```
[Style Block] Small bottom card over the Home screen: icon of a pause symbol turning into play, title "Monitoring paused after restart", text "Android needs your OK to resume.", coral button "Resume monitoring".
```

### S-4 Demo mode banner [US-34]
```
[Style Block] Home dashboard with a thin striped amber banner across the top reading "DEMO MODE · sample data". Hero budget card "12 / 150", status pill "Check-ins every 3 reels". A small "Reset demo" text button in the banner. Bottom navigation with Home active.
```

---

## 11. Full-flow boards (for presentations)

### F-1 Onboarding flow board
```
[Style Block] Presentation board on a dark background showing 6 phone screens in a row, connected by thin coral arrows, left to right: Welcome ("Turn doom into boom."), Your scroll budget (big "300" slider), Anything specific? (category chips), What would you love to learn? (topic cards), Try a lesson (stickman preview), Let's get BoomDoom ready (permissions checklist). Small step labels under each phone. Consistent design across all screens.
```

### F-2 Core loop board: See → Understand → Replace
```
[Style Block] Presentation board on a dark background with three columns titled "See", "Understand", "Replace". Column 1: phone with a blurred generic vertical video and a thin red budget bar at the top. Column 2: phone showing the Insights donut chart and a "Flagged: Rage-bait" tag. Column 3: phone showing a cream-paper stickman lesson with a quiz card. Coral arrows between columns. Title at the top: "BoomDoom". No real brand logos.
```

---

## 12. Checklist after generating

- [ ] Do all screens share the same colours, corner radius, button style and stickman style?
- [ ] Is there exactly one primary (coral) button per screen?
- [ ] Is every overlay clearly lighter and less dominant than the video beneath it?
- [ ] Do check-in and budget cards feel supportive, not alarming?
- [ ] Do flagged/warning states use an icon as well as colour?
- [ ] Is text readable at a glance (no tiny grey text on dark grey)?
- [ ] No real brand logos or UI anywhere?
- [ ] Rebuild the winning direction in Figma with real text and components before handing to developers.
