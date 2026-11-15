'use strict';
// 사용법: node send-demo-telemetry.cjs [기본URL]   (토큰은 AFTERSTOP_TOKEN 환경변수)
const {telemetryFor}=require('./shared/incident-core.cjs');
const base=(process.argv[2]||process.env.AFTERSTOP_URL||'http://localhost:8787').replace(/\/$/,'');
const token=process.env.AFTERSTOP_TOKEN;
(async()=>{const streamId='demo_'+Date.now();const headers={'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})};
for(const sample of telemetryFor('highway')){const response=await fetch(base+'/api/telemetry',{method:'POST',headers,body:JSON.stringify({streamId,samples:[sample]})});if(!response.ok)throw Error('서버가 텔레메트리를 거부했습니다 ('+response.status+')');const result=await response.json();if(result.events.length)console.log('예시 차량 신호의 사고 후보를 전송했습니다. 실제 사고 입력이 아닙니다.');await new Promise(r=>setTimeout(r,100));}})().catch(e=>{console.error('서버 연결을 확인하세요:',e.message);process.exitCode=1;});
