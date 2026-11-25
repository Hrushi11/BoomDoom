# Engine toolkit

Reuse this. Writing the film is only `film.js` and a few lines of `audio_mix.py`.

## Film structure
`film.js` sets a global `FILM`, then lists sound events:

```js
FILM={dur:30,cuts:[0,90,150,...,900],fadeIn:.5,fadeOut:.6,
 cam(shot,u,t){return {x,y,z,b:[...]}},   // u is 0..1 through the shot
 draw(t,cam,shot,u){...},                 // paints layers, then composites
 warm(t){return 0},                        // optional golden grade 0..1
 plants(){return {name:walk.plants()}}};  // footfall times for the mix
say(3.2,'door');                           // sound event on the picture's clock
```

`cuts` are frames at 30 fps. The first is 0 and the last equals `dur*30`. All pose values are pure functions of `t`.

## Core (`core.js`)
- `layer(name,cam,parallax)` clears a named layer and returns its context with the camera applied.
- `comp([[name,blur,alpha],...])` draws layers to the screen, bottom first. `blit(ctx,L.name,blur)` adds another.
- `reflect(name,floorY,cam)` adds a wet-floor reflection.
- `kf`, `lerp`, `ease`, `easeIO`, `seg`, `clamp`, `rng(seed)`.
- `report(id,p)` and `contact(name,a,b)` feed the audit. `REPORT.sym=1` marks a symbolic frame.

## Characters (`chars.js`)
`HERO, FRIEND, WOMAN, STRANGER, GUEST_A, GUEST_B`, and the newer cast (prefer these):

| Name | Look | Fits |
|---|---|---|
| `ENGINEER` | teal hoodie, curly hair, lanyard and badge | tech, system design |
| `INTERVIEWER` | plum blazer over a cream shirt, short hair | interviews, careers, business |
| `ANALYST` | light blue shirt, coral tie, round glasses | finance, data, office |
| `PROFESSOR` | mustard cardigan, grey hair and beard, glasses | any explainer, science, history |
| `STUDENT` | denim jacket over an amber tee, ponytail, backpack | learner, the viewer's stand-in |
| `KURTA` | teal kurta with coral trim, long braid, earrings | everyday life, family, money |
| `SAREE` | coral saree with an amber sash, grey bun, glasses | grandmother, wisdom, savings |
| `CURATOR` | plum vest, cream shirt, teal scarf, locs | history, museum; give her `pointerStick` |

Add new ones by copying a colour object. Optional keys: `hair`, `coat`, and (drawn on the rig, so they follow every
pose and walk) `hairStyle` ('short','curly','side','ponytail','braid','bun','locs'), `beard`, `specs` (round glasses),
`earrings`, `tie`, `lanyard`+`badge`, `strings`, `scarf`, `backpack`, `outfit` ('shirt','blazer','cardigan','vest',
'jacket' open over `shirt`; 'kurta' with `trim`; 'saree' with `sash`), `sleeve`, `sleeveEdge`, `shortSleeve`, `cuff`.

`actor(x,id,CHAR,pose,cam,rimColour,rimSide)` draws with rim light and reports to the audit. It returns the pose, with `p._h` holding hands. `h2w(p,'r')` gives a hand's world position and `w2l(p,[x,y])` converts back.

Pose keys are listed at the top of `drawPerson`. Seated figures use `noLegs:true` behind a table. Standing on floor line `y`: `y:standY(y,s)`.

## Motion (`motion.js`)
```js
const w=makeWalk({pts:[[x0,y0],[x1,y1]],t0:2,t1:5,steps:6,scale:cafeScale}); // or s:.66
function who(t){const q=w.at(t),p={x:q.c[0],y:standY(q.c[1],q.s),s:q.s,face:1,breath:t*2};return walkPose(p,q)}
```
`walkPose` gives planted feet and swinging arms, and sets `p.walk`. Pass `{arms:false}` when hands hold something. Also: `ramp`, `blinkAt(t,[times])`, `talk(t,a,b)` for a speaking mouth.

## Props (`props.js`)
`drawUmbrella` with `umbrellaGeo`, `drawCup`, `saucer`, `cupGrip`, `gripHand`, `openHand`, `sideTable`, `sideChair`, `drawGun`, `drawBullet`, `muzzle`, `drawClock`, `drawRain`, `rainHits`, `groundHits`, `softShadow`.

## Sets (`sets/`)
- `street.js`: `streetSky(x,sun)` at parallax .35, `streetBuildings` at .65, `streetGround`, `stopPole`, `drawBus(x,X)`. Floor line `GY`=930, with `s` about .66.
- `cafe.js`: `cafeOutside`, `cafeRain`, `cafeWall`, `cafeFloor`, `pendant`, `doorLeaf`, `doorLight`, `backTable`, `cafeRefl`, `cafeScale(y)`, `DOOR`. The room spans x -900 to 3700 and the floor runs from 642 down. Any shot at any x works, because it is one room.
- `room.js`: `drawRoom(x,night,t)`, `drawRoomTable` for waist-up scenes across a table, plus `drawLift` and `drawHall`. Seated figures: `x` about 650 and 1270, `y` 560, `s` 1, drawn before the table.
- `symbolic.js`: `drawVoid`, `slab`, `drawSils`, `makeChunks`, `drawChunks`, and silhouettes. See `symbolic-beats.md`.

