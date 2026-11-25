# Film asset prompts: backgrounds, characters, props

Image-generation prompts for new art in the stickman films. Use any image model that does portrait output and
transparent PNGs (ChatGPT / gpt-image works for both). Generate in this order: **style anchor first**, then attach it
as a reference image to every other prompt, so everything shares one look.

**Where the files go:** save them in this folder (`designs/film-assets/`) with the file names given below.

**How each kind is used:**
- **Backgrounds** become the film's stage: characters walk and stand on the floor, and the camera pans and zooms
  across the image.
- **Props** become sprites that characters hold, point at or stand next to.
- **Characters** are design references. The engine draws people with its own animated rig, so I turn each sheet
  into a rig colour scheme and outfit (skin, clothes, hair, accessories). A generated picture can't walk or talk.

---

## 0. Style anchor (generate first)

File: `style_anchor.png`. Size 1024×1536 (portrait).

> A flat 2D vector illustration style sheet for an animated explainer film. Solid colour shapes with no outlines,
> rounded geometric forms, soft long shadows, gentle rim light from a warm lamp, no texture, no grain, no gradients
> except soft glows around lights. Dusk colour palette: deep aubergine #2A1838 and #241736 for darkest areas, plum
> #3B2352 and #4C2D63, muted purple #7A56A0, warm coral #E4493F and #FF8F7A, amber light #FFD08A and #FFC66E, teal
> #2F9A98 and #237F80, cream #F2E7D6. Show: a small room corner with a lamp, a window at dusk, a plant, a table and a
> chair, all in this style, plus a row of the palette colours as swatches along the bottom. No people, no text, no
> letters, no logos.

---

## 1. Backgrounds (4)

**Rules for every background (paste this block after each prompt):**

> Portrait 2:3, 1024×1536. Flat 2D vector style matching the attached style anchor, same dusk palette, no outlines,
> no texture. Eye-level camera, straight-on view (not top-down). The floor is flat and visible across the bottom
> third; the line where the floor meets the back wall sits at about 70% of the image height. Keep the middle of the
> picture (the centre 60% of the width, from 40% to 85% of the height) clear of furniture so two characters can stand
> there in front of everything. Put details at the sides and on the back wall. No people, no animals, no text, no
> letters, no numbers, no logos, no signs with writing. Calm, not busy: large simple shapes.

### 1a. Meeting room with whiteboard (system design, interview prep)
File: `bg_meeting_room.png`

> A modern office meeting room at dusk. Back wall: a large blank whiteboard centred high on the wall (empty, nothing
> drawn on it), a tall window on the right with a city skyline in purple dusk light, a wall clock without numbers.
> Left side: a potted plant and a low cabinet. A long meeting table pushed to the right edge with two chairs. Warm
> pendant lights. Teal and plum walls, amber light pools on the floor.

### 1b. Server room (system design, tech)
File: `bg_server_room.png`

> Inside a data centre at night. Rows of tall server racks on the left and right edges, receding into the back, with
> small glowing status lights in teal and amber, cable trays along the ceiling, a raised tiled floor. Back wall:
> a large dark wall-mounted screen (blank, glowing faintly teal). Cool teal and deep plum tones with warm amber
> highlights. The centre aisle between the racks is empty.

### 1c. Living room corner (psychology, personal finance, everyday life)
File: `bg_living_room.png`

> A cosy living room in the evening. Back wall: a large window with warm dusk sky, a bookshelf on the left with
> plain coloured book spines, a framed abstract picture (shapes only). Right edge: a sofa with cushions and a floor
> lamp casting a warm amber glow. A rug on the floor. Plum walls, coral and amber accents, cream highlights.

### 1d. Museum hall (history, general knowledge)
File: `bg_museum_hall.png`

> A museum gallery hall at dusk with soft spotlights. Back wall: a tall arched doorway in the centre leading to
> another hall, two large blank framed canvases (soft colour fields, no images) on either side. Left edge: a stone
> column and a glass display case on a plinth with an ancient clay pot inside. Right edge: a marble bust on a
> pedestal (abstract, no recognisable person). Polished floor with reflections. Warm amber spotlights on plum and
> teal walls.

---

## 2. Characters (8 design sheets)

These are references for the animated rig. Every sheet uses the **same body**, so I can map it onto the engine.

**Rules for every character (paste this block after each prompt):**

> Character design sheet, flat 2D vector, matching the attached style anchor. The same simple stickman-style body
> for every character: a large round head with no neck, two small dot eyes and a simple curved mouth, a soft rounded
> trapezoid torso, thick rounded arm and leg tubes, rounded mitten hands, chunky rounded shoes. About 5 heads tall.
> Show the character three times side by side: front view, side view facing right, and a waving pose. Plain flat
> background #E8E4F4. No outlines, no text, no labels.

Files: `char_<name>.png`.

