package com.afterstop.app.net

import com.afterstop.app.core.IncidentEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** SSE 한 블록 */
data class SseMessage(val id: String?, val event: String, val data: String)

/** 줄 단위 SSE 파서 (WHATWG 규칙 중 이 앱이 쓰는 부분). */
class SseParser {
    private var id: String? = null
    private var event: String = "message"
    private val data = StringBuilder()
    var retryMs: Long? = null
        private set

    /** 한 줄을 넣고, 빈 줄로 블록이 끝나면 메시지를 돌려준다. */
    fun feed(line: String): SseMessage? {
        if (line.isEmpty()) {
            if (data.isEmpty()) { event = "message"; return null }
            val msg = SseMessage(id, event, data.toString().removeSuffix("\n"))
            data.setLength(0); event = "message"
            return msg
        }
        if (line.startsWith(":")) return null
        val idx = line.indexOf(':')
        val field = if (idx < 0) line else line.substring(0, idx)
        var value = if (idx < 0) "" else line.substring(idx + 1)
        if (value.startsWith(" ")) value = value.substring(1)
        when (field) {
            "id" -> id = value
            "event" -> event = value
            "data" -> data.append(value).append('\n')
            "retry" -> retryMs = value.toLongOrNull()
        }
        return null
    }

    val lastId: String? get() = id
}

/** 최근 이벤트 ID 를 기억해 재연결·재전송으로 같은 이벤트가 두 번 처리되지 않게 한다. */
class EventDeduper(private val capacity: Int = 100) {
    private val seen = LinkedHashSet<String>()
    fun firstTime(key: String): Boolean {
        if (key in seen) return false
        seen.add(key)
        while (seen.size > capacity) seen.remove(seen.first())
        return true
    }
}

sealed interface StreamState {
    data object Connecting : StreamState
    data object Connected : StreamState
    data class Waiting(val reason: String, val retryInMs: Long) : StreamState
}

/** 서버 사고 후보 이벤트 수신 (자동 재연결 + Last-Event-ID + 중복 억제). */
class IncidentEventStream(
    private val client: ServerClient,
    private val onState: (StreamState) -> Unit,
) {
    fun events(): Flow<IncidentEvent> = callbackFlow {
        val dedupe = EventDeduper()
        var lastId: Long? = null
        var backoff = 1000L
        val job = launch {
            while (isActive) {
                onState(StreamState.Connecting)
                val call = client.eventsCall(lastId)
                try {
                    call.await().use { r ->
                        if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
                        onState(StreamState.Connected)
                        backoff = 1000L
                        val parser = SseParser()
                        val src = r.body!!.source()
                        while (isActive) {
                            val line = src.readUtf8Line() ?: break
                            val msg = parser.feed(line) ?: continue
                            parser.retryMs?.let { backoff = it.coerceIn(1000, 30000) }
                            msg.id?.toLongOrNull()?.let { lastId = it }
                            if (msg.event != "incident") continue
                            val ev = IncidentEventParser.parse(msg.data) ?: continue
                            val key = ev.eventId?.toString() ?: "${ev.inputSource}:${ev.t}"
                            if (dedupe.firstTime(key)) trySend(ev)
                        }
                    }
                } catch (e: CancellationException) {
                    call.cancel(); throw e
                } catch (e: Exception) {
                    // 아래에서 재연결
                }
                if (!isActive) break
                onState(StreamState.Waiting("연결이 끊겼습니다. 다시 연결합니다.", backoff))
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30000)
            }
        }
        awaitClose { job.cancel() }
    }.flowOn(Dispatchers.IO)
}
