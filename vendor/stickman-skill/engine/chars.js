// ===== STICKMAN ENGINE: characters. One rig, fixed proportions, so identity never drifts. =====
const INK='#1c1a2b';
const HERO={skin:'#F8E9CC',cloth:'#FF6B5E',hood:'#E4493F',hand:'#F8E9CC',face:INK,leg:'#2C2A49',shoe:'#17141F',finger:INK};
const FRIEND={skin:'#17141F',cloth:'#F6B73C',hood:'#D9962A',hand:'#17141F',face:'#FFFFFF',leg:'#3A2F55',shoe:'#17141F',finger:'#5b5468'};
const WOMAN={skin:'#E9C6A0',cloth:'#2DB2AE',hood:'#187E86',hand:'#E9C6A0',face:INK,leg:'#2C2A49',shoe:'#17141F',finger:INK,hair:'#3A2230'};
const STRANGER={skin:'#C9C3CF',cloth:'#2A2536',hood:'#191622',hand:'#C9C3CF',face:'#17141F',leg:'#17141F',shoe:'#0f0d15',finger:'#17141F',coat:true};
const GUEST_A={skin:'#17141F',cloth:'#7A56A0',hood:'#5C3F80',hand:'#17141F',face:'#FFFFFF',leg:'#2C2A49',shoe:'#17141F',finger:'#5b5468'};
const GUEST_B={skin:'#E9C7A2',cloth:'#3A5C9A',hood:'#223A6C',hand:'#E9C7A2',face:INK,leg:'#2C2A49',shoe:'#17141F',finger:INK};
// New cast (BoomDoom), drawn by the same rig. Extra optional keys, all handled in drawPerson's hooks below:
//  hairStyle 'short'|'curly'|'side'|'ponytail'|'braid'|'bun'|'locs' (colour: hair), hairTie, beard, specs (round glasses),
//  earrings, tie, lanyard+badge, strings (hoodie cords), scarf, backpack,
//  outfit 'shirt'|'blazer'|'cardigan'|'vest'|'jacket' (open front shows `shirt`) | 'kurta' (long tunic, `trim`) | 'saree'
//  (skirt to the ankles + `sash` over one shoulder), sleeve/sleeveEdge (arm colours when not cloth/hood), shortSleeve, cuff.
const ENGINEER={skin:'#A8683F',cloth:'#2F7F88',hood:'#24656E',hand:'#A8683F',face:INK,leg:'#3B2B52',shoe:'#EEDDC0',finger:INK,hair:'#2E2340',hairStyle:'curly',lanyard:'#3A2F55',badge:'#F2E3C8',strings:'#CFCBD8'};
const INTERVIEWER={skin:'#7C4A2A',cloth:'#5E3457',hood:'#48264A',hand:'#7C4A2A',face:INK,leg:'#2F2238',shoe:'#1E1A22',finger:INK,hair:'#2E2236',hairStyle:'short',outfit:'blazer',shirt:'#F4E4C6'};
const ANALYST={skin:'#F7C48E',cloth:'#AFC5E0',hood:'#8DA6C7',hand:'#F7C48E',face:INK,leg:'#5C5F72',shoe:'#7A4536',finger:INK,hair:'#6E4436',hairStyle:'side',outfit:'shirt',shirt:'#C9D8EC',tie:'#EA6F57',specs:'#7A4650'};
const PROFESSOR={skin:'#D99A5C',cloth:'#E0A33A',hood:'#C2872A',hand:'#D99A5C',face:INK,leg:'#6E4636',shoe:'#4A2C40',finger:INK,hair:'#A29CA6',hairStyle:'side',beard:'#A29CA6',specs:'#5A2F3A',outfit:'cardigan',shirt:'#F6DCAE'};
const STUDENT={skin:'#C27A45',cloth:'#2F6488',hood:'#244F6C',hand:'#C27A45',face:INK,leg:'#4A3462',shoe:'#2F6488',finger:INK,hair:'#2A2238',hairStyle:'ponytail',hairTie:'#6A4A8A',outfit:'jacket',shirt:'#F6A83C',backpack:'#4E3A66',cuff:'#3F7DA3'};
const KURTA={skin:'#C47A45',cloth:'#2E8585',hood:'#236B6C',hand:'#C47A45',face:INK,leg:'#F5DDB5',shoe:'#8A4A30',finger:INK,hair:'#2A2238',hairStyle:'braid',earrings:'#F6B73C',outfit:'kurta',trim:'#E8704A',cuff:'#E8704A'};
const SAREE={skin:'#B5703F',cloth:'#E8604A',hood:'#C94E3C',hand:'#B5703F',face:INK,leg:'#C94E3C',shoe:'#6E3A52',finger:INK,hair:'#A39AA4',hairStyle:'bun',specs:'#6A3A30',outfit:'saree',sash:'#F59A3C',shortSleeve:true};
const CURATOR={skin:'#7E4A33',cloth:'#5E3A66',hood:'#4A2C52',hand:'#7E4A33',face:'#140E16',leg:'#3A2440',shoe:'#3A1E30',finger:'#140E16',hair:'#3A2448',hairStyle:'locs',outfit:'vest',shirt:'#F3DDB8',sleeve:'#F3DDB8',sleeveEdge:'#D8BF98',scarf:'#2E8A8A'};
// rig units: full figure height is about 800 at s=1
const UA=102,FA=98,HR=96,TH=235,THIGH=130,SHIN=130,STANDY=TH+250,TW=215;
/* pose p: x,y,s  face(1 right,-1 left)  breath  look lookY blink brow sad mouth mouthW open wide laugh tilt headX headY
   hands{l,r}(rig units) showH{l,r} tuck{l,r} walk feet{l,r} noLegs noFace bend(hip lean radians)
   accessories: hat glasses glassY glassR  coat flutter: flut time
   Idle arms end with no hand, on purpose. Show a hand only when it is doing something. */
