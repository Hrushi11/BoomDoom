// ===== STICKMAN ENGINE: image stages and prop sprites (BoomDoom). =====
// Four painted backgrounds and 42 prop pictures, drawn with drawImage. People stay the code rig (chars.js).
// The pictures come from data/assets_data.js (ASSET_DATA, WebP data URIs made by tools/pack_film_assets.py);
// bootFilm() waits for ASSETS_READY, so every image is decoded before the first frame.
const ASSETS={bg:{},prop:{}};
window.ASSETS_READY=typeof ASSET_DATA=='undefined'?null:Promise.all(Object.entries(ASSET_DATA).flatMap(([k,g])=>
 Object.entries(g).map(([n,d])=>{const im=new Image();im.src=d.src;ASSETS[k][n]=im;return im.decode().catch(()=>{})})));

/* ---- STAGES: one painted room per stage, drawn at world rect 0,0 .. w,h (portrait, 1536 x 2304).
   horizon: world y of eye level. A standing person's eyes sit on it at any depth, so scale follows from the floor y:
            stageScale(st,fy) = (fy-horizon)/EYE_H. Use stand(st,fx,fy) to get the pose x,y,s directly.
   wall:    floor meets the back wall.  floor:[nearest-to-wall, nearest-to-camera] feet y for people (nearer = bigger:
            s is about 1 at the back and 1.6 at the front; a person is about 740*s tall).
   The rooms are painted to real scale, so a person standing in front of the board hides part of it: when the board
   matters, put people at spots.left / spots.right (beside it), or frame the board in its own shot.
   x:       feet x range that is clear of furniture.  spots: good named feet points [x,y].
   board:   the writable rectangle (whiteboard / wall screen / museum panel) for boardText, boardDiagram, barChart.
   regions: other named rectangles {x,y,w,h}.  Camera: stageCam(st,cam) clamps a camera so the frame never leaves
   the picture (z >= .84 in portrait). Wide portrait shot: {x:768,y:1250,z:.85}. */
const EYE_H=STANDY+22+HR+14; // rig units from the floor line to the eyes (617)
const STAGES={
 meeting_room:{w:1536,h:2304,horizon:1035,wall:1628,floor:[1690,2000],x:[120,1000],ink:'#2A2236',
  spots:{left:[250,1760],centre:[620,1820],right:[990,1740],front:[560,1960]},
  board:{x:402,y:755,w:618,h:412},
  regions:{window:{x:1128,y:578,w:309,h:757},cabinet:{x:30,y:1330,w:420,h:6},lampL:{x:265,y:513,w:0,h:0},lampR:{x:1265,y:513,w:0,h:0}}},
 server_room:{w:1536,h:2304,horizon:1020,wall:1448,floor:[1500,2050],x:[470,1070],ink:'#8CF5E8',glow:true,
  spots:{left:[520,1760],centre:[768,1760],right:[1010,1720],front:[768,1980]},
  board:{x:513,y:845,w:492,h:330},
  regions:{racksL:{x:0,y:290,w:430,h:1720},racksR:{x:1090,y:290,w:446,h:1720},lamp:{x:750,y:472,w:0,h:0}}},
 living_room:{w:1536,h:2304,horizon:975,wall:1665,floor:[1720,1990],x:[200,1080],ink:'#2A2236',
  spots:{left:[440,1860],centre:[660,1880],right:[900,1850],front:[640,1980]},
  board:null, card:{x:330,y:300,w:880,h:420},   // no wall board: boardText draws a cream card here instead
  regions:{frame:{x:1278,y:595,w:188,h:270},window:{x:582,y:375,w:438,h:930},rug:{x:112,y:1732,w:1163,h:353},lamp:{x:1372,y:1042,w:0,h:0}}},
 museum_hall:{w:1536,h:2304,horizon:978,wall:1530,floor:[1600,2050],x:[420,1220],ink:'#2A1838',
  spots:{left:[560,1760],centre:[768,1820],right:[1040,1780],front:[768,1980]},
  board:{x:218,y:755,w:297,h:568},
  regions:{panelL:{x:218,y:755,w:297,h:568},panelR:{x:1095,y:755,w:309,h:568},arch:{x:695,y:720,w:290,h:810},
   spotL:{x:357,y:446,w:0,h:0},spotR:{x:1257,y:440,w:0,h:0}}}};
