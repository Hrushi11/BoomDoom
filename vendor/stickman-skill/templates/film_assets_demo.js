// ASSETS DEMO (portrait 1080x1920, 10 s): the image stages, picture props and the new cast. One stage per shot.
// Patterns: stand(st,x,y) for pose x/y/s on a stage floor, makeWalk with stageWalk(st), stageCam to stay inside the
// picture, stage()+stageLife() on the back layer, board overlays, floorProp behind people, propInHand after actor().
const SH=['meeting_room','server_room','living_room','museum_hall'],T=[0,2.5,5,7.5,10];
const pose=(st,fx,fy,o)=>Object.assign({face:1,breath:0},stand(st,fx,fy),o);
// shot 1: the engineer walks to the board, the interviewer answers with a clipboard
const engW=makeWalk({pts:[[40,1790],[270,1770]],t0:.15,t1:1.6,steps:4,scale:stageWalk('meeting_room')});
function engineer(t){const w=engW.at(t),p={x:w.c[0],y:standY(w.c[1],w.s),s:w.s,face:1,breath:t*2,look:kf(t,[[0,.3],[1.6,.3],[1.9,.8]]),mouth:.4,open:talk(t,1.7,2.4),blink:blinkAt(t,[1.2]),brow:.3};
 walkPose(p,w);if(!w.moving){const g=ease(seg(t,1.6,2));p.hands={r:[lerp(60,140,g),lerp(220,10,g)],l:[-70,222]};p.showH={r:g>.5}}return p}
function interviewer(t){const p=pose('meeting_room',1070,1750,{breath:t*2.2,look:-.6,mouth:.5,open:talk(t,.3,1.4),blink:blinkAt(t,[.8,2.1]),hands:{l:[-30,170],r:[lerp(110,175,ease(seg(t,.4,.9))),lerp(215,110,ease(seg(t,.4,.9)))]},showH:{r:true},tuck:{l:true}});return p}
// shot 2: the analyst lifts a laptop while packets run from the server to it
function analyst(t){const u=ease(seg(t,2.9,3.7)),p=pose('server_room',520,1780,{breath:t*2,look:kf(t,[[2.5,.5],[3.7,.2],[4.2,.6]]),lookY:lerp(.4,0,u),mouth:.6,open:talk(t,3.8,4.6),blink:blinkAt(t,[3.1,4.4]),hands:{r:[lerp(70,40,u),lerp(215,120,u)]},tuck:{r:true}});return p}
// shot 3: granddaughter checks her phone, grandmother explains saving
function kurta(t){const u=ease(seg(t,5.2,5.8)),p=pose('living_room',430,1860,{breath:t*2,look:lerp(.1,.7,ease(seg(t,6.2,6.6))),lookY:lerp(.6,0,ease(seg(t,6.2,6.6))),mouth:.5,blink:blinkAt(t,[5.6,7]),hands:{r:[lerp(80,40,u),lerp(220,110,u)]},tuck:{r:true}});return p}
function saree(t){const g=.5+.5*Math.sin(t*3),p=pose('living_room',900,1850,{breath:t*1.8,look:-.6,mouth:.6,open:talk(t,5.5,7.3),blink:blinkAt(t,[6.1]),brow:.4,hands:{l:[-150+g*30,lerp(130,60,g)],r:[90,222]},showH:{l:true}});return p}
// shot 4: the curator points at the panel, the professor walks in with a scroll
const profW=makeWalk({pts:[[1420,1790],[1040,1790]],t0:7.6,t1:9.4,steps:5,scale:stageWalk('museum_hall')});
function curator(t){const p=pose('museum_hall',690,1770,{breath:t*2,look:kf(t,[[7.5,-.6],[8.8,-.6],[9.2,.5]]),lookY:-.2,mouth:.5,open:talk(t,7.7,8.8),blink:blinkAt(t,[8.3]),hands:{l:[-150,lerp(150,40,ease(seg(t,7.6,8.1)))],r:[80,222]}});return p}
function professor(t){const w=profW.at(t),p={x:w.c[0],y:standY(w.c[1],w.s),s:w.s,face:-1,breath:t*2,look:kf(t,[[7.5,-.4],[9.4,-.4],[9.7,-.7]]),mouth:.6,blink:blinkAt(t,[9]),hands:{l:[-60,190]},tuck:{l:true}};
 walkPose(p,w,{arms:false});const sw=w.moving?Math.sin(w.U*5*Math.PI)*40:0;p.hands.r=[96+sw,222];return p}
