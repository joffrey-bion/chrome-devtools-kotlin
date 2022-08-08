package org.hildan.chrome.devtools.protocol

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hildan.krossbow.websocket.WebSocketConnection
import org.hildan.krossbow.websocket.WebSocketFrame

private val json = Json { ignoreUnknownKeys = true }

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
        .map { frame -> json.decodeFromString(InboundFrameSerializer, frame.text) }
        .shareIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
        )

    /**
     * Sends the given ChromeDP [request], and returns the corresponding [ResponseFrame].
     */
    suspend fun request(request: RequestFrame): ResponseFrame {
        val resultFrame = frames.onSubscription { webSocket.sendText(json.encodeToString(request)) }
            .filterIsInstance<ResultFrame>()
            .filter { it.matchesRequest(request) }
            .first() // a shared flow never completes anyway, so this will never throw (but can hang forever)

        when (resultFrame) {
            is ResponseFrame -> return resultFrame
            is ErrorFrame -> throw RequestFailed(request, resultFrame.error)
        }
    }

    private fun ResultFrame.matchesRequest(request: RequestFrame): Boolean =
        // id is only unique within a session, so we need to check sessionId too
        id == request.id && sessionId == request.sessionId

    /**
     * A flow of incoming events.
     */
    fun events() = frames.filterIsInstance<EventFrame>()

    /**
     * Stops listening to incoming events and closes the underlying web socket connection.
     */
    suspend fun close() {
        coroutineScope.cancel()
        webSocket.close()
    }
}

/**
 * An exception thrown when an error occurred during the processing of a request on server side.
 */
class RequestFailed(val request: RequestFrame, val error: RequestError) : Exception(error.message)
