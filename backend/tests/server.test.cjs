'use strict';
const test = require('node:test'), assert = require('node:assert/strict'), http = require('node:http');
const { createApp, isClientAllowed } = require('../server.cjs');
const { telemetryFor } = require('../shared/incident-core.cjs');
const TOKEN = 'test-token-0123456789abcdef';
const fixture = { scene: 'tunnel', visibility: 'usable', smoke: 'possible', fire: 'unknown', evidence_frames: [0], observations: ['흰색 흐림이 보입니다.', '왼쪽으로 이동하세요.'] };
const listen = (srv, host = '127.0.0.1') => new Promise(r => srv.listen(0, host, r));

// 가짜 Ollama: chat/tags 동작을 테스트가 바꿀 수 있다.
async function fakeUpstream(t) {
  const state = { chatCalls: 0, chatDelay: 0, chatContent: JSON.stringify(fixture), tags: ['test-vision-fixture:latest'], tagsStatus: 200, lastBody: null };
  const srv = http.createServer(async (req, res) => {
    if (req.url === '/api/tags') { res.writeHead(state.tagsStatus, { 'Content-Type': 'application/json' }); return res.end(JSON.stringify({ models: state.tags.map(name => ({ name })) })); }
    let body = ''; for await (const c of req) body += c;
    state.lastBody = JSON.parse(body); state.chatCalls++;
    await new Promise(r => setTimeout(r, state.chatDelay));
    if (res.destroyed) return;
    res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify({ message: { content: state.chatContent } }));
  });
  await listen(srv);
  t.after(() => { srv.closeAllConnections?.(); return new Promise(r => srv.close(r)); });
  state.url = 'http://127.0.0.1:' + srv.address().port + '/api/chat';
  return state;
}

async function start(t, config = {}) {
  const up = await fakeUpstream(t);
  const app = createApp({ HOST: '127.0.0.1', PORT: 0, VISION_MODEL: 'test-vision-fixture', OLLAMA_URL: up.url, ...config });
  await listen(app);
  t.after(() => { app.closeAllConnections?.(); return new Promise(r => app.close(r)); });
  const port = app.address().port, base = 'http://127.0.0.1:' + port;
  const call = (route, data, extra = {}) => fetch(base + route, { method: data ? 'POST' : 'GET', headers: { 'Content-Type': 'application/json', ...extra }, ...(data ? { body: JSON.stringify(data) } : {}) });
  return { app, up, port, base, call };
}

// fetch는 Host 헤더를 바꿀 수 없으므로 http.request 사용
const rawGet = (port, path, headers) => new Promise((resolve, reject) => {
  const r = http.request({ host: '127.0.0.1', port, path, headers }, res => {
    let b = ''; res.on('data', c => b += c); res.on('end', () => resolve({ status: res.statusCode, body: b ? JSON.parse(b) : null }));
  });
  r.on('error', reject); r.end();
});

async function readUntil(reader, re, limit = 20) {
  let text = ''; const dec = new TextDecoder();
  for (let i = 0; i < limit && !re.test(text); i++) { const { value, done } = await reader.read(); if (done) break; text += dec.decode(value); }
  return text;
}

test('원본 동작: 텔레메트리·데모·모델 브리지·잘못된 요청 거부', async t => {
  const { call, up } = await start(t);
  assert.equal((await (await call('/api/health')).json()).hardwareConnected, false);
  const eventStream = await call('/api/events'); const reader = eventStream.body.getReader();
  assert.match(await readUntil(reader, /event: ready/), /event: ready/);
  const a = await (await call('/api/telemetry', { samples: telemetryFor('highway') })).json(); assert.equal(a.events.length, 1);
  assert.match(await readUntil(reader, /synthetic_fixture/), /synthetic_fixture/); await reader.cancel();
  const b = await (await call('/api/telemetry', { samples: telemetryFor('bump') })).json(); assert.equal(b.events.length, 0);
  const fx = await (await call('/api/demo-analyze', { scenario: 'tunnel' })).json(); assert.equal(fx.source, 'fixture_response_not_model_inference');
  assert.equal((await call('/api/demo-analyze', { scenario: 'missing' })).status, 400);
  assert.equal((await call('/api/telemetry', { samples: [{ t: 0, speedKmh: -1, linearG: 0 }] })).status, 400);
  const live = await (await call('/api/analyze', { images: ['/9j/AA=='] })).json();
  assert.equal(live.source, 'local_vision_model'); assert.equal(live.analysis.smoke, 'possible'); assert.equal(up.chatCalls, 1);
  assert.equal(up.lastBody.stream, false); assert.equal(up.lastBody.options.temperature, 0); assert.ok(up.lastBody.format && up.lastBody.format.properties);
  assert.equal(up.lastBody.messages[0].images.length, 1);
  assert.equal((await call('/api/analyze', { images: ['not-an-image'] })).status, 400);
  assert.equal((await call('/api/health', null, { origin: 'https://untrusted.example' })).status, 403);
  assert.equal((await call('/missing')).status, 404);
});