const stageScale=(st,fy)=>(fy-STAGES[st].horizon)/EYE_H;
// stand(st,fx,fy): pose fields {x,y,s} for someone whose feet are at world point (fx,fy). Spread into the pose.
const stand=(st,fx,fy)=>{const s=stageScale(st,fy);return {x:fx,y:standY(fy,s),s}};
// makeWalk scale for a stage: makeWalk({pts,t0,t1,steps,scale:stageWalk('meeting_room')})
const stageWalk=st=>y=>stageScale(st,y);
function stageCam(st,c){const S=STAGES[st],z=Math.max(c.z,W/S.w,H/S.h)*1.001,hw=W/2/z,hh=H/2/z;return Object.assign({},c,{z,x:clamp(c.x,hw,S.w-hw),y:clamp(c.y,hh,S.h-hh)})}
// region(st,'board'|name) or a rect passed straight through
function region(st,name){if(st&&st.w!==undefined)return st;const S=STAGES[st];if(!S)return null;return name=='board'||!name?S.board:(S.regions[name]||null)}
function stage(x,st){const S=STAGES[st],im=ASSETS.bg[st];if(!im)return;x.drawImage(im,0,0,S.w,S.h)}

/* ---- stageLife(x,st,t): small code-drawn motion inside the picture. Call right after stage(), same layer.
   meeting_room: lamp glow, a few city windows switching on and off.  server_room: rack LEDs blink, screen glow.
   living_room: lamp breathing, dust in the lamp light, city windows.  museum_hall: dust drifting in both spotlights. */
function stageLife(x,st,t){x.save();x.globalCompositeOperation='lighter';
 const glow=(cx,cy,r,col,a)=>{const g=x.createRadialGradient(cx,cy,0,cx,cy,r);g.addColorStop(0,`rgba(${col},${a})`);g.addColorStop(1,`rgba(${col},0)`);x.fillStyle=g;x.fillRect(cx-r,cy-r,2*r,2*r)};
 const wins=(pts,seed)=>{for(let i=0;i<pts.length;i++){const q=pts[i],on=.5+.5*Math.sin(t*(.35+.13*i)+seed+i*2.1);x.fillStyle=`rgba(255,200,120,${.55*ease(clamp(on*1.6-.3))})`;x.fillRect(q[0],q[1],15,18)}};
 if(st=='meeting_room'){for(const [lx,ly,ph] of [[265,513,0],[1265,513,1.7]])glow(lx,ly+20,300,'255,190,120',.10+.03*Math.sin(t*1.3+ph));
  wins([[1163,1106],[1196,1106],[1403,1144],[1291,1237],[1328,1214]],0)}
 if(st=='server_room'){const L=(ASSET_DATA.bg.server_room||{}).leds||[];x.globalCompositeOperation='source-over';
  for(let i=0;i<L.length;i++){const [lx,ly,lw,lh,c]=L[i],ph=(i*0.6180339)%1,f=.4+((i*7)%11)/5,X=lx*1.5,Y=ly*1.5,w=lw*1.5,h=lh*1.5;
   const v=Math.sin((t*f+ph)*6.283);
   if(v<-.55){x.fillStyle='rgba(16,14,32,.88)';x.fillRect(X-1,Y-1,w+2,h+2)}
   else if(v>.75){x.fillStyle=c?'rgba(255,214,130,.9)':'rgba(150,255,240,.9)';x.fillRect(X+w*.15,Y+h*.15,w*.7,h*.7)}}
  x.globalCompositeOperation='lighter';const b=STAGES.server_room.board;glow(b.x+b.w/2,b.y+b.h/2,b.w*.75,'60,200,190',.05+.025*Math.sin(t*2.1));glow(750,480,220,'255,190,110',.08+.02*Math.sin(t*3.1))}
 if(st=='living_room'){glow(1372,1060,420,'255,170,90',.10+.035*Math.sin(t*.9)+.015*Math.sin(t*5.3));dust(x,[1372,1045],1440,300,t,26,11,'255,214,160');
  wins([[642,1068],[663,1068],[960,1046],[993,1046],[732,1140],[795,1170]],3)}
 if(st=='museum_hall'){for(const [sx,sy,sd] of [[357,446,1],[1257,440,2]]){glow(sx,sy+6,120,'255,210,150',.12+.03*Math.sin(t*1.1+sd));dust(x,[sx,sy+10],1330,300,t,30,sd*7,'255,226,180')}}
 x.restore()}
