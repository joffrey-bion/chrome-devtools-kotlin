package org.hildan.chrome.devtools.protocol

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.broadcastIn
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.plus
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.hildan.chrome.devtools.domains.target.SessionID
import org.hildan.krossbow.websocket.WebSocketFrame
import org.hildan.krossbow.websocket.WebSocketSession
import org.hildan.krossbow.websocket.defaultWebSocketClient
import java.util.concurrent.atomic.AtomicLong

/**
 * ChromeDebuggerConnection represents connection to chrome's debugger via
 * [DevTools Protocol](https://chromedevtools.github.io/devtools-protocol/).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChromeDebuggerConnection private constructor(
    private val session: WebSocketSession,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val sessionId: SessionID? = null,
) {
    private val job = Job()
    private val coroutineScope = CoroutineScope(job)

    private val nextRequestId = AtomicLong(0)

    @OptIn(FlowPreview::class)
    private val frames =
        session.incomingFrames.consumeAsFlow()
            .map { frame -> frame.decodeInboundFrame() }
            .broadcastIn(coroutineScope + CoroutineName("ChromeDP-frame-decoder"))

    internal suspend fun <I> request(methodName: String, requestParams: I?): InboundFrame {
        val request =
            RequestFrame(
                id = nextRequestId.incrementAndGet(),
                method = methodName,
                params = requestParams,
                sessionId = sessionId,
            )
        val framesSubscription = frames.openSubscription()
        session.sendText(json.encodeToString(request))
        val response = framesSubscription.consumeAsFlow().filter { it.matchesRequest(request) }.first()
        if (response.error != null) {
            throw RequestFailed(response.error)
        }
        return response
    }

    suspend inline fun <I, reified O : Any> requestForResult(methodName: String, requestParams: I?): O =
        requestForResult(methodName, requestParams, deserializer = serializer())

    /**
     * Sends request and captures response from the stream.
     */
    suspend fun <I, O : Any> requestForResult(
        methodName: String,
        requestParams: I?,
        deserializer: DeserializationStrategy<O>,
    ): O = request<I>(methodName, requestParams).decodeResponsePayload(deserializer)

    /**
     * Captures events by given name and casts received messages to specified class.
     */
    inline fun <reified E> events(eventName: String): Flow<E> = events(eventName, serializer())

    /**
     * Captures events by given name and casts received messages to specified class.
     */
    fun <E> events(eventName: String, deserializer: DeserializationStrategy<E>): Flow<E> =
        sessionEvents().filter { it.matchesMethod(eventName) }.map { it.decodeEventPayload(deserializer) }

    private fun sessionEvents() = frames.openSubscription()
        .consumeAsFlow()
        .filter(InboundFrame::isEvent)
        .filter { it.matchesSessionId(sessionId) }

    /**
     * Reuse existing debugger connection but for new sessionID sharing underlying websocket connection.
     */
    fun cloneForSessionId(sessionID: SessionID) = ChromeDebuggerConnection(session, json, sessionID)

    /**
     * Closes connection to remote debugger.
     */
    suspend fun close() {
        session.close()
    }

    companion object Factory {
        /**
         * Creates new ChromeDebuggerConnection session for given websocket uri and frames buffer size.
         */
        suspend fun open(websocketUri: String): ChromeDebuggerConnection =
            ChromeDebuggerConnection(client.connect(websocketUri))

        private val client by lazy { defaultWebSocketClient() }
    }
}

private val lenientJson = Json { ignoreUnknownKeys = true }

private fun WebSocketFrame.decodeInboundFrame() =
    lenientJson.decodeFromString<InboundFrame>((this as WebSocketFrame.Text).text)

private fun <T> InboundFrame.decodeResponsePayload(deserializer: DeserializationStrategy<T>): T =
    lenientJson.decodeFromJsonElement(deserializer, result ?: error("Missing result in response"))

private fun <T> InboundFrame.decodeEventPayload(deserializer: DeserializationStrategy<T>): T =
    lenientJson.decodeFromJsonElement(deserializer, params ?: error("Missing params field in event"))

class RequestFailed(val error: RequestError) : Exception(error.message)
