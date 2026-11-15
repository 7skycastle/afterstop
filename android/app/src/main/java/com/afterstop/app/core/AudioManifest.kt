package com.afterstop.app.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AudioClip(
    val key: String,
    val file: String,
    val text: String,
    val provider: String,
    val voice: String,
    val sha256: String,
)

/** assets/audio/manifest.json 의 키·문구·파일 연결. 이 매핑을 기준으로만 녹음을 재생한다. */
object AudioManifest {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(text: String): List<AudioClip> = json.decodeFromString(text)

    /** 안내 문구와 녹음 문구가 다른 키는 재생 대상에서 제외한다 (문구를 바꾸면 기존 녹음과 연결하지 않음). */
    fun playable(clips: List<AudioClip>): Map<String, AudioClip> =
        clips.filter { Guides.ALL[it.key]?.text == it.text }.associateBy { it.key }

    /** 보험사 연결 대화 녹음(assets/audio/prompts/manifest.json). 문구가 [Prompts] 와 같은 것만 재생한다. */
    fun playablePrompts(clips: List<AudioClip>): Map<String, AudioClip> =
        clips.filter { Prompts.ALL[it.key]?.text == it.text }.associateBy { it.key }
}
