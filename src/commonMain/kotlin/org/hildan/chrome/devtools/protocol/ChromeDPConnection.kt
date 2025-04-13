package org.hildan.chrome.devtools.protocol

import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.*

/**
 * Wraps this [WebSocketSession] to provide Chrome DevTools Protocol capabilities.
 *
 * The returned [ChromeDPConnection] can be used to send requests and listen to events.
 *
 * It launches a coroutine internally to process and share incoming events.
 * The [eventProcessingContext] can be used to customize the context of this coroutine.
 */
internal fun WebSocketSession.chromeDp(
    eventProcessingContext: CoroutineContext = EmptyCoroutineContext,
): ChromeDPConnection = ChromeDPConnection(this, eventProcessingContext)

/**
 * A connection to Chrome, providing communication primitives for the Chrome DevTools protocol.
 *
 * It encodes/decodes ChromeDP frames, and handles sharing of incoming events.
 *
 * It launches a coroutine internally to process and share incoming events.
 * The [eventProcessingContext] can be used to customize the context of this coroutine.
 */
internal class ChromeDPConnection(
    private val webSocket: WebSocketSession,
    eventProcessingContext: CoroutineContext = EmptyCoroutineContext,
) {
    private val coroutineScope = CoroutineScope(CoroutineName("ChromeDP-frame-decoder") + eventProcessingContext)

    private val frames = webSocket.incoming.receiveAsFlow()
        .filterIsInstance<Frame.Text>()
        .map { frame -> chromeDpJson.decodeFromString(InboundFrameSerializer, frame.readText()) }
        .materializeErrors()
        .shareIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
        )

    /**
     * Sends the given ChromeDP [request], and returns the corresponding [ResponseFrame].
     *
     * @throws RequestNotSentException if the socket is already closed and the request cannot be sent
     * @throws RequestFailed if the Chrome debugger returns an error frame
     */
    suspend fun request(request: RequestFrame): ResponseFrame {
        val resultFrame = frames.onSubscription { sendOrFailUniformly(request) }
            .dematerializeErrors()
            .filterIsInstance<ResultFrame>()
            .filter { it.matchesRequest(request) }
            .first() // a shared flow never completes, so this will never throw NoSuchElementException (but can hang forever)

        when (resultFrame) {
            is ResponseFrame -> return resultFrame
            is ErrorFrame -> throw RequestFailed(request, resultFrame.error)
        }
    }

    private suspend fun sendOrFailUniformly(request: RequestFrame) {
        try {
            webSocket.send(chromeDpJson.encodeToString(request))
        } catch (e: Exception) {
            // It's possible to get CancellationException without being cancelled, for example
            // when ChromeDPConnection.close() was called before calling request().
            // Not sure why we don't get ClosedSendChannelException in that case - requires further investigation.
            currentCoroutineContext().ensureActive()
            throw RequestNotSentException(request, e)
        }
    }

    private fun ResultFrame.matchesRequest(request: RequestFrame): Boolean =
        // id is only unique within a session, so we need to check sessionId too
        id == request.id && sessionId == request.sessionId

    /**
     * A flow of incoming events.
     */
    fun events() = frames.dematerializeErrors().filterIsInstance<EventFrame>()

    /**
     * Stops listening to incoming events and closes the underlying web socket connection.
     */
    suspend fun close() {
        coroutineScope.cancel()
        webSocket.close()
    }
}

private fun Flow<InboundFrame>.materializeErrors(): Flow<InboundFrameOrError> =
    catch<InboundFrameOrError> { emit(InboundFramesConnectionError(cause = it)) }

private fun Flow<InboundFrameOrError>.dematerializeErrors(): Flow<InboundFrame> =
    map {
        when (it) {
            is InboundFramesConnectionError -> throw it.cause
            is InboundFrame -> it
        }
    }

/**
 * An exception thrown when an error occurred during the processing of a request on Chrome side.
 */
class RequestFailed(val request: RequestFrame, val error: RequestError) : Exception(error.message)

/**
 * An exception thrown when an error prevented sending a request via the Chrome web socket.
 */
class RequestNotSentException(
    val request: RequestFrame,
    cause: Throwable?,
) : Exception("Could not send request '${request.method}': $cause", cause)
