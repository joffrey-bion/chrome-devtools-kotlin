package org.hildan.chrome.devtools.protocol

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.plus
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
internal class ChromeDPConnection private constructor(
    private val webSocket: WebSocketSession
) {
    private val job = Job()
    private val coroutineScope = CoroutineScope(job)

    private val nextRequestId = AtomicLong(0)

    @OptIn(FlowPreview::class)
    private val frames = webSocket.incomingFrames.consumeAsFlow()
        .map { frame -> frame.decodeInboundFrame() }
        .broadcastIn(coroutineScope + CoroutineName("ChromeDP-frame-decoder"))

    suspend fun <I> request(methodName: String, requestParams: I?, sessionId: SessionID): InboundFrame {
        val request = RequestFrame(
            id = nextRequestId.incrementAndGet(),
            method = methodName,
            params = requestParams,
            sessionId = sessionId,
        )
        val framesSubscription = frames.openSubscription()
        webSocket.sendText(lenientJson.encodeToString(request))
        val response = framesSubscription.consumeAsFlow().filter { it.matchesRequest(request) }.first()
        if (response.error != null) {
            throw RequestFailed(response.error)
        }
        return response
    }

    fun sessionEvents(sessionId: SessionID) = frames.openSubscription()
        .consumeAsFlow()
        .filter(InboundFrame::isEvent)
        .filter { it.matchesSessionId(sessionId) }

    /**
     * Closes connection to remote debugger.
     */
    suspend fun close() {
        webSocket.close()
    }

    companion object Factory {
        /**
         * Creates new ChromeDebuggerConnection session for given websocket uri and frames buffer size.
         */
        suspend fun open(websocketUri: String): ChromeDPConnection =
            ChromeDPConnection(client.connect(websocketUri))

        private val client by lazy { defaultWebSocketClient() }
    }
}

private val lenientJson = Json { ignoreUnknownKeys = true }

private fun WebSocketFrame.decodeInboundFrame() =
    lenientJson.decodeFromString<InboundFrame>((this as WebSocketFrame.Text).text)

class RequestFailed(val error: RequestError) : Exception(error.message)
