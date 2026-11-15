package com.afterstop.app.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.afterstop.app.core.DemoScenario
import com.afterstop.app.core.Frame
import com.afterstop.app.core.FrameProvenance
import com.afterstop.app.core.FrameRingBuffer
import com.afterstop.app.core.IncidentEvent
import com.afterstop.app.core.SourceStatus
import com.afterstop.app.core.VideoSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 제품 시연용 합성 도로 장면. 원본 drawRoad() 를 Android Canvas 로 옮겼다.
 * 모든 장면에 "DEMO · 실제 촬영 영상 아님" 표시를 그린다.
 */
object DemoSceneRenderer {
    const val W = 960
    const val H = 400

    fun render(s: DemoScenario, frame: Double, event: Boolean, typeface: Typeface? = null): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = W.toFloat(); val h = H.toFloat()
        val night = s.limited
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h,
            if (night) 0xFF101624.toInt() else if (s.tunnel) 0xFF29313E.toInt() else 0xFF9CB8CB.toInt(),
            if (night) 0xFF26354A.toInt() else 0xFFD0DCE2.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        if (s.tunnel) {
            p.color = 0xFF344254.toInt()
            c.drawRect(0f, h * .53f - 10, w, h, p)
            c.drawArc(RectF(0f, h * .53f - h * .7f, w, h * .53f + h * .7f), 180f, 180f, true, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 5f; p.color = 0xFF617184.toInt()
            for (i in 0 until 5) {
                val rx = w * (.48f - i * .065f); val ry = h * (.65f - i * .08f); val cy = h * .55f
                c.drawArc(RectF(w / 2 - rx, cy - ry, w / 2 + rx, cy + ry), 180f, 180f, false, p)
            }
            p.style = Paint.Style.FILL
        } else {
            p.color = if (night) 0xFF1C2B36.toInt() else 0xFF7B9292.toInt()
            c.drawPath(Path().apply {
                moveTo(0f, 160f); lineTo(270f, 124f); lineTo(465f, 176f); lineTo(740f, 132f); lineTo(w, 162f); lineTo(w, h); lineTo(0f, h); close()
            }, p)
        }
        p.color = if (night) 0xFF262E3E.toInt() else 0xFF526274.toInt()
        c.drawPath(Path().apply { moveTo(w * .41f, 164f); lineTo(w * .59f, 164f); lineTo(w * .98f, h); lineTo(w * .02f, h); close() }, p)
        p.style = Paint.Style.STROKE; p.color = 0xFFDAE2EB.toInt(); p.strokeWidth = 3f
        for (side in intArrayOf(-1, 1)) c.drawLine(w / 2 + side * 82, 171f, w / 2 + side * 400, h, p)
        p.strokeWidth = 5f; p.color = 0xFFD9E0E8.toInt()
        for (i in 0 until 4) {
            val y = 190 + ((i * 48 + frame * 8) % 170).toFloat(); val q = (y - 160) / 200
            c.drawLine(w / 2, y, w / 2, y + 10 + q * 14, p)
        }
        p.style = Paint.Style.FILL
        val carX = 510f + if (event) 12 else 0; val carY = 205f + if (event) 12 else 0
        p.color = 0xFFD9E4EE.toInt(); c.drawRoundRect(RectF(carX, carY, carX + 108, carY + 52), 10f, 10f, p)
        p.color = 0xFF32475E.toInt(); c.drawRect(carX + 18, carY + 7, carX + 89, carY + 23, p)
        p.color = if (event) 0xFFFF645F.toInt() else 0xFFC65A5A.toInt()
        c.drawRect(carX + 7, carY + 36, carX + 27, carY + 43, p); c.drawRect(carX + 80, carY + 36, carX + 100, carY + 43, p)
        if (s.smoke && event) for (i in 0 until 7) {
            p.color = android.graphics.Color.argb(((.12f + i * .025f) * 255).toInt(), 224, 229, 232)
            val cx = 590f + i * 15; val cy = 200f - i * 11; val rx = 32f + i * 4; val ry = 19f + i * 4
            c.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), p)
        }
        if (night) {
            p.color = 0x9907101B.toInt(); c.drawRect(0f, 0f, w, h, p)
            p.color = 0xFFD5DDED.toInt(); for (i in 0 until 12) c.drawRect(38f + i * 76, 80f + (i % 3) * 18, 40f + i * 76, 82f + (i % 3) * 18, p)
        }
        p.color = 0xFF172333.toInt(); c.drawArc(RectF(w / 2 - w * .65f, h + 80 - 110, w / 2 + w * .65f, h + 80 + 110), 180f, 180f, true, p)
        p.color = 0xD9FFFFFF.toInt(); p.textSize = 22f; p.typeface = typeface
        c.drawText("DEMO · 실제 촬영 영상 아님", 24f, h - 22, p)
        return bmp
    }
}

/** 시연용 영상 입력. 실제 입력과 같은 프레임 형식·버퍼 코드를 사용한다. */
class SyntheticDemoVideoSource(private val scenario: DemoScenario, private val typeface: Typeface?) : VideoSource {
    override val id = "synthetic_demo"
    override val provenance = FrameProvenance.SYNTHETIC_DEMO
    override val status: StateFlow<SourceStatus> = MutableStateFlow(SourceStatus(true, "블랙박스 연결 · 시연 (합성 장면)"))

    /** 사건 전후 장면 6개를 생성해 버퍼에 넣고 사건 구간을 고른다 (원본 runProductDemo 와 같은 간격). */
    fun framesFor(event: IncidentEvent): List<Frame> {
        val buffer = FrameRingBuffer<Frame>(20) { it.t }
        for (i in 0 until 6) {
            val bmp = DemoSceneRenderer.render(scenario, i.toDouble(), true, typeface)
            val t = event.t - 5000 + i * 1600
            buffer.push(Frame(t, (t - event.t) / 1000.0, FrameEncoding.toJpeg(bmp), provenance))
            bmp.recycle()
        }
        return buffer.around(event.t)
    }

    override suspend fun captureIncidentFrames(anchorMs: Double?): List<Frame> =
        framesFor(IncidentEvent(t = anchorMs ?: 1500.0, impactG = 0.0, speedDropKmh = 0.0))

    override fun release() {}
}
