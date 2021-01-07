package org.hildan.chrome.devtools.protocol

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hildan.krossbow.websocket.WebSocketFrame
import org.hildan.krossbow.websocket.WebSocketSession

internal fun WebSocketSession.chromeDp(): ChromeDPConnection = ChromeDPConnection(this)

@OptIn(ExperimentalCoroutinesApi::class)
internal class ChromeDPConnection(
    private val webSocket: WebSocketSession
) {
    private val job = Job()
    private val coroutineScope = CoroutineScope(job)

    private val frames = webSocket.incomingFrames.consumeAsFlow()
        .filterIsInstance<WebSocketFrame.Text>()
        .map { frame -> frame.decodeInboundFrame() }
        .broadcastIn(coroutineScope + CoroutineName("ChromeDP-frame-decoder"))

    suspend fun request(request: RequestFrame): InboundFrame {
        val framesSubscription = frames.openSubscription()
        webSocket.sendText(json.encodeToString(request))
        val response = framesSubscription.consumeAsFlow().filter { it.matchesRequest(request) }.firstOrNull()
            ?: throw MissingResponse(request)
        if (response.error != null) {
            throw RequestFailed(request, response.error)
        }
        return response
    }

    fun events() = frames.openSubscription().consumeAsFlow().filter(InboundFrame::isEvent)

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
