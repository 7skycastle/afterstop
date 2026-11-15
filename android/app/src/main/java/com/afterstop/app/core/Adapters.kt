package com.afterstop.app.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicLong

/**
 * 교체 가능한 입력·처리부 계약.
 * 차량 제조사(현대차 빌트인 캠·충돌 신호·SOS) 연동은 권한과 규격이 확보되면 이 인터페이스의 구현을 바꿔 끼운다.
 * 현재는 휴대폰 카메라·영상 파일·예시 신호 구현만 동작하며, OEM 구현은 '연결 안 됨'만 보고한다.
 */

enum class FrameProvenance(val code: String, val label: String) {
    SYNTHETIC_DEMO("synthetic_illustration", "체험용 그림 장면"),
    PHONE_CAMERA("phone_camera", "휴대폰 카메라"),
    USER_VIDEO_FILE("user_video_file", "내가 고른 영상"),
    OEM_BUILTIN_CAM("oem_builtin_cam", "차량 블랙박스"),
}

/** 분석용 프레임. 영상 원본은 기록에 저장하지 않는다. */
class Frame(
    /** 입력 스트림 기준 시각(ms) */
    val t: Double,
    /** 사건 시점 기준 초 단위 차이(음수면 사건 전) */
    val offsetSeconds: Double,
    val jpeg: ByteArray,
    val provenance: FrameProvenance,
)

data class SourceStatus(val connected: Boolean, val message: String)

/** 영상 입력부. */
interface VideoSource {
    val id: String
    val provenance: FrameProvenance
    val status: StateFlow<SourceStatus>
    /** 사고 전후 프레임 확보. [anchorMs] 는 사건 시각(입력 스트림 기준), null 이면 현재 시점. 최대 6장. */
    suspend fun captureIncidentFrames(anchorMs: Double?): List<Frame>
    /** 버퍼와 장치를 정리한다. */
    fun release()
}

/** 차량 신호 입력부. */
interface TelemetrySource {
    val id: String
    /** TelemetrySourceTag 값 */
    val sourceTag: String
    val status: StateFlow<SourceStatus>
    fun samples(): Flow<TelemetrySample>
}

enum class AnalysisSource(val code: String, val label: String) {
    FIXTURE("fixture_response_not_model_inference", "체험용 예시 결과"),
    LOCAL_VISION_MODEL("local_vision_model", "AI 확인 결과"),
}

sealed interface VisionResult {
    data class Success(val observation: VisionObservation, val source: AnalysisSource, val model: String?) : VisionResult
    data class Failure(val code: String, val message: String) : VisionResult
}

/** 장면 분석부. 실패 시 절대 예시 응답으로 대체하지 않는다. */
interface VisionClient {
    val analysisSource: AnalysisSource
    suspend fun analyze(frames: List<Frame>, requestId: String): VisionResult
}

data class AudioState(val playingKey: String? = null, val message: String = "음성 준비")

/** 음성 재생부. */
interface AudioPlayer {
    val state: StateFlow<AudioState>
    fun hasClip(key: String): Boolean
    fun play(key: String)
    fun stop(reason: String? = null)
    fun release()
}

/** 긴급 도움 요청 연결부. 현재 구현은 모두 시연이며 실제 발신·신고를 하지 않는다. */
interface EmergencyBridge {
    val id: String
    val performsRealCall: Boolean
    val description: String
}

/** 늦게 도착한 이전 요청의 응답을 버리기 위한 세대 번호. */
class RequestEpoch {
    private val value = AtomicLong(0)
    fun next(): Long = value.incrementAndGet()
    fun invalidate() { value.incrementAndGet() }
    fun isCurrent(epoch: Long): Boolean = value.get() == epoch
}

@Serializable
data class FrameMeta(val index: Int, val offsetSeconds: Double, val source: String)
