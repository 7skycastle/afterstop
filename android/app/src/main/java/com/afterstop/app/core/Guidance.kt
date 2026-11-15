package com.afterstop.app.core

/**
 * 안내 문구. [text] 는 assets/audio/manifest.json 의 녹음 문구와 글자 하나까지 같아야 한다
 * (GuidanceManifestTest 로 검사). 문구를 바꾸면 기존 녹음과 연결하지 말 것.
 */
data class Guide(val key: String, val label: String, val title: String, val text: String, val note: String)

object Guides {
    val ALL: Map<String, Guide> = listOf(
        Guide("highway", "고속도로", "주변 위험을 먼저 살피세요.", "차가 완전히 멈췄고 스스로 안전하게 이동할 수 있다면 차도 밖 안전한 곳으로 대피하세요.", "움직여도 안전한지는 직접 살펴 주세요."),
        Guide("tunnel", "터널", "현장 비상 표지를 확인하세요.", "안전하게 이동할 수 있는지 살피고 대피 방향은 현장 표지로 확인하세요.", "출구 방향은 앱이 정하지 않아요. 터널 안 표지를 따라 주세요."),
        Guide("unknown", "위치 모름", "주변 위험부터 확인하세요.", "차량이 멈췄는지와 주변에서 다가오는 위험이 있는지 살피세요.", "위치를 몰라서 도로 종류나 대피 방향은 말씀드리지 않아요."),
        Guide("observed", "영상에서 본 것", "연기처럼 보이는 징후가 있어요.", "실제로 연기나 불꽃이 보이고 안전하게 이동할 수 있다면 차량에서 떨어진 안전한 곳으로 대피하세요.", "AI가 영상에서 본 것이에요. 불이 났다고 확정한 건 아니에요."),
        Guide("smoke", "알려 주신 내용", "연기·불꽃을 알려주셨어요.", "스스로 안전하게 이동할 수 있다면 차량에서 떨어진 안전한 곳으로 대피하세요.", "알려 주신 내용으로 안내해요."),
        Guide("immobile", "알려 주신 내용", "이동이 어려운 상황이에요.", "이동이 어렵다는 사실을 알리고 구조 요청을 우선하세요.", "무리해서 내리라고 안내하지 않아요. 구조 요청이 먼저예요."),
        Guide("combinedObserved", "위험이 겹쳤어요", "이동이 어려운 위험 상황이에요.", "움직이기 어렵다는 사실과 실제로 보이는 위험을 구조 요청 시 알려주세요.", "연기는 AI가 영상에서 봤고, 움직이기 어렵다는 건 알려 주셨어요."),
        Guide("combined", "알려 주신 내용", "두 가지 위험을 함께 알려주세요.", "연기·불꽃이 보이고 움직이기 어렵다는 두 가지 상황을 구조 요청 시 함께 알려주세요.", "어떻게 움직여야 안전한지는 앱이 판단하지 못해요."),
        Guide("help", "도움 요청", "도움 요청을 우선하세요.", "필요하다면 차량 SOS나 긴급전화로 도움을 요청하세요.", "앱이 대신 신고하지 않아요. 119 버튼을 누르면 전화 화면이 열려요."),
        Guide("facts", "전화할 때", "확인한 사실만 전달하세요.", "알고 있는 도로명과 진행 방향, 눈앞의 상황을 알려주세요.", "다친 정도와 정확한 차로는 앱이 알 수 없어요."),
        Guide("factsUnknown", "전화할 때", "위치가 미확인임을 알려주세요.", "현재 위치를 확인하지 못했다는 점과 눈앞의 상황을 알려주세요.", "모르는 건 짐작해서 말하지 않아도 돼요."),
    ).associateBy { it.key }

    operator fun get(key: String): Guide = ALL.getValue(key)
}

enum class AnalysisKind { NONE, DEMO, AI }

/** 안내 선택에 쓰이는 입력. 원본 freshState() 중 안내 분기에 영향을 주는 필드만 옮겼다. */
data class GuidanceState(
    /** "highway" | "tunnel" | "unknown" */
    val scenario: String = "unknown",
    val lost: Boolean = false,
    val analysisReady: Boolean = false,
    val observedSmoke: Boolean = false,
    /** 사용자가 알린 연기·불꽃 */
    val smoke: Boolean = false,
    /** 사용자가 알린 이동 어려움 */
    val immobile: Boolean = false,
    val analysisKind: AnalysisKind = AnalysisKind.NONE,
    val visionScene: String? = null,
)

/** 교체 가능한 안내 선택 정책. */
interface GuidancePolicy {
    /** 3단계 안내 키 [상황 안내, 도움 요청, 정보 전달] */
    fun resolve(state: GuidanceState): List<String>
}

/** reference 의 resolveKeys() 를 그대로 이식. 이동 어려움 알림이 하차·이동 안내보다 우선한다. */
object DefaultGuidancePolicy : GuidancePolicy {
    override fun resolve(state: GuidanceState): List<String> {
        val s = state
        val unknown = s.lost || s.scenario == "unknown"
        val observed = s.analysisReady && s.observedSmoke
        val key = when {
            s.immobile && (s.smoke || observed) -> if (s.smoke) "combined" else "combinedObserved"
            s.immobile -> "immobile"
            s.smoke -> "smoke"
            observed -> "observed"
            s.analysisKind == AnalysisKind.AI && s.visionScene == "tunnel" -> "tunnel"
            unknown -> "unknown"
            else -> s.scenario
        }
        return listOf(key, "help", if (unknown) "factsUnknown" else "facts")
    }
}