test('sanitize는 행동 지시 문장을 제거한다', async t => {
  const { call } = await start(t);
  const r = await (await call('/api/analyze', { images: ['/9j/AA=='] })).json();
  assert.deepEqual(r.analysis.observations, ['흰색 흐림이 보입니다.']);
});

test('HOST가 루프백이 아닌데 토큰이 없거나 짧으면 시작 시 예외', () => {
  assert.throws(() => createApp({ HOST: '0.0.0.0', PORT: 0 }), /AFTERSTOP_TOKEN/);
  assert.throws(() => createApp({ HOST: '0.0.0.0', PORT: 0, AFTERSTOP_TOKEN: 'short' }), /AFTERSTOP_TOKEN/);
  assert.doesNotThrow(() => createApp({ HOST: '0.0.0.0', PORT: 0, AFTERSTOP_TOKEN: TOKEN }));
});

test('토큰: 없음/틀림은 401, 맞으면 200, health는 토큰 없이 허용', async t => {
  const { call } = await start(t, { AFTERSTOP_TOKEN: TOKEN });
  const r = await call('/api/adapters'); assert.equal(r.status, 401); assert.equal((await r.json()).code, 'unauthorized');
  assert.equal((await call('/api/adapters', null, { Authorization: 'Bearer wrong-token-wrong-token' })).status, 401);
  assert.equal((await call('/api/adapters', null, { Authorization: 'Bearer ' + TOKEN })).status, 200);
  assert.equal((await call('/api/analyze', { images: ['/9j/AA=='] })).status, 401);
  const h = await (await call('/api/health')).json(); assert.equal(h.authRequired, true); assert.equal(h.authorized, false);
  const h2 = await (await call('/api/health', null, { Authorization: 'Bearer ' + TOKEN })).json(); assert.equal(h2.authorized, true);
  assert.equal((await call('/api/events')).status, 401);
  const ok = await call('/api/events?token=' + TOKEN); assert.equal(ok.status, 200); await ok.body.cancel();
});

test('Host: 10.0.2.2:<port> 허용, evil.example·잘못된 포트 거부, 신뢰할 수 없는 Origin 거부', async t => {
  const { port } = await start(t);
  assert.equal((await rawGet(port, '/api/health', { Host: `10.0.2.2:${port}` })).status, 200);
  assert.equal((await rawGet(port, '/api/health', { Host: `localhost:${port}` })).status, 200);
  const r = await rawGet(port, '/api/health', { Host: `evil.example:${port}` }); assert.equal(r.status, 403); assert.equal(r.body.code, 'host_not_allowed');
  assert.equal((await rawGet(port, '/api/health', { Host: '10.0.2.2:1' })).body.code, 'host_not_allowed');
  assert.equal((await rawGet(port, '/api/health', { Host: '10.0.2.2' })).body.code, 'host_not_allowed');
  const o = await rawGet(port, '/api/health', { Host: `10.0.2.2:${port}`, Origin: 'http://evil.example:' + port }); assert.equal(o.body.code, 'origin_not_allowed');
  assert.equal((await rawGet(port, '/api/health', { Host: `10.0.2.2:${port}`, Origin: `http://10.0.2.2:${port}` })).status, 200);
});

test('클라이언트 허용 목록: 정확 일치·CIDR·::ffff: 매핑·거부', () => {
  const list = ['127.0.0.1', '::1', '192.168.0.0/16', '10.1.2.3/32'];
  assert.ok(isClientAllowed('127.0.0.1', list)); assert.ok(isClientAllowed('::1', list));
  assert.ok(isClientAllowed('::ffff:127.0.0.1', list)); assert.ok(isClientAllowed('::ffff:192.168.5.9', list));
  assert.ok(isClientAllowed('192.168.255.255', list)); assert.ok(isClientAllowed('10.1.2.3', list));
  assert.ok(!isClientAllowed('192.169.0.1', list)); assert.ok(!isClientAllowed('10.1.2.4', list));
  assert.ok(!isClientAllowed('8.8.8.8', list)); assert.ok(!isClientAllowed('::2', list)); assert.ok(!isClientAllowed('', list));
  assert.ok(isClientAllowed('1.2.3.4', ['0.0.0.0/0'])); assert.ok(!isClientAllowed('1.2.3.4', ['bad/99']));
});

test('허용되지 않은 클라이언트는 403 client_not_allowed', async t => {
  const { call } = await start(t, { ALLOWED_CLIENTS: '203.0.113.0/24' });
  const r = await call('/api/health'); assert.equal(r.status, 403); assert.equal((await r.json()).code, 'client_not_allowed');
});

