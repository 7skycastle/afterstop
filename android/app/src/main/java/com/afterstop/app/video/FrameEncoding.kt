package com.afterstop.app.video

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object FrameEncoding {
    const val MAX_EDGE = 640
    const val JPEG_QUALITY = 65

    /** 긴 변을 640px 이하로 줄이고 회전을 적용한 뒤 JPEG 로 인코딩한다. */
    fun toJpeg(src: Bitmap, rotationDegrees: Int = 0): ByteArray {
        val scale = minOf(1f, MAX_EDGE.toFloat() / max(src.width, src.height))
        val m = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat())
        }
        val bmp = if (scale < 1f || rotationDegrees != 0) Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true) else src
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (bmp !== src) bmp.recycle()
        return out.toByteArray()
    }

    fun decode(jpeg: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)

    fun scaledSize(w: Int, h: Int): Pair<Int, Int> {
        val s = minOf(1.0, MAX_EDGE.toDouble() / max(w, h))
        return (w * s).roundToInt() to (h * s).roundToInt()
    }
}
