package com.afterstop.app.core

/** 제품 시연용 상황. 모든 값은 예시(synthetic_fixture)이며 실제 촬영·차량 신호가 아니다. */
enum class DemoScenario(
    val key: String,
    val label: String,
    val place: String,
    val smoke: Boolean,
    val tunnel: Boolean,
    val limited: Boolean,
    val collision: Boolean,
) {
    HIGHWAY("highway", "고속도로 · 추돌 후 정차", "고속도로 · 위치 예시", smoke = false, tunnel = false, limited = false, collision = true),
    TUNNEL("tunnel", "터널 · 연기 의심", "터널 · 위치 예시", smoke = true, tunnel = true, limited = false, collision = true),
    LOWLIGHT("lowlight", "야간 · 어두운 화면", "위치 미확인", smoke = false, tunnel = false, limited = true, collision = true),
    BUMP("bump", "과속방지턱 · 안내 안 함", "일반 도로 · 위치 예시", smoke = false, tunnel = false, limited = false, collision = false);

    /** 시연 결과가 안내 선택에 넘기는 장소 구분 (원본 runProductDemo 와 동일). */
    val guidanceScenario: String
        get() = when (this) {
            HIGHWAY -> "highway"
            TUNNEL -> "tunnel"
            else -> "unknown"
        }

    companion object {
        fun of(key: String): DemoScenario = entries.firstOrNull { it.key == key }
            ?: throw IllegalArgumentException("Unknown scenario")
    }
}

object DemoFixtures {
    /** incident-core.cjs telemetryFor() 이식. 100ms 간격 30개 샘플. */
    fun telemetryFor(s: DemoScenario): List<TelemetrySample> = (0 until 30).map { i ->
        val speed = if (s == DemoScenario.BUMP) {
            if (i < 12) 36.0 else 28.0
        } else {
            if (i < 12) 72.0 else if (i < 16) 72.0 - (i - 11) * 17 else 0.0
        }
        TelemetrySample(
            t = i * 100.0,
            speedKmh = speed,
            linearG = if (i == 12) 3.1 else 0.04,
            source = TelemetrySourceTag.SYNTHETIC_FIXTURE,
        )
    }

    /** incident-core.cjs demoObservation() 이식. 실제 모델 추론 결과가 아닌 예시 응답. */
    fun demoObservation(s: DemoScenario): RawObservation = RawObservation(
        scene = if (s.tunnel) "tunnel" else if (s.limited) "unknown" else "road",
        visibility = if (s.limited) "unusable" else "usable",
        smoke = if (s.limited) "unknown" else if (s.smoke) "possible" else "not_observed",
        fire = "unknown",
        evidenceFrames = if (s.limited) emptyList() else listOf(2, 3, 4),
        observations = if (s.limited) emptyList() else listOf(
            if (s.tunnel) "터널 구조물이 보입니다." else "도로 위에 차량이 정차한 장면입니다.",
            if (s.smoke) "차량 앞쪽에 흰색 흐림이 보입니다." else "선택한 프레임에서는 뚜렷한 연기 단서를 관찰하지 못했습니다.",
        ),
    )
}