function drawPerson(x,ch,p){x.save();x.translate(p.x,p.y);x.scale(p.s,p.s);const br=Math.sin(p.breath||0)*3,face=p.face||1;
 const ft=p.feet||{l:[-48,STANDY],r:[48,STANDY]};
 if(!p.noLegs)for(const k of (face<0?['r','l']:['l','r'])){const hip=[k=='l'?-46:46,TH-14],f=ft[k],r=ik(hip,f,THIGH,SHIN,face<0?1:-1);limb(x,hip,r.e,62,50,ch.leg);limb(x,r.e,r.h,50,38,ch.leg);
  x.fillStyle=ch.shoe;x.beginPath();x.roundRect(r.h[0]-(face<0?58:20),r.h[1]+6,78,28,13);x.fill()}
 if(ch.outfit=='saree'&&!p.noLegs)sareeSkirt(x,ch,ft);
 if(p.bend){x.translate(0,TH-14);x.rotate(p.bend);x.translate(0,-(TH-14))}
 charBack(x,ch,br);
 if(ch.outfit)outfitBody(x,ch,p,br,ft);else{x.fillStyle=ch.hood;x.beginPath();x.ellipse(-8,-30+br,98,70,0,0,7);x.fill();
 if(ch.coat){const fl=p.flut||0,fa=Math.sin((p.time||0)*5.5),fb=Math.sin((p.time||0)*4.1+1),hem=TH+150;
  x.fillStyle=ch.cloth;x.beginPath();x.moveTo(-TW/2,70);x.quadraticCurveTo(-TW/2,-5,-TW/2+60,-8+br);x.lineTo(TW/2-60,-8+br);x.quadraticCurveTo(TW/2,-5,TW/2,70);
  x.quadraticCurveTo(TW/2+8,TH,TW/2+34+fl*(20+fa*20),hem+fb*fl*10);x.quadraticCurveTo(50,hem+24+fa*fl*12,0,hem+8);x.quadraticCurveTo(-60,hem+24-fb*fl*12,-TW/2-34-fl*(46+fb*20),hem-fa*fl*12);x.quadraticCurveTo(-TW/2-8,TH,-TW/2,70);x.closePath();x.fill();
  x.strokeStyle=ch.hood;x.lineWidth=7;x.lineCap='round';x.beginPath();x.moveTo(0,10);x.lineTo(0,hem);x.stroke();x.lineWidth=16;x.beginPath();x.moveTo(-TW/2+6,TH-20);x.lineTo(TW/2-6,TH-20);x.stroke();
  x.fillStyle=ch.hood;x.beginPath();x.moveTo(-70,-10);x.lineTo(0,70);x.lineTo(70,-10);x.lineTo(30,-16);x.lineTo(0,18);x.lineTo(-30,-16);x.closePath();x.fill()}
 else{x.fillStyle=ch.cloth;x.beginPath();x.moveTo(-TW/2,TH);x.lineTo(-TW/2,60);x.quadraticCurveTo(-TW/2,-5,-TW/2+60,-8+br);x.lineTo(TW/2-60,-8+br);x.quadraticCurveTo(TW/2,-5,TW/2,60);x.lineTo(TW/2,TH);x.quadraticCurveTo(0,TH+14,-TW/2,TH);x.closePath();x.fill();
  x.strokeStyle=ch.hood;x.lineWidth=14;x.lineCap='round';x.beginPath();x.arc(0,-8+br,52,.15*Math.PI,.85*Math.PI);x.stroke();x.lineWidth=6;x.globalAlpha=.55;x.beginPath();x.moveTo(-60,TH-90);x.lineTo(60,TH-90);x.stroke();x.globalAlpha=1}}
 charFront(x,ch,br);
 const sh={l:[-TW/2+34,34+br],r:[TW/2-34,34+br]},hd=p.hands||{};p._h={};p._over={};p._e={};
 for(const k of ['l','r']){const s=sh[k],tgt=hd[k]||[s[0]+(k=='l'?-24:24),s[1]+UA+FA-14],side=k=='l'?1:-1;
  // walking: elbows trail the direction of travel. A tucked arm folds toward the body to hold something close.
  const dir=(p.tuck&&p.tuck[k])?-side:(p.walk?(face>0?1:-1):side),r=ik(s,tgt,UA,FA,dir);
  armPaint(x,ch,s,r);
  if(p.showH&&p.showH[k]){const a=Math.atan2(r.h[1]-r.e[1],r.h[0]-r.e[0]);x.save();x.translate(r.h[0],r.h[1]);x.rotate(a);x.fillStyle=ch.hand;x.beginPath();x.roundRect(-4,-12,32,24,12);x.fill();x.restore()}
  p._h[k]=r.h;p._over[k]=r.over;p._e[k]=r.e}
 x.save();x.translate(p.headX||0,-HR-20+br+(p.headY||0));x.rotate(p.tilt||0);
 if(ch.hairStyle)hairBack(x,ch);else if(ch.hair){x.fillStyle=ch.hair;x.beginPath();x.moveTo(-HR-16,44);x.quadraticCurveTo(-HR-24,-HR-22,0,-HR-16);x.quadraticCurveTo(HR+24,-HR-22,HR+16,44);x.quadraticCurveTo(HR+8,66,HR-18,62);x.lineTo(-HR+18,62);x.quadraticCurveTo(-HR-8,66,-HR-16,44);x.fill()}
 x.fillStyle=ch.skin;x.beginPath();x.arc(0,0,HR,0,7);x.fill();
 if(ch.hairStyle)hairFront(x,ch);else if(ch.hair){x.fillStyle=ch.hair;x.beginPath();x.moveTo(-HR-4,-6);x.quadraticCurveTo(-HR+4,-HR-10,10,-HR-6);x.quadraticCurveTo(HR+4,-HR+4,HR+4,-6);x.quadraticCurveTo(HR-16,-52,44,-58);x.quadraticCurveTo(-30,-44,-HR-4,-6);x.fill()}
 if(ch.beard||ch.earrings)headExtras(x,ch);
 if(!p.noFace){const lk=p.look||0,ly=p.lookY||0,fx=lk*46,fy=ly*18+6;x.strokeStyle=ch.face;x.fillStyle=ch.face;x.lineCap='round';const bl=p.blink||0;
  for(let i=0;i<2;i++){const e=(i?30:-30)+fx*(i?1.1:.9);x.lineWidth=8;
   if(p.laugh){x.beginPath();x.arc(e,fy+6,13,Math.PI*1.1,Math.PI*1.9);x.stroke()}
   else if(bl>.5){x.beginPath();x.moveTo(e-12,fy);x.lineTo(e+12,fy);x.stroke()}
   else{x.beginPath();x.ellipse(e,fy,7,13*(1-bl*1.6)+2+(p.wide||0)*4,0,0,7);x.fill()}
   // brows: positive sad lifts the inner end. Negative sad reads angry.
   const by=fy-30-(p.brow||0)*7,sl=(p.sad||0)*8*(i?-1:1);x.lineWidth=6;x.beginPath();x.moveTo(e-14,by+sl);x.lineTo(e+14,by-sl);x.stroke()}
  const m=p.mouth||0,my=fy+44,mx=fx*.95,mw=p.mouthW||26;x.lineWidth=7;x.beginPath();
  if(p.open>.02){x.ellipse(mx,my+6,14+p.open*7,5+p.open*17,0,0,7);x.fill()}else{x.moveTo(mx-mw,my-m*5);x.quadraticCurveTo(mx,my+m*22,mx+mw,my-m*5);x.stroke()}
  if(p.glasses){x.save();x.translate(0,p.glassY||0);x.rotate(p.glassR||0);x.fillStyle='#0d0b14';for(const s of [-1,1]){x.beginPath();x.roundRect(fx+s*32-26,fy-15,52,31,12);x.fill()}x.strokeStyle='#0d0b14';x.lineWidth=5;x.beginPath();x.moveTo(fx-8,fy-6);x.lineTo(fx+8,fy-6);x.moveTo(fx-58,fy-6);x.lineTo(-92,fy-14);x.moveTo(fx+58,fy-6);x.lineTo(92,fy-14);x.stroke();x.strokeStyle='rgba(255,255,255,.28)';x.lineWidth=3;x.beginPath();x.moveTo(fx-48,fy+7);x.lineTo(fx-34,fy-7);x.stroke();x.restore()}
  if(ch.specs&&!p.glasses)specs(x,ch,fx,fy)}
 if(p.hat){x.fillStyle='#14111B';x.beginPath();x.ellipse(0,-68,150,20,0,0,7);x.fill();x.beginPath();x.moveTo(-82,-70);x.quadraticCurveTo(-78,-165,-30,-168);x.quadraticCurveTo(0,-154,30,-168);x.quadraticCurveTo(78,-165,82,-70);x.closePath();x.fill();x.fillStyle='#8E2F2C';x.fillRect(-81,-94,162,18);x.strokeStyle='rgba(255,190,150,.32)';x.lineWidth=3;x.beginPath();x.moveTo(-140,-62);x.quadraticCurveTo(0,-48,140,-62);x.stroke()}
 x.restore();x.restore()}
