package com.afterstop.app

import com.afterstop.app.core.AnalysisKind
import com.afterstop.app.core.DefaultGuidancePolicy
import com.afterstop.app.core.GuidanceState
import com.afterstop.app.core.InvalidTelemetryException
import com.afterstop.app.core.RuleBasedIncidentDetector
import com.afterstop.app.core.TelemetrySample
import com.afterstop.app.core.VisionSanitizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 원본 JavaScript(reference/web-prototype/shared)를 Node 로 실행해 만든 기대값과 Kotlin 이식본을 대조한다.
 * 기대값 생성: node android/tools/gen-parity.cjs
 */
class ParityTest {
    private fun load(name: String) = Json.parseToJsonElement(
        javaClass.classLoader!!.getResource("parity/$name")!!.readText()
    ).jsonArray

    @Test
    fun detectorMatchesJavaScriptOnAllStreams() {
        val cases = load("detector.json")
        var events = 0; var errors = 0
        cases.forEachIndexed { ci, c ->
            val d = RuleBasedIncidentDetector()
            val samples = c.jsonObject["samples"]!!.jsonArray
            val results = c.jsonObject["results"]!!.jsonArray
            samples.forEachIndexed { i, s ->
                val o = s.jsonObject
                val sample = TelemetrySample(o["t"]!!.jsonPrimitive.double, o["speedKmh"]!!.jsonPrimitive.double, o["linearG"]!!.jsonPrimitive.double)
                val expected = results[i].jsonObject
                if (expected["error"] != null) {
                    try { d.push(sample); throw AssertionError("case $ci sample $i: JS 는 거부했지만 Kotlin 은 받았습니다") } catch (_: InvalidTelemetryException) { errors++ }
                } else {
                    val got = d.push(sample)
                    val e = expected["event"]
                    if (e == null || e is JsonNull) assertEquals("case $ci sample $i", null, got)
                    else {
                        events++
                        val eo = e.jsonObject
                        assertTrue("case $ci sample $i: 이벤트가 없습니다", got != null)
                        assertEquals(eo["t"]!!.jsonPrimitive.double, got!!.t, 1e-9)
                        assertEquals(eo["impactG"]!!.jsonPrimitive.double, got.impactG, 1e-9)
                        assertEquals(eo["speedDropKmh"]!!.jsonPrimitive.double, got.speedDropKmh, 1e-9)
                        assertEquals(false, got.confirmedCrash)
                    }
                }
            }
        }
        assertTrue("대조 이벤트가 충분해야 합니다", events > 50)
        assertTrue(errors >= 6)
    }

    @Test
    fun sanitizeMatchesJavaScript() {
        load("vision.json").forEachIndexed { i, c ->
            val o = c.jsonObject
            val got = VisionSanitizer.sanitize(o["raw"], o["n"]!!.jsonPrimitive.int)
            val exp = o["out"]!!.jsonObject
            assertEquals("case $i scene", exp["scene"]!!.jsonPrimitive.content, got.scene)
            assertEquals("case $i visibility", exp["visibility"]!!.jsonPrimitive.content, got.visibility)
            assertEquals("case $i smoke", exp["smoke"]!!.jsonPrimitive.content, got.smoke)
            assertEquals("case $i fire", exp["fire"]!!.jsonPrimitive.content, got.fire)
            assertEquals("case $i frames", exp["evidence_frames"]!!.jsonArray.map { it.jsonPrimitive.int }, got.evidenceFrames)
            assertEquals("case $i observations", exp["observations"]!!.jsonArray.map { it.jsonPrimitive.content }, got.observations)
            assertEquals("case $i risk", exp["inferredRisk"]!!.jsonPrimitive.boolean, got.inferredRisk)
        }
    }

    @Test
    fun guidancePolicyMatchesJavaScriptForAllCombinations() {
        val cases = load("guidance.json")
        assertEquals(1152, cases.size)
        cases.forEachIndexed { i, c ->
            val st = c.jsonObject["state"]!!.jsonObject
            fun b(k: String) = st[k]!!.jsonPrimitive.boolean
            val scene = (st["scene"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val state = GuidanceState(
                scenario = st["scenario"]!!.jsonPrimitive.content,
                lost = b("lost"), analysisReady = b("analysisReady"), observedSmoke = b("observedSmoke"),
                smoke = b("smoke"), immobile = b("immobile"),
                analysisKind = when (st["analysisKind"]!!.jsonPrimitive.content) { "ai" -> AnalysisKind.AI; "demo" -> AnalysisKind.DEMO; else -> AnalysisKind.NONE },
                visionScene = scene,
            )
            val expected = c.jsonObject["keys"]!!.jsonArray.map { it.jsonPrimitive.content }
            assertEquals("case $i $state", expected, DefaultGuidancePolicy.resolve(state))
        }
    }

    @Suppress("unused") private fun unused(a: JsonArray, o: JsonObject) = a to o
}
