package com.afterstop.app.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.afterstop.app.BuildConfig
import com.afterstop.app.R
import com.afterstop.app.audio.AssetAudioPlayer
import com.afterstop.app.core.AnalysisKind
import com.afterstop.app.core.AnalysisSource
import com.afterstop.app.core.DefaultGuidancePolicy
import com.afterstop.app.core.DemoFixtures
import com.afterstop.app.core.DemoScenario
import com.afterstop.app.core.FixtureVisionClient
import com.afterstop.app.core.Frame
import com.afterstop.app.core.FrameMeta
import com.afterstop.app.core.FrameProvenance
import com.afterstop.app.core.GuidancePolicy
import com.afterstop.app.core.GuidanceReducer
import com.afterstop.app.core.GuidanceState
import com.afterstop.app.core.VoiceAnswer
import com.afterstop.app.core.VoiceAnswerParser
import com.afterstop.app.insurance.InsuranceProfile
import com.afterstop.app.insurance.InsuranceStore
import com.afterstop.app.insurance.PhoneCaller
import com.afterstop.app.insurance.PhoneNumbers
import com.afterstop.app.voice.VoiceListener
import com.afterstop.app.core.Guides
import com.afterstop.app.core.IncidentEvent
import com.afterstop.app.core.IncidentPipeline
import com.afterstop.app.core.PipelineEvent
import com.afterstop.app.core.RequestEpoch
import com.afterstop.app.core.RuleBasedIncidentDetector
import com.afterstop.app.core.Stage
import com.afterstop.app.core.TelemetrySourceTag
import com.afterstop.app.core.VideoSource
import com.afterstop.app.core.VisionObservation
import com.afterstop.app.core.VisionResult
import com.afterstop.app.data.IncidentRecord
import com.afterstop.app.data.RecordStore
import com.afterstop.app.data.UserReports
import com.afterstop.app.net.HealthStatus
import com.afterstop.app.net.IncidentEventStream
import com.afterstop.app.net.ServerClient
import com.afterstop.app.net.ServerConfig
import com.afterstop.app.net.ServerUrlPolicy
import com.afterstop.app.net.StreamState
import com.afterstop.app.net.UrlCheck
import com.afterstop.app.telemetry.PhoneSensorTelemetrySource
import com.afterstop.app.video.CameraXVideoSource
import com.afterstop.app.video.DemoSceneRenderer
import com.afterstop.app.video.FrameEncoding
import com.afterstop.app.video.InvalidVideoException
import com.afterstop.app.video.SyntheticDemoVideoSource
import com.afterstop.app.video.VideoFileSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

enum class Screen(val label: String) { HOME("홈"), DEMO("체험"), LIVE("연결"), RECORDS("기록"), SETTINGS("설정"), COMPONENTS("앱 구조") }

/** 보험사 연결 대화 단계 */
enum class InsStage { IDLE, ASKING, LISTENING, CONNECTED, DECLINED }
enum class LiveSource { NONE, CAMERA, FILE, OEM }

data class ShownFrame(val index: Int, val offsetSeconds: Double, val provenance: FrameProvenance, val image: ImageBitmap)

data class UiState(
    val screen: Screen = Screen.HOME,
    val night: Boolean = false,
    // 안내
    val guidance: GuidanceState = GuidanceState(),
    val started: Boolean = false,
    val step: Int = 0,
    val paused: Boolean = false,
    val inCall: Boolean = false,
    val showFacts: Boolean = false,
    val autoVoice: Boolean = true,
    val audioMessage: String = "",
    val audioPlaying: Boolean = false,
    // 시연
    val demoScenario: DemoScenario = DemoScenario.HIGHWAY,
    val demoRunning: Boolean = false,
    val demoStage: Stage? = null,
    val demoHeadline: String = DEMO_IDLE_HEAD,
    val demoDescription: String = DEMO_IDLE_DESC,
    val demoLog: List<String> = emptyList(),
    val demoTelemetry: String = "",
    val demoClock: String = "0.0초",
    val demoScene: ImageBitmap? = null,
    // 분석 공통
    val frames: List<ShownFrame> = emptyList(),
    val observation: VisionObservation? = null,
    val analysisSource: AnalysisSource? = null,
    val analysisModel: String? = null,
    val analysisError: String? = null,
    val analysisBusy: Boolean = false,
    val captureBusy: Boolean = false,
    val analysisStatus: String = NO_FRAMES,
    val lastIncident: IncidentEvent? = null,
    val incidentOrigin: String? = null,
    // 실제 입력
    val liveSource: LiveSource = LiveSource.NONE,
    val sourceMessage: String = "장면을 가져올 곳을 골라 주세요.",
    val cameraConnected: Boolean = false,
    val cameraFrames: Int = 0,
    val cameraNeedsBind: Int = 0,
    val fileName: String? = null,
    val fileDurationMs: Long = 0,
    val fileAnchorMs: Long = 0,
    val filePreview: ImageBitmap? = null,
    // 서버
    val server: ServerConfig = ServerConfig(BuildConfig.DEFAULT_SERVER_URL),
    val urlMessage: String? = null,
    val health: HealthStatus? = null,
    val healthBusy: Boolean = false,
    val consent: Boolean = false,
    val eventsOn: Boolean = false,
    val eventsState: String = "서버 신호 받기 꺼짐",
    val sensorOn: Boolean = false,
    val sensorStatus: String = "휴대폰 센서 꺼짐",
    // 보험사 연결
    val insurance: InsuranceProfile = InsuranceProfile(),
    val insStage: InsStage = InsStage.IDLE,
    val insMessage: String = "",
    val insHeard: String? = null,
    val insOutcome: String = "none",
    val incidentAt: Instant? = null,
    // 기록
    val pendingRecord: IncidentRecord? = null,
    val records: List<IncidentRecord> = emptyList(),
    val toast: String? = null,
)