// ---- drawPerson hooks for the new cast. Each is a no-op for the original six, which draw exactly as before. ----
const _poly=(x,pts,col)=>{x.fillStyle=col;x.beginPath();pts.forEach((q,i)=>i?x.lineTo(q[0],q[1]):x.moveTo(q[0],q[1]));x.closePath();x.fill()};
// torso from the shoulders down to hem y, widening by flare at the hem; sway shifts the hem sideways (walking)
function torsoPath(x,br,hem,flare=0,sway=0){const w=TW/2;x.beginPath();x.moveTo(-w-flare+sway,hem);x.lineTo(-w,60);x.quadraticCurveTo(-w,-5,-w+60,-8+br);x.lineTo(w-60,-8+br);x.quadraticCurveTo(w,-5,w,60);x.lineTo(w+flare+sway,hem);x.quadraticCurveTo(sway,hem+14,-w-flare+sway,hem);x.closePath()}
function charBack(x,ch,br){if(ch.backpack){x.fillStyle=ch.backpack;x.beginPath();x.roundRect(-TW/2-18,16+br,TW+36,200,44);x.fill()}}
function outfitBody(x,ch,p,br,ft){const o=ch.outfit,sh=ch.shirt||ch.cloth,sway=p.walk&&ft?clamp((ft.l[0]+ft.r[0])/2*.1,-14,14):0;
 x.fillStyle=ch.skin;x.beginPath();x.roundRect(-30,-70+br,60,84,22);x.fill();                       // neck
 if(o=='kurta'){const hem=TH+100;torsoPath(x,br,hem,20,sway);x.fillStyle=ch.cloth;x.fill();x.save();x.clip();x.fillStyle=ch.trim;x.fillRect(-200,hem-22,400,40);x.restore();
  x.strokeStyle=ch.trim;x.lineWidth=12;x.lineCap='round';x.beginPath();x.arc(0,-14+br,40,.12*Math.PI,.88*Math.PI);x.stroke();x.beginPath();x.moveTo(0,26+br);x.lineTo(0,92+br);x.stroke();
  x.strokeStyle=ch.hood;x.lineWidth=5;x.globalAlpha=.5;x.beginPath();x.moveTo(-70,TH-60);x.lineTo(-74-sway*.3,hem-30);x.moveTo(70,TH-60);x.lineTo(74+sway*.3,hem-30);x.stroke();x.globalAlpha=1;return}
 torsoPath(x,br,TH);x.fillStyle=ch.cloth;x.fill();
 if(o=='saree'){x.save();torsoPath(x,br,TH);x.clip();_poly(x,[[TW/2-8,-12+br],[TW/2-70,-12+br],[-TW/2-10,TH-24],[-TW/2-10,TH+30],[-TW/2+50,TH+30]],ch.sash);x.restore();return}
 if(o=='blazer'||o=='jacket'){_poly(x,[[-34,-6+br],[34,-6+br],[30,TH],[-30,TH]],sh);
  const lap=o=='blazer'?ch.hood:ch.cuff||ch.hood;_poly(x,[[-34,-8+br],[-10,96],[-30,TH],[-46,TH]],ch.cloth);_poly(x,[[34,-8+br],[10,96],[30,TH],[46,TH]],ch.cloth);
  x.strokeStyle=lap;x.lineWidth=7;x.lineCap='round';x.beginPath();x.moveTo(-36,-6+br);x.lineTo(-12,94);x.lineTo(-58,36);x.moveTo(36,-6+br);x.lineTo(12,94);x.lineTo(58,36);x.stroke();
  if(o=='jacket'){x.lineWidth=5;x.beginPath();x.roundRect(-84,70,40,30,6);x.roundRect(44,70,40,30,6);x.stroke();x.fillStyle=lap;for(const s of [-1,1])for(const y of [120,170]){x.beginPath();x.arc(s*42,y,5,0,7);x.fill()}}}
 else if(o=='cardigan'||o=='vest'){const vy=o=='vest'?132:118;_poly(x,[[-40,-8+br],[40,-8+br],[0,vy]],sh);x.fillStyle=ch.hood;for(const y of (o=='vest'?[160,200]:[150,196])){x.beginPath();x.arc(0,y,8,0,7);x.fill()}
  if(o=='vest'){x.strokeStyle=ch.hood;x.lineWidth=4;x.beginPath();x.moveTo(0,vy);x.lineTo(0,TH);x.stroke()}}
 if(o!='vest'){_poly(x,[[-40,-10+br],[-2,20+br],[-24,34+br]],o=='shirt'?ch.shirt:sh);_poly(x,[[40,-10+br],[2,20+br],[24,34+br]],o=='shirt'?ch.shirt:sh)}}  // collar
