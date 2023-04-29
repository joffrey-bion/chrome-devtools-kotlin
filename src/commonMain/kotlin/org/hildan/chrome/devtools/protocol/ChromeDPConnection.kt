package org.hildan.chrome.devtools.protocol

import io.ktor.utils.io.errors.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString

/**
 * Wraps this [WebSocketSession] to provide Chrome DevTools Protocol capabilities.
 *
 * The returned [ChromeDPConnection] can be used to send requests and listen to events.
 */
internal fun WebSocketSession.chromeDp(): ChromeDPConnection = ChromeDPConnection(this)

/**
 * A connection to Chrome, providing communication primitives for the Chrome DevTools protocol.
 *
 * It encodes/decodes ChromeDP frames, and handles sharing of incoming events.
 */
internal class ChromeDPConnection(
    private val webSocket: WebSocketSession,
) {
    private val coroutineScope = CoroutineScope(CoroutineName("ChromeDP-frame-decoder"))

    private val frames = webSocket.incoming.receiveAsFlow()
        .filterIsInstance<Frame.Text>()
        .map { frame -> chromeDpJson.decodeFromString(InboundFrameSerializer, frame.readText()) }
        .shareIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
        )

    /**
     * Sends the given ChromeDP [request], and returns the corresponding [ResponseFrame].
     *
     * Throws [RequestFailed] in case of error.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun request(request: RequestFrame): ResponseFrame {
        if (webSocket.outgoing.isClosedForSend) {
            throw IOException("Cannot perform Chrome DevTools request ${request.method}, the web socket is closed.")
        }
        val resultFrame = frames.onSubscription { webSocket.send(chromeDpJson.encodeToString(request)) }
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
