package com.afterstop.app.core

/**
 * 보험사 연결 대화에 쓰는 하윤 음성 문구.
 * 사고 안내 11개(assets/audio/manifest.json)와 섞지 않고 assets/audio/prompts/manifest.json 에 따로 연결한다.
 * [text] 는 녹음 문구와 글자 하나까지 같아야 재생한다.
 */
data class Prompt(val key: String, val text: String)

object Prompts {
    val ALL: Map<String, Prompt> = listOf(
        Prompt("insAsk", "다친 사람이 있으면 119가 먼저입니다. 안전한 곳이라면 보험사에 연결해 드릴까요?"),
        Prompt("insConnect", "보험사에 연결합니다. 화면에 정리한 사고 정보를 읽어 주세요."),
        Prompt("insLater", "알겠습니다. 필요할 때 보험사 연결 버튼을 눌러 주세요."),
        Prompt("insRetry", "잘 듣지 못했어요. 연결하려면 연결해 줘라고 말하거나 버튼을 눌러 주세요."),
    ).associateBy { it.key }

    operator fun get(key: String): Prompt = ALL.getValue(key)
}

enum class VoiceAnswer { YES, NO, UNCLEAR }

/**
 * "보험사에 연결할까요?" 에 대한 말 대답을 해석한다.
 * 잘못 거는 것보다 안 거는 쪽이 안전하므로, 후보 중 하나라도 거절이면 거절로 본다.
 */
object VoiceAnswerParser {
    private val noPhrases = listOf("아니", "아뇨", "안 해", "안해", "하지 마", "하지마", "안 돼", "안돼", "나중", "괜찮", "됐어", "싫어", "취소", "그만", "끊어", "필요 없", "필요없")
    private val yesTokens = setOf("응", "웅", "어", "네", "넵", "넹", "예", "그래", "좋아", "콜", "오케이", "ok", "okay", "yes")
    private val yesPhrases = listOf("연결", "해 줘", "해줘", "해 주세요", "해주세요", "걸어", "전화해", "전화 해", "부탁", "그렇게 해")

    fun parse(candidates: List<String>): VoiceAnswer {
        val texts = candidates.map { normalize(it) }.filter { it.isNotBlank() }
        if (texts.isEmpty()) return VoiceAnswer.UNCLEAR
        if (texts.any { t -> noPhrases.any { t.contains(it) } }) return VoiceAnswer.NO
        if (texts.any { t -> t.split(' ').any { it in yesTokens } || yesPhrases.any { t.contains(it) } }) return VoiceAnswer.YES
        return VoiceAnswer.UNCLEAR
    }

    private fun normalize(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()
}