function sareeSkirt(x,ch,ft){const L=Math.min(ft.l[0],ft.r[0])-54,R=Math.max(ft.l[0],ft.r[0])+54,hy=Math.max(ft.l[1],ft.r[1])+2,w=TW/2+6,top=TH-46;
 x.save();x.beginPath();x.moveTo(-w,top);x.lineTo(w,top);x.quadraticCurveTo(w+8,(top+hy)/2,Math.max(R,w+6),hy);x.quadraticCurveTo(0,hy+12,Math.min(L,-w-6),hy);x.quadraticCurveTo(-w-8,(top+hy)/2,-w,top);x.closePath();
 x.fillStyle=ch.cloth;x.fill();x.clip();x.fillStyle=ch.hood;x.globalAlpha=.35;x.fillRect(-6,top,12,hy-top);x.globalAlpha=1;
 _poly(x,[[-w-20,top+20],[-w+60,top+20],[-w-60,hy-120],[-w-80,hy-70]],ch.sash);x.fillStyle=ch.sash;x.fillRect(-400,hy-24,800,40);x.restore()}
function charFront(x,ch,br){x.lineCap='round';
 if(ch.strings){x.strokeStyle=ch.strings;x.lineWidth=7;x.beginPath();x.moveTo(-20,34+br);x.lineTo(-22,104);x.moveTo(20,34+br);x.lineTo(22,104);x.stroke()}
 if(ch.backpack){x.strokeStyle=ch.backpack;x.lineWidth=20;x.beginPath();x.moveTo(-66,-4+br);x.lineTo(-74,190);x.moveTo(66,-4+br);x.lineTo(74,190);x.stroke()}
 if(ch.tie){_poly(x,[[-13,6+br],[13,6+br],[9,26+br],[-9,26+br]],ch.tie);_poly(x,[[-9,24+br],[9,24+br],[18,150],[0,174],[-18,150]],ch.tie)}
 if(ch.lanyard){x.strokeStyle=ch.lanyard;x.lineWidth=7;x.beginPath();x.moveTo(-46,-4+br);x.lineTo(-4,118);x.moveTo(46,-4+br);x.lineTo(4,118);x.stroke();x.fillStyle=ch.badge||'#F2E7D6';x.beginPath();x.roundRect(-26,114,52,68,8);x.fill();x.fillStyle=ch.lanyard;x.fillRect(-14,128,28,6)}
 if(ch.scarf){x.strokeStyle=ch.scarf;x.lineWidth=22;x.beginPath();x.arc(0,-20+br,42,.1*Math.PI,.9*Math.PI);x.stroke();_poly(x,[[0,22+br],[-30,96+br],[-6,90+br]],ch.scarf);_poly(x,[[0,22+br],[28,92+br],[6,98+br]],ch.scarf);x.fillStyle=ch.scarf;x.beginPath();x.arc(0,24+br,15,0,7);x.fill()}}