New sets follow the same rule: flat shapes, the palette, one world. Add sets, and do not restyle the existing ones.

## Painted stages and picture props (`assets.js`) — prefer these
Four painted portrait rooms and 42 prop pictures, drawn as images; people are still the rig. The world of every stage
is the picture at 0,0 to 1536,2304. `templates/film_assets_demo.js` is a working example of all of it.

| Stage | Picture | Board | Use for |
|---|---|---|---|
| `meeting_room` | whiteboard wall, cabinet, window, chairs and table at right | whiteboard | system design, tech, interviews, finance |
| `server_room` | rack corridor, ceiling lamp, wall screen | dark screen (glowing text) | databases, networks, scaling, cloud |
| `living_room` | bookshelf, window, lamp, sofa at right, rug | none (a cream card is drawn) | money at home, psychology, habits |
| `museum_hall` | two exhibit panels, arch, vase case, bust | left panel (`panelL`, also `panelR`) | history, general knowledge |

- `stage(x,st)` then `stageLife(x,st,t)` on the back layer (blinking rack LEDs, lamp glow, dust in spotlights, city windows).
- `stageCam(st,{x,y,z,...})` clamps a camera so the frame never leaves the picture. Portrait wide: `{x:768,y:1250,z:.85}`;
  medium two-shot z about 1.1; close-up 1.4–1.8 centred on a face.
- People: `Object.assign({face:1,breath:t*2,...},stand(st,fx,fy))` gives `x,y,s` for feet at (fx,fy). Scale follows depth
  (eyes always on the horizon): s is about 1 at the back wall and 1.6 at the front. Keep feet inside `STAGES[st].floor`
  (y range) and `.x` (x range); `STAGES[st].spots` has good points (`left`,`centre`,`right`,`front`). Walk with
  `makeWalk({pts,t0,t1,steps,scale:stageWalk(st)})`. Draw people further back first. Add `softShadow` under feet.
- The rooms are to scale, so people in front of the board hide it: put them at `spots.left`/`spots.right` when the
  board matters, or give the board its own shot.
- Board overlays: `boardText(x,st,['BIG','WORDS'],t,{t0,cps})` writes on the board; `boardDiagram(x,st,[[label,u,v],...],
  [[i,j],...],t,{t0,step,flow:true})` draws boxes and arrows (u,v 0..1 across the board) with packets flowing;
  `barChart(x,st,[.3,.6,.9],t,{t0,labels})`. `o.region` picks another region or a rect `{x,y,w,h}`.
- Props (`PROPS` lists each with its natural height `h` at s=1, `kind` and `grip`): laptop, phone, server, database,
  cloud, loadbalancer, queue, router, coins, notes, piggybank, wallet, card, chart_board, calculator, receipt, brain,
  thought_bubble, notebook, lightbulb, hourglass, mug, sofa, bed, scroll, old_book, globe, map, compass, column, pot,
  crown, magnifier, flag, whiteboard, clipboard, speech_bubble, arrow, calendar, envelope, desk, trophy.
  - `floorProp(x,st,name,fx,fy,{scale})` stands one on the floor with perspective and a shadow. Draw it before people
    who stand behind it in depth order (smaller fy first).
  - `propInHand(x,p,'r'|'l',name,h,{rot,flip})` after `actor()`: the prop's grip goes to the hand, a gripping hand is drawn
    over it and `contact()` is recorded. Animate `p.hands` and the prop follows (tuck the arm to hold it close).
  - `prop(x,name,px,py,h,{rot,flip,alpha,s})` draws anywhere (icons such as brain, cloud, database, thought_bubble are
    centred on the point); returns its box `{x,y,w,h,cx,cy,top}`.
  - `pointerStick(x,p,k,angle,len)` gives a pointer to point at the board; returns the tip.
  - `packet(x,a,b,t,{n,period,arc,t0,t1})` sends glowing data packets from point a to b (e.g. server to laptop).
  - `label(x,'CACHE',cx,cy,{size})` puts a small tag on a prop.

## Patterns worth copying from the starter film
- The door entrance: draw the walker's own layer behind the wall until `inside`, then over it.
- `contact()` on every held prop.
- Cup and hand driven from one `cupAt(t)` function.

## Scripts
| Script | Does |
|---|---|
| `doctor.py` | Checks and installs what is needed |
| `new_film.py <folder> [--3d]` | Creates a film folder |
| `stills.py <folder> <out.jpg> <times>` | Contact sheet of chosen times |
| `build.py <folder> --out x.mp4 [--shots ...] [--audio-only] [--jobs N] [--crf N]` | Everything from render to audit |
| `storyboard.py <folder>` | Numbered preview of every shot, before rendering |
| `audit.py <folder> <file.mp4>` | The audit alone |
| `align.py <folder> <mp3> <script.txt>` | Word timings from a voiceover |
| `bundle_player.py <folder>` | One-file HTML player with WebM export |