FILM={dur:10,cuts:[0,75,150,225,300],fadeIn:.3,fadeOut:.4,
 cam(s,u,t){const c=[{x:lerp(600,660,ease(u)),y:1350,z:lerp(.86,.96,ease(u))},{x:768,y:lerp(1300,1340,u),z:lerp(.95,1.06,ease(u))},
  {x:660,y:1330,z:lerp(1.02,.92,ease(u))},{x:lerp(700,820,ease(u)),y:1330,z:.9}][s];return stageCam(SH[s],c)},
 draw(t,cam,s){const st=SH[s],x=layer('bg',cam);stage(x,st);stageLife(x,st,t);const a=layer('act',cam);
  const act=(id,ch,p)=>{softShadow(a,p.x,p.y+(STANDY+22)*p.s,110*p.s,14*p.s);return actor(a,id,ch,p,cam)};
  if(s==0){boardText(x,st,['CACHE?'],t,{t0:.3,cps:8,size:70,region:{x:402,y:770,w:618,h:120}});
   boardDiagram(x,st,[['APP',.16,.5],['CACHE',.45,.82],['DB',.74,.5]],[[0,1],[1,2]],t,{t0:.6,step:.35,flow:true,size:40,region:{x:402,y:840,w:618,h:320}});
   const e=act('engineer',ENGINEER,engineer(t)),i=act('interviewer',INTERVIEWER,interviewer(t));propInHand(a,i,'l','clipboard',null,{rot:.1})}
  if(s==1){boardText(x,st,['LATENCY','12 ms'],t,{t0:2.7,cps:10});const sv=floorProp(a,st,'server',1010,1640),
   p=act('analyst',ANALYST,analyst(t)),lp=propInHand(a,p,'r','laptop');packet(a,[sv.cx,sv.y+sv.h*.3],lp.top,t,{n:4,period:1,r:14,arc:120,t0:3.3});
   prop(a,'database',870,1330+Math.sin(t*2)*10,170,{alpha:ease(seg(t,3.6,4.1))})}
  if(s==2){barChart(x,st,[.35,.6,.95],t,{t0:5.4,labels:['SAVE','SPEND','GROW']});floorProp(a,st,'piggybank',690,1780);
   const k=act('kurta',KURTA,kurta(t)),g=act('saree',SAREE,saree(t));propInHand(a,k,'r','phone');
   floorProp(a,st,'coins',700,1960,{scale:.9});const tb=ease(seg(t,6.3,6.8));if(tb>0){prop(a,'thought_bubble',1080,830,230,{alpha:tb});prop(a,'brain',1080,820,115,{alpha:tb})}}
  if(s==3){boardText(x,st,['ASHOKA','268','BCE'],t,{t0:7.9,cps:9});floorProp(a,st,'pot',800,1640);
   const c=act('curator',CURATOR,curator(t)),tip=pointerStick(a,c,'l',lerp(-2.2,-1.9,ease(seg(t,7.6,8.1))),380);
   const pr=act('professor',PROFESSOR,professor(t));propInHand(a,pr,'l','scroll',null,{rot:-.2});
   floorProp(a,st,'column',1290,1900,{scale:.8})}  // nearer the camera than both people, so drawn after them
  comp([['bg'],['act']])},
 plants(){return {engineer:engW.plants(),professor:profW.plants()}}};
say(.3,'whoosh');
