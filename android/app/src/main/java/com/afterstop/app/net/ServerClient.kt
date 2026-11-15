package com.afterstop.app.net

import com.afterstop.app.core.AnalysisSource
import com.afterstop.app.core.Frame
import com.afterstop.app.core.IncidentEvent
import com.afterstop.app.core.InvalidVisionResponseException
import com.afterstop.app.core.VisionClient
import com.afterstop.app.core.VisionResult
import com.afterstop.app.core.VisionSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 서버 연결·모델 준비·마지막 분석 결과는 서로 다른 상태로 표시한다. */
data class HealthStatus(
    val reachable: Boolean,
    val message: String,
    val authRequired: Boolean = false,
    val authorized: Boolean = false,
    val modelConfigured: Boolean = false,
    val modelName: String? = null,
    /** not_checked | ready | missing | unreachable */
    val modelReady: String = "not_checked",
)

private val JSON = Json { ignoreUnknownKeys = true }
private val MEDIA_JSON = "application/json; charset=utf-8".toMediaType()

fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(65, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .callTimeout(70, TimeUnit.SECONDS)
    .build()

/** OkHttp Call 을 코루틴 취소와 연결한다. 코루틴이 취소되면 요청도 끊긴다. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) { cont.resume(response) }
    })
}

class ServerClient(
    private val config: ServerConfig,
    private val http: OkHttpClient = defaultHttpClient(),
) {
    private fun request(path: String): Request.Builder = Request.Builder().url(config.baseUrl.trimEnd('/') + path).apply {
        if (config.token.isNotBlank()) header("Authorization", "Bearer ${config.token}")
    }

    suspend fun health(checkModel: Boolean): HealthStatus = withContext(Dispatchers.IO) {
        try {
            http.newBuilder().callTimeout(6, TimeUnit.SECONDS).build()
                .newCall(request("/api/health" + if (checkModel) "?checkModel=1" else "").get().build()).await().use { r ->
                    val body = r.body?.string().orEmpty()
                    if (!r.isSuccessful) return@withContext HealthStatus(false, "서버가 거부했습니다 (${r.code}) ${errorText(body)}")
                    val o = JSON.parseToJsonElement(body).jsonObject
                    if ((o["service"] as? JsonPrimitive)?.content != "afterstop-backend")
                        return@withContext HealthStatus(false, "멈춘 다음 분석 서버가 아닙니다.")
                    val m = o["model"] as? JsonObject
                    HealthStatus(
                        reachable = true,
                        message = "서버 도달 · 추론 성공을 뜻하지 않음",
                        authRequired = (o["authRequired"] as? JsonPrimitive)?.booleanOrNull ?: false,
                        authorized = (o["authorized"] as? JsonPrimitive)?.booleanOrNull ?: false,
                        modelConfigured = (m?.get("configured") as? JsonPrimitive)?.booleanOrNull ?: false,
                        modelName = (m?.get("name") as? JsonPrimitive)?.takeIf { it.isString }?.content,
                        modelReady = (m?.get("ready") as? JsonPrimitive)?.content ?: "not_checked",
                    )
                }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            HealthStatus(false, "서버에 연결하지 못했습니다: ${e.javaClass.simpleName}. 주소·같은 네트워크·방화벽을 확인하세요.")
        }
    }

    private fun errorText(body: String): String = runCatching {
        (JSON.parseToJsonElement(body).jsonObject["error"] as? JsonPrimitive)?.content.orEmpty()
    }.getOrDefault("")

    /** 텔레메트리를 서버 감지기로 보낸다(서버 쪽 감지 경로 시험용). */
    suspend fun postTelemetry(streamId: String, bodyJson: String): Int = withContext(Dispatchers.IO) {
        http.newCall(request("/api/telemetry").post(bodyJson.toRequestBody(MEDIA_JSON)).build()).await().use { it.code }
    }

    fun visionClient(): VisionClient = ServerVisionClient(this)

    internal suspend fun analyzeRaw(frames: List<Frame>, requestId: String): VisionResult = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonArray("images") { frames.forEach { add(JsonPrimitive(Base64.getEncoder().encodeToString(it.jpeg))) } }
            if (config.model.isNotBlank()) put("model", config.model)
            put("requestId", requestId)
        }.toString()
        try {
            http.newCall(request("/api/analyze").post(body.toRequestBody(MEDIA_JSON)).build()).await().use { r ->
                val text = r.body?.string().orEmpty()
                val o = runCatching { JSON.parseToJsonElement(text).jsonObject }.getOrNull()
                if (!r.isSuccessful) {
                    val code = (o?.get("code") as? JsonPrimitive)?.content ?: "http_${r.code}"
                    val msg = (o?.get("error") as? JsonPrimitive)?.content ?: "분석 서버 오류 (${r.code})"
                    return@withContext VisionResult.Failure(code, msg)
                }
                if (o == null) return@withContext VisionResult.Failure("invalid_json", "서버 응답이 JSON 이 아닙니다.")
                val echoed = (o["requestId"] as? JsonPrimitive)?.content
                if (echoed != null && echoed != requestId) return@withContext VisionResult.Failure("request_mismatch", "다른 요청의 응답이 도착해 버렸습니다.")
                if ((o["source"] as? JsonPrimitive)?.content != AnalysisSource.LOCAL_VISION_MODEL.code)
                    return@withContext VisionResult.Failure("unexpected_source", "실제 모델 응답이 아닌 결과는 반영하지 않습니다.")
                // 서버가 이미 검사했어도 앱에서 같은 규칙으로 한 번 더 검사한다.
                val obs = VisionSanitizer.sanitize(o["analysis"], frames.size)
                VisionResult.Success(obs, AnalysisSource.LOCAL_VISION_MODEL, (o["model"] as? JsonPrimitive)?.content)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: InvalidVisionResponseException) {
            VisionResult.Failure("invalid_analysis", e.message ?: "분석 결과 형식 오류")
        } catch (e: SocketTimeoutException) {
            VisionResult.Failure("timeout", "분석 요청 시간이 초과됐습니다.")
        } catch (e: IOException) {
            if (e.message?.contains("timeout", true) == true) VisionResult.Failure("timeout", "분석 요청 시간이 초과됐습니다.")
            else VisionResult.Failure("network", "분석 서버에 연결하지 못했습니다: ${e.javaClass.simpleName}")
        }
    }

    /** SSE 스트림 요청 (토큰은 헤더로 보낸다). */
    internal fun eventsCall(lastEventId: Long?): Call {
        val b = request("/api/events").header("Accept", "text/event-stream")
        if (lastEventId != null) b.header("Last-Event-ID", lastEventId.toString())
        return http.newBuilder().readTimeout(45, TimeUnit.SECONDS).callTimeout(0, TimeUnit.SECONDS).build().newCall(b.get().build())
    }
}