// arms: identical to before unless sleeve/sleeveEdge/shortSleeve/cuff are set
function armPaint(x,ch,s,r){const ed=ch.sleeveEdge||ch.hood,sl=ch.sleeve||ch.cloth;
 if(ch.shortSleeve){limb(x,r.e,r.h,48,40,ch.skin);limb(x,s,r.e,66,54,ed);limb(x,s,r.e,58,48,sl);return}
 limb(x,s,r.e,66,54,ed);limb(x,r.e,r.h,54,44,ed);limb(x,s,r.e,58,48,sl);limb(x,r.e,r.h,46,38,sl);
 if(ch.cuff){const c=[lerp(r.e[0],r.h[0],.8),lerp(r.e[1],r.h[1],.8)];limb(x,c,r.h,46,40,ch.cuff)}}
// hair, in head space (origin at the head centre, radius HR). Back parts go behind the head, front parts over it.
function hairBack(x,ch){const st=ch.hairStyle,P=Math.PI;x.fillStyle=ch.hair;
 if(st=='curly'){for(let i=0;i<12;i++){const a=P*(.96+i*.098);x.beginPath();x.arc(Math.cos(a)*(HR+4),Math.sin(a)*(HR+4)-2,32,0,7);x.fill()}return}
 if(st=='locs'){x.strokeStyle=ch.hair;x.lineCap='round';x.lineWidth=26;for(let i=0;i<11;i++){const a=P*(.95+i*.11),cx=Math.cos(a)*(HR-4),cy=Math.sin(a)*(HR-4),len=40+Math.abs(Math.cos(a))*70;x.beginPath();x.moveTo(cx,cy);x.lineTo(cx*1.16,cy+len);x.stroke()}return}
 const long=st!='short'&&st!='side',d=long?28:-8;x.beginPath();x.moveTo(-HR-10,d);x.quadraticCurveTo(-HR-16,-HR-22,0,-HR-14);x.quadraticCurveTo(HR+16,-HR-22,HR+10,d);x.closePath();x.fill();
 if(st=='bun'){x.beginPath();x.arc(0,-HR-18,40,0,7);x.fill()}
 if(st=='ponytail'){x.beginPath();x.moveTo(46,-92);x.bezierCurveTo(150,-120,170,-10,128,40);x.bezierCurveTo(150,70,120,96,96,92);x.bezierCurveTo(118,40,100,-20,66,-48);x.closePath();x.fill();x.fillStyle=ch.hairTie||ch.hair;x.beginPath();x.arc(60,-90,15,0,7);x.fill()}}
