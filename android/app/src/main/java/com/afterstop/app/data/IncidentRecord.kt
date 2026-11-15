package com.afterstop.app.data

import com.afterstop.app.core.FrameMeta
import com.afterstop.app.core.IncidentEvent
import com.afterstop.app.core.VisionObservation
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class UserReports(val smoke: Boolean, val immobile: Boolean)

/** 사건 기록. 영상 원본과 프레임 이미지는 포함하지 않는다. */
@Serializable
data class IncidentRecord(
    val id: String,
    val createdAt: String,
    /** synthetic_product_demo | live_input */
    val mode: String,
    val scenario: String? = null,
    /** 영상 입력 출처 (FrameProvenance.code) */
    val inputSource: String,
    /** 신호 출처 */
    val telemetrySource: String? = null,
    /** 분석 출처: fixture_response_not_model_inference | local_vision_model | none */
    val analysisSource: String,
    val analysisError: String? = null,
    val model: String? = null,
    val incident: IncidentEvent? = null,
    val frames: List<FrameMeta> = emptyList(),
    val observation: VisionObservation? = null,
    val userReports: UserReports,
    val guidanceKeys: List<String>,
    val guidanceText: String,
    val unknowns: List<String>,
    val emergencyCall: String = "simulation_only",
    /** 보험사 연결: none | demo_simulated | declined | dialer_opened | call_started | failed */
    val insuranceCall: String = "none",
)

/** 앱 개인 저장소의 JSON 기록. 앱을 다시 켜도 과거 사건을 자동 실행하지 않는다(목록만 표시). */
class RecordStore(private val dir: File) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    init { dir.mkdirs() }

    private fun fileOf(id: String): File {
        require(Regex("^[A-Za-z0-9_-]{1,64}$").matches(id)) { "invalid id" }
        return File(dir, "$id.json")
    }

    fun save(r: IncidentRecord) { fileOf(r.id).writeText(json.encodeToString(r)) }

    fun list(): List<IncidentRecord> = (dir.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray())
        .mapNotNull { f -> runCatching { json.decodeFromString<IncidentRecord>(f.readText()) }.getOrNull() }
        .sortedByDescending { it.createdAt }

    fun delete(id: String): Boolean = fileOf(id).delete()

    fun export(r: IncidentRecord): String = json.encodeToString(r)
}
