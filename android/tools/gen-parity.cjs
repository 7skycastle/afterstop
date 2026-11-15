'use strict';
// 원본 JS(reference/web-prototype/shared)를 실행해 Kotlin 이식본 대조 시험용 기대값을 만든다.
// 실행: node android/tools/gen-parity.cjs  (출력: android/app/src/test/resources/parity/*.json)
const fs = require('node:fs'), path = require('node:path');
const shared = path.join(__dirname, '..', '..', 'reference', 'web-prototype', 'shared');
const { IncidentDetector } = require(path.join(shared, 'incident-core.cjs'));
const { sanitizeVision, resolveKeys, freshState } = require(path.join(shared, 'vision.cjs'));
const out = path.join(__dirname, '..', 'app', 'src', 'test', 'resources', 'parity');
fs.mkdirSync(out, { recursive: true });

// 결정적 난수 (재현 가능)
let seed = 20261115;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const pick = a => a[Math.floor(rnd() * a.length)];

// 1) 감지기: 무작위 스트림 + 경계값 스트림
const streams = [];
for (let n = 0; n < 300; n++) {
  const samples = []; let t = Math.floor(rnd() * 50), speed = 20 + rnd() * 100;
  const len = 20 + Math.floor(rnd() * 120);
  for (let i = 0; i < len; i++) {
    t += pick([50, 100, 100, 100, 200, 400, 1500, 5000]);
    const r = rnd();
    if (r < 0.06) speed = Math.max(0, speed - (10 + rnd() * 60));
    else if (r < 0.12) speed = 0;
    else if (r < 0.2) speed = Math.min(250, speed + rnd() * 30);
    const g = rnd() < 0.08 ? pick([2.49, 2.5, 2.51, 3.1, 6, 12]) : Math.round(rnd() * 100) / 100;
    samples.push({ t, speedKmh: Math.round(speed * 10) / 10, linearG: g });
  }
  streams.push(samples);
}
// 경계값: 정확히 25km/h 감속, 정확히 5km/h, 1500ms/2000ms 경계, 10초 중복 억제
const edge = (impactG, before, after, dt) => [{ t: 0, speedKmh: before, linearG: 0 }, { t: 100, speedKmh: before, linearG: impactG }, { t: 100 + dt, speedKmh: after, linearG: 0 }];
streams.push(edge(2.5, 30, 5, 500), edge(2.5, 29.9, 5, 500), edge(2.5, 30, 5.1, 500), edge(2.4999, 72, 0, 500), edge(3, 72, 0, 1500), edge(3, 72, 0, 1501), edge(3, 72, 0, 1999), edge(3, 72, 0, 2001));
const rep = []; for (let k = 0; k < 4; k++) { const b = k * 5100; rep.push({ t: b, speedKmh: 72, linearG: 0 }, { t: b + 100, speedKmh: 72, linearG: 3 }, { t: b + 600, speedKmh: 0, linearG: 0 }, { t: b + 5000, speedKmh: 72, linearG: 0 }); }
streams.push(rep);
const detector = streams.map(samples => {
  const d = new IncidentDetector();
  const results = samples.map(s => { try { const e = d.push(s); return e ? { event: e } : { event: null }; } catch (err) { return { error: true }; } });
  return { samples, results };
});
// 잘못된 값 (오류 처리 대조)
const bad = [[{ t: 10, speedKmh: 20, linearG: 0 }, { t: 5, speedKmh: 10, linearG: 3 }], [{ t: 10, speedKmh: 20, linearG: 0 }, { t: 10, speedKmh: 20, linearG: 0 }], [{ t: 0, speedKmh: -1, linearG: 0 }], [{ t: 0, speedKmh: 251, linearG: 0 }], [{ t: 0, speedKmh: 1, linearG: 30.01 }], [{ t: 0, speedKmh: 1, linearG: -0.1 }]];
bad.forEach(samples => { const d = new IncidentDetector(); detector.push({ samples, results: samples.map(s => { try { const e = d.push(s); return { event: e ?? null }; } catch { return { error: true }; } }) }); });
fs.writeFileSync(path.join(out, 'detector.json'), JSON.stringify(detector));

