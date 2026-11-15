package com.afterstop.app.core

import kotlinx.coroutines.delay

/** 처리 단계: 영상 입력 → 사고 후보 감지 → 버퍼 → 분석 → 안내·음성 → 기록 */
enum class Stage(val label: String) {
    INPUT("주행 확인"),
    DETECT("충격 감지"),
    BUFFER("사고 장면 저장"),
    ANALYZE("AI로 상황 확인"),
    GUIDE("음성 안내"),
    RECORD("기록"),
}

sealed interface PipelineEvent {
    data class Driving(val sample: TelemetrySample) : PipelineEvent
    data class Detected(val event: IncidentEvent) : PipelineEvent
    data class Buffered(val frames: List<Frame>) : PipelineEvent
    data object Analyzing : PipelineEvent
    data class Analyzed(val result: VisionResult) : PipelineEvent
    data object NoIncident : PipelineEvent
}

sealed interface PipelineOutcome {
    data class NoIncident(val samples: List<TelemetrySample>) : PipelineOutcome
    data class Completed(
        val event: IncidentEvent,
        val frames: List<Frame>,
        val result: VisionResult,
        val samples: List<TelemetrySample>,
    ) : PipelineOutcome
}

/** 분석 결과를 안내 선택 입력에 반영한다. 실패하면 공통 안내(판단 보류)를 유지한다. */
object GuidanceReducer {
    fun afterAnalysis(base: GuidanceState, result: VisionResult, demoScenario: DemoScenario?): GuidanceState = when (result) {
        is VisionResult.Success -> {
            val o = result.observation
            if (result.source == AnalysisSource.FIXTURE) base.copy(
                scenario = demoScenario?.guidanceScenario ?: "unknown",
                analysisKind = AnalysisKind.DEMO,
                analysisReady = true,
                observedSmoke = o.inferredRisk,
                visionScene = o.scene,
                lost = false,
            ) else base.copy(
                scenario = "unknown",
                analysisKind = AnalysisKind.AI,
                analysisReady = true,
                observedSmoke = o.inferredRisk,
                visionScene = o.scene,
                lost = false,
            )
        }
        is VisionResult.Failure -> base.copy(
            scenario = "unknown", analysisKind = AnalysisKind.NONE, analysisReady = false,
            observedSmoke = false, visionScene = null,
        )
    }

    fun clearAnalysis(base: GuidanceState): GuidanceState = base.copy(
        analysisKind = AnalysisKind.NONE, analysisReady = false, observedSmoke = false, visionScene = null,
    )
}

/**
 * 신호 → 감지 → 프레임 확보 → 분석의 공통 흐름.
 * 제품 시연과 실제 입력이 같은 감지기·응답 검사·안내 선택 코드를 거치도록 한다.
 */
class IncidentPipeline(
    private val detector: IncidentDetector,
    private val sampleIntervalMs: Long = 100,
) {
    suspend fun run(
        samples: List<TelemetrySample>,
        captureFrames: suspend (IncidentEvent) -> List<Frame>,
        vision: VisionClient,
        requestId: String,
        onEvent: suspend (PipelineEvent) -> Unit,
        bufferPauseMs: Long = 850,
        analyzePauseMs: Long = 1500,
    ): PipelineOutcome {
        detector.reset()
        var incident: IncidentEvent? = null
        for (sample in samples) {
            onEvent(PipelineEvent.Driving(sample))
            val e = detector.push(sample)
            if (e != null && incident == null) {
                incident = e
                onEvent(PipelineEvent.Detected(e))
            }
            delay(sampleIntervalMs)
        }
        val event = incident ?: run {
            onEvent(PipelineEvent.NoIncident)
            return PipelineOutcome.NoIncident(samples)
        }
        val frames = captureFrames(event)
        onEvent(PipelineEvent.Buffered(frames))
        delay(bufferPauseMs)
        onEvent(PipelineEvent.Analyzing)
        delay(analyzePauseMs)
        val result = if (frames.isEmpty()) VisionResult.Failure("no_frames", "프레임을 확보하지 못했습니다.")
        else vision.analyze(frames, requestId)
        onEvent(PipelineEvent.Analyzed(result))
        return PipelineOutcome.Completed(event, frames, result, samples)
    }
}

/** 시연용 분석부. 예시 응답을 실제 응답과 같은 형식(JSON)으로 만든 뒤 같은 검사를 거친다. */
class FixtureVisionClient(private val scenario: DemoScenario) : VisionClient {
    override val analysisSource = AnalysisSource.FIXTURE
    override suspend fun analyze(frames: List<Frame>, requestId: String): VisionResult {
        val json = VisionSanitizer.toSchemaJson(DemoFixtures.demoObservation(scenario))
        return try {
            VisionResult.Success(VisionSanitizer.sanitizeJson(json, frames.size), AnalysisSource.FIXTURE, null)
        } catch (e: InvalidVisionResponseException) {
            VisionResult.Failure("invalid_fixture", e.message ?: "형식 오류")
        }
    }
}
