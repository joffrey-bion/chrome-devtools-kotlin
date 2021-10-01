package org.hildan.chrome.devtools.protocol

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hildan.krossbow.websocket.WebSocketConnection
import org.hildan.krossbow.websocket.WebSocketFrame

internal fun WebSocketConnection.chromeDp(): ChromeDPConnection = ChromeDPConnection(this)

internal class ChromeDPConnection(
    private val webSocket: WebSocketConnection
) {
    private val job = Job()
    private val coroutineScope = CoroutineScope(job)

    private val frames = webSocket.incomingFrames.consumeAsFlow()
        .filterIsInstance<WebSocketFrame.Text>()
        .map { frame -> frame.decodeInboundFrame() }
        .shareIn(
            scope = coroutineScope + CoroutineName("ChromeDP-frame-decoder"),
            started = SharingStarted.Eagerly,
        )

    suspend fun request(request: RequestFrame): InboundFrame {
        val response = frames.onSubscription { webSocket.sendText(json.encodeToString(request)) }
            .filter { it.matchesRequest(request) }
            .firstOrNull() ?: throw MissingResponse(request)
        if (response.error != null) {
            throw RequestFailed(request, response.error)
        }
        return response
    }

    fun events() = frames.filter(InboundFrame::isEvent)

    suspend fun close() {
        job.cancelAndJoin()
        webSocket.close()
    }
}

private val json = Json { ignoreUnknownKeys = true }

private fun WebSocketFrame.Text.decodeInboundFrame() = json.decodeFromString<InboundFrame>(text)

class RequestFailed(var request: RequestFrame, val error: RequestError) : Exception(error.message)

class MissingResponse(var request: RequestFrame) :
    Exception("Missing response for request ${request.method} #${request.id}")