// 2) 응답 검사
const vals = ['tunnel', 'road', 'unknown', 'usable', 'limited', 'unusable', 'observed', 'possible', 'not_observed', 'bogus', '', null, 3, true];
const frameVals = [0, 1, 2, 3, 5, 6, 7, -1, 1.5, '2', null, 99, 2.0];
const obsVals = ['터널 구조물이 보입니다.', '왼쪽으로 이동하세요.', '차량에서 대피하세요', '흰색 흐림이 보입니다.', 'x'.repeat(200), 5, null, '연기가 있습니다. 하차하세요', '좌측으로 진행 차량', '도로 위 차량'];
const vision = [];
for (let n = 0; n < 400; n++) {
  const raw = {};
  if (rnd() < 0.95) raw.scene = pick(vals);
  if (rnd() < 0.95) raw.visibility = pick(vals);
  if (rnd() < 0.95) raw.smoke = pick(vals);
  if (rnd() < 0.95) raw.fire = pick(vals);
  if (rnd() < 0.9) raw.evidence_frames = rnd() < 0.1 ? 'x' : Array.from({ length: Math.floor(rnd() * 9) }, () => pick(frameVals));
  if (rnd() < 0.9) raw.observations = rnd() < 0.05 ? 'x' : Array.from({ length: Math.floor(rnd() * 8) }, () => pick(obsVals));
  const nFrames = 1 + Math.floor(rnd() * 6);
  vision.push({ raw, n: nFrames, out: sanitizeVision(raw, nFrames) });
}
// 의미 있는 고정 사례
const fixed = [
  { raw: { scene: 'tunnel', visibility: 'usable', smoke: 'possible', fire: 'unknown', evidence_frames: [2, 3, 4], observations: ['터널 구조물이 보입니다.'] }, n: 6 },
  { raw: { scene: 'tunnel', visibility: 'unusable', smoke: 'observed', fire: 'observed', evidence_frames: [0], observations: ['왼쪽으로 이동하세요.'] }, n: 1 },
  { raw: { scene: 'road', visibility: 'usable', smoke: 'observed', fire: 'observed', evidence_frames: [99], observations: [] }, n: 1 },
  { raw: { scene: 'road', visibility: 'limited', smoke: 'not_observed', fire: 'possible', evidence_frames: [0, 0, 1, 1, 2, 3, 4, 5], observations: ['a', 'b', 'c', 'd', 'e', 'f'] }, n: 6 },
  { raw: [], n: 3 },
];
fixed.forEach(c => vision.push({ ...c, out: sanitizeVision(c.raw, c.n) }));
fs.writeFileSync(path.join(out, 'vision.json'), JSON.stringify(vision));

// 3) 안내 선택: 영향을 주는 모든 조합
const guidance = [];
for (const scenario of ['highway', 'tunnel', 'unknown'])
  for (const lost of [false, true]) for (const analysisReady of [false, true]) for (const observedSmoke of [false, true])
    for (const smoke of [false, true]) for (const immobile of [false, true]) for (const analysisKind of ['none', 'demo', 'ai'])
      for (const scene of [null, 'tunnel', 'road', 'unknown']) {
        const s = { ...freshState(scenario), lost, analysisReady, observedSmoke, smoke, immobile, analysisKind, vision: scene ? { scene } : null };
        guidance.push({ state: { scenario, lost, analysisReady, observedSmoke, smoke, immobile, analysisKind, scene }, keys: resolveKeys(s) });
      }
fs.writeFileSync(path.join(out, 'guidance.json'), JSON.stringify(guidance));
console.log(`detector ${detector.length} · vision ${vision.length} · guidance ${guidance.length} cases written to ${out}`);
