package com.afterstop.app.video

import android.content.Context
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.afterstop.app.core.Frame
import com.afterstop.app.core.FrameProvenance
import com.afterstop.app.core.FrameRingBuffer
import com.afterstop.app.core.SourceStatus
import com.afterstop.app.core.VideoSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraX 휴대폰 카메라 입력.
 * 약 1초에 1장씩 분석용 프레임을 메모리에만 보관한다(최대 20장). 앱이 백그라운드로 가면 [release] 로 정리한다.
 * USB·UVC 카메라 지원 여부는 장치별 시험 대상이며 자동 지원을 가정하지 않는다.
 */
class CameraXVideoSource(private val context: Context) : VideoSource {
    override val id = "phone_camera_camerax"
    override val provenance = FrameProvenance.PHONE_CAMERA
    private val _status = MutableStateFlow(SourceStatus(false, "카메라 연결 전"))
    override val status: StateFlow<SourceStatus> = _status

    val buffer = FrameRingBuffer<Frame>(20) { it.t }
    private val _bufferCount = MutableStateFlow(0)
    val bufferCount: StateFlow<Int> = _bufferCount

    private var executor: ExecutorService? = null
    private var provider: ProcessCameraProvider? = null
    private var lastCaptureAt = 0L
    var lensFacing: Int = CameraSelector.LENS_FACING_BACK
        private set

    fun bind(owner: LifecycleOwner, previewView: PreviewView, facing: Int = lensFacing) {
        lensFacing = facing
        buffer.clear(); _bufferCount.value = 0
        _status.value = SourceStatus(false, "카메라를 여는 중")
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val p = future.get()
                provider = p
                p.unbindAll()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val exec = executor ?: Executors.newSingleThreadExecutor().also { executor = it }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(exec) { image ->
                    try {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastCaptureAt >= 1000) {
                            lastCaptureAt = now
                            val bmp = image.toBitmap()
                            val jpeg = FrameEncoding.toJpeg(bmp, image.imageInfo.rotationDegrees)
                            bmp.recycle()
                            buffer.push(Frame(now.toDouble(), 0.0, jpeg, provenance))
                            _bufferCount.value = buffer.size
                        }
                    } catch (_: Exception) {
                    } finally {
                        image.close()
                    }
                }
                val selector = CameraSelector.Builder().requireLensFacing(facing).build()
                if (!p.hasCamera(selector)) {
                    _status.value = SourceStatus(false, "선택한 방향의 카메라가 없습니다.")
                    return@addListener
                }
                val camera = p.bindToLifecycle(owner, selector, preview, analysis)
                camera.cameraInfo.cameraState.observe(owner) { st ->
                    val err = st.error
                    _status.value = when {
                        err != null -> SourceStatus(false, cameraErrorText(err.code))
                        st.type == CameraState.Type.OPEN -> SourceStatus(true, "휴대폰 카메라 입력 중 · 최근 프레임만 메모리에 보관")
                        st.type == CameraState.Type.CLOSED -> SourceStatus(false, "카메라가 닫혔습니다. 다시 연결하세요.")
                        else -> SourceStatus(false, "카메라 준비 중")
                    }
                }
            } catch (e: Exception) {
                _status.value = SourceStatus(false, "카메라를 연결하지 못했습니다: ${e.javaClass.simpleName}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun cameraErrorText(code: Int): String = when (code) {
        CameraState.ERROR_CAMERA_IN_USE, CameraState.ERROR_MAX_CAMERAS_IN_USE -> "다른 앱이 카메라를 사용 중입니다."
        CameraState.ERROR_CAMERA_DISABLED -> "기기 정책으로 카메라가 비활성화되었습니다."
        CameraState.ERROR_CAMERA_REMOVED -> "카메라 장치가 해제되었습니다. 다시 연결하세요."
        CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> "방해 금지 모드로 카메라를 열 수 없습니다."
        else -> "카메라 오류가 발생했습니다. 다시 연결하세요. (코드 $code)"
    }

    /**
     * [anchorMs] 가 null 이면 최근 최대 6장을 돌려준다.
     * 사건 시각이 주어지면 사건 전 3장을 보관하고 이후 약 3초 동안 3장을 더 확보한다.
     */
    override suspend fun captureIncidentFrames(anchorMs: Double?): List<Frame> {
        val anchor = anchorMs ?: SystemClock.elapsedRealtime().toDouble()
        if (anchorMs == null) return buffer.last(6).map { it.withOffset(anchor) }
        val prior = buffer.snapshot().filter { it.t <= anchor }.takeLast(3)
        delay(3200)
        val post = buffer.snapshot().filter { it.t > anchor }.take(3)
        return (prior + post).takeLast(6).map { it.withOffset(anchor) }
    }

    private fun Frame.withOffset(anchor: Double) = Frame(t, (t - anchor) / 1000.0, jpeg, provenance)

    override fun release() {
        runCatching { provider?.unbindAll() }
        buffer.clear(); _bufferCount.value = 0
        _status.value = SourceStatus(false, "카메라 연결을 종료했습니다.")
    }

    fun shutdown() {
        release()
        executor?.shutdown(); executor = null
    }
}
