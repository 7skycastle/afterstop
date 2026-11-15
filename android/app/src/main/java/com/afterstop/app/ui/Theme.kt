package com.afterstop.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.afterstop.app.R

val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_bold, FontWeight.SemiBold),
)

/** 숫자 0–9 만 들어 있는 본고딕(Noto Sans CJK KR) 부분 글꼴. 숫자에만 적용한다. */
val NotoNumbers = FontFamily(
    Font(R.font.noto_numbers_regular, FontWeight.Normal),
    Font(R.font.noto_numbers_bold, FontWeight.Bold),
    Font(R.font.noto_numbers_bold, FontWeight.SemiBold),
)

@Immutable
data class Palette(
    val blue: Color, val page: Color, val ink: Color, val sub: Color, val line: Color, val panel: Color,
    val soft: Color, val warn: Color, val warnBg: Color, val red: Color, val redBg: Color, val border: Color, val onBlue: Color,
    /** K_study 스타일 확장 */
    val blueSoft: Color, val accent: Color, val accentSoft: Color, val body: Color, val success: Color, val successBg: Color,
    val pageTop: Color, val pageBottom: Color, val shadow: Color,
)

// K_study(src/index.css) 디자인 토큰을 기준으로 맞췄다. 글자색은 흰 배경 대비 4.5:1 이상.
val DayPalette = Palette(
    blue = Color(0xFF3267FF), page = Color(0xFFF5F8FF), ink = Color(0xFF101828), sub = Color(0xFF5C6B82),
    line = Color(0xFFE6ECF7), panel = Color.White, soft = Color(0xFFF8FAFF), warn = Color(0xFFB45309),
    warnBg = Color(0xFFFFF7ED), red = Color(0xFFD4265A), redBg = Color(0xFFFFF1F4), border = Color(0xFFDCE3F1), onBlue = Color.White,
    blueSoft = Color(0xFFEEF4FF), accent = Color(0xFF7C3AED), accentSoft = Color(0xFFF7F2FF), body = Color(0xFF334155),
    success = Color(0xFF047857), successBg = Color(0xFFECFDF5), pageTop = Color(0xFFF5F8FF), pageBottom = Color(0xFFF6F2FF), shadow = Color(0x1A3267FF),
)
// 야간: 운전 중 눈부심을 줄이도록 어두운 남색 바탕
val NightPalette = Palette(
    blue = Color(0xFF8EAFFF), page = Color(0xFF0E1424), ink = Color(0xFFF2F5FB), sub = Color(0xFFB4BFD0),
    line = Color(0xFF2B3753), panel = Color(0xFF172036), soft = Color(0xFF1E2944), warn = Color(0xFFFFCA86),
    warnBg = Color(0xFF3F3020), red = Color(0xFFFF9BAA), redBg = Color(0xFF42243A), border = Color(0xFF3A4868), onBlue = Color(0xFF0E1626),
    blueSoft = Color(0xFF22325C), accent = Color(0xFFC4A6FF), accentSoft = Color(0xFF2D2450), body = Color(0xFFD5DCEA),
    success = Color(0xFF6EE7B7), successBg = Color(0xFF173A33), pageTop = Color(0xFF0E1424), pageBottom = Color(0xFF151231), shadow = Color(0x33000000),
)

val LocalPalette = staticCompositionLocalOf { DayPalette }

@Composable
fun AfterStopTheme(night: Boolean, content: @Composable () -> Unit) {
    val p = if (night) NightPalette else DayPalette
    val base = TextStyle(fontFamily = Pretendard, color = p.ink)
    val scheme = if (night) darkColorScheme(primary = p.blue, background = p.page, surface = p.panel, onSurface = p.ink, onBackground = p.ink)
    else lightColorScheme(primary = p.blue, background = p.page, surface = p.panel, onSurface = p.ink, onBackground = p.ink)
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography.run {
            copy(
                bodyLarge = bodyLarge.merge(base), bodyMedium = bodyMedium.merge(base), bodySmall = bodySmall.merge(base),
                titleLarge = titleLarge.merge(base), titleMedium = titleMedium.merge(base), labelLarge = labelLarge.merge(base),
                headlineSmall = headlineSmall.merge(base), headlineMedium = headlineMedium.merge(base),
            )
        },
    ) { CompositionLocalProvider(LocalPalette provides p, content = content) }
}

/** 숫자 구간에만 본고딕 숫자 글꼴을 입힌 문자열. */
fun withNumbers(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c in '0'..'9') {
            val start = i
            while (i < text.length && text[i] in '0'..'9') i++
            pushStyle(SpanStyle(fontFamily = NotoNumbers)); append(text.substring(start, i)); pop()
        } else { append(c); i++ }
    }
}

/** 앱 공통 텍스트: 한국어 Pretendard + 숫자 본고딕. */
@Composable
fun T(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 16.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = LocalPalette.current.ink,
    maxLines: Int = Int.MAX_VALUE,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    Text(
        withNumbers(text), modifier = modifier, fontSize = size, fontWeight = weight, color = color, fontFamily = Pretendard,
        maxLines = maxLines, overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        lineHeight = if (lineHeight == TextUnit.Unspecified) (size.value * 1.4f).sp else lineHeight,
    )
}
