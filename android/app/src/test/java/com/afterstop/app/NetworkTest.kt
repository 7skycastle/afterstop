package com.afterstop.app

import com.afterstop.app.core.AnalysisSource
import com.afterstop.app.core.Frame
import com.afterstop.app.core.FrameProvenance
import com.afterstop.app.core.VisionResult
import com.afterstop.app.data.IncidentRecord
import com.afterstop.app.data.RecordStore
import com.afterstop.app.data.UserReports
import com.afterstop.app.net.EventDeduper
import com.afterstop.app.net.IncidentEventStream
import com.afterstop.app.net.ServerClient
import com.afterstop.app.net.ServerConfig
import com.afterstop.app.net.ServerUrlPolicy
import com.afterstop.app.net.SseParser
import com.afterstop.app.net.UrlCheck
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** 앱의 서버 연결 코드를 가짜 HTTP 서버(MockWebServer)로 시험한다. 실제 모델 추론 검증이 아니다. */
class NetworkTest {
    private lateinit var server: MockWebServer
    private val frames = List(3) { Frame(it * 1000.0, it - 1.0, byteArrayOf(-1, -40, -1, 0), FrameProvenance.USER_VIDEO_FILE) }

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    private fun client(token: String = "", http: OkHttpClient = OkHttpClient()) =
        ServerClient(ServerConfig(server.url("/").toString().trimEnd('/'), token, "qwen-vl-test"), http)

