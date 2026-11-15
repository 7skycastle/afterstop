package com.afterstop.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import com.afterstop.app.core.AudioClip
import com.afterstop.app.core.AudioManifest
import com.afterstop.app.core.AudioPlayer
import com.afterstop.app.core.AudioState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 앱에 포함한 하윤(Fish Audio) 녹음 11개를 manifest 기준으로 재생한다.
 * 다른 앱이 오디오 포커스를 가져가면(전화·내비 등) 즉시 멈추고 자동으로 다시 재생하지 않는다.
 */
class AssetAudioPlayer(private val context: Context) : AudioPlayer {
    private val clips: Map<String, AudioClip> = runCatching {
        AudioManifest.playable(AudioManifest.parse(context.assets.open("audio/manifest.json").bufferedReader().use { it.readText() }))
    }.getOrDefault(emptyMap())
    /** 보험사 연결 대화 녹음. 파일 경로는 audio/ 기준(prompts/xxx.mp3). */
    private val promptClips: Map<String, AudioClip> = runCatching {
        AudioManifest.playablePrompts(AudioManifest.parse(context.assets.open("audio/prompts/manifest.json").bufferedReader().use { it.readText() }))
            .mapValues { (_, c) -> c.copy(file = "prompts/" + c.file) }
    }.getOrDefault(emptyMap())
    val clipCount: Int get() = clips.size
    val promptCount: Int get() = promptClips.size
    /** 녹음이 끝까지 재생되면 키를 알려 준다(멈춤·오류는 알리지 않음). */
    var onFinished: ((String) -> Unit)? = null

    private val _state = MutableStateFlow(AudioState(message = if (clips.size == 11) "하윤 목소리 안내 준비됨" else "녹음 ${clips.size}개 준비 · 화면 글로 안내해요"))
    override val state: StateFlow<AudioState> = _state

    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var player: MediaPlayer? = null
    private var token = 0
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            stop("통화 등 다른 소리 때문에 음성을 멈췄어요 · 다시 듣기로 재생")
        }
    }
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs)
        .setOnAudioFocusChangeListener(focusListener)
        .build()

    override fun hasClip(key: String) = key in clips || key in promptClips

    override fun play(key: String) {
        stop(null)
        val clip = clips[key] ?: promptClips[key] ?: run { _state.value = AudioState(null, "이 안내는 녹음이 없어 화면 글로 안내해요"); return }
        if (am.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            _state.value = AudioState(null, "다른 소리가 나오고 있어 음성을 시작하지 않았어요"); return
        }
        val my = ++token
        try {
            val mp = MediaPlayer()
            context.assets.openFd("audio/${clip.file}").use { fd -> mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            mp.setAudioAttributes(attrs)
            mp.setOnPreparedListener { if (my == token) { it.start(); _state.value = AudioState(key, "하윤이 안내하고 있어요") } }
            mp.setOnCompletionListener { if (my == token) { release(it); _state.value = AudioState(null, "안내를 마쳤어요 · 다시 듣기 가능"); onFinished?.invoke(key) } }
            mp.setOnErrorListener { p, _, _ -> if (my == token) { release(p); _state.value = AudioState(null, "음성을 재생하지 못했어요 · 화면 글로 안내해요") }; true }
            player = mp
            mp.prepareAsync()
        } catch (e: Exception) {
            _state.value = AudioState(null, "음성을 재생하지 못했어요 · 화면 글로 안내해요")
            am.abandonAudioFocusRequest(focusRequest)
        }
    }

    private fun release(mp: MediaPlayer) {
        runCatching { mp.release() }
        if (player === mp) player = null
        am.abandonAudioFocusRequest(focusRequest)
    }

    override fun stop(reason: String?) {
        token++
        player?.let { p -> runCatching { if (p.isPlaying) p.stop() }; release(p) }
        player = null
        if (reason != null || _state.value.playingKey != null) _state.value = AudioState(null, reason ?: "음성을 멈췄어요")
    }

    override fun release() = stop(null)
}