test('health: inferenceVerified false, 모델 ready/missing/unreachable', async t => {
  const { call, up } = await start(t);
  let h = await (await call('/api/health')).json();
  assert.equal(h.inferenceVerified, false); assert.equal(h.serverReachable, true); assert.equal(h.model.configured, true);
  assert.equal(h.model.name, 'test-vision-fixture'); assert.equal(h.model.ready, 'not_checked');
  h = await (await call('/api/health?checkModel=1')).json(); assert.equal(h.model.ready, 'ready'); assert.equal(h.inferenceVerified, false);
  up.tags = ['other-model:latest']; h = await (await call('/api/health?checkModel=1')).json(); assert.equal(h.model.ready, 'missing');
  up.tagsStatus = 500; h = await (await call('/api/health?checkModel=1')).json(); assert.equal(h.model.ready, 'unreachable');
  const dead = await start(t, { OLLAMA_URL: 'http://127.0.0.1:1/api/chat' });
  h = await (await dead.call('/api/health?checkModel=1')).json(); assert.equal(h.model.ready, 'unreachable');
});

test('health checkModel은 인증된 경우에만 조회', async t => {
  const { call } = await start(t, { AFTERSTOP_TOKEN: TOKEN });
  assert.equal((await (await call('/api/health?checkModel=1')).json()).model.ready, 'not_checked');
  assert.equal((await (await call('/api/health?checkModel=1', null, { Authorization: 'Bearer ' + TOKEN })).json()).model.ready, 'ready');
});

test('analyze 오류: 비JSON 502, 타임아웃 504, 연결 실패 502, 모델 미설정 503, requestId 반영', async t => {
  const { call, up } = await start(t, { MODEL_TIMEOUT_MS: 200 });
  let r = await call('/api/analyze', { images: ['/9j/AA=='], requestId: 'req_abc-1' }); let j = await r.json();
  assert.equal(r.status, 200); assert.equal(j.requestId, 'req_abc-1'); assert.equal(j.model, 'test-vision-fixture');
  up.chatContent = 'not json at all'; r = await call('/api/analyze', { images: ['/9j/AA=='], requestId: 'r2' }); j = await r.json();
  assert.equal(r.status, 502); assert.equal(j.code, 'model_invalid_json'); assert.equal(j.source, 'error_no_fallback_result'); assert.equal(j.requestId, 'r2'); assert.equal(j.analysis, undefined);
  up.chatContent = JSON.stringify(fixture); up.chatDelay = 1000; r = await call('/api/analyze', { images: ['/9j/AA=='] }); j = await r.json();
  assert.equal(r.status, 504); assert.equal(j.code, 'model_timeout'); assert.equal(j.source, 'error_no_fallback_result'); assert.ok(j.requestId);
  r = await call('/api/analyze', { images: [] }); j = await r.json(); assert.equal(r.status, 400); assert.equal(j.code, 'invalid_images'); assert.equal(j.source, 'error_no_fallback_result');
  const down = await start(t, { OLLAMA_URL: 'http://127.0.0.1:1/api/chat' });
  r = await down.call('/api/analyze', { images: ['/9j/AA=='] }); assert.equal(r.status, 502); assert.equal((await r.json()).code, 'model_request_failed');
  const nomodel = await start(t, { VISION_MODEL: '' });
  r = await nomodel.call('/api/analyze', { images: ['/9j/AA=='] }); j = await r.json(); assert.equal(r.status, 503); assert.equal(j.code, 'model_not_configured');
  r = await nomodel.call('/api/analyze', { images: ['/9j/AA=='], model: 'req-model' }); assert.equal(r.status, 200); assert.equal((await r.json()).model, 'req-model');
});

test('SSE: retry 먼저, id 줄 출력, Last-Event-ID/lastEventId 재생', async t => {
  const { call, base } = await start(t);
  const first = await call('/api/events'); const reader = first.body.getReader();
  const hello = await readUntil(reader, /event: ready/); assert.match(hello, /^retry: 3000\n/);
  await call('/api/telemetry', { samples: telemetryFor('highway') });
  const live = await readUntil(reader, /"eventId":1/);
  assert.match(live, /id: 1\nevent: incident\ndata: .*"eventId":1/);
  await reader.cancel();
  await call('/api/telemetry', { samples: telemetryFor('highway') });
  const r2 = await fetch(base + '/api/events', { headers: { 'Last-Event-ID': '1' } }); const rd2 = r2.body.getReader();
  const t2 = await readUntil(rd2, /id: 2\n/); assert.match(t2, /id: 2\nevent: incident/); assert.doesNotMatch(t2, /id: 1\n/); await rd2.cancel();
  const r3 = await fetch(base + '/api/events?lastEventId=0'); const rd3 = r3.body.getReader();
  const t3 = await readUntil(rd3, /id: 2\n/); assert.match(t3, /id: 1\n/); assert.match(t3, /id: 2\n/); await rd3.cancel();
});
