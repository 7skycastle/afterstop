package com.afterstop.app.net

import java.net.URI

/** 사용자가 설정한 분석 서버 정보. 토큰은 앱 개인 저장소에만 두며 APK 에 고정하지 않는다. */
data class ServerConfig(
    val baseUrl: String,
    val token: String = "",
    val model: String = "",
)

sealed interface UrlCheck {
    data class Ok(val normalized: String, val warning: String?) : UrlCheck
    data class Rejected(val reason: String) : UrlCheck
}

/**
 * 서버 주소 정책.
 * - HTTPS 는 허용.
 * - HTTP 는 debug 빌드에서 에뮬레이터의 호스트 PC 별칭(10.0.2.2), 기기 loopback, 빌드 시 지정한 개발 PC 주소만 허용.
 *   (실제 차단은 network_security_config 가 하며, 여기서는 사용자에게 이유를 먼저 알려준다.)
 * - 앱 안의 localhost/127.0.0.1 은 PC 가 아니라 휴대폰 자신이다.
 */
object ServerUrlPolicy {
    fun check(raw: String, debug: Boolean, devHost: String): UrlCheck {
        val text = raw.trim().trimEnd('/')
        if (text.isEmpty()) return UrlCheck.Rejected("서버 주소를 입력하세요.")
        val uri = try { URI(text) } catch (e: Exception) { return UrlCheck.Rejected("주소 형식이 올바르지 않습니다.") }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase() ?: return UrlCheck.Rejected("주소에 호스트가 없습니다.")
        if (uri.userInfo != null) return UrlCheck.Rejected("주소에 사용자 이름·비밀번호를 넣지 마세요.")
        if (!uri.path.isNullOrEmpty() && uri.path != "/") return UrlCheck.Rejected("경로 없이 서버 주소만 입력하세요. 예: http://10.0.2.2:8787")
        if (uri.query != null || uri.fragment != null) return UrlCheck.Rejected("주소에 ? 또는 # 을 넣지 마세요.")
        val loopback = host == "localhost" || host == "127.0.0.1"
        val warning = if (loopback) "앱 안의 localhost 는 PC 가 아니라 이 기기 자신입니다. adb reverse 를 쓰지 않았다면 에뮬레이터는 10.0.2.2, 실제 휴대폰은 PC 의 LAN IP 를 입력하세요." else null
        return when (scheme) {
            "https" -> UrlCheck.Ok(text, warning)
            "http" -> {
                val allowed = setOf("10.0.2.2", "127.0.0.1", "localhost") + listOfNotNull(devHost.lowercase().takeIf { it.isNotBlank() })
                when {
                    !debug -> UrlCheck.Rejected("이 빌드는 HTTPS 서버만 사용할 수 있습니다.")
                    host !in allowed -> UrlCheck.Rejected(
                        "debug 빌드의 HTTP 허용 주소가 아닙니다 ($host). 허용: ${allowed.joinToString()}. 실제 휴대폰은 -PafterstopDevHost=<PC IP> 로 다시 빌드하세요."
                    )
                    else -> UrlCheck.Ok(text, warning)
                }
            }
            else -> UrlCheck.Rejected("http 또는 https 주소만 사용할 수 있습니다.")
        }
    }
}
