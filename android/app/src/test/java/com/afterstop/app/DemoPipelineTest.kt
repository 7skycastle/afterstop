package com.afterstop.app

import com.afterstop.app.core.AnalysisSource
import com.afterstop.app.core.DefaultGuidancePolicy
import com.afterstop.app.core.DemoFixtures
import com.afterstop.app.core.DemoScenario
import com.afterstop.app.core.FixtureVisionClient
import com.afterstop.app.core.Frame
import com.afterstop.app.core.FrameProvenance
import com.afterstop.app.core.FrameRingBuffer
import com.afterstop.app.core.GuidanceReducer
import com.afterstop.app.core.GuidanceState
import com.afterstop.app.core.IncidentPipeline
import com.afterstop.app.core.PipelineEvent
import com.afterstop.app.core.PipelineOutcome
import com.afterstop.app.core.RuleBasedIncidentDetector
import com.afterstop.app.core.VisionResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 네트워크·카메라 없이 네 가지 시연 흐름이 끝까지 진행되는지 (가상 시간). */
class DemoPipelineTest {
    private fun fakeFrames(eventT: Double): List<Frame> {
        val b = FrameRingBuffer<Frame>(20) { it.t }
        for (i in 0 until 6) { val t = eventT - 5000 + i * 1600; b.push(Frame(t, (t - eventT) / 1000, byteArrayOf(1), FrameProvenance.SYNTHETIC_DEMO)) }
        return b.around(eventT)
    }

    private suspend fun run(s: DemoScenario, base: GuidanceState = GuidanceState()): Triple<PipelineOutcome, List<PipelineEvent>, GuidanceState> {
        val events = mutableListOf<PipelineEvent>()
        val outcome = IncidentPipeline(RuleBasedIncidentDetector()).run(
            DemoFixtures.telemetryFor(s), { fakeFrames(it.t) }, FixtureVisionClient(s), "demo", { events += it },
        )
        val g = (outcome as? PipelineOutcome.Completed)?.let { GuidanceReducer.afterAnalysis(base, it.result, s) } ?: base
        return Triple(outcome, events, g)
    }

    @Test fun highwayRunsDetectFramesAnalysisGuidanceInOrder() = runTest {
        val (outcome, events, g) = run(DemoScenario.HIGHWAY)
        assertTrue(outcome is PipelineOutcome.Completed)
        val order = events.filter { it !is PipelineEvent.Driving }.map { it::class.simpleName }
        assertEquals(listOf("Detected", "Buffered", "Analyzing", "Analyzed"), order)
        val c = outcome as PipelineOutcome.Completed
        assertTrue("사건 전후 프레임 확보", c.frames.size in 1..6)
        assertEquals(1500.0, c.event.t, 0.0)
        val r = c.result as VisionResult.Success
        assertEquals(AnalysisSource.FIXTURE, r.source)
        assertEquals(listOf("highway", "help", "facts"), DefaultGuidancePolicy.resolve(g))
    }

    @Test fun tunnelSmokeCueChangesGuidanceAndImmobileTakesPriority() = runTest {
        val (_, _, g) = run(DemoScenario.TUNNEL)
        assertEquals("observed", DefaultGuidancePolicy.resolve(g)[0])
        assertEquals("combinedObserved", DefaultGuidancePolicy.resolve(g.copy(immobile = true))[0])
        assertEquals("combined", DefaultGuidancePolicy.resolve(g.copy(immobile = true, smoke = true))[0])
        assertEquals("immobile", DefaultGuidancePolicy.resolve(GuidanceState(scenario = "highway", immobile = true))[0])
    }

    @Test fun lowlightWithholdsJudgementAndKeepsCommonGuidance() = runTest {
        val (outcome, _, g) = run(DemoScenario.LOWLIGHT)
        val r = (outcome as PipelineOutcome.Completed).result as VisionResult.Success
        assertFalse("관찰 불가를 위험 없음으로 판단하지 않음", r.observation.inferredRisk)
        assertEquals("unknown", r.observation.smoke)
        assertTrue(r.observation.evidenceFrames.isEmpty())
        assertEquals(listOf("unknown", "help", "factsUnknown"), DefaultGuidancePolicy.resolve(g))
    }

    @Test fun bumpDoesNotSwitchToIncidentGuidance() = runTest {
        val (outcome, events, _) = run(DemoScenario.BUMP)
        assertTrue(outcome is PipelineOutcome.NoIncident)
        assertTrue(events.none { it is PipelineEvent.Detected || it is PipelineEvent.Analyzed })
    }

    @Test fun analysisFailureKeepsCommonGuidanceWithoutFixtureFallback() {
        val g = GuidanceReducer.afterAnalysis(GuidanceState(scenario = "highway"), VisionResult.Failure("timeout", "시간 초과"), null)
        assertFalse(g.analysisReady)
        assertEquals(listOf("unknown", "help", "factsUnknown"), DefaultGuidancePolicy.resolve(g))
    }
}
