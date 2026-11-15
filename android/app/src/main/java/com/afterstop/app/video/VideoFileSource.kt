package com.afterstop.app.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.afterstop.app.core.Frame
import com.afterstop.app.core.FrameProvenance
import com.afterstop.app.core.SourceStatus
import com.afterstop.app.core.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class InvalidVideoException(message: String) : Exception(message)

/** 프레임 시각 선택 (순수 함수, 단위 테스트 대상). 사건 시점 기준 -5,-3,-1,0,+2,+5초, 영상 범위로 자른다. */
object FrameTimes {
    val OFFSETS_MS = longArrayOf(-5000, -3000, -1000, 0, 2000, 5000)

    fun around(anchorMs: Long, durationMs: Long): List<Long> {
        require(durationMs > 0) { "duration" }
        val last = maxOf(0L, durationMs - 50)
        return OFFSETS_MS.map { (anchorMs + it).coerceIn(0L, last) }
            .map { it / 10 * 10 }
            .distinct()
    }
}

/**
 * 사용자가 시스템 파일 선택기로 고른 블랙박스 영상.
 * 실제 촬영 자료일 수 있으므로 기기 안에서만 읽고, 분석 서버에는 사용자가 요청할 때만 프레임을 보낸다.
 */
class VideoFileSource(private val context: Context, val uri: Uri, val displayName: String) : VideoSource {
    override val id = "video_file"
    override val provenance = FrameProvenance.USER_VIDEO_FILE
    private val _status = MutableStateFlow(SourceStatus(false, "영상 정보를 읽는 중"))
    override val status: StateFlow<SourceStatus> = _status

    var durationMs: Long = 0
        private set

    /** 파일을 열어 길이를 확인한다. 잘못된 파일·재생 불가 형식이면 예외. */
    suspend fun open(): Long = withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        try {
            try {
                r.setDataSource(context, uri)
            } catch (e: Exception) {
                throw InvalidVideoException("이 파일을 열 수 없습니다. 영상 파일이 아니거나 지원하지 않는 형식입니다.")
            }
            val hasVideo = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            if (hasVideo != null && hasVideo != "yes") throw InvalidVideoException("영상 트랙이 없는 파일입니다.")
            val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (d == null || d <= 0) throw InvalidVideoException("영상 길이를 읽지 못했습니다. 다른 파일을 선택하세요.")
            durationMs = d
            _status.value = SourceStatus(true, "$displayName · 기기 안에서만 읽음")
            d
        } catch (e: InvalidVideoException) {
            _status.value = SourceStatus(false, e.message ?: "잘못된 파일")
            throw e
        } finally {
            runCatching { r.release() }
        }
    }

    suspend fun frameAt(ms: Long): Bitmap? = withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            grab(r, ms)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    private fun grab(r: MediaMetadataRetriever, ms: Long): Bitmap? {
        val us = ms * 1000
        return if (Build.VERSION.SDK_INT >= 27) {
            r.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST, FrameEncoding.MAX_EDGE, FrameEncoding.MAX_EDGE)
        } else r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST)
    }

    /** [anchorMs] 는 사용자가 고른 사고 시점(영상 기준 ms). null 이면 영상 중간. */
    override suspend fun captureIncidentFrames(anchorMs: Double?): List<Frame> = withContext(Dispatchers.IO) {
        if (durationMs <= 0) open()
        val anchor = (anchorMs?.toLong() ?: (durationMs / 2)).coerceIn(0, durationMs)
        val r = MediaMetadataRetriever()
        try {
            try { r.setDataSource(context, uri) } catch (e: Exception) { throw InvalidVideoException("영상 파일을 다시 열지 못했습니다. 파일 접근 권한을 확인하세요.") }
            FrameTimes.around(anchor, durationMs).mapNotNull { t ->
                coroutineContext.ensureActive()
                val bmp = grab(r, t) ?: return@mapNotNull null
                val jpeg = FrameEncoding.toJpeg(bmp)
                bmp.recycle()
                Frame(t.toDouble(), (t - anchor) / 1000.0, jpeg, provenance)
            }
        } finally {
            runCatching { r.release() }
        }
    }

    override fun release() {
        _status.value = SourceStatus(false, "영상 선택을 지웠습니다.")
    }
}