function hairFront(x,ch){const st=ch.hairStyle,P=Math.PI;x.fillStyle=ch.hair;x.beginPath();
 if(st=='short'||st=='curly'||st=='locs'){x.arc(0,0,HR+3,P*1.04,P*1.96);x.quadraticCurveTo(84,-60,34,-66);x.quadraticCurveTo(0,-58,-34,-66);x.quadraticCurveTo(-84,-60,-HR-1,-12)}
 else if(st=='side'){x.arc(0,0,HR+5,P,P*2);x.quadraticCurveTo(96,-58,62,-66);x.quadraticCurveTo(10,-40,-44,-66);x.quadraticCurveTo(-88,-44,-HR-5,0)}
 else if(st=='ponytail'){x.arc(0,0,HR+5,P*.97,P*2.03);x.quadraticCurveTo(86,-70,-6,-70);x.quadraticCurveTo(-40,-44,-74,-34);x.quadraticCurveTo(-92,-10,-HR-4,15)}
 else{x.arc(0,0,HR+5,P*.97,P*2.03);x.quadraticCurveTo(92,-62,2,-82);x.quadraticCurveTo(-92,-62,-HR-4,15)}           // centre part: braid, bun
 x.closePath();x.fill();
 if(st=='curly')for(const c of [[-80,-44],[-56,-66],[-24,-78],[12,-78],[46,-70],[76,-50]]){x.beginPath();x.arc(c[0],c[1],24,0,7);x.fill()}
 if(st=='side'){x.beginPath();x.arc(46,-HR+4,26,0,7);x.fill()}
 if(st=='locs'){x.strokeStyle=ch.hair;x.lineCap='round';x.lineWidth=22;x.beginPath();for(const s of [-1,1]){x.moveTo(s*(HR-6),-40);x.lineTo(s*(HR+2),34)}x.moveTo(-52,-70);x.lineTo(-58,-40);x.moveTo(-14,-78);x.lineTo(-12,-54);x.moveTo(30,-76);x.lineTo(40,-50);x.stroke()}
 if(st=='braid'){for(let i=0;i<6;i++){x.beginPath();x.ellipse(-82-i*2+(i%2?7:-7),74+i*40,26-i*1.6,25,(i%2?.35:-.35),0,7);x.fill()}x.fillStyle=ch.trim||ch.hair;x.beginPath();x.ellipse(-90,304,9,14,0,0,7);x.fill()}}
