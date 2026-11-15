/*
 * 멈춘 다음 · 웹 체험판
 * 안드로이드 앱(core/Guidance.kt, Prompts.kt, Scenarios.kt, Pipeline.kt, Telemetry.kt, Vision.kt,
 * ui/AppViewModel.kt, ui/Components.kt)의 문구와 흐름을 그대로 옮겼다. 외부 라이브러리 없음.
 * 모든 신호·장면·AI 결과는 체험용 예시다.
 */
(function () {
  'use strict';

  // ───────── 안내 문구 (Guidance.kt · text 는 녹음과 글자 하나까지 같음) ─────────
  const GUIDES = {
    highway: { label: '고속도로', title: '주변 위험을 먼저 살피세요.', text: '차가 완전히 멈췄고 스스로 안전하게 이동할 수 있다면 차도 밖 안전한 곳으로 대피하세요.', note: '움직여도 안전한지는 직접 살펴 주세요.' },
    tunnel: { label: '터널', title: '현장 비상 표지를 확인하세요.', text: '안전하게 이동할 수 있는지 살피고 대피 방향은 현장 표지로 확인하세요.', note: '출구 방향은 앱이 정하지 않아요. 터널 안 표지를 따라 주세요.' },
    unknown: { label: '위치 모름', title: '주변 위험부터 확인하세요.', text: '차량이 멈췄는지와 주변에서 다가오는 위험이 있는지 살피세요.', note: '위치를 몰라서 도로 종류나 대피 방향은 말씀드리지 않아요.' },
    observed: { label: '영상에서 본 것', title: '연기처럼 보이는 징후가 있어요.', text: '실제로 연기나 불꽃이 보이고 안전하게 이동할 수 있다면 차량에서 떨어진 안전한 곳으로 대피하세요.', note: 'AI가 영상에서 본 것이에요. 불이 났다고 확정한 건 아니에요.' },
    smoke: { label: '알려 주신 내용', title: '연기·불꽃을 알려주셨어요.', text: '스스로 안전하게 이동할 수 있다면 차량에서 떨어진 안전한 곳으로 대피하세요.', note: '알려 주신 내용으로 안내해요.' },
    immobile: { label: '알려 주신 내용', title: '이동이 어려운 상황이에요.', text: '이동이 어렵다는 사실을 알리고 구조 요청을 우선하세요.', note: '무리해서 내리라고 안내하지 않아요. 구조 요청이 먼저예요.' },
    combinedObserved: { label: '위험이 겹쳤어요', title: '이동이 어려운 위험 상황이에요.', text: '움직이기 어렵다는 사실과 실제로 보이는 위험을 구조 요청 시 알려주세요.', note: '연기는 AI가 영상에서 봤고, 움직이기 어렵다는 건 알려 주셨어요.' },
    combined: { label: '알려 주신 내용', title: '두 가지 위험을 함께 알려주세요.', text: '연기·불꽃이 보이고 움직이기 어렵다는 두 가지 상황을 구조 요청 시 함께 알려주세요.', note: '어떻게 움직여야 안전한지는 앱이 판단하지 못해요.' },
    help: { label: '도움 요청', title: '도움 요청을 우선하세요.', text: '필요하다면 차량 SOS나 긴급전화로 도움을 요청하세요.', note: '앱이 대신 신고하지 않아요. 119 버튼을 누르면 전화 화면이 열려요.' },
    facts: { label: '전화할 때', title: '확인한 사실만 전달하세요.', text: '알고 있는 도로명과 진행 방향, 눈앞의 상황을 알려주세요.', note: '다친 정도와 정확한 차로는 앱이 알 수 없어요.' },
    factsUnknown: { label: '전화할 때', title: '위치가 미확인임을 알려주세요.', text: '현재 위치를 확인하지 못했다는 점과 눈앞의 상황을 알려주세요.', note: '모르는 건 짐작해서 말하지 않아도 돼요.' },
  };
  const WARN_KEYS = ['observed', 'smoke', 'immobile', 'combined', 'combinedObserved'];

  // ───────── 보험사 연결 대화 문구 (Prompts.kt) ─────────
  const PROMPTS = {
    insAsk: '다친 사람이 있으면 119가 먼저입니다. 안전한 곳이라면 보험사에 연결해 드릴까요?',
    insConnect: '보험사에 연결합니다. 화면에 정리한 사고 정보를 읽어 주세요.',
    insLater: '알겠습니다. 필요할 때 보험사 연결 버튼을 눌러 주세요.',
    insRetry: '잘 듣지 못했어요. 연결하려면 연결해 줘라고 말하거나 버튼을 눌러 주세요.',
  };

  // ───────── 처리 단계 (Pipeline.kt Stage) ─────────
  const STAGES = [
    ['INPUT', '주행 확인'], ['DETECT', '충격 감지'], ['BUFFER', '사고 장면 저장'],
    ['ANALYZE', 'AI로 상황 확인'], ['GUIDE', '음성 안내'], ['RECORD', '기록'],
  ];
  const stageIndex = (k) => STAGES.findIndex((s) => s[0] === k);

  // ───────── 체험 상황 (Scenarios.kt DemoScenario) ─────────
  const SCENARIOS = [
    { key: 'highway', label: '고속도로 · 추돌 후 정차', place: '고속도로 · 위치 예시', smoke: false, tunnel: false, limited: false, collision: true },
    { key: 'tunnel', label: '터널 · 연기 의심', place: '터널 · 위치 예시', smoke: true, tunnel: true, limited: false, collision: true },
    { key: 'lowlight', label: '야간 · 어두운 화면', place: '위치 미확인', smoke: false, tunnel: false, limited: true, collision: true },
    { key: 'bump', label: '과속방지턱 · 안내 안 함', place: '일반 도로 · 위치 예시', smoke: false, tunnel: false, limited: false, collision: false },
  ];
  const guidanceScenario = (s) => (s.key === 'highway' ? 'highway' : s.key === 'tunnel' ? 'tunnel' : 'unknown');

  /** DemoFixtures.telemetryFor · 100ms 간격 30개 예시 신호 */
  function telemetryFor(s) {
    const out = [];
    for (let i = 0; i < 30; i++) {
      let speed;
      if (s.key === 'bump') speed = i < 12 ? 36 : 28;
      else speed = i < 12 ? 72 : i < 16 ? 72 - (i - 11) * 17 : 0;
      out.push({ t: i * 100, speedKmh: speed, linearG: i === 12 ? 3.1 : 0.04 });
    }
    return out;
  }

  /** DemoFixtures.demoObservation · 미리 준비한 예시 결과 */
  function demoObservation(s) {
    return {
      scene: s.tunnel ? 'tunnel' : s.limited ? 'unknown' : 'road',
      visibility: s.limited ? 'unusable' : 'usable',
      smoke: s.limited ? 'unknown' : s.smoke ? 'possible' : 'not_observed',
      fire: 'unknown',
      evidence_frames: s.limited ? [] : [2, 3, 4],
      observations: s.limited ? [] : [
        s.tunnel ? '터널 구조물이 보입니다.' : '도로 위에 차량이 정차한 장면입니다.',
        s.smoke ? '차량 앞쪽에 흰색 흐림이 보입니다.' : '선택한 프레임에서는 뚜렷한 연기 단서를 관찰하지 못했습니다.',
      ],
    };
  }

  /** VisionSanitizer.sanitize · 실제 결과와 같은 검사를 거친다 */
  const INSTRUCTION = /대피하|이동하|하차하|하세요|하십시오|피하세요|좌측으로|우측으로/;
  function sanitize(raw, frameCount) {
    if (raw === null || typeof raw !== 'object' || frameCount < 1) throw new Error('형식 오류');
    const obj = Array.isArray(raw) ? {} : raw;
    const one = (k, allowed, fb) => (typeof obj[k] === 'string' && allowed.includes(obj[k]) ? obj[k] : fb);
    const scene = one('scene', ['tunnel', 'road', 'unknown'], 'unknown');
    const visibility = one('visibility', ['usable', 'limited', 'unusable'], 'unusable');
    const cues = ['observed', 'possible', 'not_observed', 'unknown'];
    const evidence = [...new Set((Array.isArray(obj.evidence_frames) ? obj.evidence_frames : [])
      .filter((d) => typeof d === 'number' && Number.isInteger(d) && d >= 0 && d < frameCount))].slice(0, 6);
    const smoke = one('smoke', cues, 'unknown');
    const fire = one('fire', cues, 'unknown');
    const canUse = visibility !== 'unusable' && evidence.length > 0;
    const observations = !canUse ? [] : (Array.isArray(obj.observations) ? obj.observations : [])
      .filter((x) => typeof x === 'string' && !INSTRUCTION.test(x)).slice(0, 5).map((x) => x.slice(0, 160));
    const risky = ['observed', 'possible'];
    return {
      scene: canUse ? scene : 'unknown', visibility,
      smoke: canUse ? smoke : 'unknown', fire: canUse ? fire : 'unknown',
      evidenceFrames: evidence, observations,
      inferredRisk: canUse && (risky.includes(smoke) || risky.includes(fire)),
    };
  }

  /** RuleBasedIncidentDetector · 시제품 시험용 수치 */
  function makeDetector() {
    const IMPACT_G = 2.5, IMPACT_WINDOW_MS = 1500, SPEED_WINDOW_MS = 2000, RETAIN_MS = 3000,
      SPEED_DROP_KMH = 25, STOP_KMH = 5, DEDUPE_MS = 10000;
    let samples = [], lastTime = -Infinity, lastEvent = -Infinity;
    return {
      push(s) {
        if (!(s.t > lastTime) || s.speedKmh < 0 || s.speedKmh > 250 || s.linearG < 0 || s.linearG > 30) throw new Error('Invalid telemetry');
        lastTime = s.t;
        samples.push(s);
        samples = samples.filter((x) => s.t - x.t <= RETAIN_MS);
        const impact = samples.find((x) => s.t - x.t <= IMPACT_WINDOW_MS && x.linearG >= IMPACT_G);
        const recent = samples.filter((x) => s.t - x.t <= SPEED_WINDOW_MS);
        const before = impact ? recent.filter((x) => x.t <= impact.t) : [];
        const prior = before.length ? Math.max(...before.map((x) => x.speedKmh)) : 0;
        const stop = !!impact && s.t >= impact.t && s.speedKmh <= STOP_KMH;
        const drop = prior - s.speedKmh >= SPEED_DROP_KMH;
        if (impact && stop && drop && s.t - lastEvent > DEDUPE_MS) {
          lastEvent = s.t;
          return { t: s.t, impactG: impact.linearG, speedDropKmh: prior - s.speedKmh };
        }
        return null;
      },
    };
  }

  /** DefaultGuidancePolicy.resolve · 이동 어려움 알림이 하차·이동 안내보다 우선한다 */
  function resolveKeys(g) {
    const unknown = g.lost || g.scenario === 'unknown';
    const observed = g.analysisReady && g.observedSmoke;
    let key;
    if (g.immobile && (g.smoke || observed)) key = g.smoke ? 'combined' : 'combinedObserved';
    else if (g.immobile) key = 'immobile';
    else if (g.smoke) key = 'smoke';
    else if (observed) key = 'observed';
    else if (g.analysisKind === 'AI' && g.visionScene === 'tunnel') key = 'tunnel';
    else if (unknown) key = 'unknown';
    else key = g.scenario;
    return [key, 'help', unknown ? 'factsUnknown' : 'facts'];
  }

  const freshGuidance = () => ({ scenario: 'unknown', lost: false, analysisReady: false, observedSmoke: false, smoke: false, immobile: false, analysisKind: 'NONE', visionScene: null });

  // ───────── 상태 ─────────
  const DEMO_IDLE_HEAD = '사고가 나면 이렇게 안내해요.';
  const DEMO_IDLE_DESC = '체험 시작을 누르면 충격 감지부터 음성 안내, 보험사 연결까지 순서대로 보여 드려요.';
  const NO_FRAMES = '아직 저장한 장면이 없어요';
  const DEMO_INSURER = '예시 보험사';

  const S = {
    scenario: SCENARIOS[0],
    running: false, stage: null, head: DEMO_IDLE_HEAD, desc: DEMO_IDLE_DESC, log: [],
    clock: '0.0초', speed: 0, impact: 0, sceneFrame: 0, sceneEvent: false,
    guidance: freshGuidance(), started: false, step: 0, paused: false, inCall: false, showFacts: false,
    autoVoice: true, audioMsg: '하윤 목소리 안내 준비됨', audioPlaying: false,
    frames: [], observation: null, analysisSource: null, analysisBusy: false, analysisStatus: NO_FRAMES,
    lastIncident: false, incidentAt: null, pendingRecord: false,
    insStage: 'IDLE', insMessage: '', insSay: null, insPending: false,
  };
  let epoch = 0;
  const keys = () => resolveKeys(S.guidance);
  const currentKey = () => keys()[S.step];

  // ───────── 음성 (AssetAudioPlayer) ─────────
  const audio = { el: null, token: 0, playingKey: null };
  const guideFailed = {};
  const promptOk = {};
  let promptsProbed = false;

  function probePrompts() {
    if (promptsProbed) return;
    promptsProbed = true;
    Object.keys(PROMPTS).forEach((k) => {
      const a = new Audio();
      a.preload = 'metadata';
      a.addEventListener('loadedmetadata', () => { promptOk[k] = true; render(); });
      a.addEventListener('error', () => { promptOk[k] = false; });
      a.src = 'audio/prompts/' + k + '.mp3';
    });
  }
  const hasClip = (k) => (k in GUIDES ? !guideFailed[k] : promptOk[k] === true);

  function setAudio(playingKey, msg) {
    audio.playingKey = playingKey;
    S.audioPlaying = playingKey !== null;
    S.audioMsg = msg;
    render();
  }

  function play(key) {
    stopAudio(null);
    if (!hasClip(key)) { setAudio(null, '이 안내는 녹음이 없어 화면 글로 안내해요'); onAudioUnavailable(key); return; }
    const my = ++audio.token;
    let settled = false;
    const el = new Audio((key in GUIDES ? 'audio/' : 'audio/prompts/') + key + '.mp3');
    audio.el = el;
    const fail = (blocked) => {
      if (settled || my !== audio.token) return;
      settled = true;
      audio.el = null;
      if (!blocked && key in GUIDES) guideFailed[key] = true;
      setAudio(null, blocked ? '브라우저가 소리를 막았어요 · 화면 글로 안내해요' : '음성을 재생하지 못했어요 · 화면 글로 안내해요');
      onAudioUnavailable(key);
    };
    el.addEventListener('playing', () => { if (my === audio.token) setAudio(key, '하윤이 안내하고 있어요'); });
    el.addEventListener('ended', () => {
      if (settled || my !== audio.token) return;
      settled = true;
      audio.el = null;
      setAudio(null, '안내를 마쳤어요 · 다시 듣기 가능');
      onAudioFinished(key);
    });
    el.addEventListener('error', () => fail(false));
    const p = el.play();
    if (p && p.catch) p.catch((e) => fail(e && e.name === 'NotAllowedError'));
  }

  function stopAudio(reason) {
    audio.token++;
    if (audio.el) { try { audio.el.pause(); } catch (e) { /* 무시 */ } audio.el = null; }
    if (reason !== null || audio.playingKey !== null) setAudio(null, reason !== null ? reason : '음성을 멈췄어요');
  }

  /** 음성이 없거나 막혔을 때도 흐름이 멈추지 않게, 끝난 것처럼 다음 단계로 넘긴다. */
  function onAudioUnavailable(key) {
    if (key === 'insAsk' && S.insStage === 'ASKING') startListening();
    else if (key in GUIDES && S.step === 0) maybeAskInsurance();
  }

  function onAudioFinished(key) {
    if (key === 'insAsk' && S.insStage === 'ASKING') startListening();
    else if (key in GUIDES && S.step === 0) maybeAskInsurance();
  }

  // ───────── 공통 안내 (AppViewModel) ─────────
  function speak() {
    if (!S.started || S.paused || S.inCall || S.showFacts || !S.autoVoice) return;
    play(currentKey());
  }
  function speakThenAsk() {
    speak();
    if (!S.autoVoice || !hasClip(currentKey())) maybeAskInsurance();
  }
  function replay() { if (!S.started || S.paused || S.inCall) return; play(currentKey()); }
  function startCommonGuidance() { probePrompts(); Object.assign(S, { started: true, step: 0, paused: false, showFacts: false }); render(); speak(); }
  function nextGuide() { if (S.paused || S.inCall) return; S.step = (S.step + 1) % 3; render(); speak(); }
  function togglePause() { S.paused = !S.paused; render(); if (S.paused) stopAudio('안내를 잠시 멈췄어요'); else speak(); }
  function toggleSmoke() { S.guidance.smoke = !S.guidance.smoke; Object.assign(S, { step: 0, started: true, paused: false }); render(); speak(); }
  function toggleImmobile() { S.guidance.immobile = !S.guidance.immobile; Object.assign(S, { step: 0, started: true, paused: false }); render(); speak(); }
  function toggleCall() {
    stopAudio('체험용 도움 요청 화면 · 음성 안내 멈춤');
    if (S.inCall) { S.inCall = false; S.paused = S.started; } else S.inCall = true;
    render();
  }
  function showFacts(show) { if (show) stopAudio(null); S.showFacts = show; render(); }
  function setAutoVoice(on) { S.autoVoice = on; if (!on) stopAudio(null); render(); }

  // ───────── 보험사 연결 ─────────
  function armInsurance() { Object.assign(S, { insPending: true, insStage: 'IDLE', insMessage: '', insSay: null, incidentAt: new Date() }); }
  function resetInsurance() { Object.assign(S, { insPending: false, insStage: 'IDLE', insMessage: '', insSay: null, incidentAt: null }); }

  /** 위험 신호(이동 어려움·연기)가 있으면 119·구조 요청이 먼저이므로 자동으로 묻지 않는다. */
  function maybeAskInsurance() {
    if (!S.insPending || !S.started || S.paused || S.inCall || S.showFacts || S.analysisBusy) return;
    if (S.stage !== 'GUIDE') return;
    S.insPending = false;
    const g = S.guidance;
    const danger = g.immobile || g.smoke || (g.analysisReady && g.observedSmoke);
    if (danger) { S.insMessage = '위험 신호가 있어 보험사 연결은 묻지 않았어요. 구조 요청을 먼저 하세요.'; render(); return; }
    offerInsurance();
  }

  function offerInsurance() {
    probePrompts();
    S.insPending = false;
    Object.assign(S, { insStage: 'ASKING', insSay: 'insAsk', insMessage: DEMO_INSURER + '에 연결할까요? 말로 대답하거나 버튼을 눌러 주세요.' });
    render();
    revealInsurance();
    if (S.autoVoice && hasClip('insAsk')) play('insAsk'); else startListening();
  }

  /** 웹 체험판에는 음성 인식이 없어 버튼으로 대답한다. */
  function startListening() {
    if (S.insStage !== 'ASKING' && S.insStage !== 'LISTENING') return;
    S.insStage = 'ASKING';
    S.insMessage = '웹 체험판에서는 버튼으로 대답해 주세요. 앱에서는 "연결해 줘"라고 말로 대답할 수 있어요.';
    render();
  }

  /** 사용자가 "연결해 줘" 또는 버튼으로 승낙했을 때만 연결한다. 체험에서는 전화하지 않는다. */
  function connectInsurance() {
    Object.assign(S, { insStage: 'CONNECTED', insSay: 'insConnect', insMessage: '체험 중이라 실제로 전화하지 않아요. 실제 사용에서는 ' + DEMO_INSURER + '에 바로 연결돼요.' });
    render();
    if (S.autoVoice && hasClip('insConnect')) play('insConnect'); else stopAudio(null);
  }

  function declineInsurance() {
    Object.assign(S, { insStage: 'DECLINED', insSay: 'insLater', insMessage: '보험사 연결을 미뤘어요. 필요할 때 다시 누르세요.' });
    render();
    if (S.autoVoice && hasClip('insLater')) play('insLater'); else stopAudio(null);
  }

  // ───────── 체험 진행 (startDemo · onDemoEvent) ─────────
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const isCurrent = (e) => e === epoch;
  function log(t) { S.log = S.log.concat(t).slice(-4); }
  function headline(h, d) { S.head = h; S.desc = d; }

  function resetGuidance() {
    stopAudio(null);
    Object.assign(S, { guidance: freshGuidance(), started: false, step: 0, paused: false, inCall: false, showFacts: false, lastIncident: false, pendingRecord: false });
    resetInsurance();
  }
  function invalidateAnalysis() {
    epoch++;
    Object.assign(S, { frames: [], observation: null, analysisSource: null, analysisBusy: false, analysisStatus: NO_FRAMES });
    Object.assign(S.guidance, { analysisKind: 'NONE', analysisReady: false, observedSmoke: false, visionScene: null, scenario: 'unknown' });
  }

  function selectScenario(sc) {
    if (S.running) return;
    S.scenario = sc;
    Object.assign(S, { stage: null, log: [], clock: '0.0초', speed: 0, impact: 0, sceneFrame: 0, sceneEvent: false, head: DEMO_IDLE_HEAD, desc: DEMO_IDLE_DESC });
    invalidateAnalysis(); resetGuidance(); render();
  }

  function stopDemo(message) {
    if (S.running) {
      epoch++; stopAudio(null);
      if (message) log('체험을 멈췄어요. 다시 시작하면 처음부터 보여 드려요.');
    }
    S.running = false; S.analysisBusy = false;
    render();
  }

  async function startDemo() {
    probePrompts();
    stopDemo(false);
    invalidateAnalysis(); resetGuidance();
    const sc = S.scenario;
    const my = ++epoch;
    Object.assign(S, { running: true, stage: 'INPUT', log: [] });
    headline('주행 중이에요.', '블랙박스 화면과 차량 신호를 살피고 있어요. 체험용 예시 화면이에요.');
    log('주행 중 · 체험용 화면과 예시 신호');
    render();

    const detector = makeDetector();
    let incident = null;
    for (const sample of telemetryFor(sc)) {
      if (!isCurrent(my)) return;
      Object.assign(S, { sceneFrame: sample.t / 100, sceneEvent: incident !== null, clock: (sample.t / 1000).toFixed(1) + '초', speed: sample.speedKmh, impact: sample.linearG });
      const e = detector.push(sample);
      if (e && !incident) {
        incident = e;
        Object.assign(S, { stage: 'DETECT', lastIncident: true, started: true, step: 0, paused: false });
        armInsurance();
        headline('충격을 감지했어요.', '큰 충격 뒤 속도가 크게 줄고 차가 멈췄어요. 바로 기본 안내를 시작해요.');
        log('충격 감지 · 바로 기본 안내 시작');
        render();
        speak();
      } else render();
      await sleep(100);
    }
    if (!isCurrent(my)) return;

    if (!incident) {
      S.stage = 'DETECT';
      headline('사고가 아니라고 봤어요.', '덜컹하는 충격은 있었지만 차가 그대로 달렸어요. 안내하지 않고 주행을 이어가요.');
      log('과속방지턱 · 충격만으로는 안내하지 않아요');
      Object.assign(S, { running: false, pendingRecord: true });
      render();
      return;
    }

    // 사고 전후 장면 6개 (SyntheticDemoVideoSource.framesFor 와 같은 간격)
    const frames = [];
    for (let i = 0; i < 6; i++) {
      const t = incident.t - 5000 + i * 1600;
      if (t >= incident.t - 5000 && t <= incident.t + 3000) frames.push({ index: frames.length, frame: i, offset: (t - incident.t) / 1000 });
    }
    Object.assign(S, { frames, stage: 'BUFFER', analysisStatus: '장면 ' + frames.length + '개 저장' });
    headline('사고 장면을 저장했어요.', '사고 전후 장면 ' + frames.length + '개를 저장했어요.');
    log('사고 전후 장면 ' + frames.length + '개 저장');
    render();
    await sleep(850);
    if (!isCurrent(my)) return;

    Object.assign(S, { stage: 'ANALYZE', analysisBusy: true, analysisStatus: 'AI가 장면을 보고 있어요 · 체험용 예시 결과' });
    headline('AI가 장면을 살펴보고 있어요.', '체험에서는 미리 준비한 결과를 보여 드려요. 실제 결과와 똑같이 검사해요.');
    log('AI 확인 중 · 체험용 예시 결과');
    render();
    await sleep(1500);
    if (!isCurrent(my)) return;

    // 예시 결과를 실제 응답과 같은 형식(JSON)으로 만든 뒤 같은 검사를 거친다
    const o = sanitize(JSON.parse(JSON.stringify(demoObservation(sc))), frames.length);
    Object.assign(S.guidance, { scenario: guidanceScenario(sc), analysisKind: 'DEMO', analysisReady: true, observedSmoke: o.inferredRisk, visionScene: o.scene, lost: false });
    Object.assign(S, { observation: o, analysisSource: 'FIXTURE', started: true, step: 0, paused: S.inCall, showFacts: false, analysisStatus: '체험용 예시 결과예요', stage: 'GUIDE', analysisBusy: false });
    headline(
      sc.limited ? '어두워서 판단을 미뤘어요.' : sc.smoke ? '연기처럼 보이는 게 있어요.' : '지금 할 일을 알려 드려요.',
      sc.limited ? '확실하지 않은 건 말하지 않고, 꼭 필요한 안내만 드려요.'
        : sc.smoke ? '그에 맞춰 안내를 바꿨어요. 움직이기 어렵다면 아래 버튼으로 알려 주세요.'
          : '움직이기 어렵거나 연기가 보이면 아래 버튼으로 알려 주세요. 안내가 바뀌어요.'
    );
    log('AI 확인 끝 · ' + (o.evidenceFrames.length === 0 ? '판단 미룸' : '근거 장면 ' + o.evidenceFrames.map((i) => i + 1).join(', ')) + ' · 하윤 음성 안내');
    S.running = false; S.pendingRecord = true;
    render();
    speakThenAsk();
  }

  function saveRecord() {
    if (!S.pendingRecord || S.running) return;
    S.pendingRecord = false;
    S.stage = 'RECORD';
    log('앱에서는 여기서 기록을 남겨요 · 영상 원본은 저장하지 않아요');
    render();
  }

  // ───────── 도로 장면 그림 (DemoSceneRenderer 이식) ─────────
  function ellipseTop(ctx, cx, cy, rx, ry) { ctx.beginPath(); ctx.ellipse(cx, cy, rx, ry, 0, Math.PI, Math.PI * 2); }
  function drawScene(ctx, s, frame, event) {
    const w = 960, h = 400, night = s.limited;
    const g = ctx.createLinearGradient(0, 0, 0, h);
    g.addColorStop(0, night ? '#101624' : s.tunnel ? '#29313E' : '#9CB8CB');
    g.addColorStop(1, night ? '#26354A' : '#D0DCE2');
    ctx.fillStyle = g; ctx.fillRect(0, 0, w, h);
    if (s.tunnel) {
      ctx.fillStyle = '#344254';
      ctx.fillRect(0, h * 0.53 - 10, w, h);
      ellipseTop(ctx, w / 2, h * 0.53, w / 2, h * 0.7); ctx.closePath(); ctx.fill();
      ctx.strokeStyle = '#617184'; ctx.lineWidth = 5;
      for (let i = 0; i < 5; i++) { ellipseTop(ctx, w / 2, h * 0.55, w * (0.48 - i * 0.065), h * (0.65 - i * 0.08)); ctx.stroke(); }
      // 터널 조명
      ctx.fillStyle = 'rgba(255, 214, 140, .75)';
      for (let i = 0; i < 6; i++) { const y = 70 + i * 14, x = 140 + i * 52; ctx.fillRect(x, y, 26 - i * 3, 5); ctx.fillRect(w - x - (26 - i * 3), y, 26 - i * 3, 5); }
    } else {
      ctx.fillStyle = night ? '#1C2B36' : '#7B9292';
      ctx.beginPath(); ctx.moveTo(0, 160); ctx.lineTo(270, 124); ctx.lineTo(465, 176); ctx.lineTo(740, 132); ctx.lineTo(w, 162); ctx.lineTo(w, h); ctx.lineTo(0, h); ctx.closePath(); ctx.fill();
    }
    ctx.fillStyle = night ? '#262E3E' : '#526274';
    ctx.beginPath(); ctx.moveTo(w * 0.41, 164); ctx.lineTo(w * 0.59, 164); ctx.lineTo(w * 0.98, h); ctx.lineTo(w * 0.02, h); ctx.closePath(); ctx.fill();
    ctx.strokeStyle = '#DAE2EB'; ctx.lineWidth = 3;
    [-1, 1].forEach((side) => { ctx.beginPath(); ctx.moveTo(w / 2 + side * 82, 171); ctx.lineTo(w / 2 + side * 400, h); ctx.stroke(); });
    ctx.lineWidth = 5; ctx.strokeStyle = '#D9E0E8';
    for (let i = 0; i < 4; i++) {
      const y = 190 + ((i * 48 + frame * 8) % 170), q = (y - 160) / 200;
      ctx.beginPath(); ctx.moveTo(w / 2, y); ctx.lineTo(w / 2, y + 10 + q * 14); ctx.stroke();
    }
    const carX = 510 + (event ? 12 : 0), carY = 205 + (event ? 12 : 0);
    ctx.fillStyle = '#D9E4EE'; roundRect(ctx, carX, carY, 108, 52, 10); ctx.fill();
    ctx.fillStyle = '#32475E'; ctx.fillRect(carX + 18, carY + 7, 71, 16);
    ctx.fillStyle = event ? '#FF645F' : '#C65A5A';
    ctx.fillRect(carX + 7, carY + 36, 20, 7); ctx.fillRect(carX + 80, carY + 36, 20, 7);
    if (s.smoke && event) {
      for (let i = 0; i < 7; i++) {
        ctx.fillStyle = 'rgba(224, 229, 232, ' + (0.12 + i * 0.025) + ')';
        ctx.beginPath(); ctx.ellipse(590 + i * 15, 200 - i * 11, 32 + i * 4, 19 + i * 4, 0, 0, Math.PI * 2); ctx.fill();
      }
    }
    if (night) {
      ctx.fillStyle = 'rgba(7, 16, 27, .6)'; ctx.fillRect(0, 0, w, h);
      ctx.fillStyle = '#D5DDED';
      for (let i = 0; i < 12; i++) ctx.fillRect(38 + i * 76, 80 + (i % 3) * 18, 2, 2);
    }
    ctx.fillStyle = '#172333'; ellipseTop(ctx, w / 2, h + 80, w * 0.65, 110); ctx.closePath(); ctx.fill();
    ctx.fillStyle = 'rgba(255, 255, 255, .85)';
    ctx.font = '600 22px "Pretendard Variable", Pretendard, sans-serif';
    ctx.fillText('DEMO · 실제 촬영 영상 아님', 24, h - 22);
  }
  function roundRect(ctx, x, y, w, h, r) {
    ctx.beginPath(); ctx.moveTo(x + r, y); ctx.lineTo(x + w - r, y); ctx.quadraticCurveTo(x + w, y, x + w, y + r);
    ctx.lineTo(x + w, y + h - r); ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h); ctx.lineTo(x + r, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - r); ctx.lineTo(x, y + r); ctx.quadraticCurveTo(x, y, x + r, y); ctx.closePath();
  }

  // ───────── 화면 그리기 ─────────
  const $ = (id) => document.getElementById(id);
  const esc = (t) => String(t).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const HINT = { observed: '보여요', possible: '있는 것 같아요', not_observed: '안 보여요', unknown: '모르겠어요' };

  function whenLabel(v) {
    if (Math.abs(v) < 0.05) return '사고 순간';
    const a = Math.abs(v);
    const n = Math.abs(a - Math.round(a)) < 0.05 ? a.toFixed(0) : a.toFixed(1);
    return v < 0 ? n + '초 전' : n + '초 후';
  }
  function observedSummary() {
    const o = S.observation;
    if (!o) return S.analysisBusy ? 'AI가 보는 중이에요 · 그동안 기본 안내를 이어가요' : '아직 확인하지 않았어요';
    if (o.visibility === 'unusable' || o.evidenceFrames.length === 0) return '화면이 잘 안 보여 판단을 미뤘어요';
    return '연기 ' + HINT[o.smoke] + ' · 불꽃 ' + HINT[o.fire] + (!o.inferredRisk ? ' · 안 보인다고 안전한 건 아니에요' : '');
  }
  function contextNote(key) {
    if (['smoke', 'immobile', 'combined'].includes(key) || key === 'help' || key.indexOf('facts') === 0) return GUIDES[key].note;
    if (S.analysisSource === 'FIXTURE') return '체험용 예시 결과예요. 다친 정도나 움직여도 안전한지는 알 수 없어요.';
    return GUIDES[key].note;
  }
  const kv = (k, v, n) => '<div class="kv"><span class="kv-k">' + esc(k) + '</span><span class="kv-v">' + esc(v) + '</span>' + (n ? '<span class="kv-n">' + esc(n) + '</span>' : '') + '</div>';
  const btn = (label, act, cls, extra) => '<button type="button" class="btn ' + (cls || 'btn-secondary') + '" data-act="' + act + '"' + (extra || '') + '>' + esc(label) + '</button>';
  const isUnknownPlace = () => S.guidance.lost || S.guidance.scenario === 'unknown';

  function timeLabel(d) {
    const h = d.getHours(), m = d.getMinutes();
    return (h < 12 ? '오전 ' : '오후 ') + (h % 12 === 0 ? 12 : h % 12) + '시 ' + m + '분';
  }

  function callSheetHtml() {
    const g = S.guidance;
    const what = [
      S.lastIncident ? '충격 뒤 차가 멈췄어요' : null,
      g.immobile ? '움직이기 어려워요' : null,
      g.smoke ? '연기·불꽃이 보여요' : null,
      S.observation && S.observation.inferredRisk ? '영상에서 연기처럼 보이는 게 있어요(AI 추정)' : null,
    ].filter(Boolean).join(' · ') || '눈앞의 상황을 그대로 말해 주세요';
    return '<div class="call-view"><p class="call-title">통화할 때 읽어 주세요</p><div class="kv-list">' +
      kv('언제', S.incidentAt ? timeLabel(S.incidentAt) + ' 쯤' : '시간 모름') +
      kv('어디서', isUnknownPlace() ? '위치를 몰라요 · 주변 표지판이나 도로명을 알려 주세요' : S.scenario.place) +
      kv('무슨 일', what) +
      kv('내 정보', '앱 설정에서 차량번호·증권번호를 넣으면 여기에 보여요') +
      kv('앱이 모르는 것', '다친 정도 · 과실 · 움직여도 안전한지') +
      '</div></div>';
  }

  function sayHtml(key) {
    if (!key) return '';
    const mute = hasClip(key) && S.autoVoice ? '' : '<span class="say-mute">' + (S.autoVoice ? '녹음이 아직 없어 글로 보여 드려요' : '음성 안내 꺼짐 · 글로 보여 드려요') + '</span>';
    return '<div class="say"><span class="say-who">하윤</span><span>' + esc(PROMPTS[key]) + mute + '</span></div>';
  }

  function insuranceHtml() {
    const name = DEMO_INSURER;
    let body = '';
    if (S.insStage === 'ASKING' || S.insStage === 'LISTENING') {
      body = '<p class="ins-ask">' + esc(name) + '에 연결할까요?</p>' + sayHtml(S.insSay) +
        (S.insMessage ? '<p class="ins-msg">' + esc(S.insMessage) + '</p>' : '') +
        '<div class="g-btns">' + btn('연결해 줘', 'insConnect', 'btn-primary btn-lg') + btn('나중에', 'insLater', 'btn-secondary btn-lg') + '</div>';
    } else if (S.insStage === 'CONNECTED') {
      body = '<p class="ins-msg strong">' + esc(S.insMessage) + '</p>' + sayHtml(S.insSay) +
        btn('통화할 때 읽을 내용', 'factsOn', 'btn-primary btn-block');
    } else if (S.insStage === 'DECLINED') {
      body = '<p class="ins-msg">' + esc(S.insMessage) + '</p>' + sayHtml(S.insSay) +
        btn('지금 ' + name + '에 연결', 'insOffer', 'btn-secondary btn-block');
    } else {
      const danger = S.insMessage.indexOf('위험 신호') === 0;
      body = '<p class="ins-msg' + (danger ? ' warn' : '') + '">' + esc(S.insMessage || '안전을 확인한 뒤, 한 번 묻고 ' + name + '에 연결해 드려요.') + '</p>' +
        btn(name + '에 연결', 'insOffer', 'btn-secondary btn-block');
    }
    return '<div class="ins" id="insCard"><div class="ins-head"><span class="icon-circle" aria-hidden="true">☎</span><span class="ins-title">보험사 연결</span><span class="badge warn">체험</span></div>' + body + '</div>';
  }

  function guideHtml() {
    const help = '119 도움 요청 · 체험';
    if (S.inCall) {
      return '<div class="call-view"><p class="call-title">119 도움 요청 · 체험</p>' +
        '<p class="sub">체험 중이라 실제로 전화하지 않아요. 실제 사용에서는 119가 입력된 전화 화면이 열려요.</p>' +
        '<p class="g-text">' + esc(GUIDES.help.text) + '</p>' + btn('체험 화면 닫기', 'call', 'btn-danger btn-lg btn-block') + '</div>';
    }
    if (S.showFacts) return callSheetHtml() + btn('안내로 돌아가기', 'factsOff', 'btn-primary btn-lg btn-block');
    if (!S.started) {
      return '<div class="pre-start"><div class="g-row"><span class="badge">안내 전</span></div>' +
        '<p>체험을 시작하면 충격을 감지한 순간 바로 안내가 시작돼요.</p>' +
        btn('안내 바로 듣기', 'listenNow', 'btn-primary btn-lg btn-block') + btn(help, 'call', 'btn-danger btn-block') + '</div>';
    }
    const key = currentKey(), g = GUIDES[key];
    const place = isUnknownPlace() ? '위치를 몰라요' : S.scenario.place;
    const voiceMsg = S.autoVoice ? S.audioMsg : '음성 안내 꺼짐 · 화면 글로 안내해요';
    return '' +
      '<div class="g-row"><span class="g-step">지금 할 일 ' + (S.step + 1) + '/3</span><span class="badge ' + (WARN_KEYS.includes(key) ? 'warn' : 'ok') + '">' + esc(g.label) + '</span>' + (S.paused ? '<span class="badge">잠시 멈춤</span>' : '') + '</div>' +
      '<p class="g-title" data-guide-key="' + key + '">' + esc(g.title) + '</p>' +
      '<p class="g-text">' + esc(g.text) + '</p>' +
      '<p class="g-note">' + esc(contextNote(key)) + '</p>' +
      '<div class="g-voice"><span class="dot' + (S.audioPlaying ? ' on' : '') + '"></span><span>' + esc(voiceMsg) + '</span></div>' +
      '<div class="g-btns">' +
      btn('다시 듣기', 'replay', 'btn-secondary', S.paused || !hasClip(key) ? ' disabled' : '') +
      btn(S.paused ? '이어서 듣기' : '잠시 멈춤', 'pause', 'btn-secondary') +
      btn(S.step === 2 ? '처음부터' : '다음 안내', 'next', 'btn-primary', S.paused ? ' disabled' : '') +
      '</div>' +
      '<p class="g-hint">해당하면 눌러 주세요 · 안내가 바뀌어요</p>' +
      '<div class="toggles">' +
      '<button type="button" class="toggle" role="switch" data-act="immobile" aria-checked="' + S.guidance.immobile + '">움직이기 어려워요</button>' +
      '<button type="button" class="toggle" role="switch" data-act="smoke" aria-checked="' + S.guidance.smoke + '">연기·불꽃이 보여요</button>' +
      '</div>' +
      '<div class="g-btns">' + btn(help, 'call', 'btn-danger') + btn('통화할 때 읽을 내용', 'factsOn', 'btn-secondary') + '</div>' +
      insuranceHtml() +
      '<div class="kv-list">' +
      kv('지금 상황', place, S.lastIncident ? '충격 감지 · 예시 차량 신호 · 사고로 확정한 건 아니에요' : null) +
      kv('영상에서 본 것', observedSummary(), S.analysisSource === 'FIXTURE' ? '체험용 예시 결과예요' : null) +
      kv('움직일 수 있나요', S.guidance.immobile ? '움직이기 어렵다고 알려 주셨어요' : '앱은 알 수 없어요 · 직접 살펴 주세요') +
      '</div>';
  }

  let lastGuideHtml = '', lastAnalysisHtml = '', lastFramesKey = '';
  const sceneCtx = $('scene').getContext('2d');

  function render() {
    // 왼쪽
    $('scenarioLabel').textContent = S.scenario.label;
    $('demoHead').textContent = S.head;
    $('demoDesc').textContent = S.desc;
    $('demoClock').textContent = S.clock;
    $('mSpeed').textContent = Math.round(S.speed);
    $('mImpact').textContent = S.impact.toFixed(1);
    $('mImpact').parentElement.classList.toggle('hit', S.impact >= 2.5);
    drawScene(sceneCtx, S.scenario, S.sceneFrame, S.sceneEvent);

    const ai = S.stage ? stageIndex(S.stage) : -1;
    $('stages').innerHTML = STAGES.map((st, i) =>
      '<li class="stage' + (i === ai ? ' active' : i < ai ? ' done' : '') + '"' + (i === ai ? ' aria-current="step"' : '') + '>' + (i + 1) + ' ' + st[1] + '</li>').join('');
    $('demoLog').innerHTML = S.log.map((t) => '<li>' + esc(t) + '</li>').join('');
    document.querySelectorAll('.scenario').forEach((b) => {
      b.setAttribute('aria-checked', String(b.dataset.key === S.scenario.key));
      b.disabled = S.running;
    });
    $('btnStart').disabled = S.running;
    $('btnStop').disabled = !S.running;
    $('btnRecord').disabled = !S.pendingRecord || S.running;

    // AI가 본 장면
    const badge = $('analysisBadge');
    badge.className = 'badge' + (S.analysisSource === 'FIXTURE' ? ' warn' : '');
    badge.textContent = S.analysisSource === 'FIXTURE' ? '체험용 예시 결과' : S.analysisBusy ? '확인 중' : '아직 없음';
    $('analysisStatus').textContent = S.analysisStatus;
    const ev = S.observation ? S.observation.evidenceFrames : [];
    const fk = S.scenario.key + '|' + S.frames.length + '|' + ev.join(',');
    if (fk !== lastFramesKey) {
      lastFramesKey = fk;
      const box = $('frames');
      box.innerHTML = '';
      S.frames.forEach((f) => {
        const isEv = ev.includes(f.index);
        const wrap = document.createElement('div');
        wrap.className = 'frame' + (isEv ? ' ev' : '');
        const c = document.createElement('canvas');
        c.width = 320; c.height = 133;
        c.setAttribute('role', 'img');
        c.setAttribute('aria-label', '사고 장면 ' + (f.index + 1));
        const cx = c.getContext('2d');
        cx.scale(320 / 960, 133 / 400);
        drawScene(cx, S.scenario, f.frame, true);
        wrap.appendChild(c);
        const cap = document.createElement('span');
        cap.textContent = '장면 ' + (f.index + 1) + ' · ' + whenLabel(f.offset) + (isEv ? ' · 근거' : '');
        const src = document.createElement('span');
        src.className = 'f-src';
        src.textContent = '체험용 그림 장면';
        wrap.appendChild(cap); wrap.appendChild(src);
        box.appendChild(wrap);
      });
    }
    let ah = '';
    const o = S.observation;
    if (o) {
      ah = '<div class="kv-list">' +
        kv('장소 모습', { tunnel: '터널 안으로 보여요', road: '도로로 보여요', unknown: '알 수 없어요' }[o.scene] || '알 수 없어요') +
        kv('화면 상태', { usable: '잘 보여요', limited: '일부만 보여요', unusable: '잘 안 보여서 판단하기 어려워요' }[o.visibility] || '') +
        kv('연기·불꽃', observedSummary()) +
        kv('판단 근거', o.evidenceFrames.length === 0 ? '근거로 쓸 장면이 없어 판단을 미뤘어요' : '장면 ' + o.evidenceFrames.map((i) => i + 1).join(', ') + '을 근거로 봤어요') +
        '</div>' + (o.observations.length ? '<ul class="obs">' + o.observations.map((t) => '<li>' + esc(t) + '</li>').join('') + '</ul>' : '');
    }
    if (ah !== lastAnalysisHtml) { $('analysisResult').innerHTML = ah; lastAnalysisHtml = ah; }

    // 오른쪽 안내
    const gh = guideHtml();
    if (gh !== lastGuideHtml) {
      const panel = $('guidePanel');
      const focused = document.activeElement && panel.contains(document.activeElement) ? document.activeElement.dataset.act : null;
      panel.innerHTML = gh;
      lastGuideHtml = gh;
      if (focused) { const again = panel.querySelector('[data-act="' + focused + '"]'); if (again) again.focus({ preventScroll: true }); }
    }
  }

  /** 보험사 질문이 나오면 오른쪽 안내 칸 안에서 카드가 보이게 한다(페이지는 움직이지 않음). */
  function revealInsurance() {
    const card = $('insCard'), pane = $('paneRight');
    if (!card) return;
    card.classList.remove('flash'); void card.offsetWidth; card.classList.add('flash');
    if (pane.scrollHeight > pane.clientHeight + 4) {
      const top = card.getBoundingClientRect().top - pane.getBoundingClientRect().top + pane.scrollTop - 12;
      pane.scrollTo({ top, behavior: 'smooth' });
    }
  }

  // ───────── 이벤트 연결 ─────────
  $('scenarios').innerHTML = SCENARIOS.map((sc) =>
    '<button type="button" class="scenario" role="radio" data-key="' + sc.key + '" aria-checked="false" aria-label="' + esc(sc.label) + '"><b>' + esc(sc.label.split(' · ')[0]) + '</b><small>' + esc(sc.label.split(' · ')[1]) + '</small></button>').join('');
  $('scenarios').addEventListener('click', (e) => {
    const b = e.target.closest('.scenario');
    if (b) selectScenario(SCENARIOS.find((s) => s.key === b.dataset.key));
  });
  $('btnStart').addEventListener('click', () => { startDemo(); });
  $('btnStop').addEventListener('click', () => stopDemo(true));
  $('btnRecord').addEventListener('click', saveRecord);
  $('autoVoice').addEventListener('change', (e) => setAutoVoice(e.target.checked));
  $('guidePanel').addEventListener('click', (e) => {
    const b = e.target.closest('[data-act]');
    if (!b || b.disabled) return;
    ({
      listenNow: startCommonGuidance, call: toggleCall, replay, pause: togglePause, next: nextGuide,
      immobile: toggleImmobile, smoke: toggleSmoke, factsOn: () => showFacts(true), factsOff: () => showFacts(false),
      insConnect: connectInsurance, insLater: declineInsurance, insOffer: offerInsurance,
    }[b.dataset.act] || (() => {}))();
  });

  // 화면 밖으로 나가면 음성을 멈춘다 (onBackground 와 같은 뜻)
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) { stopAudio(null); if (S.running) stopDemo(true); if (S.started && !S.inCall) { S.paused = true; render(); } }
  });

  function tickClock() {
    const d = new Date();
    $('deviceClock').textContent = String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
  }
  tickClock(); setInterval(tickClock, 15000);

  // 글꼴이 늦게 와도 장면 글자가 맞게 보이게 다시 그린다
  if (document.fonts && document.fonts.ready) document.fonts.ready.then(() => { lastFramesKey = ''; render(); });

  // ───────── 앱 화면 갤러리 ─────────
  const SCREENS = [
    { file: 'screens/01_home.png', cap: '홈' },
    { file: 'screens/02_demo_guide.png', cap: '체험: 지금 할 일 안내' },
    { file: 'screens/03_insurance_ask.png', cap: '보험사 연결 묻기' },
    { file: 'screens/04_call_sheet.png', cap: '통화할 때 읽을 내용' },
    { file: 'screens/05_tunnel.png', cap: '터널·연기 의심' },
    { file: 'screens/06_settings_insurance.png', cap: '내 보험 정보' },
    { file: 'screens/07_live.png', cap: '실제 카메라·영상 연결' },
    { file: 'screens/08_phone_home.png', cap: '휴대폰 세로 화면' },
  ];
  const grid = $('galleryGrid'), gallery = $('gallery');
  SCREENS.forEach((sc) => {
    const fig = document.createElement('figure');
    fig.className = 'shot';
    fig.hidden = true;
    const b = document.createElement('button');
    b.type = 'button';
    b.setAttribute('aria-label', sc.cap + ' 크게 보기');
    const img = new Image();
    img.alt = '멈춘 다음 앱 화면: ' + sc.cap;
    img.decoding = 'async';
    img.addEventListener('load', () => { fig.hidden = false; gallery.hidden = false; });
    img.addEventListener('error', () => { fig.remove(); });
    img.src = sc.file;
    b.appendChild(img);
    b.addEventListener('click', () => openLightbox(sc, b));
    const cap = document.createElement('figcaption');
    cap.textContent = sc.cap;
    fig.appendChild(b); fig.appendChild(cap);
    grid.appendChild(fig);
  });

  const lb = $('lightbox');
  let lbReturn = null;
  function openLightbox(sc, from) {
    lbReturn = from;
    $('lightboxImg').src = sc.file;
    $('lightboxImg').alt = '멈춘 다음 앱 화면: ' + sc.cap;
    $('lightboxCap').textContent = sc.cap;
    lb.hidden = false;
    $('lightboxClose').focus();
  }
  function closeLightbox() { lb.hidden = true; if (lbReturn) lbReturn.focus(); }
  $('lightboxClose').addEventListener('click', closeLightbox);
  lb.addEventListener('click', (e) => { if (e.target === lb) closeLightbox(); });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && !lb.hidden) closeLightbox(); });

  render();

  // 확인용 (헤드리스 점검에서 읽음)
  window.__afterstop = { state: S, keys, resolveKeys };
})();
