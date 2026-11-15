package com.afterstop.app

import com.afterstop.app.core.AudioManifest
import com.afterstop.app.core.DemoFixtures
import com.afterstop.app.core.DemoScenario
import com.afterstop.app.core.FrameRingBuffer
import com.afterstop.app.core.GateResult
import com.afterstop.app.core.GravityFilter
import com.afterstop.app.core.Guides
import com.afterstop.app.core.InvalidTelemetryException
import com.afterstop.app.core.InvalidVisionResponseException
import com.afterstop.app.core.RequestEpoch
import com.afterstop.app.core.RuleBasedIncidentDetector
import com.afterstop.app.core.SensorMath
import com.afterstop.app.core.SpeedReading
import com.afterstop.app.core.TelemetryGate
import com.afterstop.app.core.TelemetrySample
import com.afterstop.app.core.VisionSanitizer
import com.afterstop.app.video.FrameTimes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class CoreTest {
    private val assets = File("src/main/assets")

    // ── 원본 core.test.cjs 이식 ──
    @Test fun combinedImpactSpeedLossAndStopCreateOneCandidate() {
        val d = RuleBasedIncidentDetector()
        val events = DemoFixtures.telemetryFor(DemoScenario.HIGHWAY).mapNotNull { d.push(it) }
        assertEquals(1, events.size); assertFalse(events[0].confirmedCrash)
    }

    @Test fun bumpAloneDoesNotCreateCandidate() {
        val d = RuleBasedIncidentDetector()
        assertEquals(0, DemoFixtures.telemetryFor(DemoScenario.BUMP).count { d.push(it) != null })
    }

    @Test fun hardBrakingWithoutImpactIsNotEnough() {
        val d = RuleBasedIncidentDetector()
        assertEquals(0, DemoFixtures.telemetryFor(DemoScenario.HIGHWAY).count { d.push(it.copy(linearG = 0.1)) != null })
    }

    @Test fun invalidAndUnorderedValuesFailClosed() {
        val d = RuleBasedIncidentDetector()
        d.push(TelemetrySample(10.0, 20.0, 0.0))
        expectInvalid { d.push(TelemetrySample(5.0, 10.0, 3.0)) }
        expectInvalid { d.push(TelemetrySample(20.0, Double.NaN, 3.0)) }
        expectInvalid { d.push(TelemetrySample(30.0, 10.0, Double.POSITIVE_INFINITY)) }
    }

    private fun expectInvalid(block: () -> Unit) = try { block(); fail("예외가 필요합니다") } catch (_: InvalidTelemetryException) {}

    @Test fun ringBufferBoundsAndSelectsInterval() {
        val b = FrameRingBuffer<Double>(3) { it }
        for (t in 0 until 5) b.push(t * 1000.0)
        assertEquals(3, b.size)
        assertEquals(listOf(2000.0, 3000.0), b.around(3000.0, 1000.0, 0.0))
        val big = FrameRingBuffer<Double>(20) { it }
        repeat(50) { big.push(it.toDouble()) }
        assertEquals(20, big.size); assertEquals(30.0, big.snapshot().first(), 0.0)
        big.clear(); assertEquals(0, big.size)
    }

    @Test fun unusableFramesCannotInferHazard() {
        val raw = """{"scene":"tunnel","visibility":"unusable","smoke":"observed","fire":"observed","evidence_frames":[0],"observations":["왼쪽으로 이동하세요."]}"""
        val v = VisionSanitizer.sanitizeJson(raw, 1)
        assertFalse(v.inferredRisk); assertEquals(emptyList<String>(), v.observations)
        val wrongFrame = VisionSanitizer.sanitizeJson(raw.replace("unusable", "usable").replace("[0]", "[99]"), 1)
        assertFalse(wrongFrame.inferredRisk); assertEquals("unknown", wrongFrame.smoke)
    }

    @Test fun malformedJsonIsRejected() {
        for (bad in listOf("not json", "\"string\"", "42", "null")) {
            try { VisionSanitizer.sanitizeJson(bad, 3); fail(bad) } catch (_: InvalidVisionResponseException) {}
        }
    }

    // ── 감지 경계값 ──
    @Test fun detectorThresholdBoundaries() {
        fun run(g: Double, before: Double, after: Double, dt: Double): Boolean {
            val d = RuleBasedIncidentDetector()
            return listOf(TelemetrySample(0.0, before, 0.0), TelemetrySample(100.0, before, g), TelemetrySample(100 + dt, after, 0.0)).any { d.push(it) != null }
        }
        assertTrue("2.5g 정확히는 충격", run(2.5, 72.0, 0.0, 500.0))
        assertFalse("2.4999g 는 충격 아님", run(2.4999, 72.0, 0.0, 500.0))
        assertTrue("정확히 25km/h 감속", run(3.0, 30.0, 5.0, 500.0))
        assertFalse("24.9km/h 감속", run(3.0, 29.9, 5.0, 500.0))
        assertFalse("5.1km/h 는 정차 아님", run(3.0, 30.0, 5.1, 500.0))
        assertTrue("충격 후 1500ms 이내", run(3.0, 72.0, 0.0, 1500.0))
        assertFalse("충격 후 1500ms 초과", run(3.0, 72.0, 0.0, 1501.0))
    }

    @Test fun duplicateEventsAreSuppressedForTenSeconds() {
        val d = RuleBasedIncidentDetector()
        fun crash(base: Double) = listOf(TelemetrySample(base, 72.0, 0.0), TelemetrySample(base + 100, 72.0, 3.0), TelemetrySample(base + 600, 0.0, 0.0), TelemetrySample(base + 5000, 72.0, 0.0))
        val e1 = (crash(0.0) + crash(5100.0)).count { d.push(it) != null }
        assertEquals("10초 이내 두 번째 후보는 억제", 1, e1)
        val e2 = crash(11000.0).count { d.push(it) != null }
        assertEquals("10초 이후에는 다시 감지", 1, e2)
    }

    // ── 시연 fixture 가 fixtures/*.json 과 같은지 ──
    @Test fun demoFixturesMatchJsonFiles() {
        for (s in DemoScenario.entries) {
            val o = Json.parseToJsonElement(File(assets, "fixtures/${s.key}.json").readText()).jsonObject
            val tel = o["telemetry"]!!.jsonArray.map { val x = it.jsonObject; TelemetrySample(x["t"]!!.jsonPrimitive.double, x["speedKmh"]!!.jsonPrimitive.double, x["linearG"]!!.jsonPrimitive.double) }
            assertEquals(s.key, tel, DemoFixtures.telemetryFor(s))
            val a = o["analysisResponse"]!!.jsonObject
            val obs = DemoFixtures.demoObservation(s)
            assertEquals(a["scene"]!!.jsonPrimitive.content, obs.scene)
            assertEquals(a["visibility"]!!.jsonPrimitive.content, obs.visibility)
            assertEquals(a["smoke"]!!.jsonPrimitive.content, obs.smoke)
            assertEquals(a["fire"]!!.jsonPrimitive.content, obs.fire)
            assertEquals(a["evidence_frames"]!!.jsonArray.map { it.jsonPrimitive.int }, obs.evidenceFrames)
            assertEquals(a["observations"]!!.jsonArray.map { it.jsonPrimitive.content }, obs.observations)
            val d = RuleBasedIncidentDetector()
            assertEquals(o["expectedIncident"]!!.jsonPrimitive.boolean, DemoFixtures.telemetryFor(s).any { d.push(it) != null })
        }
    }

    // ── 음성 11개: 문구·파일·체크섬 ──
    @Test fun audioManifestMatchesGuidesAndFiles() {
        val clips = AudioManifest.parse(File(assets, "audio/manifest.json").readText())
        assertEquals(11, clips.size)
        assertEquals(Guides.ALL.keys, clips.map { it.key }.toSet())
        for (c in clips) {
            assertEquals("문구 불일치: ${c.key}", Guides[c.key].text, c.text)
            val f = File(assets, "audio/${c.file}")
            assertTrue("파일 없음: ${c.file}", f.isFile)
            val sha = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals("체크섬 불일치: ${c.file}", c.sha256, sha)
            assertEquals("하윤 Hayoon", c.voice)
        }
        assertEquals(11, AudioManifest.playable(clips).size)
    }

    @Test fun changedGuideTextIsNotLinkedToOldRecording() {
        val clips = AudioManifest.parse(File(assets, "audio/manifest.json").readText())
        val tampered = clips.map { if (it.key == "highway") it.copy(text = it.text + " 추가 문구") else it }
        assertFalse(AudioManifest.playable(tampered).containsKey("highway"))
        assertEquals(10, AudioManifest.playable(tampered).size)
    }

    // ── 센서 변환·입력 검사 ──
    @Test fun sensorUnitConversion() {
        assertEquals(1.0, SensorMath.metersPerSecondSquaredToG(9.80665), 1e-12)
        assertEquals(2.5, SensorMath.linearG(0f, 0f, (2.5 * 9.80665).toFloat()), 1e-5)
        assertEquals(SensorMath.linearG(3f, 4f, 0f), SensorMath.linearG(0f, 4f, 3f), 1e-12) // 방향이 바뀌어도 크기는 같음
        assertEquals(36.0, SensorMath.metersPerSecondToKmh(10.0), 1e-12)
        assertTrue("m/s² 를 그대로 g 로 쓰지 않음", SensorMath.linearG(0f, 0f, 9.80665f) < 1.01)
    }

    @Test fun gravityFilterRemovesStaticGravity() {
        val f = GravityFilter()
        var g = 0.0
        repeat(200) { g = f.linearG(0f, 0f, 9.80665f) }
        assertTrue(f.isSettled); assertTrue("정지 상태 선형 가속도 ≈ 0g", g < 0.01)
    }

    @Test fun telemetryGateRejectsMissingStaleAndUnreliableInput() {
        val gate = TelemetryGate()
        assertTrue((gate.evaluate(1000, 0.1, null, "p") as GateResult.Invalid).reason.contains("미수신"))
        assertTrue(gate.evaluate(5000, 0.1, SpeedReading(2000, 50.0, 1.0, "gps"), "p") is GateResult.Invalid) // 3초 지난 속도
        assertTrue(gate.evaluate(5000, 0.1, SpeedReading(6000, 50.0, 1.0, "gps"), "p") is GateResult.Invalid) // 미래 시각
        assertTrue(gate.evaluate(5000, 0.1, SpeedReading(4500, 50.0, 40.0, "gps"), "p") is GateResult.Invalid) // 오차 큼
        assertTrue(gate.evaluate(5000, Double.NaN, SpeedReading(4500, 50.0, 1.0, "gps"), "p") is GateResult.Invalid)
        assertTrue(gate.evaluate(5000, 31.0, SpeedReading(4500, 50.0, 1.0, "gps"), "p") is GateResult.Invalid)
        assertTrue(gate.evaluate(5000, 0.1, SpeedReading(4500, 300.0, 1.0, "gps"), "p") is GateResult.Invalid)
        val ok = gate.evaluate(5000, 0.1, SpeedReading(4500, 50.0, 1.0, "gps"), "phone_sensor_unverified")
        assertTrue(ok is GateResult.Valid)
        assertEquals("phone_sensor_unverified", (ok as GateResult.Valid).sample.source)
        assertTrue("시간 역전 거부", gate.evaluate(5000, 0.1, SpeedReading(4900, 50.0, 1.0, "gps"), "p") is GateResult.Invalid)
        assertTrue("속도 오차 미제공은 허용", gate.evaluate(5100, 0.1, SpeedReading(5000, 0.0, null, "gps"), "p") is GateResult.Valid)
    }

    @Test fun unreceivedSpeedNeverBecomesZeroStop() {
        val gate = TelemetryGate(); val d = RuleBasedIncidentDetector()
        // 72km/h → 충격 → 이후 속도 미수신: 정차로 판단해 사고 후보를 만들면 안 된다.
        val seq = listOf(
            gate.evaluate(1000, 0.0, SpeedReading(1000, 72.0, 1.0, "gps"), "p"),
            gate.evaluate(1100, 3.0, SpeedReading(1100, 72.0, 1.0, "gps"), "p"),
            gate.evaluate(1600, 0.0, null, "p"),
            gate.evaluate(4000, 0.0, SpeedReading(1100, 72.0, 1.0, "gps"), "p"),
        )
        val events = seq.filterIsInstance<GateResult.Valid>().mapNotNull { d.push(it.sample) }
        assertTrue(events.isEmpty())
    }

    // ── 영상 파일 프레임 범위 ──
    @Test fun fileFrameTimesClampToVideoRange() {
        assertEquals(listOf(5000L, 7000L, 9000L, 10000L, 12000L, 15000L), FrameTimes.around(10000, 60000))
        assertEquals("영상 시작", listOf(0L, 2000L, 5000L), FrameTimes.around(0, 60000))
        assertEquals("영상 끝", listOf(25000L, 27000L, 29000L, 29950L), FrameTimes.around(30000, 30000))
        assertEquals("아주 짧은 영상", listOf(0L, 950L), FrameTimes.around(500, 1000).let { it.distinct() }.let { listOf(it.first(), it.last()) })
        assertTrue(FrameTimes.around(500, 1000).all { it in 0..950 })
    }

    @Test fun requestEpochDropsLateResponses() {
        val e = RequestEpoch()
        val first = e.next(); val second = e.next()
        assertFalse(e.isCurrent(first)); assertTrue(e.isCurrent(second))
        e.invalidate(); assertFalse(e.isCurrent(second))
    }

    @Test fun missingResultIsNull() { assertNull(null) }
}