// dust motes drifting in a light cone from apex [x,y] down to baseY, half width hw at the base. Deterministic in t.
function dust(x,apex,baseY,hw,t,n=24,seed=1,col='255,230,190'){const r=rng(seed*977+5);for(let i=0;i<n;i++){const u0=r(),v0=r(),sp=r(),ph=r()*6.283,
  v=(v0+t*.018*(.4+sp))%1,u=clamp(u0*2-1+.06*Math.sin(t*.6+ph),-1,1),X=apex[0]+u*hw*v,Y=apex[1]+v*(baseY-apex[1]),a=.55*Math.sin(Math.PI*v)*(.55+.45*Math.sin(t*1.7+ph));
  x.fillStyle=`rgba(${col},${Math.max(0,a)})`;x.beginPath();x.arc(X,Y,2+sp*3,0,7);x.fill()}}

/* ---- PROPS: picture props. h = natural height in world units at person scale s=1 (a person is about 740 tall),
   so prop sizes stay consistent: draw at h*s where s is the person's (or the floor point's) scale.
   kind: 'stand' sits on the floor or a table (anchor bottom centre), 'hold' is carried (grip = where the hand goes,
   as fractions of the picture: [0,0] top left, [1,1] bottom right), 'icon' is a floating symbol (anchor centre). */
const PROPS={
 laptop:{h:140,kind:'hold',grip:[.5,.92]},phone:{h:75,kind:'hold',grip:[.5,.62]},server:{h:430,kind:'stand',grip:[.5,.5]},
 database:{h:260,kind:'icon'},cloud:{h:200,kind:'icon'},loadbalancer:{h:110,kind:'icon'},queue:{h:100,kind:'icon'},
 router:{h:150,kind:'stand',grip:[.5,.75]},coins:{h:140,kind:'stand',grip:[.5,.6]},notes:{h:110,kind:'hold',grip:[.25,.75]},
 piggybank:{h:170,kind:'stand',grip:[.5,.72]},wallet:{h:95,kind:'hold',grip:[.5,.6]},card:{h:60,kind:'hold',grip:[.15,.55]},
 chart_board:{h:650,kind:'stand'},calculator:{h:100,kind:'hold',grip:[.5,.75]},receipt:{h:140,kind:'hold',grip:[.35,.12]},
 brain:{h:220,kind:'icon'},thought_bubble:{h:280,kind:'icon'},notebook:{h:80,kind:'hold',grip:[.5,.85]},
 lightbulb:{h:180,kind:'icon',grip:[.5,.92]},hourglass:{h:150,kind:'stand',grip:[.5,.5]},mug:{h:60,kind:'hold',grip:[.86,.42]},
 sofa:{h:360,kind:'stand'},bed:{h:420,kind:'stand'},scroll:{h:170,kind:'hold',grip:[.5,.5]},old_book:{h:110,kind:'hold',grip:[.5,.75]},
 globe:{h:230,kind:'stand',grip:[.5,.92]},map:{h:200,kind:'hold',grip:[.12,.55]},compass:{h:80,kind:'hold',grip:[.5,.65]},
 column:{h:520,kind:'stand'},pot:{h:220,kind:'stand',grip:[.5,.55]},crown:{h:85,kind:'hold',grip:[.5,.75]},
 magnifier:{h:140,kind:'hold',grip:[.14,.86]},flag:{h:320,kind:'hold',grip:[.035,.78]},whiteboard:{h:760,kind:'stand'},
 clipboard:{h:150,kind:'hold',grip:[.12,.62]},speech_bubble:{h:240,kind:'icon'},arrow:{h:200,kind:'icon'},
 calendar:{h:130,kind:'stand',grip:[.5,.85]},envelope:{h:75,kind:'hold',grip:[.2,.6]},desk:{h:470,kind:'stand'},trophy:{h:160,kind:'hold',grip:[.5,.9]}};
