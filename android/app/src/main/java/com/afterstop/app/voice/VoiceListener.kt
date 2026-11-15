package com.afterstop.app.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

/** 짧은 한국어 대답("응", "나중에")을 한 번 듣는다. 메인 스레드에서 호출한다. */
class VoiceListener(private val ctx: Context) {
    private var rec: SpeechRecognizer? = null

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(ctx)
    val permitted: Boolean get() = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun listen(onResult: (List<String>) -> Unit, onError: (String) -> Unit) {
        stop()
        if (!permitted) return onError("마이크 권한이 없어요")
        if (!available) return onError("이 기기에서는 음성 인식을 쓸 수 없어요")
        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(ctx) }.getOrNull() ?: return onError("음성 인식을 시작하지 못했어요")
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(b: Bundle?) { val l = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty(); stop(); onResult(l) }
            override fun onError(error: Int) {
                stop()
                onError(when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "대답을 듣지 못했어요"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 없어요"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> "음성 인식 연결이 안 돼요"
                    else -> "음성 인식이 중단됐어요"
                })
            }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        runCatching { r.startListening(intent) }.onFailure { stop(); onError("음성 인식을 시작하지 못했어요") }
    }

    fun stop() { rec?.let { runCatching { it.cancel(); it.destroy() } }; rec = null }
}