private const val DEMO_IDLE_HEAD = "사고가 나면 이렇게 안내해요."
private const val DEMO_IDLE_DESC = "체험 시작을 누르면 충격 감지부터 음성 안내, 보험사 연결까지 순서대로 보여 드려요."
private const val NO_FRAMES = "아직 저장한 장면이 없어요"
/** 체험에서 보험 정보를 입력하지 않았을 때 보여 줄 이름. 번호는 만들지 않는다. */
private const val DEMO_INSURER = "예시 보험사"

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    val audio = AssetAudioPlayer(app)
    val camera = CameraXVideoSource(app)
    private val sensor = PhoneSensorTelemetrySource(app)
    private val policy: GuidancePolicy = DefaultGuidancePolicy
    private val store = RecordStore(app.filesDir.resolve("records"))
    private val prefs = app.getSharedPreferences("afterstop", Context.MODE_PRIVATE)
    private val insuranceStore = InsuranceStore(prefs)
    val voice = VoiceListener(app)
    /** 이번 사고에서 보험사 연결을 아직 묻지 않았음 */
    private var insPending = false
    /** 연결 안내 음성이 끝나면 전화를 건다 */
    private var callAfterPrompt = false
    /** 마지막으로 쓴 모드(체험/실제 연결) */
    private var lastMode: Screen? = null
    private val numberTypeface: Typeface? = runCatching { ResourcesCompat.getFont(app, R.font.pretendard_bold) }.getOrNull()

    private val analysisEpoch = RequestEpoch()
    private var demoJob: Job? = null
    private var analysisJob: Job? = null
    private var captureJob: Job? = null
    private var eventsJob: Job? = null
    private var sensorJob: Job? = null
    private var previewJob: Job? = null
    private var fileSource: VideoFileSource? = null
    private var rawFrames: List<Frame> = emptyList()
    private var lastTelemetrySource: String? = null

    init {
        _ui.update {
            it.copy(
                server = ServerConfig(
                    prefs.getString("serverUrl", BuildConfig.DEFAULT_SERVER_URL) ?: "",
                    prefs.getString("token", "") ?: "",
                    prefs.getString("model", "") ?: "",
                ),
                audioMessage = audio.state.value.message,
                records = store.list(),
                insurance = insuranceStore.load(),
                demoScene = DemoSceneRenderer.render(DemoScenario.HIGHWAY, 0.0, false, numberTypeface).asImageBitmap(),
            )
        }
        audio.onFinished = { key -> onAudioFinished(key) }
        viewModelScope.launch { audio.state.collect { a -> _ui.update { it.copy(audioMessage = a.message, audioPlaying = a.playingKey != null) } } }
        viewModelScope.launch { camera.status.collect { s -> _ui.update { it.copy(cameraConnected = s.connected, sourceMessage = if (it.liveSource == LiveSource.CAMERA) s.message else it.sourceMessage) } } }
        viewModelScope.launch { camera.bufferCount.collect { n -> _ui.update { it.copy(cameraFrames = n) } } }
        viewModelScope.launch { sensor.lastInvalid.collect { r -> if (_ui.value.sensorOn) _ui.update { it.copy(sensorStatus = r?.let { "센서 값을 쓸 수 없어요: $it" } ?: "휴대폰 센서로 살피는 중") } } }
    }

    // ───────── 공통 안내 ─────────
    fun keys(s: UiState = _ui.value) = policy.resolve(s.guidance)
    fun currentKey(s: UiState = _ui.value) = keys(s)[s.step]

    private fun speak() {
        val s = _ui.value
        if (!s.started || s.paused || s.inCall || s.showFacts || !s.autoVoice) return
        audio.play(currentKey())
    }

    fun replay() { val s = _ui.value; if (!s.started || s.paused || s.inCall) return; audio.play(currentKey()) }
    /** 안내 음성을 재생하고, 음성이 없으면(꺼짐·녹음 없음) 바로 보험사 연결 질문 단계로 넘어간다. */
    private fun speakThenAsk() {
        speak()
        val s = _ui.value
        if (!s.autoVoice || !audio.hasClip(currentKey(s))) maybeAskInsurance()
    }

    fun startCommonGuidance() { _ui.update { it.copy(started = true, step = 0, paused = false, showFacts = false) }; speak() }
    fun nextGuide() { val s = _ui.value; if (s.paused || s.inCall) return; _ui.update { it.copy(step = (it.step + 1) % 3) }; speak() }
    fun togglePause() { _ui.update { it.copy(paused = !it.paused) }; if (_ui.value.paused) audio.stop("안내를 잠시 멈췄어요") else speak() }
    fun toggleSmoke() { _ui.update { it.copy(guidance = it.guidance.copy(smoke = !it.guidance.smoke), step = 0, started = true, paused = false) }; speak() }
    fun toggleImmobile() { _ui.update { it.copy(guidance = it.guidance.copy(immobile = !it.guidance.immobile), step = 0, started = true, paused = false) }; speak() }
    fun toggleCall() {
        audio.stop("체험용 도움 요청 화면 · 음성 안내 멈춤")
        _ui.update { if (it.inCall) it.copy(inCall = false, paused = it.started) else it.copy(inCall = true) }
    }
    fun showFacts(show: Boolean) { if (show) audio.stop(null); _ui.update { it.copy(showFacts = show) } }
    fun setAutoVoice(on: Boolean) { _ui.update { it.copy(autoVoice = on) }; if (!on) audio.stop(null) }
    fun toggleNight() = _ui.update { it.copy(night = !it.night) }

    /** 119: 체험에서는 체험용 화면, 실제 연결에서는 119 가 입력된 전화 화면을 연다(통화 버튼은 사용자가 누름). */
    fun callEmergency() {
        if (_ui.value.screen != Screen.LIVE) return toggleCall()
        audio.stop(null); voice.stop()
        if (PhoneCaller.dialEmergency(ctx) == PhoneCaller.Result.FAILED) toast("전화 화면을 열지 못했어요. 119에 직접 전화해 주세요.")
    }

    // ───────── 보험사 연결 ─────────
    fun updateInsurance(p: InsuranceProfile) {
        val clean = p.copy(company = p.company.trim(), phone = p.phone.trim(), policyNo = p.policyNo.trim(), carNo = p.carNo.trim())
        insuranceStore.save(clean)
        _ui.update { it.copy(insurance = clean) }
        toast(if (clean.ready) "보험 정보를 저장했어요." else if (clean.phone.isNotBlank() && !PhoneNumbers.isValid(clean.phone)) "번호 형식을 확인해 주세요. 보험사 긴급출동 번호(7~12자리)를 넣어 주세요." else "저장했어요. 보험사 이름과 번호를 모두 넣어야 연결할 수 있어요.")
    }

    private fun insurerName(s: UiState = _ui.value) = s.insurance.company.ifBlank { if (s.screen == Screen.DEMO) DEMO_INSURER else "보험사" }

    /** 새 사고마다 한 번, 안전 안내가 끝난 뒤 보험사 연결을 묻도록 준비한다. */
    private fun armInsurance() {
        voice.stop(); callAfterPrompt = false
        insPending = true
        _ui.update { it.copy(insStage = InsStage.IDLE, insMessage = "", insHeard = null, insOutcome = "none", incidentAt = Instant.now()) }
    }

    private fun resetInsurance() {
        voice.stop(); callAfterPrompt = false; insPending = false
        _ui.update { it.copy(insStage = InsStage.IDLE, insMessage = "", insHeard = null, insOutcome = "none", incidentAt = null) }
    }

    private fun onAudioFinished(key: String) {
        val s = _ui.value
        when {
            key == "insAsk" && s.insStage == InsStage.ASKING -> startListening()
            key == "insConnect" && callAfterPrompt -> { callAfterPrompt = false; placeInsuranceCall() }
            key in Guides.ALL && s.step == 0 -> maybeAskInsurance()
        }
    }

    /** 위험 신호(이동 어려움·연기)가 있으면 119·구조 요청이 먼저이므로 자동으로 묻지 않는다. 버튼은 그대로 쓸 수 있다. */
    private fun maybeAskInsurance() {
        val s = _ui.value
        if (!insPending || !s.started || s.paused || s.inCall || s.showFacts || s.analysisBusy || s.captureBusy) return
        if (!(s.screen == Screen.LIVE || (s.screen == Screen.DEMO && s.demoStage == Stage.GUIDE))) return
        insPending = false
        val g = s.guidance
        val danger = g.immobile || g.smoke || (g.analysisReady && g.observedSmoke)
        if (danger) { _ui.update { it.copy(insMessage = "위험 신호가 있어 보험사 연결은 묻지 않았어요. 구조 요청을 먼저 하세요.") }; return }
        if (!s.insurance.askAfterIncident) return
        if (s.screen == Screen.LIVE && !s.insurance.ready) { _ui.update { it.copy(insMessage = "보험 정보를 넣어 두면 사고 뒤 바로 연결해 드려요.") }; return }
        offerInsurance()
    }

    /** "보험사에 연결할까요?" 를 묻는다(버튼으로도 시작). */
    fun offerInsurance() {
        val s = _ui.value
        insPending = false
        voice.stop()
        if (s.screen == Screen.LIVE && !s.insurance.ready) {
            _ui.update { it.copy(insStage = InsStage.IDLE, insMessage = "설정에서 보험사 이름과 긴급출동 번호를 먼저 넣어 주세요.") }; return
        }
        _ui.update { it.copy(insStage = InsStage.ASKING, insHeard = null, insMessage = "${insurerName(it)}에 연결할까요? 말로 대답하거나 버튼을 눌러 주세요.") }
        if (s.autoVoice && audio.hasClip("insAsk")) audio.play("insAsk") else startListening()
    }

    fun startListening() {
        if (_ui.value.insStage !in listOf(InsStage.ASKING, InsStage.LISTENING)) return
        if (!voice.permitted || !voice.available) {
            _ui.update { it.copy(insStage = InsStage.ASKING, insMessage = (if (!voice.permitted) "마이크 권한이 없어요." else "이 기기는 음성 인식을 지원하지 않아요.") + " 버튼으로 대답해 주세요.") }; return
        }
        audio.stop(null)
        _ui.update { it.copy(insStage = InsStage.LISTENING, insMessage = "듣고 있어요. \"연결해 줘\" 또는 \"나중에\"라고 말해 주세요.") }
        voice.listen(
            onResult = { list -> onVoiceAnswer(VoiceAnswerParser.parse(list), list.firstOrNull()) },
            onError = { msg -> if (_ui.value.insStage == InsStage.LISTENING) _ui.update { it.copy(insStage = InsStage.ASKING, insMessage = "$msg. 버튼으로 대답하거나 다시 말하기를 눌러 주세요.") } },
        )
    }

    private fun onVoiceAnswer(a: VoiceAnswer, heard: String?) {
        if (_ui.value.insStage != InsStage.LISTENING) return
        _ui.update { it.copy(insHeard = heard) }
        when (a) {
            VoiceAnswer.YES -> connectInsurance()
            VoiceAnswer.NO -> declineInsurance()
            VoiceAnswer.UNCLEAR -> {
                _ui.update { it.copy(insStage = InsStage.ASKING, insMessage = "\"${heard ?: ""}\"라고 들었어요. 연결할까요?") }
                if (_ui.value.autoVoice && audio.hasClip("insRetry")) audio.play("insRetry")
            }
        }
    }

    /** 사용자가 "연결해 줘" 또는 버튼으로 승낙했을 때만 연결한다. */
    fun connectInsurance() {
        voice.stop()
        val s = _ui.value
        if (s.screen == Screen.DEMO) {
            val target = if (s.insurance.ready) "${s.insurance.company}(${PhoneNumbers.display(s.insurance.phone)})" else DEMO_INSURER
            _ui.update { it.copy(insStage = InsStage.CONNECTED, insOutcome = "demo_simulated", insMessage = "체험 중이라 실제로 전화하지 않아요. 실제 사용에서는 ${target}에 바로 연결돼요.") }
            if (s.autoVoice && audio.hasClip("insConnect")) audio.play("insConnect")
            return
        }
        if (!s.insurance.ready) { _ui.update { it.copy(insStage = InsStage.IDLE, insMessage = "설정에서 보험사 이름과 긴급출동 번호를 먼저 넣어 주세요.") }; return }
        _ui.update { it.copy(insStage = InsStage.CONNECTED, insMessage = "${s.insurance.company}에 연결하고 있어요.") }
        if (s.autoVoice && audio.hasClip("insConnect")) { callAfterPrompt = true; audio.play("insConnect") } else placeInsuranceCall()
    }

    private fun placeInsuranceCall() {
        val p = _ui.value.insurance
        val r = PhoneCaller.call(ctx, p.phone, direct = p.voiceDirectCall)
        _ui.update { it.copy(insOutcome = when (r) { PhoneCaller.Result.CALL_STARTED -> "call_started"; PhoneCaller.Result.DIALER_OPENED -> "dialer_opened"; PhoneCaller.Result.FAILED -> "failed" },
            insMessage = when (r) {
                PhoneCaller.Result.CALL_STARTED -> "${p.company}(${PhoneNumbers.display(p.phone)})에 전화를 걸었어요. 아래 내용을 읽어 주세요."
                PhoneCaller.Result.DIALER_OPENED -> "전화 화면에 ${p.company} 번호를 넣었어요. 통화 버튼을 눌러 주세요."
                PhoneCaller.Result.FAILED -> "전화를 열지 못했어요. ${PhoneNumbers.display(p.phone)}로 직접 걸어 주세요."
            }) }
    }

    fun declineInsurance() {
        voice.stop(); callAfterPrompt = false
        _ui.update { it.copy(insStage = InsStage.DECLINED, insOutcome = "declined", insMessage = "보험사 연결을 미뤘어요. 필요할 때 다시 누르세요.") }
        if (_ui.value.autoVoice && audio.hasClip("insLater")) audio.play("insLater")
    }
    fun consumeToast() = _ui.update { it.copy(toast = null) }
    private fun toast(t: String) = _ui.update { it.copy(toast = t) }

    // ───────── 화면 이동 ─────────
    fun go(screen: Screen) {
        val prev = _ui.value.screen
        if (prev == screen) return
        if (prev == Screen.DEMO && screen != Screen.DEMO) stopDemo(false)
        if (screen in listOf(Screen.DEMO, Screen.LIVE)) {
            // 체험 ↔ 실제 연결 전환(사이에 다른 화면을 거쳐도): 이전 장면·결과·안내를 정리하고 늦게 도착한 응답은 버린다.
            if (lastMode != null && lastMode != screen) {
                invalidateAnalysis("체험과 실제 연결이 바뀌어 이전 결과를 정리했어요.")
                resetGuidance()
                if (lastMode == Screen.LIVE) disconnectLiveInputs()
            }
            lastMode = screen
        }
        if (screen == Screen.RECORDS) _ui.update { it.copy(records = store.list()) }
        _ui.update { it.copy(screen = screen) }
    }

    private fun resetGuidance() {
        audio.stop(null)
        _ui.update { it.copy(guidance = GuidanceState(), started = false, step = 0, paused = false, inCall = false, showFacts = false, lastIncident = null, pendingRecord = null) }
        resetInsurance()
    }

    /** 앱이 백그라운드로 가면 음성·시연·카메라·분석을 정리한다. */
    fun onBackground() {
        audio.stop(null); voice.stop(); callAfterPrompt = false
        if (_ui.value.insStage == InsStage.LISTENING) _ui.update { it.copy(insStage = InsStage.ASKING, insMessage = "다시 말하기를 누르거나 버튼으로 대답해 주세요.") }
        if (_ui.value.demoRunning) stopDemo(true)
        if (_ui.value.analysisBusy || _ui.value.captureBusy) invalidateAnalysis("앱 화면을 벗어나 AI 확인을 멈췄어요. 기본 안내는 계속 쓸 수 있어요.")
        if (_ui.value.liveSource == LiveSource.CAMERA) camera.release()
        stopSensor()
        _ui.update { it.copy(paused = it.started && !it.inCall) }
    }

    fun onForeground() {
        if (_ui.value.liveSource == LiveSource.CAMERA && _ui.value.screen == Screen.LIVE) _ui.update { it.copy(cameraNeedsBind = it.cameraNeedsBind + 1) }
    }

    // ───────── 제품 시연 ─────────
    fun selectDemoScenario(s: DemoScenario) {
        stopDemo(false)
        _ui.update {
            it.copy(demoScenario = s, demoStage = null, demoLog = emptyList(), demoTelemetry = "", demoClock = "0.0초",
                demoHeadline = DEMO_IDLE_HEAD,
                demoDescription = DEMO_IDLE_DESC,
                demoScene = DemoSceneRenderer.render(s, 0.0, false, numberTypeface).asImageBitmap())
        }
        invalidateAnalysis(null); resetGuidance()
    }

    private fun log(t: String) = _ui.update { it.copy(demoLog = (it.demoLog + t).takeLast(4)) }
    private fun headline(h: String, d: String) = _ui.update { it.copy(demoHeadline = h, demoDescription = d) }

    fun startDemo() {
        stopDemo(false)
        if (_ui.value.screen != Screen.DEMO) go(Screen.DEMO)
        invalidateAnalysis(null); resetGuidance()
        val scenario = _ui.value.demoScenario
        val demoVideo = SyntheticDemoVideoSource(scenario, numberTypeface)
        val epoch = analysisEpoch.next()
        _ui.update { it.copy(demoRunning = true, demoStage = Stage.INPUT, demoLog = emptyList()) }
        headline("주행 중이에요.", "블랙박스 화면과 차량 신호를 살피고 있어요. 체험용 예시 화면이에요.")
        log("주행 중 · 체험용 화면과 예시 신호")
        var detected: IncidentEvent? = null
        demoJob = viewModelScope.launch {
            val pipeline = IncidentPipeline(RuleBasedIncidentDetector())
            val outcome = pipeline.run(
                samples = DemoFixtures.telemetryFor(scenario),
                captureFrames = { ev -> withContext(Dispatchers.Default) { demoVideo.framesFor(ev) } },
                vision = FixtureVisionClient(scenario),
                requestId = "demo-${UUID.randomUUID()}",
                onEvent = { e -> if (analysisEpoch.isCurrent(epoch)) onDemoEvent(e, scenario, detected != null).also { if (e is PipelineEvent.Detected) detected = e.event } },
            )
            if (!analysisEpoch.isCurrent(epoch)) return@launch
            if (outcome is com.afterstop.app.core.PipelineOutcome.Completed) {
                _ui.update { it.copy(pendingRecord = buildRecord("synthetic_product_demo", scenario.key, FrameProvenance.SYNTHETIC_DEMO, TelemetrySourceTag.SYNTHETIC_FIXTURE)) }
            } else {
                _ui.update { it.copy(pendingRecord = buildRecord("synthetic_product_demo", scenario.key, FrameProvenance.SYNTHETIC_DEMO, TelemetrySourceTag.SYNTHETIC_FIXTURE, noIncident = true)) }
            }
            _ui.update { it.copy(demoRunning = false) }
        }
    }

    private suspend fun onDemoEvent(e: PipelineEvent, scenario: DemoScenario, hasEvent: Boolean) {
        when (e) {
            is PipelineEvent.Driving -> {
                val img = withContext(Dispatchers.Default) { DemoSceneRenderer.render(scenario, e.sample.t / 100, hasEvent, numberTypeface).asImageBitmap() }
                _ui.update { it.copy(demoScene = img, demoClock = "%.1f초".format(e.sample.t / 1000), demoTelemetry = "속도 %.0fkm/h · 충격 %.1fg · 예시".format(e.sample.speedKmh, e.sample.linearG)) }
            }
            is PipelineEvent.Detected -> {
                _ui.update { it.copy(demoStage = Stage.DETECT, lastIncident = e.event, incidentOrigin = "예시 차량 신호", started = true, step = 0, paused = false) }
                armInsurance()
                headline("충격을 감지했어요.", "큰 충격 뒤 속도가 크게 줄고 차가 멈췄어요. 바로 기본 안내를 시작해요.")
                log("충격 감지 · 바로 기본 안내 시작")
                speak()
            }
            PipelineEvent.NoIncident -> {
                _ui.update { it.copy(demoStage = Stage.DETECT) }
                headline("사고가 아니라고 봤어요.", "덜컹하는 충격은 있었지만 차가 그대로 달렸어요. 안내하지 않고 주행을 이어가요.")
                log("과속방지턱 · 충격만으로는 안내하지 않아요")
            }
            is PipelineEvent.Buffered -> {
                setFrames(e.frames)
                _ui.update { it.copy(demoStage = Stage.BUFFER) }
                headline("사고 장면을 저장했어요.", "사고 전후 장면 ${e.frames.size}개를 저장했어요.")
                log("사고 전후 장면 ${e.frames.size}개 저장")
            }
            PipelineEvent.Analyzing -> {
                _ui.update { it.copy(demoStage = Stage.ANALYZE, analysisBusy = true, analysisStatus = "AI가 장면을 보고 있어요 · 체험용 예시 결과") }
                headline("AI가 장면을 살펴보고 있어요.", "체험에서는 미리 준비한 결과를 보여 드려요. 실제 결과와 똑같이 검사해요.")
                log("AI 확인 중 · 체험용 예시 결과")
            }
            is PipelineEvent.Analyzed -> {
                applyResult(e.result, scenario)
                val o = (e.result as? VisionResult.Success)?.observation
                _ui.update { it.copy(demoStage = Stage.GUIDE, analysisBusy = false) }
                headline(
                    if (scenario.limited) "어두워서 판단을 미뤘어요." else if (scenario.smoke) "연기처럼 보이는 게 있어요." else "지금 할 일을 알려 드려요.",
                    if (scenario.limited) "확실하지 않은 건 말하지 않고, 꼭 필요한 안내만 드려요."
                    else if (scenario.smoke) "그에 맞춰 안내를 바꿨어요. 움직이기 어렵다면 아래 버튼으로 알려 주세요."
                    else "움직이기 어렵거나 연기가 보이면 아래 버튼으로 알려 주세요. 안내가 바뀌어요.",
                )
                log("AI 확인 끝 · " + (if (o == null || o.evidenceFrames.isEmpty()) "판단 미룸" else "근거 장면 ${o.evidenceFrames.joinToString(", ") { (it + 1).toString() }}") + " · 하윤 음성 안내")
                speakThenAsk()
            }
        }
    }

    fun stopDemo(message: Boolean = true) {
        if (demoJob?.isActive == true) {
            demoJob?.cancel(); analysisEpoch.invalidate(); audio.stop(null)
            if (message) log("체험을 멈췄어요. 다시 시작하면 처음부터 보여 드려요.")
        }
        demoJob = null
        _ui.update { it.copy(demoRunning = false, analysisBusy = false) }
    }

    // ───────── 분석 결과 반영 ─────────
    private suspend fun setFrames(frames: List<Frame>) {
        rawFrames = frames
        val shown = withContext(Dispatchers.Default) {
            frames.mapIndexedNotNull { i, f -> FrameEncoding.decode(f.jpeg)?.let { ShownFrame(i, f.offsetSeconds, f.provenance, it.asImageBitmap()) } }
        }
        _ui.update { it.copy(frames = shown, analysisStatus = if (frames.isEmpty()) NO_FRAMES else "장면 ${frames.size}개 저장") }
    }

    private fun applyResult(result: VisionResult, demo: DemoScenario?) {
        _ui.update { s ->
            val g = GuidanceReducer.afterAnalysis(s.guidance, result, demo)
            when (result) {
                is VisionResult.Success -> s.copy(
                    guidance = g, observation = result.observation, analysisSource = result.source, analysisModel = result.model, analysisError = null,
                    started = true, step = 0, paused = s.inCall, showFacts = false,
                    analysisStatus = if (result.source == AnalysisSource.FIXTURE) "체험용 예시 결과예요" else "AI 확인 결과가 왔어요 · 근거가 확실한 것만 반영해요",
                )
                is VisionResult.Failure -> s.copy(
                    guidance = g, observation = null, analysisSource = null, analysisError = "${result.message} (${result.code})", step = 0,
                    analysisStatus = "AI가 확인하지 못했어요 (${result.message}) · 결과를 지어내지 않고 기본 안내를 이어가요.",
                )
            }
        }
    }

    private fun invalidateAnalysis(message: String?) {
        analysisEpoch.invalidate()
        analysisJob?.cancel(); analysisJob = null
        captureJob?.cancel(); captureJob = null
        rawFrames = emptyList()
        _ui.update {
            it.copy(frames = emptyList(), observation = null, analysisSource = null, analysisModel = null, analysisError = null,
                analysisBusy = false, captureBusy = false, guidance = GuidanceReducer.clearAnalysis(it.guidance).copy(scenario = "unknown"),
                analysisStatus = message ?: NO_FRAMES)
        }
    }

    // ───────── 실제 입력: 영상 ─────────
    fun selectCamera() {
        if (_ui.value.liveSource == LiveSource.FILE) clearFile()
        invalidateAnalysis("장면을 가져올 곳이 바뀌었어요. 새로 저장해 주세요.")
        _ui.update { it.copy(liveSource = LiveSource.CAMERA, sourceMessage = "카메라 권한을 확인할게요. 카메라로 소리는 녹음하지 않아요.", cameraNeedsBind = it.cameraNeedsBind + 1) }
    }

    fun onCameraPermission(granted: Boolean) {
        if (granted) {
            _ui.update { it.copy(cameraNeedsBind = it.cameraNeedsBind + 1, sourceMessage = "카메라를 여는 중이에요.") }
        } else {
            camera.release()
            _ui.update { it.copy(liveSource = LiveSource.NONE, sourceMessage = "카메라 권한이 없어요. 블랙박스 영상 파일로도 쓸 수 있어요.") }
        }
    }

    fun reconnectCamera() { invalidateAnalysis("카메라를 다시 연결했어요."); _ui.update { it.copy(cameraNeedsBind = it.cameraNeedsBind + 1) } }

    fun disconnectCamera() {
        camera.release(); invalidateAnalysis("카메라 연결을 끊었어요.")
        _ui.update { it.copy(liveSource = LiveSource.NONE, sourceMessage = "카메라 연결을 끊었어요.") }
    }

    fun selectOem() {
        _ui.update { it.copy(liveSource = LiveSource.OEM, sourceMessage = "차량 블랙박스(빌트인 캠) 직접 연결은 아직 준비 중이에요. 지금은 블랙박스 앱에서 받은 영상 파일을 골라 주세요.") }
    }

    fun selectFile(uri: Uri, name: String) {
        camera.release(); clearFile()
        invalidateAnalysis("새 영상을 골랐어요. 사고 순간을 고른 뒤 장면을 저장해 주세요.")
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val src = VideoFileSource(ctx, uri, name)
        fileSource = src
        _ui.update { it.copy(liveSource = LiveSource.FILE, fileName = name, sourceMessage = "$name · 영상을 읽는 중") }
        viewModelScope.launch {
            try {
                val d = src.open()
                if (fileSource !== src) return@launch
                _ui.update { it.copy(fileDurationMs = d, fileAnchorMs = d / 2, sourceMessage = "$name · 휴대폰 안에서만 읽어요") }
                loadPreview(d / 2)
            } catch (e: InvalidVideoException) {
                if (fileSource !== src) return@launch
                fileSource = null
                _ui.update { it.copy(liveSource = LiveSource.NONE, fileName = null, fileDurationMs = 0, filePreview = null, sourceMessage = e.message ?: "열 수 없는 파일이에요.") }
            }
        }
    }

    fun setAnchor(ms: Long) { _ui.update { it.copy(fileAnchorMs = ms) }; loadPreview(ms) }

    private fun loadPreview(ms: Long) {
        val src = fileSource ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(120)
            val bmp: Bitmap? = src.frameAt(ms)
            if (fileSource === src) _ui.update { it.copy(filePreview = bmp?.asImageBitmap()) }
        }
    }

    fun clearFile() {
        fileSource?.release(); fileSource = null; previewJob?.cancel()
        _ui.update { it.copy(fileName = null, fileDurationMs = 0, fileAnchorMs = 0, filePreview = null, liveSource = if (it.liveSource == LiveSource.FILE) LiveSource.NONE else it.liveSource) }
    }

    private fun currentLiveSource(): VideoSource? = when (_ui.value.liveSource) {
        LiveSource.CAMERA -> camera.takeIf { _ui.value.cameraConnected }
        LiveSource.FILE -> fileSource
        else -> null
    }

    fun captureFrames(incidentAnchor: Double? = null, then: (() -> Unit)? = null) {
        val src = currentLiveSource() ?: run { _ui.update { it.copy(analysisStatus = "카메라나 영상 파일을 먼저 연결해 주세요.") }; return }
        if (_ui.value.captureBusy || _ui.value.analysisBusy) return
        val epoch = analysisEpoch.next()
        analysisJob?.cancel()
        _ui.update { it.copy(captureBusy = true, frames = emptyList(), observation = null, analysisSource = null, analysisError = null,
            analysisStatus = if (src === camera && incidentAnchor != null) "사고 직전 장면을 지키고, 이후 3초를 더 저장하고 있어요." else "장면을 저장하는 중", guidance = GuidanceReducer.clearAnalysis(it.guidance)) }
        captureJob = viewModelScope.launch {
            try {
                val anchor = if (src === fileSource) _ui.value.fileAnchorMs.toDouble() else incidentAnchor
                val frames = src.captureIncidentFrames(anchor)
                if (!analysisEpoch.isCurrent(epoch)) return@launch
                if (frames.isEmpty()) throw InvalidVideoException("장면을 저장하지 못했어요. 영상을 다시 확인해 주세요.")
                setFrames(frames)
                _ui.update { it.copy(captureBusy = false, analysisStatus = "${src.provenance.label}에서 장면 ${frames.size}개 저장 · 아직 밖으로 보내지 않았어요") }
                then?.invoke()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (analysisEpoch.isCurrent(epoch)) _ui.update { it.copy(captureBusy = false, analysisStatus = e.message ?: "장면을 저장하지 못했어요.") }
            }
        }
    }

    // ───────── 실제 입력: 서버 분석 ─────────
    fun updateServer(url: String, token: String, model: String) {
        val cfg = ServerConfig(url.trim(), token.trim(), model.trim())
        prefs.edit().putString("serverUrl", cfg.baseUrl).putString("token", cfg.token).putString("model", cfg.model).apply()
        val changed = cfg.baseUrl != _ui.value.server.baseUrl
        _ui.update { it.copy(server = cfg, health = if (changed) null else it.health, consent = if (changed) false else it.consent, urlMessage = when (val c = ServerUrlPolicy.check(cfg.baseUrl, BuildConfig.DEBUG, BuildConfig.DEV_HOST)) { is UrlCheck.Ok -> c.warning; is UrlCheck.Rejected -> c.reason }) }
        if (changed) { disconnectEvents(); invalidateAnalysis("AI 서버 주소가 바뀌었어요. 이전 결과는 쓰지 않아요.") }
    }

    private fun checkedClient(): ServerClient? {
        val cfg = _ui.value.server
        return when (val c = ServerUrlPolicy.check(cfg.baseUrl, BuildConfig.DEBUG, BuildConfig.DEV_HOST)) {
            is UrlCheck.Ok -> ServerClient(cfg.copy(baseUrl = c.normalized))
            is UrlCheck.Rejected -> { _ui.update { it.copy(urlMessage = c.reason) }; null }
        }
    }

    fun checkHealth() {
        val client = checkedClient() ?: return
        _ui.update { it.copy(healthBusy = true) }
        viewModelScope.launch {
            val h = client.health(checkModel = true)
            _ui.update { it.copy(health = h, healthBusy = false) }
        }
    }

    fun setConsent(v: Boolean) = _ui.update { it.copy(consent = v) }

    fun runAnalysis() {
        val s = _ui.value
        if (s.analysisBusy || s.captureBusy) return
        if (rawFrames.isEmpty()) { _ui.update { it.copy(analysisStatus = "먼저 사고 장면을 저장해 주세요.") }; return }
        if (!s.consent) { _ui.update { it.copy(analysisStatus = "장면을 AI 서버로 보내는 데 동의해 주세요.") }; return }
        val client = checkedClient() ?: run { _ui.update { it.copy(analysisStatus = "AI 서버 주소를 확인해 주세요.") }; return }
        val frames = rawFrames.take(6)
        val epoch = analysisEpoch.next()
        val requestId = "a-" + UUID.randomUUID().toString().replace("-", "").take(20)
        _ui.update { it.copy(analysisBusy = true, observation = null, analysisSource = null, analysisError = null, guidance = GuidanceReducer.clearAnalysis(it.guidance),
            analysisStatus = "AI가 장면을 보고 있어요. 그동안에도 안내와 도움 요청은 쓸 수 있어요.") }
        analysisJob = viewModelScope.launch {
            val result = client.visionClient().analyze(frames, requestId)
            if (!analysisEpoch.isCurrent(epoch)) return@launch // 취소·입력 전환 뒤 늦게 도착한 응답은 버린다
            applyResult(result, null)
            _ui.update { it.copy(analysisBusy = false, pendingRecord = buildRecord("live_input", null, frames.first().provenance, lastTelemetrySource)) }
            if (result is VisionResult.Success) speakThenAsk() else maybeAskInsurance()
        }
    }

    fun cancelAnalysis() {
        invalidateAnalysisKeepFrames()
        _ui.update { it.copy(analysisStatus = "AI 확인을 취소했어요. 기본 안내는 계속 쓸 수 있어요.") }
    }

    private fun invalidateAnalysisKeepFrames() {
        analysisEpoch.invalidate(); analysisJob?.cancel(); analysisJob = null; captureJob?.cancel(); captureJob = null
        _ui.update { it.copy(analysisBusy = false, captureBusy = false) }
    }

    // ───────── 사고 후보 이벤트 ─────────
    /** 사고 후보 → 즉시 공통 안내 → 프레임 확보 → (설정돼 있으면) 분석. */
    fun onIncident(event: IncidentEvent?, origin: String) {
        if (_ui.value.screen != Screen.LIVE) return
        val ev = event ?: IncidentEvent(t = SystemClock.elapsedRealtime().toDouble(), impactG = 0.0, speedDropKmh = 0.0, source = "manual_test", inputSource = "manual_test")
        lastTelemetrySource = ev.inputSource
        invalidateAnalysisKeepFrames()
        _ui.update { it.copy(lastIncident = ev, incidentOrigin = origin, guidance = GuidanceReducer.clearAnalysis(it.guidance).copy(scenario = "unknown"), started = true, step = 0, paused = false, inCall = false, showFacts = false) }
        armInsurance()
        speakThenAsk()
        if (currentLiveSource() == null) {
            _ui.update { it.copy(analysisStatus = "연결한 카메라·영상이 없어 장면은 저장하지 못했어요. 기본 안내를 이어가요.") }; return
        }
        val anchor = if (_ui.value.liveSource == LiveSource.CAMERA) SystemClock.elapsedRealtime().toDouble() else null
        captureFrames(anchor) {
            val st = _ui.value
            if (st.consent && st.server.baseUrl.isNotBlank()) runAnalysis()
            else { _ui.update { it.copy(analysisStatus = it.analysisStatus + " · AI 확인은 설정하지 않아 기본 안내를 이어가요.") }; if (!_ui.value.audioPlaying) maybeAskInsurance() }
        }
    }

    fun connectEvents() {
        val client = checkedClient() ?: return
        disconnectEvents()
        _ui.update { it.copy(eventsOn = true, eventsState = "서버에 연결하는 중") }
        eventsJob = viewModelScope.launch {
            IncidentEventStream(client) { st ->
                _ui.update { it.copy(eventsState = when (st) {
                    StreamState.Connecting -> "서버에 연결하는 중"
                    StreamState.Connected -> "서버에서 충격 신호를 기다리는 중"
                    is StreamState.Waiting -> "${st.reason} · ${st.retryInMs / 1000}초 뒤 다시 연결해요"
                }) }
            }.events().collect { ev ->
                val origin = if (ev.inputSource == TelemetrySourceTag.SYNTHETIC_FIXTURE) "서버 신호 · 예시(실제 사고 아님)" else "서버 신호 · 차량 신호"
                onIncident(ev, origin)
            }
        }
    }

    fun disconnectEvents() {
        eventsJob?.cancel(); eventsJob = null
        _ui.update { it.copy(eventsOn = false, eventsState = "서버 신호 받기 꺼짐") }
    }

    fun startSensor() {
        stopSensor()
        _ui.update { it.copy(sensorOn = true, sensorStatus = "휴대폰 센서로 살피는 중") }
        val detector = RuleBasedIncidentDetector()
        sensorJob = viewModelScope.launch {
            launch { sensor.status.collect { st -> if (_ui.value.sensorOn && !st.connected) _ui.update { it.copy(sensorStatus = st.message) } } }
            sensor.samples().collect { s ->
                val ev = try { detector.push(s) } catch (e: IllegalArgumentException) { null }
                if (ev != null) onIncident(ev, "휴대폰 센서")
            }
        }
    }

    fun stopSensor() {
        sensorJob?.cancel(); sensorJob = null
        _ui.update { it.copy(sensorOn = false, sensorStatus = "휴대폰 센서 꺼짐") }
    }

    private fun disconnectLiveInputs() {
        camera.release(); clearFile(); disconnectEvents(); stopSensor()
        _ui.update { it.copy(liveSource = LiveSource.NONE, sourceMessage = "장면을 가져올 곳을 골라 주세요.") }
    }

    // ───────── 기록 ─────────
    private fun buildRecord(mode: String, scenario: String?, input: FrameProvenance, telemetry: String?, noIncident: Boolean = false): IncidentRecord {
        val s = _ui.value
        val keys = keys(s)
        return IncidentRecord(
            id = "r" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().take(6),
            createdAt = Instant.now().toString(),
            mode = mode, scenario = scenario,
            inputSource = input.code,
            telemetrySource = telemetry,
            analysisSource = s.analysisSource?.code ?: "none",
            analysisError = s.analysisError,
            model = s.analysisModel,
            incident = if (noIncident) null else s.lastIncident,
            frames = if (noIncident) emptyList() else rawFrames.mapIndexed { i, f -> FrameMeta(i, f.offsetSeconds, f.provenance.code) },
            observation = s.observation,
            userReports = UserReports(s.guidance.smoke, s.guidance.immobile),
            guidanceKeys = if (s.started) keys else emptyList(),
            guidanceText = if (s.started) Guides[keys[s.step]].text else "",
            unknowns = listOf("부상 정도", "안전한 이동 가능 여부", "확정 대피 방향") +
                (if (s.observation == null || s.observation.visibility == "unusable") listOf("영상 속 위험 단서(판단 보류)") else emptyList()) +
                (if (s.guidance.scenario == "unknown") listOf("현재 위치") else emptyList()),
            insuranceCall = s.insOutcome,
        )
    }

    fun saveCurrentRecord() {
        val base = _ui.value.pendingRecord ?: return toast("남길 기록이 없어요.")
        // 저장 시점의 사용자 알림·안내를 반영한다.
        val s = _ui.value
        val keys = keys(s)
        val r = base.copy(userReports = UserReports(s.guidance.smoke, s.guidance.immobile),
            guidanceKeys = if (s.started) keys else base.guidanceKeys,
            guidanceText = if (s.started) Guides[keys[s.step]].text else base.guidanceText,
            insuranceCall = s.insOutcome)
        store.save(r)
        _ui.update { it.copy(records = store.list(), pendingRecord = null) }
        toast("기록을 남겼어요. 영상 원본은 저장하지 않아요.")
    }

    fun deleteRecord(id: String) { store.delete(id); _ui.update { it.copy(records = store.list()) }; toast("기록을 지웠어요.") }

    fun exportRecord(r: IncidentRecord, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(store.export(r).toByteArray()) } ?: error("no stream") }.isSuccess
            toast(if (ok) "기록을 파일로 저장했어요." else "파일로 저장하지 못했어요.")
        }
    }

    override fun onCleared() {
        audio.onFinished = null; voice.stop(); audio.release(); camera.shutdown()
    }

    @Suppress("unused") private val kindRef = AnalysisKind.NONE
}
