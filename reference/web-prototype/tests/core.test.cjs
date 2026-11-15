const test=require('node:test'),assert=require('node:assert/strict');
const {IncidentDetector,FrameRingBuffer,telemetryFor}=require('../shared/incident-core.cjs');
const {sanitizeVision}=require('../shared/vision.cjs');
test('combined impact, speed loss and stop create one candidate',()=>{const d=new IncidentDetector(),events=telemetryFor('highway').map(s=>d.push(s)).filter(Boolean);assert.equal(events.length,1);assert.equal(events[0].confirmedCrash,false);});
test('a bump alone does not create an accident candidate',()=>{const d=new IncidentDetector();assert.equal(telemetryFor('bump').filter(s=>d.push(s)).length,0);});
test('hard braking without impact is not enough',()=>{const d=new IncidentDetector();assert.equal(telemetryFor('highway').filter(s=>d.push({...s,linearG:0.1})).length,0);});
test('invalid and unordered sensor values fail closed',()=>{const d=new IncidentDetector();d.push({t:10,speedKmh:20,linearG:0});assert.throws(()=>d.push({t:5,speedKmh:10,linearG:3}));assert.throws(()=>d.push({t:20,speedKmh:NaN,linearG:3}));});
test('ring buffer bounds retained frames and selects event interval',()=>{const b=new FrameRingBuffer(3);for(let t=0;t<5;t++)b.push({t:t*1000,data:'frame'});assert.equal(b.items.length,3);assert.deepEqual(b.around(3000,1000,0).map(x=>x.t),[2000,3000]);});
test('invalid evidence and unusable frames cannot infer a hazard',()=>{const raw={scene:'tunnel',visibility:'unusable',smoke:'observed',fire:'observed',evidence_frames:[0],observations:['왼쪽으로 이동하세요.']};const v=sanitizeVision(raw,1);assert.equal(v.inferredRisk,false);assert.deepEqual(v.observations,[]);assert.equal(sanitizeVision({...raw,visibility:'usable',evidence_frames:[99]},1).inferredRisk,false);});
