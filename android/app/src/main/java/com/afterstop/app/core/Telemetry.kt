package com.afterstop.app.core

import kotlinx.serialization.Serializable

/**
 * 텔레메트리 한 샘플.
 * - [t]: 같은 스트림 안에서 증가하는 밀리초 단위 시간 값
 * - [speedKmh]: km/h
 * - [linearG]: 중력 성분을 제거한 가속도의 크기(g 단위). m/s² 값을 그대로 넣지 않는다.
 */
@Serializable
data class TelemetrySample(
    val t: Double,
    val speedKmh: Double,
    val linearG: Double,
    val source: String = TelemetrySourceTag.SYNTHETIC_FIXTURE,
)

object TelemetrySourceTag {
    const val SYNTHETIC_FIXTURE = "synthetic_fixture"
    const val PHONE_SENSOR = "phone_sensor_unverified"
    const val EXTERNAL = "external_telemetry"
    const val OEM_VEHICLE = "oem_vehicle"
}

@Serializable
data class IncidentEvent(
    val type: String = "incident_candidate",
    val t: Double,
    val impactG: Double,
    val speedDropKmh: Double,
    val source: String = "telemetry_rule",
    /** 사고 후보일 뿐 확정 사고가 아니다. 항상 false. */
    val confirmedCrash: Boolean = false,
    val inputSource: String = TelemetrySourceTag.SYNTHETIC_FIXTURE,
    val eventId: Long? = null,
)

class InvalidTelemetryException(message: String) : IllegalArgumentException(message)

/** 교체 가능한 사고 후보 감지기. */
interface IncidentDetector {
    /** 샘플을 넣고, 사고 후보 조건이 충족되면 이벤트를 돌려준다. 잘못된 샘플은 예외로 거부한다. */
    fun push(sample: TelemetrySample): IncidentEvent?
    fun reset()
}

/**
 * reference/web-prototype/shared/incident-core.cjs 의 IncidentDetector 를 그대로 이식한 규칙 기반 감지기.
 * 시제품 시험용 수치이며 실차에서 보정·인증된 사고 감지 기준이 아니다.
 */
class RuleBasedIncidentDetector : IncidentDetector {
    companion object {
        const val IMPACT_G = 2.5
        const val IMPACT_WINDOW_MS = 1500.0
        const val SPEED_WINDOW_MS = 2000.0
        const val RETAIN_MS = 3000.0
        const val SPEED_DROP_KMH = 25.0
        const val STOP_KMH = 5.0
        const val DEDUPE_MS = 10000.0
    }

    private var samples = ArrayList<TelemetrySample>()
    private var lastTime = Double.NEGATIVE_INFINITY
    private var lastEvent = Double.NEGATIVE_INFINITY

    override fun reset() {
        samples = ArrayList()
        lastTime = Double.NEGATIVE_INFINITY
        lastEvent = Double.NEGATIVE_INFINITY
    }

    override fun push(sample: TelemetrySample): IncidentEvent? {
        val s = sample
        if (!s.t.isFinite() || !s.speedKmh.isFinite() || !s.linearG.isFinite() ||
            s.t <= lastTime || s.speedKmh < 0 || s.speedKmh > 250 || s.linearG < 0 || s.linearG > 30
        ) throw InvalidTelemetryException("Invalid telemetry")
        lastTime = s.t
        samples.add(s)
        samples = ArrayList(samples.filter { s.t - it.t <= RETAIN_MS })
        val impact = samples.firstOrNull { s.t - it.t <= IMPACT_WINDOW_MS && it.linearG >= IMPACT_G }
        val recent = samples.filter { s.t - it.t <= SPEED_WINDOW_MS }
        val before = if (impact != null) recent.filter { it.t <= impact.t } else emptyList()
        val prior = if (before.isNotEmpty()) before.maxOf { it.speedKmh } else 0.0
        val stop = impact != null && s.t >= impact.t && s.speedKmh <= STOP_KMH
        val drop = prior - s.speedKmh >= SPEED_DROP_KMH
        if (impact != null && stop && drop && s.t - lastEvent > DEDUPE_MS) {
            lastEvent = s.t
            return IncidentEvent(
                t = s.t,
                impactG = impact.linearG,
                speedDropKmh = prior - s.speedKmh,
                inputSource = s.source,
            )
        }
        return null
    }
}

/** 메모리에 제한된 수만 보관하는 프레임 버퍼 (기본 최대 20장). */
class FrameRingBuffer<T : Any>(val max: Int = 20, private val timeOf: (T) -> Double) {
    private val items = ArrayDeque<T>()

    val size: Int get() = synchronized(items) { items.size }

    fun push(frame: T) {
        val t = timeOf(frame)
        require(t.isFinite()) { "Invalid frame" }
        synchronized(items) {
            items.addLast(frame)
            while (items.size > max) items.removeFirst()
        }
    }

    fun snapshot(): List<T> = synchronized(items) { items.toList() }

    fun around(t: Double, before: Double = 5000.0, after: Double = 3000.0): List<T> =
        snapshot().filter { timeOf(it) >= t - before && timeOf(it) <= t + after }

    fun last(n: Int): List<T> = snapshot().takeLast(n)

    fun clear() = synchronized(items) { items.clear() }
}
