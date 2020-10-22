package org.hildan.chrome.devtools.protocol

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
        .filterIsInstance<WebSocketFrame.Text>()
        .map { frame -> frame.decodeInboundFrame() }
        .broadcastIn(coroutineScope + CoroutineName("ChromeDP-frame-decoder"))

    suspend fun request(methodName: String, requestParams: JsonElement?, sessionId: SessionID?): InboundFrame {
        val request = RequestFrame(
            id = nextRequestId.incrementAndGet(),
            method = methodName,
            params = requestParams,
            sessionId = sessionId,
        )
        val framesSubscription = frames.openSubscription()
        webSocket.sendText(json.encodeToString(request))
        val response = framesSubscription.consumeAsFlow().filter { it.matchesRequest(request) }.first()
        if (response.error != null) {
            throw RequestFailed(response.error)
        }
        return response
    }

    fun events() = frames.openSubscription()
        .consumeAsFlow()
        .filter(InboundFrame::isEvent)

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
        suspend fun open(websocketUrl: String): ChromeDPConnection = ChromeDPConnection(client.connect(websocketUrl))

        private val client by lazy { defaultWebSocketClient() }
    }
}

private val json = Json { ignoreUnknownKeys = true }

private fun WebSocketFrame.Text.decodeInboundFrame() = json.decodeFromString<InboundFrame>(text)

class RequestFailed(val error: RequestError) : Exception(error.message)
