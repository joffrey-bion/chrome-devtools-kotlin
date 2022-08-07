package org.hildan.chrome.devtools.protocol

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hildan.krossbow.websocket.WebSocketConnection
import org.hildan.krossbow.websocket.WebSocketFrame

internal fun WebSocketConnection.chromeDp(): ChromeDPConnection = ChromeDPConnection(this)

/**
 * A connection to Chrome, providing communication primitives for the Chrome DevTools protocol.
 *
 * It encodes/decodes ChromeDP frames, and handles sharing of incoming events.
 */
internal class ChromeDPConnection(
    private val webSocket: WebSocketConnection
) {
    private val coroutineScope = CoroutineScope(CoroutineName("ChromeDP-frame-decoder"))

    private val frames = webSocket.incomingFrames
        .filterIsInstance<WebSocketFrame.Text>()
        .map { frame -> frame.decodeInboundFrame() }
        .shareIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
        )

    /**
     * Sends the given ChromeDP [request], and returns the corresponding [InboundFrame].
     */
    suspend fun request(request: RequestFrame): InboundFrame {
        val response = frames.onSubscription { webSocket.sendText(json.encodeToString(request)) }
            .filter { it.matchesRequest(request) }
            .firstOrNull() ?: throw MissingResponse(request)
        if (response.error != null) {
            throw RequestFailed(request, response.error)
        }
        return response
    }

    /**
     * A flow of incoming events.
     */
    fun events() = frames.filter(InboundFrame::isEvent)

    /**
     * Stops listening to incoming events and closes the underlying web socket connection.
     */
    suspend fun close() {
        coroutineScope.cancel()
        webSocket.close()
    }
}

private val json = Json { ignoreUnknownKeys = true }

private fun WebSocketFrame.Text.decodeInboundFrame() = json.decodeFromString(InboundFrame.serializer(), text)

class RequestFailed(var request: RequestFrame, val error: RequestError) : Exception(error.message)

class MissingResponse(var request: RequestFrame) :
    Exception("Missing response for request ${request.method} #${request.id}")