function headExtras(x,ch){if(ch.beard){x.fillStyle=ch.beard;x.beginPath();x.moveTo(-HR+1,-6);x.quadraticCurveTo(-HR+6,HR-4,0,HR+12);x.quadraticCurveTo(HR-6,HR-4,HR-1,-6);x.quadraticCurveTo(HR-22,36,36,40);x.quadraticCurveTo(0,28,-36,40);x.quadraticCurveTo(-HR+22,36,-HR+1,-6);x.fill()}
 if(ch.earrings){x.fillStyle=ch.earrings;for(const s of [-1,1]){x.beginPath();x.arc(s*(HR-2),34,10,0,7);x.fill()}}}
function specs(x,ch,fx,fy){x.strokeStyle=ch.specs;x.lineWidth=5.5;x.beginPath();for(const i of [0,1]){const e=(i?30:-30)+fx*(i?1.1:.9);x.moveTo(e+23,fy);x.arc(e,fy,23,0,7)}const a=-30+fx*.9+23,b=30+fx*1.1-23;x.moveTo(a,fy-4);x.quadraticCurveTo((a+b)/2,fy-10,b,fy-4);x.moveTo(-30+fx*.9-23,fy-4);x.lineTo(-HR+2,fy-10);x.moveTo(30+fx*1.1+23,fy-4);x.lineTo(HR-2,fy-10);x.stroke()}
// Rim light: a tinted, offset copy of the silhouette under the figure. Legs are skipped because rim on dark trousers reads as an outline.
function personRim(dst,ch,p,rimCol='rgba(255,196,150,1)',dx=4,dy=-1){const qr=Object.assign({},p,{noLegs:true}),t2=L.tmp2,tc=t2.getContext('2d');tc.setTransform(1,0,0,1,0,0);tc.globalCompositeOperation='source-over';tc.clearRect(0,0,W,H);tc.setTransform(dst.getTransform());drawPerson(tc,ch,qr);tc.setTransform(1,0,0,1,0,0);tc.globalCompositeOperation='source-in';tc.fillStyle=rimCol;tc.fillRect(0,0,W,H);tc.globalCompositeOperation='source-over';dst.save();dst.setTransform(1,0,0,1,0,0);dst.drawImage(t2,dx,dy);dst.restore();drawPerson(dst,ch,p);return p}
// actor(x,id,ch,p,cam): rim + draw + report for audit. Use this for every character.
function actor(x,id,ch,p,cam,rim='rgba(255,196,150,1)',side=1){p._ch=ch;personRim(x,ch,p,rim,side*(3+1.4*(cam?cam.z:1)),-1);report(id,p);return p}
// rig-space hand point to world, and back
const h2w=(p,k)=>[p.x+p._h[k][0]*p.s,p.y+p._h[k][1]*p.s];
const w2l=(p,w)=>[(w[0]-p.x)/p.s,(w[1]-p.y)/p.s];
