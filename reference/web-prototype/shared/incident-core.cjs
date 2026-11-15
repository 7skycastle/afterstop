(function(root){
'use strict';
// Prototype thresholds: not a calibrated crash detector.
class IncidentDetector{
 constructor(){this.reset();}
 reset(){this.samples=[];this.lastTime=-Infinity;this.lastEvent=-Infinity;}
 push(s){
  if(!s||!['t','speedKmh','linearG'].every(k=>Number.isFinite(s[k]))||s.t<=this.lastTime||s.speedKmh<0||s.speedKmh>250||s.linearG<0||s.linearG>30)throw Error('Invalid telemetry');
  this.lastTime=s.t;this.samples.push({...s});this.samples=this.samples.filter(x=>s.t-x.t<=3000);
  const impact=this.samples.find(x=>s.t-x.t<=1500&&x.linearG>=2.5),recent=this.samples.filter(x=>s.t-x.t<=2000);
  const before=impact?recent.filter(x=>x.t<=impact.t):[],prior=before.length?Math.max(...before.map(x=>x.speedKmh)):0;
  const stop=impact&&s.t>=impact.t&&s.speedKmh<=5,drop=prior-s.speedKmh>=25;
  if(impact&&stop&&drop&&s.t-this.lastEvent>10000){this.lastEvent=s.t;return{type:'incident_candidate',t:s.t,impactG:impact.linearG,speedDropKmh:prior-s.speedKmh,source:'telemetry_rule',confirmedCrash:false};}
  return null;
 }
}
class FrameRingBuffer{
 constructor(max=20){this.max=max;this.items=[];}
 push(frame){if(!frame||!Number.isFinite(frame.t))throw Error('Invalid frame');this.items.push(frame);while(this.items.length>this.max)this.items.shift();}
 around(t,before=5000,after=3000){return this.items.filter(f=>f.t>=t-before&&f.t<=t+after);}
}
const scenarios={
 highway:{label:'고속도로 · 추돌 후 정차',place:'고속도로 · 위치 예시',smoke:false,immobile:false,tunnel:false,limited:false,collision:true},
 tunnel:{label:'터널 · 연기 의심',place:'터널 · 위치 예시',smoke:true,immobile:false,tunnel:true,limited:false,collision:true},
 lowlight:{label:'야간 · 시야 제한',place:'위치 미확인',smoke:false,immobile:false,tunnel:false,limited:true,collision:true},
 bump:{label:'과속방지턱 · 사고 안내 없음',place:'일반 도로 · 위치 예시',smoke:false,immobile:false,tunnel:false,limited:false,collision:false}
};
function telemetryFor(key){if(!scenarios[key])throw Error('Unknown scenario');return Array.from({length:30},(_,i)=>({t:i*100,speedKmh:key==='bump'?(i<12?36:28):(i<12?72:i<16?72-(i-11)*17:0),linearG:i===12?3.1:.04,source:'synthetic_fixture'}));}
function demoObservation(key){const s=scenarios[key];if(!s)throw Error('Unknown scenario');return{scene:s.tunnel?'tunnel':s.limited?'unknown':'road',visibility:s.limited?'unusable':'usable',smoke:s.limited?'unknown':s.smoke?'possible':'not_observed',fire:s.limited?'unknown':'unknown',evidence_frames:s.limited?[]:[2,3,4],observations:s.limited?[]:[s.tunnel?'터널 구조물이 보입니다.':'도로 위에 차량이 정차한 장면입니다.',s.smoke?'차량 앞쪽에 흰색 흐림이 보입니다.':'선택한 프레임에서는 뚜렷한 연기 단서를 관찰하지 못했습니다.']};}
const api={IncidentDetector,FrameRingBuffer,scenarios,telemetryFor,demoObservation};if(typeof module!=='undefined')module.exports=api;else root.IncidentCore=api;
})(typeof globalThis!=='undefined'?globalThis:this);
