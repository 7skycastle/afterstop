package com.afterstop.app.oem

import com.afterstop.app.core.EmergencyBridge
import com.afterstop.app.core.Frame
import com.afterstop.app.core.FrameProvenance
import com.afterstop.app.core.SourceStatus
import com.afterstop.app.core.TelemetrySample
import com.afterstop.app.core.TelemetrySource
import com.afterstop.app.core.TelemetrySourceTag
import com.afterstop.app.core.VideoSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 현대차 전용 연결부 자리표시 어댑터.
 *
 * 이 앱은 현대차 빌트인 캠 영상, 충돌 신호, SOS 에 접근할 권한·규격을 갖고 있지 않다.
 * 아래 클래스는 '교체할 위치'만 보여주며 항상 연결 안 됨을 보고한다. 아무 데이터도 만들지 않는다.
 *
 * 연동 시 할 일 (규격·권한 확보 후):
 * 1. [HyundaiBuiltinCamVideoSource]: 제조사가 허용한 스트림/클립 API 에서 프레임을 받아 [Frame] 으로 변환
 *    (provenance = OEM_BUILTIN_CAM, t = 차량 시각 ms).
 * 2. [HyundaiVehicleTelemetrySource]: 차량 속도(km/h)·중력 제거 가속도(g)·충돌 신호를 [TelemetrySample] 로 변환
 *    (source = oem_vehicle). 차량 자체 충돌 신호가 있으면 IncidentDetector 구현을 교체할 수 있다.
 * 3. [HyundaiSosBridge]: 차량 SOS 호출 API 가 허용될 때만 performsRealCall = true 로 구현.
 * 4. AdapterRegistry 에서 기본 구현을 바꾸고, 화면의 연결 상태는 실제 연결 여부로만 표시한다.
 */
private const val NOT_CONNECTED = "연결 안 됨 · 제조사 권한·규격 확보 후 교체할 어댑터"

class HyundaiBuiltinCamVideoSource : VideoSource {
    override val id = "hyundai_builtin_cam"
    override val provenance = FrameProvenance.OEM_BUILTIN_CAM
    override val status: StateFlow<SourceStatus> = MutableStateFlow(SourceStatus(false, NOT_CONNECTED))
    override suspend fun captureIncidentFrames(anchorMs: Double?): List<Frame> =
        throw UnsupportedOperationException("현대차 빌트인 캠 연동은 구현되지 않았습니다.")
    override fun release() {}
}

class HyundaiVehicleTelemetrySource : TelemetrySource {
    override val id = "hyundai_vehicle_signal"
    override val sourceTag = TelemetrySourceTag.OEM_VEHICLE
    override val status: StateFlow<SourceStatus> = MutableStateFlow(SourceStatus(false, NOT_CONNECTED))
    override fun samples(): Flow<TelemetrySample> = emptyFlow()
}

class HyundaiSosBridge : EmergencyBridge {
    override val id = "hyundai_sos"
    override val performsRealCall = false
    override val description = "차량 SOS 연동 안 됨 · $NOT_CONNECTED"
}

/** 앱 기본 도움 요청: 화면 시연만 하며 실제 전화·신고를 하지 않는다. */
class SimulatedEmergencyBridge : EmergencyBridge {
    override val id = "simulated_call"
    override val performsRealCall = false
    override val description = "모의 통화 화면 · 실제 발신·신고 없음"
}

data class AdapterInfo(val role: String, val name: String, val connected: Boolean, val note: String)

/** 제품 구성 화면에 보여줄 어댑터 목록. 연결 상태는 실제 값만 쓴다. */
object AdapterRegistry {
    fun oemAdapters(): List<AdapterInfo> = listOf(
        AdapterInfo("VideoSource", "HyundaiBuiltinCamVideoSource", false, NOT_CONNECTED),
        AdapterInfo("TelemetrySource", "HyundaiVehicleTelemetrySource", false, NOT_CONNECTED),
        AdapterInfo("EmergencyBridge", "HyundaiSosBridge", false, NOT_CONNECTED),
    )
}