| File | Prompt (then the rules block) |
|---|---|
| `char_engineer.png` | A young software engineer, medium brown skin, short curly black hair, teal hoodie, a lanyard with a blank badge, dark trousers, white sneakers. |
| `char_interviewer.png` | A confident interviewer, deep brown skin, short neat hair, plum blazer over a cream shirt, dark trousers, black shoes, holds a blank clipboard. |
| `char_analyst.png` | A finance analyst, light skin, side-parted brown hair, light blue shirt with a coral tie, grey trousers, brown shoes, thin round glasses. |
| `char_professor.png` | An older professor, tan skin, grey hair and a short grey beard, mustard cardigan over a shirt, brown trousers, glasses. |
| `char_student.png` | A college student, light brown skin, black hair in a ponytail, amber t-shirt, denim jacket, a backpack, sneakers. |
| `char_kurta.png` | A woman in a teal kurta with coral trim and cream leggings, warm brown skin, long black hair in a braid, small earrings, flat sandals. |
| `char_saree.png` | An older woman in a coral and amber saree, medium brown skin, grey hair in a bun, small round glasses. |
| `char_curator.png` | A museum guide, dark skin, short locs, a plum vest over a cream shirt, a teal scarf, holds a pointer stick. |

---

## 3. Props (sprites)

**Rules for every prop (paste this block after each prompt):**

> A single object, flat 2D vector matching the attached style anchor, same palette, no outlines, three-quarter front
> view, centred, filling about 80% of a square 1024×1024 canvas. Transparent background (PNG with alpha) all the way to
> the edges: no frame, no border, no panel behind the object, no shadow on the ground. Do not copy the colour swatches
> from the reference. No text, no letters, no numbers, no logos.

Files: `prop_<name>.png`. One image per prop. If your tool supports it, ask for 4 at once in a 2×2 grid on a
transparent background, and I'll cut them apart.

### Tech and system design
| File | Object |
|---|---|
| `prop_laptop.png` | an open laptop with a blank glowing teal screen |
| `prop_phone.png` | a smartphone with a blank glowing screen |
| `prop_server.png` | a single server box with small teal and amber lights |
| `prop_database.png` | a database symbol: a stack of three rounded cylinders |
| `prop_cloud.png` | a soft rounded cloud shape, teal with a lighter top |
| `prop_loadbalancer.png` | a flat rounded box with one input port and three output ports |
| `prop_queue.png` | a large clear glass tube lying horizontally, filling the width of the canvas, holding a row of six chunky rounded blocks in coral, amber, teal and plum, like messages waiting in line |
| `prop_router.png` | a small network router with two short antennas |

### Finance
| File | Object |
|---|---|
| `prop_coins.png` | a stack of gold coins (no symbols on them) |
| `prop_notes.png` | a fanned bundle of plain green banknotes (no writing or faces) |
| `prop_piggybank.png` | a coral piggy bank |
| `prop_wallet.png` | a brown leather wallet |
| `prop_card.png` | a plain teal bank card with a chip |
| `prop_chart_board.png` | a presentation board on an easel with a rising bar chart (bars only, no numbers) |
| `prop_calculator.png` | a calculator with blank keys |
| `prop_receipt.png` | a long paper receipt with grey lines instead of text |

### Psychology and everyday life
| File | Object |
|---|---|
| `prop_brain.png` | a stylised soft pink brain |
| `prop_thought_bubble.png` | an empty cream thought bubble with small trailing circles |
| `prop_notebook.png` | an open notebook with grey lines |
| `prop_lightbulb.png` | a glowing amber light bulb |
| `prop_hourglass.png` | an hourglass with amber sand |
| `prop_mug.png` | a coffee mug with steam |
| `prop_sofa.png` | a small two-seat plum sofa |
| `prop_bed.png` | a single bed with a coral blanket, side view |

### History and general knowledge
| File | Object |
|---|---|
| `prop_scroll.png` | a partly unrolled parchment scroll with grey lines instead of text |
| `prop_old_book.png` | a thick old leather book, closed |
| `prop_globe.png` | a desk globe with simple continent shapes |
| `prop_map.png` | an old folded paper map with simple shapes, no names |
| `prop_compass.png` | a brass navigation compass |
| `prop_column.png` | a short ancient stone column |
| `prop_pot.png` | an ancient clay pot with a simple band pattern |
| `prop_crown.png` | a simple gold crown |
| `prop_magnifier.png` | a magnifying glass |
| `prop_flag.png` | a plain flag on a pole, single teal colour, no symbols |

### General
| File | Object |
|---|---|
| `prop_whiteboard.png` | a standing whiteboard on wheels, blank |
| `prop_clipboard.png` | a clipboard with a blank sheet |
| `prop_speech_bubble.png` | an empty rounded speech bubble |
| `prop_arrow.png` | a thick curved coral arrow |
| `prop_calendar.png` | a desk calendar with a blank page |
| `prop_envelope.png` | a closed cream envelope |
| `prop_desk.png` | a simple office desk, front view |
| `prop_trophy.png` | a gold trophy cup |

---

## 4. After generating

1. Drop the files into `designs/film-assets/` with the names above. Partial sets are fine; the minimum useful set is
   the style anchor, the 4 backgrounds, 3–4 characters and about 10 props for the topics you want to test.
2. I then:
   - **Backgrounds:** crop each to 9:16 and teach the engine to draw it as the stage (floor height, where people can
     stand, camera limits).
   - **Characters:** turn each sheet into a rig colour scheme and outfit (hair, glasses, tie, lanyard, saree or kurta
     drape).
   - **Props:** trim the transparent edges and register them so `film.js` can place, hold and animate them.
   - **Prompt:** update the film prompt to choose a background, cast and props that fit the topic, and to vary them
     between a topic's lessons.
3. **Local test:** render the same 3 lessons (system design, finance, history) with the old and the new assets on
   your Mac, and compare them side by side before anything goes to the server.
