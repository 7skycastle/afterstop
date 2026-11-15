package com.afterstop.app.telemetry

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import com.afterstop.app.core.DemoFixtures
import com.afterstop.app.core.DemoScenario
import com.afterstop.app.core.GateResult
import com.afterstop.app.core.GravityFilter
import com.afterstop.app.core.SensorMath
import com.afterstop.app.core.SourceStatus
import com.afterstop.app.core.SpeedReading
import com.afterstop.app.core.TelemetryGate
import com.afterstop.app.core.TelemetrySample
import com.afterstop.app.core.TelemetrySource
import com.afterstop.app.core.TelemetrySourceTag
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 시연용 예시 차량 신호 (synthetic_fixture). */
class FixtureTelemetrySource(private val scenario: DemoScenario, private val intervalMs: Long = 100) : TelemetrySource {
    override val id = "fixture_${scenario.key}"
    override val sourceTag = TelemetrySourceTag.SYNTHETIC_FIXTURE
    override val status: StateFlow<SourceStatus> = MutableStateFlow(SourceStatus(true, "예시 차량 신호 · 실제 센서 아님"))
    override fun samples(): Flow<TelemetrySample> = flow {
        for (s in DemoFixtures.telemetryFor(scenario)) { emit(s); delay(intervalMs) }
    }
}

/**
 * 휴대폰 센서 어댑터 (선형 가속도 + 위치 기반 속도).
 * - 휴대폰 센서는 차량용 인증 충돌 센서가 아니며 감지 성능을 검증하지 않았다.
 * - 속도는 GPS 위치 기반 추정치이며 차량 속도계 값이 아니다 (출처: phone_sensor_unverified).
 * - 속도가 없거나 2초보다 오래되면 샘플을 내보내지 않고 상태에 이유를 표시한다(0 으로 채우지 않음).
 */
class PhoneSensorTelemetrySource(private val context: Context) : TelemetrySource {
    override val id = "phone_sensor"
    override val sourceTag = TelemetrySourceTag.PHONE_SENSOR
    private val _status = MutableStateFlow(SourceStatus(false, "휴대폰 센서 연결 전"))
    override val status: StateFlow<SourceStatus> = _status
    private val _lastInvalid = MutableStateFlow<String?>(null)
    val lastInvalid: StateFlow<String?> = _lastInvalid

    @SuppressLint("MissingPermission")
    override fun samples(): Flow<TelemetrySample> = callbackFlow {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val linear = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val raw = if (linear == null) sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null
        if (linear == null && raw == null) {
            _status.value = SourceStatus(false, "가속도 센서가 없는 기기입니다.")
            close(); return@callbackFlow
        }
        val gravity = GravityFilter()
        val windowMaxG = java.util.concurrent.atomic.AtomicReference<Double?>(null)
        val speedRef = java.util.concurrent.atomic.AtomicReference<SpeedReading?>(null)
        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val g = if (e.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) SensorMath.linearG(e.values[0], e.values[1], e.values[2])
                else gravity.linearG(e.values[0], e.values[1], e.values[2]).takeIf { gravity.isSettled }
                if (g != null) windowMaxG.getAndUpdate { maxOf(it ?: 0.0, g) }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        val locListener = LocationListener { loc: Location ->
            if (!loc.hasSpeed()) return@LocationListener
            val acc = if (Build.VERSION.SDK_INT >= 26 && loc.hasSpeedAccuracy()) SensorMath.metersPerSecondToKmh(loc.speedAccuracyMetersPerSecond.toDouble()) else null
            speedRef.set(SpeedReading(loc.elapsedRealtimeNanos / 1_000_000, SensorMath.metersPerSecondToKmh(loc.speed.toDouble()), acc, "phone_gps_speed"))
        }
        sm.registerListener(sensorListener, linear ?: raw, SensorManager.SENSOR_DELAY_GAME)
        val gpsOk = try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, locListener, Looper.getMainLooper()); true
        } catch (e: SecurityException) { false } catch (e: IllegalArgumentException) { false }
        _status.value = SourceStatus(true,
            (if (linear != null) "선형 가속도 센서" else "가속도계(중력 추정 제거)") +
                (if (gpsOk) " + GPS 속도 · 차량 인증 센서 아님" else " · 위치 권한 없음: 속도 미수신으로 감지 입력 무효"))
        val gate = TelemetryGate()
        val ticker = launch {
            while (isActive) {
                delay(100)
                val g = windowMaxG.getAndSet(null)
                when (val r = gate.evaluate(SystemClock.elapsedRealtime(), g, speedRef.get(), TelemetrySourceTag.PHONE_SENSOR)) {
                    is GateResult.Valid -> { _lastInvalid.value = null; trySend(r.sample) }
                    is GateResult.Invalid -> _lastInvalid.value = r.reason
                }
            }
        }
        awaitClose {
            ticker.cancel()
            sm.unregisterListener(sensorListener)
            runCatching { lm.removeUpdates(locListener) }
            _status.value = SourceStatus(false, "휴대폰 센서 연결을 종료했습니다.")
        }
    }
}
