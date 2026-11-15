package com.afterstop.app.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** fixtures/vision-schema.json 계약을 통과한 관찰 결과. */
@Serializable
data class VisionObservation(
    val scene: String,
    val visibility: String,
    val smoke: String,
    val fire: String,
    val evidenceFrames: List<Int>,
    val observations: List<String>,
    /** 유효한 근거 프레임과 관찰 가능한 영상이 있을 때만 true. */
    val inferredRisk: Boolean,
)

class InvalidVisionResponseException(message: String) : IllegalArgumentException(message)

/**
 * reference/web-prototype/shared/vision.cjs 의 sanitizeVision 을 그대로 이식했다.
 * 시야 제한·근거 부족·형식 오류는 판단 보류(unknown)로 처리하고, 관찰 문장 속 지시문은 제거한다.
 */
object VisionSanitizer {
    val SCENES = listOf("tunnel", "road", "unknown")
    val VISIBILITY = listOf("usable", "limited", "unusable")
    val CUES = listOf("observed", "possible", "not_observed", "unknown")
    private val INSTRUCTION = Regex("대피하|이동하|하차하|하세요|하십시오|피하세요|좌측으로|우측으로")
    private val json = Json { ignoreUnknownKeys = true }

    fun sanitizeJson(text: String, frameCount: Int): VisionObservation {
        val element = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw InvalidVisionResponseException("영상 분석 결과 형식이 올바르지 않습니다.")
        }
        return sanitize(element, frameCount)
    }

    fun sanitize(raw: JsonElement?, frameCount: Int): VisionObservation {
        // JS의 typeof raw === 'object' 와 같게: 배열은 빈 객체처럼, 그 외 원시값/null 은 거부.
        val obj: JsonObject = when (raw) {
            is JsonObject -> raw
            is JsonArray -> JsonObject(emptyMap())
            else -> null
        } ?: throw InvalidVisionResponseException("영상 분석 결과 형식이 올바르지 않습니다.")
        if (frameCount < 1) throw InvalidVisionResponseException("영상 분석 결과 형식이 올바르지 않습니다.")

        fun one(key: String, allowed: List<String>, fallback: String): String {
            val p = obj[key] as? JsonPrimitive ?: return fallback
            return if (p.isString && p.content in allowed) p.content else fallback
        }

        val scene = one("scene", SCENES, "unknown")
        val visibility = one("visibility", VISIBILITY, "unusable")
        val evidence = (obj["evidence_frames"] as? JsonArray).orEmpty()
            .mapNotNull { el ->
                val p = el as? JsonPrimitive ?: return@mapNotNull null
                if (p.isString) return@mapNotNull null
                val d = p.doubleOrNull ?: return@mapNotNull null
                if (!d.isFinite() || d != Math.floor(d)) return@mapNotNull null
                if (d < 0 || d >= frameCount) return@mapNotNull null
                d.toInt()
            }
            .distinct()
            .take(6)
        val smoke = one("smoke", CUES, "unknown")
        val fire = one("fire", CUES, "unknown")
        val canUse = visibility != "unusable" && evidence.isNotEmpty()
        val observations = if (!canUse) emptyList() else (obj["observations"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            .filter { !INSTRUCTION.containsMatchIn(it) }
            .take(5)
            .map { it.take(160) }
        val risky = listOf("observed", "possible")
        return VisionObservation(
            scene = if (canUse) scene else "unknown",
            visibility = visibility,
            smoke = if (canUse) smoke else "unknown",
            fire = if (canUse) fire else "unknown",
            evidenceFrames = evidence,
            observations = observations,
            inferredRisk = canUse && (smoke in risky || fire in risky),
        )
    }

    /** 관찰 결과 객체를 스키마 형식(JSON)으로 되돌린다. 시연 응답도 이 경로를 거친다. */
    fun toSchemaJson(o: RawObservation): String = buildString {
        val q = { s: String -> Json.encodeToString(kotlinx.serialization.serializer<String>(), s) }
        append("{\"scene\":").append(q(o.scene))
        append(",\"visibility\":").append(q(o.visibility))
        append(",\"smoke\":").append(q(o.smoke))
        append(",\"fire\":").append(q(o.fire))
        append(",\"evidence_frames\":[").append(o.evidenceFrames.joinToString(",")).append("]")
        append(",\"observations\":[").append(o.observations.joinToString(",") { q(it) }).append("]}")
    }
}

/** 검증 전 원시 관찰 결과 (시연 fixture 표현용). */
data class RawObservation(
    val scene: String,
    val visibility: String,
    val smoke: String,
    val fire: String,
    val evidenceFrames: List<Int>,
    val observations: List<String>,
)