/** 앱 → 분석 서버 → 로컬 Ollama 비전 모델. 실패하면 Failure 를 돌려주며 예시 응답으로 대체하지 않는다. */
class ServerVisionClient(private val client: ServerClient) : VisionClient {
    override val analysisSource = AnalysisSource.LOCAL_VISION_MODEL
    override suspend fun analyze(frames: List<Frame>, requestId: String): VisionResult {
        if (frames.isEmpty() || frames.size > 6) return VisionResult.Failure("invalid_images", "프레임은 1~6장이어야 합니다.")
        return client.analyzeRaw(frames, requestId)
    }
}

/** 서버 SSE 의 incident 이벤트를 앱 이벤트로 바꾼다. */
object IncidentEventParser {
    fun parse(data: String): IncidentEvent? = runCatching {
        val o = JSON.parseToJsonElement(data).jsonObject
        if ((o["type"] as? JsonPrimitive)?.content != "incident_candidate") return null
        fun d(k: String) = (o[k] as? JsonPrimitive)?.content?.toDoubleOrNull()
        IncidentEvent(
            t = d("t") ?: return null,
            impactG = d("impactG") ?: 0.0,
            speedDropKmh = d("speedDropKmh") ?: 0.0,
            inputSource = (o["inputSource"] as? JsonPrimitive)?.content ?: "external_telemetry",
            eventId = (o["eventId"] as? JsonPrimitive)?.content?.toLongOrNull(),
            confirmedCrash = false,
        )
    }.getOrNull()

    @Suppress("unused")
    private fun JsonArray?.orEmptyList() = this ?: JsonArray(emptyList())
}