/* prop(x,name,px,py,h,o): draw a prop with its anchor at world point (px,py), h world units tall (null: PROPS h * (o.s||1)).
   o: {rot (radians, about the anchor), flip (mirror), alpha, anchor:[u,v] to override}. Returns the drawn box
   {x,y,w,h,cx,cy,top:[x,y]} (unrotated), handy for labels or packet() endpoints. */
function prop(x,name,px,py,h,o={}){const d=(typeof ASSET_DATA!='undefined'&&ASSET_DATA.prop[name])||null,im=ASSETS.prop[name],m=PROPS[name]||{};
 const H=h||(m.h||150)*(o.s??1),Wd=d?H*d.w/d.h:H,a=o.anchor||(m.kind=='icon'?[.5,.5]:[.5,1]),bx=px-a[0]*Wd,by=py-a[1]*H;
 if(im){x.save();x.translate(px,py);if(o.rot)x.rotate(o.rot);if(o.flip)x.scale(-1,1);if(o.alpha!=null)x.globalAlpha*=o.alpha;x.drawImage(im,-a[0]*Wd,-a[1]*H,Wd,H);x.restore()}
 return {x:bx,y:by,w:Wd,h:H,cx:bx+Wd/2,cy:by+H/2,top:[bx+Wd/2,by]}}
// floorProp(x,st,name,fx,fy,o): a standing prop on the stage floor at feet point (fx,fy), sized by the stage's
// perspective (o.scale multiplies), with a soft contact shadow. Draw it before people standing in front of it.
function floorProp(x,st,name,fx,fy,o={}){const s=stageScale(st,fy)*(o.scale||1),m=PROPS[name]||{};const H=(m.h||150)*s;
 const d=ASSET_DATA.prop[name],w=d?H*d.w/d.h:H;softShadow(x,fx,fy-4*s,w*.42,12*s,.38);return prop(x,name,fx,fy,H,Object.assign({anchor:[.5,.985]},o))}
/* propInHand(x,p,k,name,h,o): the prop's grip point goes on hand k ('l'|'r') of pose p (after actor() drew it),
   a gripping hand is drawn over it in the character's colours, and contact() is recorded for the audit.
   h null = PROPS h * p.s. o as prop() plus {hand:false} to skip the hand, handRot. Move the arm and the prop follows. */
function propInHand(x,p,k,name,h,o={}){const hw=h2w(p,k),m=PROPS[name]||{};
 const r=prop(x,name,hw[0],hw[1],h||(m.h||150)*p.s,Object.assign({anchor:m.grip||[.5,.6]},o));
 if(o.hand!==false&&p._ch)gripHand(x,hw[0],hw[1],o.handRot||0,p._ch,p.s*.95);contact('hand-'+name,hw,[hw[0],hw[1]]);return r}
// pointerStick(x,p,k,ang,len): a thin pointer held in hand k, at angle ang (0 points right, -PI/2 up). Returns the tip.
function pointerStick(x,p,k,ang,len=430){const hw=h2w(p,k),L=len*p.s,tip=[hw[0]+Math.cos(ang)*L,hw[1]+Math.sin(ang)*L];
 x.save();x.lineCap='round';x.strokeStyle='#4A2A2E';x.lineWidth=11*p.s;x.beginPath();x.moveTo(hw[0]-Math.cos(ang)*40*p.s,hw[1]-Math.sin(ang)*40*p.s);x.lineTo(tip[0],tip[1]);x.stroke();
 x.strokeStyle='#E9D8BE';x.lineWidth=12*p.s;x.beginPath();x.moveTo(lerp(hw[0],tip[0],.94),lerp(hw[1],tip[1],.94));x.lineTo(tip[0],tip[1]);x.stroke();x.restore();
 if(p._ch)gripHand(x,hw[0],hw[1],ang,p._ch,p.s*.95);contact('hand-pointer',hw,[hw[0],hw[1]]);return tip}

/* ---- Overlays drawn inside the picture: text and diagrams on the board, charts, data packets. ----
   `where` is a stage name (uses o.region: a region name or a rect {x,y,w,h}, default 'board') or a rect. Writing reveals over time from o.t0
   at o.cps characters per second (omit t0 to show everything). Ink suits the board (dark marker on the whiteboard,
   glowing teal on the server-room screen). Keep it to a few big words: it must read on a phone. */
