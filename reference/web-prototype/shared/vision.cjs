'use strict';
const GUIDES={
 highway:{label:'고속도로',title:'주변 위험을 먼저 살피세요.',text:'차가 완전히 멈췄고 스스로 안전하게 이동할 수 있다면 차도 밖 안전한 곳으로 대피하세요.',note:'안전하게 이동할 수 있는지는 현장에서 확인해야 합니다.'},
 tunnel:{label:'터널',title:'현장 비상 표지를 확인하세요.',text:'안전하게 이동할 수 있는지 살피고 대피 방향은 현장 표지로 확인하세요.',note:'위치만으로 출구 방향이나 주변의 안전을 판단하지 않습니다.'},
 unknown:{label:'위치 미확인',title:'주변 위험부터 확인하세요.',text:'차량이 멈췄는지와 주변에서 다가오는 위험이 있는지 살피세요.',note:'위치를 확인하지 못해 도로 종류나 대피 방향을 단정하지 않습니다.'},
 observed:{label:'영상 단서 가정',title:'연기처럼 보이는 징후가 있어요.',text:'실제로 연기나 불꽃이 보이고 안전하게 이동할 수 있다면 차량에서 떨어진 안전한 곳으로 대피하세요.',note:'미리 설정한 모의 결과입니다. 실제 영상 분석이나 화재 확정이 아닙니다.'},
 smoke:{label:'사용자 알림',title:'연기·불꽃을 알려주셨어요.',text:'스스로 안전하게 이동할 수 있다면 차량에서 떨어진 안전한 곳으로 대피하세요.',note:'사용자가 알린 정보입니다. 화재를 자동 감지한 결과가 아닙니다.'},
 immobile:{label:'사용자 알림',title:'이동이 어려운 상황이에요.',text:'이동이 어렵다는 사실을 알리고 구조 요청을 우선하세요.',note:'부상 여부와 원인은 미확인입니다. 일률적인 하차 안내를 중단합니다.'},
 combinedObserved:{label:'겹친 위험 정보',title:'이동이 어려운 위험 상황이에요.',text:'움직이기 어렵다는 사실과 실제로 보이는 위험을 구조 요청 시 알려주세요.',note:'연기 징후는 모의 결과이며 이동 어려움은 사용자가 알렸습니다.'},
 combined:{label:'사용자 알림',title:'두 가지 위험을 함께 알려주세요.',text:'연기·불꽃이 보이고 움직이기 어렵다는 두 가지 상황을 구조 요청 시 함께 알려주세요.',note:'앱이 안전한 이동 방법을 판단할 수는 없습니다.'},
 help:{label:'도움 요청',title:'도움 요청을 우선하세요.',text:'필요하다면 차량 SOS나 긴급전화로 도움을 요청하세요.',note:'이 시뮬레이션에서는 실제 전화나 신고가 이루어지지 않습니다.'},
 facts:{label:'정보 전달',title:'확인한 사실만 전달하세요.',text:'알고 있는 도로명과 진행 방향, 눈앞의 상황을 알려주세요.',note:'부상 정도와 정확한 차로는 판단하지 않습니다.'},
 factsUnknown:{label:'정보 전달',title:'위치가 미확인임을 알려주세요.',text:'현재 위치를 확인하지 못했다는 점과 눈앞의 상황을 알려주세요.',note:'확인하지 못한 정보는 추측해 전달하지 않습니다.'}
};
const SCENARIOS={highway:'고속도로 정차',tunnel:'터널 내 정차',unknown:'현재 위치 미확인'};
function freshState(scenario='unknown'){return{scenario,lost:false,offline:false,moving:false,mockSmoke:false,analysisReady:false,observedSmoke:false,smoke:false,immobile:false,started:false,paused:false,inCall:false,step:0,view:'guide',autoVoice:true,analysisKind:'none',vision:null};}
function resolveKeys(s){const unknown=s.lost||s.scenario==='unknown',observed=s.analysisReady&&s.observedSmoke;let key;if(s.immobile&&(s.smoke||observed))key=s.smoke?'combined':'combinedObserved';else if(s.immobile)key='immobile';else if(s.smoke)key='smoke';else if(observed)key='observed';else if(s.analysisKind==='ai'&&s.vision?.scene==='tunnel')key='tunnel';else if(unknown)key='unknown';else key=s.scenario;return[key,'help',unknown?'factsUnknown':'facts'];}
if(typeof module!=='undefined'&&module.exports)module.exports={GUIDES,freshState,resolveKeys};
const VISION_SCHEMA={type:'object',additionalProperties:false,properties:{scene:{type:'string',enum:['tunnel','road','unknown']},visibility:{type:'string',enum:['usable','limited','unusable']},smoke:{type:'string',enum:['observed','possible','not_observed','unknown']},fire:{type:'string',enum:['observed','possible','not_observed','unknown']},evidence_frames:{type:'array',items:{type:'integer',minimum:0}},observations:{type:'array',maxItems:5,items:{type:'string',maxLength:160}}},required:['scene','visibility','smoke','fire','evidence_frames','observations']};
function sanitizeVision(raw,n){
 if(!raw||typeof raw!=='object'||!Number.isInteger(n)||n<1)throw Error('영상 분석 결과 형식이 올바르지 않습니다.');
 const one=(value,allowed,fallback)=>allowed.includes(value)?value:fallback;
 const scene=one(raw.scene,['tunnel','road','unknown'],'unknown'),visibility=one(raw.visibility,['usable','limited','unusable'],'unusable');
 const evidence_frames=[...new Set((Array.isArray(raw.evidence_frames)?raw.evidence_frames:[]).filter(x=>Number.isInteger(x)&&x>=0&&x<n))].slice(0,6);
 const smoke=one(raw.smoke,['observed','possible','not_observed','unknown'],'unknown'),fire=one(raw.fire,['observed','possible','not_observed','unknown'],'unknown');
 const canUse=visibility!=='unusable'&&evidence_frames.length>0;
 return{scene:canUse?scene:'unknown',visibility,smoke:canUse?smoke:'unknown',fire:canUse?fire:'unknown',evidence_frames,observations:canUse?(Array.isArray(raw.observations)?raw.observations:[]).filter(x=>typeof x==='string'&&!/대피하|이동하|하차하|하세요|하십시오|피하세요|좌측으로|우측으로/.test(x)).slice(0,5).map(x=>x.slice(0,160)):[],inferredRisk:canUse&&(['observed','possible'].includes(smoke)||['observed','possible'].includes(fire))};
}
if(typeof module!=='undefined'&&module.exports)Object.assign(module.exports,{VISION_SCHEMA,sanitizeVision});