    @Test fun analyzeSendsBase64FramesTokenAndRevalidates() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"analysis":{"scene":"tunnel","visibility":"usable","smoke":"possible","fire":"unknown","evidence_frames":[1,7],"observations":["흰색 흐림이 보입니다.","좌측으로 대피하세요"],"inferredRisk":true},"source":"local_vision_model","model":"qwen-vl-test","requestId":"r1"}"""))
        val r = client("secret-token-123456").visionClient().analyze(frames, "r1") as VisionResult.Success
        val req = server.takeRequest()
        assertEquals("/api/analyze", req.path)
        assertEquals("Bearer secret-token-123456", req.getHeader("Authorization"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"images\":[\"/9j/AA==\""))
        assertTrue(body.contains("\"requestId\":\"r1\""))
        assertEquals(AnalysisSource.LOCAL_VISION_MODEL, r.source)
        assertEquals("앱에서 범위 밖 프레임 번호 제거", listOf(1), r.observation.evidenceFrames)
        assertEquals("지시문 제거", listOf("흰색 흐림이 보입니다."), r.observation.observations)
        assertTrue(r.observation.inferredRisk)
    }

    @Test fun serverErrorsBecomeFailuresWithoutFallback() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(504).setBody("""{"error":"모델 응답 시간이 초과되었습니다.","code":"model_timeout","source":"error_no_fallback_result"}"""))
        server.enqueue(MockResponse().setResponseCode(502).setBody("""{"error":"모델이 올바른 JSON을 반환하지 않았습니다.","code":"model_invalid_json"}"""))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"인증 필요","code":"unauthorized"}"""))
        server.enqueue(MockResponse().setBody("""{"analysis":{"scene":"road"},"source":"fixture_response_not_model_inference","requestId":"x"}"""))
        server.enqueue(MockResponse().setBody("not json"))
        server.enqueue(MockResponse().setBody("""{"analysis":{"scene":"road"},"source":"local_vision_model","requestId":"OTHER"}"""))
        val c = client().visionClient()
        assertEquals("model_timeout", (c.analyze(frames, "x") as VisionResult.Failure).code)
        assertEquals("model_invalid_json", (c.analyze(frames, "x") as VisionResult.Failure).code)
        assertEquals("unauthorized", (c.analyze(frames, "x") as VisionResult.Failure).code)
        assertEquals("예시 응답은 실제 결과로 받지 않음", "unexpected_source", (c.analyze(frames, "x") as VisionResult.Failure).code)
        assertEquals("invalid_json", (c.analyze(frames, "x") as VisionResult.Failure).code)
        assertEquals("늦게 온 다른 요청 응답 거부", "request_mismatch", (c.analyze(frames, "x") as VisionResult.Failure).code)
    }

    @Test fun clientTimeoutIsReported() = runBlocking {
        server.enqueue(MockResponse().setBody("{}").setHeadersDelay(2, TimeUnit.SECONDS))
        val http = OkHttpClient.Builder().readTimeout(300, TimeUnit.MILLISECONDS).build()
        assertEquals("timeout", (client(http = http).visionClient().analyze(frames, "x") as VisionResult.Failure).code)
    }

    @Test fun unreachableServerIsFailure() = runBlocking {
        val dead = ServerClient(ServerConfig("http://127.0.0.1:1"))
        assertEquals("network", (dead.visionClient().analyze(frames, "x") as VisionResult.Failure).code)
        assertFalse(dead.health(false).reachable)
    }

    @Test fun cancellingCoroutineCancelsHttpCall() = runBlocking {
        server.enqueue(MockResponse().setBody("{}").setHeadersDelay(10, TimeUnit.SECONDS))
        val job = async { client().visionClient().analyze(frames, "x") }
        server.takeRequest(2, TimeUnit.SECONDS)
        val start = System.nanoTime()
        job.cancel()
        try { job.await() } catch (_: kotlinx.coroutines.CancellationException) {}
        assertTrue("즉시 취소", (System.nanoTime() - start) / 1_000_000 < 2000)
    }

    @Test fun healthSeparatesReachabilityModelAndInference() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true,"service":"afterstop-backend","serverReachable":true,"authRequired":true,"authorized":true,"model":{"configured":true,"name":"qwen-vl","ready":"missing"},"inferenceVerified":false}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true,"service":"something-else"}"""))
        val h = client("t").health(true)
        assertTrue(h.reachable); assertTrue(h.modelConfigured); assertEquals("missing", h.modelReady); assertEquals("qwen-vl", h.modelName)
        assertEquals("/api/health?checkModel=1", server.takeRequest().path)
        assertFalse("다른 서비스는 연결로 보지 않음", client().health(false).reachable)
    }

    @Test fun sseParserHandlesIdsEventsRetryAndComments() {
        val p = SseParser()
        val lines = listOf("retry: 3000", "event: ready", "data: {}", "", ": keepalive", "", "id: 7", "event: incident", "data: {\"a\":1}", "")
        val msgs = lines.mapNotNull { p.feed(it) }
        assertEquals(2, msgs.size)
        assertEquals("ready", msgs[0].event)
        assertEquals("7", msgs[1].id); assertEquals("incident", msgs[1].event); assertEquals("{\"a\":1}", msgs[1].data)
        assertEquals(3000L, p.retryMs)
    }

    @Test fun deduperSuppressesRepeats() {
        val d = EventDeduper(3)
        assertTrue(d.firstTime("1")); assertFalse(d.firstTime("1")); assertTrue(d.firstTime("2"))
    }

    @Test fun eventStreamReconnectsWithLastEventIdAndDropsDuplicates() = runBlocking {
        val ev = { id: Int -> "id: $id\nevent: incident\ndata: {\"type\":\"incident_candidate\",\"t\":${id * 100},\"impactG\":3.1,\"speedDropKmh\":72,\"inputSource\":\"synthetic_fixture\",\"eventId\":$id}\n\n" }
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("retry: 1000\n\nevent: ready\ndata: {}\n\n" + ev(1) + ev(2)))
        // 재연결 시 서버가 이미 보낸 2번을 다시 보내도 한 번만 처리해야 한다.
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(ev(2) + ev(3)))
        val got = withTimeout(10_000) { IncidentEventStream(client("tok")) {}.events().take(3).toList() }
        assertEquals(listOf(1L, 2L, 3L), got.map { it.eventId })
        assertTrue(got.all { !it.confirmedCrash })
        server.takeRequest()
        val second = server.takeRequest()
        assertEquals("2", second.getHeader("Last-Event-ID"))
        assertEquals("Bearer tok", second.getHeader("Authorization"))
    }

    @Test fun urlPolicyDistinguishesEmulatorPhoneAndRelease() {
        assertTrue(ServerUrlPolicy.check("http://10.0.2.2:8787", true, "") is UrlCheck.Ok)
        val local = ServerUrlPolicy.check("http://localhost:8787", true, "") as UrlCheck.Ok
        assertTrue("앱 안의 localhost 경고", local.warning!!.contains("이 기기 자신"))
        assertTrue("지정하지 않은 LAN 주소 HTTP 거부", ServerUrlPolicy.check("http://192.168.0.10:8787", true, "") is UrlCheck.Rejected)
        assertTrue(ServerUrlPolicy.check("http://192.168.0.10:8787", true, "192.168.0.10") is UrlCheck.Ok)
        assertTrue("release 는 HTTP 거부", ServerUrlPolicy.check("http://10.0.2.2:8787", false, "") is UrlCheck.Rejected)
        assertTrue(ServerUrlPolicy.check("https://analysis.example.org", false, "") is UrlCheck.Ok)
        assertTrue(ServerUrlPolicy.check("http://user:pw@10.0.2.2:8787", true, "") is UrlCheck.Rejected)
        assertTrue(ServerUrlPolicy.check("http://10.0.2.2:8787/api/chat", true, "") is UrlCheck.Rejected)
        assertTrue(ServerUrlPolicy.check("ftp://10.0.2.2", true, "") is UrlCheck.Rejected)
    }

    @Test fun recordStoreSavesListsDeletesWithoutImages() {
        val dir = Files.createTempDirectory("rec").toFile()
        val store = RecordStore(dir)
        val r = IncidentRecord("r1", "2026-11-15T00:00:00Z", "live_input", null, "user_video_file", "external_telemetry", "local_vision_model",
            userReports = UserReports(smoke = false, immobile = true), guidanceKeys = listOf("immobile", "help", "factsUnknown"),
            guidanceText = "이동이 어렵다는 사실을 알리고 구조 요청을 우선하세요.", unknowns = listOf("부상 정도"))
        store.save(r)
        // 앱 재실행 = 새 저장소 객체: 목록만 읽고 아무것도 실행하지 않는다.
        val reopened = RecordStore(dir)
        assertEquals(listOf(r), reopened.list())
        val exported = reopened.export(r)
        assertTrue(exported.contains("\"emergencyCall\": \"simulation_only\""))
        assertFalse("영상 데이터 없음", exported.contains("jpeg") || exported.contains("base64"))
        assertTrue(reopened.delete("r1")); assertTrue(reopened.list().isEmpty())
        try { reopened.delete("../evil"); throw AssertionError() } catch (_: IllegalArgumentException) {}
    }

    @Suppress("unused") private suspend fun pause() = delay(1)
}