const FONT='"Trebuchet MS","Segoe UI","DejaVu Sans",Arial,sans-serif';
function _board(x,where,o){const S=typeof where=='string'?STAGES[where]:null;let R=o.region&&o.region.w!==undefined?o.region:region(where,o.region||'board'),card=false;
 if(!R&&S&&S.card){R=S.card;card=true}if(!R)return null;
 if(card){x.save();x.fillStyle='rgba(20,10,30,.35)';x.beginPath();x.roundRect(R.x+10,R.y+14,R.w,R.h,28);x.fill();x.fillStyle='#F4E8D4';x.beginPath();x.roundRect(R.x,R.y,R.w,R.h,28);x.fill();x.restore()}
 return {R,ink:o.color||(card?'#2A2236':S?S.ink:'#2A2236'),glow:!card&&S&&S.glow}}
function boardText(x,where,lines,t,o={}){const B=_board(x,where,o);if(!B)return;const {R,ink}=B,pad=R.w*.08,n=lines.length;
 let size=o.size||Math.min(R.h*.62/n,R.w*.3);x.save();x.font=`700 ${size}px ${FONT}`;const mw=Math.max(1,...lines.map(l=>x.measureText(l).width));
 if(mw>R.w-2*pad)size*=(R.w-2*pad)/mw;x.font=`700 ${size}px ${FONT}`;x.textBaseline='middle';x.textAlign=o.align||'center';x.fillStyle=ink;
 if(B.glow){x.shadowColor=ink;x.shadowBlur=size*.35}
 const lh=size*1.3,y0=R.y+R.h/2-(n-1)*lh/2,X=o.align=='left'?R.x+pad:R.x+R.w/2;let k=o.t0==null?1e9:(t-o.t0)*(o.cps||14);
 for(let i=0;i<n;i++){const s=lines[i].slice(0,Math.max(0,Math.floor(k)));k-=lines[i].length+3;if(s)x.fillText(s,X,y0+i*lh)}x.restore()}
/* boardDiagram(x,where,nodes,edges,t,o): boxes and arrows drawn one by one on the board.
   nodes: [[label,u,v],...] with u,v in 0..1 across the region. edges: [[from,to],...] node indexes.
   o: {t0 start, step seconds between nodes (.5), flow:true to send packets along the arrows, size}. */
function boardDiagram(x,where,nodes,edges,t,o={}){const B=_board(x,where,o);if(!B)return;const {R,ink}=B,t0=o.t0??-1e9,st=o.step??.5,size=o.size||R.h*.085;
 x.save();x.font=`700 ${size}px ${FONT}`;x.textAlign='center';x.textBaseline='middle';x.lineCap='round';x.lineJoin='round';if(B.glow){x.shadowColor=ink;x.shadowBlur=size*.3}
 const P=nodes.map(([l,u,v],i)=>({l,x:R.x+u*R.w,y:R.y+v*R.h,w:x.measureText(l).width+size*1.1,h:size*1.8,a:ease(seg(t,t0+i*st,t0+i*st+.35))}));
 edges.forEach(([i,j],e)=>{const a=P[i],b=P[j],g=ease(seg(t,t0+Math.max(i,j)*st+.2,t0+Math.max(i,j)*st+.7));if(g<=0)return;
  const dx=b.x-a.x,dy=b.y-a.y,d=Math.hypot(dx,dy)||1,ux=dx/d,uy=dy/d,ra=Math.min(a.w/2/Math.abs(ux||1e-6),a.h/2/Math.abs(uy||1e-6))+8,rb=Math.min(b.w/2/Math.abs(ux||1e-6),b.h/2/Math.abs(uy||1e-6))+10;
  const s=[a.x+ux*ra,a.y+uy*ra],f=[b.x-ux*rb,b.y-uy*rb],q=[lerp(s[0],f[0],g),lerp(s[1],f[1],g)];x.strokeStyle=ink;x.lineWidth=size*.12;x.beginPath();x.moveTo(s[0],s[1]);x.lineTo(q[0],q[1]);x.stroke();
  if(g>.98){x.fillStyle=ink;x.beginPath();x.moveTo(f[0],f[1]);x.lineTo(f[0]-ux*size*.5-uy*size*.3,f[1]-uy*size*.5+ux*size*.3);x.lineTo(f[0]-ux*size*.5+uy*size*.3,f[1]-uy*size*.5-ux*size*.3);x.closePath();x.fill();
   if(o.flow)packet(x,s,f,t,{n:2,period:1.1,r:size*.16,phase:e*.37,t0:t0+Math.max(i,j)*st+.7})}});
 for(const p of P){if(p.a<=0)continue;x.globalAlpha=p.a;x.strokeStyle=ink;x.lineWidth=size*.12;x.beginPath();x.roundRect(p.x-p.w/2,p.y-p.h/2,p.w,p.h,size*.35);x.stroke();x.fillStyle=ink;x.fillText(p.l,p.x,p.y+size*.04)}
 x.restore()}
