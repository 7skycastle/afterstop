package com.afterstop.app.core

import kotlin.math.sqrt

/**
 * 휴대폰 센서 값을 텔레메트리 규격으로 바꾸는 순수 함수 모음.
 *
 * 중력 제거와 방향:
 * - 기본 입력은 Android `TYPE_LINEAR_ACCELERATION`(센서 융합으로 중력을 뺀 m/s² 3축)이다.
 *   세 축의 벡터 크기를 쓰므로 휴대폰 거치 방향이 바뀌어도 크기는 같다(방향 정보는 버린다).
 * - 이 센서가 없는 기기는 `TYPE_ACCELEROMETER` 에 저역 통과 필터로 중력을 추정해 뺀다.
 *   이 경우 회전 직후 잠깐 오차가 크므로 [GravityFilter.isSettled] 전에는 무효로 처리한다.
 * - m/s² → g 변환은 표준 중력 9.80665 로 나눈다. m/s² 값을 그대로 g 로 쓰지 않는다.
 *
 * 휴대폰 센서는 차량용으로 인증된 충돌 센서가 아니며 감지 성능을 검증하지 않았다.
 */
object SensorMath {
    const val STANDARD_GRAVITY = 9.80665

    fun magnitude(x: Float, y: Float, z: Float): Double =
        sqrt(x.toDouble() * x + y.toDouble() * y + z.toDouble() * z)

    fun metersPerSecondSquaredToG(ms2: Double): Double = ms2 / STANDARD_GRAVITY

    fun linearG(x: Float, y: Float, z: Float): Double = metersPerSecondSquaredToG(magnitude(x, y, z))

    fun metersPerSecondToKmh(mps: Double): Double = mps * 3.6
}

/** 가속도계 원시값에서 중력을 추정해 제거하는 저역 통과 필터 (선형 가속도 센서가 없을 때만 사용). */
class GravityFilter(private val alpha: Double = 0.8, private val settleSamples: Int = 25) {
    private val gravity = DoubleArray(3)
    private var count = 0
    val isSettled: Boolean get() = count >= settleSamples

    /** 중력을 뺀 선형 가속도 크기(g). */
    fun linearG(x: Float, y: Float, z: Float): Double {
        val v = doubleArrayOf(x.toDouble(), y.toDouble(), z.toDouble())
        if (count == 0) v.copyInto(gravity)
        for (i in 0..2) gravity[i] = alpha * gravity[i] + (1 - alpha) * v[i]
        count++
        val lx = v[0] - gravity[0]
        val ly = v[1] - gravity[1]
        val lz = v[2] - gravity[2]
        return SensorMath.metersPerSecondSquaredToG(sqrt(lx * lx + ly * ly + lz * lz))
    }

    fun reset() {
        gravity.fill(0.0); count = 0
    }
}

/** 위치 기반 속도 한 건. [elapsedMs] 는 기기 부팅 후 단조 증가 시각(elapsedRealtime). */
data class SpeedReading(
    val elapsedMs: Long,
    val speedKmh: Double,
    /** km/h 단위 속도 오차. 기기가 제공하지 않으면 null. */
    val accuracyKmh: Double?,
    val source: String,
)

sealed interface GateResult {
    data class Valid(val sample: TelemetrySample) : GateResult
    data class Invalid(val reason: String) : GateResult
}

/**
 * 감지기에 넣기 전 입력 유효성 검사.
 * 속도 미수신·오래된 속도·신뢰하기 어려운 값은 무효로 처리하고, 절대 0km/h 로 채우지 않는다.
 */
class TelemetryGate(
    private val maxSpeedAgeMs: Long = 2000,
    private val maxSpeedAccuracyKmh: Double = 10.0,
) {
    private var lastT = Long.MIN_VALUE

    fun evaluate(nowElapsedMs: Long, accelG: Double?, speed: SpeedReading?, source: String): GateResult {
        if (accelG == null || !accelG.isFinite() || accelG < 0 || accelG > 30) return GateResult.Invalid("가속도 값을 읽지 못했거나 범위를 벗어났습니다")
        if (speed == null) return GateResult.Invalid("속도 미수신")
        val age = nowElapsedMs - speed.elapsedMs
        if (age < 0) return GateResult.Invalid("속도 시각이 현재보다 늦습니다")
        if (age > maxSpeedAgeMs) return GateResult.Invalid("속도 값이 오래되었습니다 (${age}ms)")
        if (!speed.speedKmh.isFinite() || speed.speedKmh < 0 || speed.speedKmh > 250) return GateResult.Invalid("속도 값이 범위를 벗어났습니다")
        val acc = speed.accuracyKmh
        if (acc != null && (!acc.isFinite() || acc > maxSpeedAccuracyKmh)) return GateResult.Invalid("속도 오차가 큽니다")
        if (nowElapsedMs <= lastT) return GateResult.Invalid("시간이 역전되었습니다")
        lastT = nowElapsedMs
        return GateResult.Valid(TelemetrySample(nowElapsedMs.toDouble(), speed.speedKmh, accelG, source))
    }

    fun reset() {
        lastT = Long.MIN_VALUE
    }
}