/* barChart(x,where,values,t,o): bars (values 0..1) growing from o.t0 over o.dur (1.2 s), each a little after the last.
   o: {labels:[...], colors:[...], region}. Works on the board or any rect. */
function barChart(x,where,values,t,o={}){const B=_board(x,where,o);if(!B)return;const {R,ink}=B,n=values.length,pad=R.w*.1,bw=(R.w-2*pad)/n,base=R.y+R.h*(o.labels?.8:.9),top=R.y+R.h*.1,t0=o.t0??-1e9,dur=o.dur??1.2;
 const cols=o.colors||['#2DB2AE','#F6B73C','#E4493F','#7A56A0','#FF8F7A'];x.save();x.strokeStyle=ink;x.lineWidth=Math.max(3,R.h*.012);x.lineCap='round';x.beginPath();x.moveTo(R.x+pad*.6,base);x.lineTo(R.x+R.w-pad*.6,base);x.stroke();
 values.forEach((v,i)=>{const g=ease(seg(t,t0+i*dur*.25,t0+i*dur*.25+dur)),h=(base-top)*clamp(v)*g;x.fillStyle=cols[i%cols.length];x.beginPath();x.roundRect(R.x+pad+i*bw+bw*.15,base-h,bw*.7,h,[bw*.12,bw*.12,0,0]);x.fill();
  if(o.labels&&o.labels[i]){x.fillStyle=ink;x.font=`700 ${R.h*.09}px ${FONT}`;x.textAlign='center';x.textBaseline='top';x.fillText(o.labels[i],R.x+pad+i*bw+bw/2,base+R.h*.04)}});x.restore()}
/* packet(x,a,b,t,o): small glowing data packets travelling from world point a to b (looping), e.g. between two props.
   o: {n:3 packets, period:1.2 s per trip, r:12 size, color, arc:0 (lift the path into a curve), t0,t1 active window, phase}. */
function packet(x,a,b,t,o={}){const t0=o.t0??-1e9,t1=o.t1??1e9;if(t<t0||t>t1)return;const n=o.n||3,per=o.period||1.2,r=o.r||12,col=o.color||'#8CF5E8',arc=o.arc||0;
 x.save();x.shadowColor=col;x.shadowBlur=r*1.6;x.fillStyle=col;
 for(let i=0;i<n;i++){const u=(((t-Math.max(t0,-1e6))/per+i/n+(o.phase||0))%1+1)%1,X=lerp(a[0],b[0],u),Y=lerp(a[1],b[1],u)-arc*Math.sin(Math.PI*u);
  x.globalAlpha=Math.min(1,Math.sin(Math.PI*u)*3);x.beginPath();x.roundRect(X-r,Y-r*.75,2*r,1.5*r,r*.4);x.fill()}x.restore()}
// label(x,text,cx,cy,o): a small rounded tag (e.g. naming a prop: 'CACHE'). o: {size:40, bg:'#F4E8D4', color:INK, alpha}
function label(x,text,cx,cy,o={}){const size=o.size||40;x.save();x.font=`700 ${size}px ${FONT}`;const w=x.measureText(text).width+size;if(o.alpha!=null)x.globalAlpha*=o.alpha;
 x.fillStyle=o.bg||'#F4E8D4';x.beginPath();x.roundRect(cx-w/2,cy-size*.75,w,size*1.5,size*.5);x.fill();x.fillStyle=o.color||INK;x.textAlign='center';x.textBaseline='middle';x.fillText(text,cx,cy+size*.04);x.restore()}
