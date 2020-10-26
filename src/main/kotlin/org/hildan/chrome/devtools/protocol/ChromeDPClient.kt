package org.hildan.chrome.devtools.protocol

import io.ktor.client.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hildan.krossbow.websocket.WebSocketClient
import org.hildan.krossbow.websocket.defaultWebSocketClient
import kotlinx.serialization.json.Json as KxJson

private val DEFAULT_HTTP_CLIENT by lazy {
    HttpClient {
        install(JsonFeature) {
            serializer = KotlinxSerializer(KxJson { ignoreUnknownKeys = true })
        }
    }
}

private val DEFAULT_WEBSOCKET_CLIENT by lazy { defaultWebSocketClient() }

/**
 * A Chrome Devtools Protocol client.
 *
 * It provides access to the basic HTTP endpoints exposed by the Chrome browser, as well as web socket connections to
 * the browser and its targets to make use of the full Chrome Devtools Protocol API.
 */
class ChromeDPClient(
    private val remoteDebugUrl: String = "http://localhost:9222",
    private val httpClient: HttpClient = DEFAULT_HTTP_CLIENT,
    private val webSocketClient: WebSocketClient = DEFAULT_WEBSOCKET_CLIENT
) {
    /** Browser version metadata. */
    suspend fun version(): ChromeVersion = httpClient.get("$remoteDebugUrl/json/version")

    /** The current devtools protocol definition, as a JSON string. */
    suspend fun protocolJson(): String = httpClient.get("$remoteDebugUrl/json/protocol")

    /** A list of all available websocket targets (e.g. browser tabs). */
    suspend fun targets(): List<ChromeDPTarget> = httpClient.get("$remoteDebugUrl/json/list")

    /** Opens a new tab. Responds with the websocket target data for the new tab. */
    suspend fun newTab(url: String = "about:blank"): ChromeDPTarget = httpClient.get("$remoteDebugUrl/json/new?$url")

    /** Brings a page into the foreground (activate a tab). */
    suspend fun activateTab(targetId: String): String = httpClient.get("$remoteDebugUrl/json/activate/$targetId")

    /** Closes the target page identified by [targetId]. */
    suspend fun closeTab(targetId: String): String = httpClient.get("$remoteDebugUrl/json/close/$targetId")

    /** Closes all targets. */
    suspend fun closeAllTargets() {
        targets().forEach {
            closeTab(it.id)
        }
    }

    /**
     * Opens a web socket connection to interact with the root (browser) session.
     *
     * The returned [ChromeBrowserSession] only provides a limited subset of the possible operations, because it is not
     * attached to any target.
     * To attach to a target using the same underlying web socket connection, call [ChromeBrowserSession.attachTo] or
     * [ChromeBrowserSession.attachToNewPage].
     *
     * You may prefer directly attaching to any target via a new web socket connection by calling [ChromeDPTarget.attach].
     */
    suspend fun webSocket(): ChromeBrowserSession {
        val browserDebuggerUrl = version().webSocketDebuggerUrl
        return webSocketClient.connectToChrome(browserDebuggerUrl)
    }
}

@Serializable
data class ChromeVersion(
    @SerialName("Browser") val browser: String,
    @SerialName("Protocol-Version") val protocolVersion: String,
    @SerialName("User-Agent") val userAgent: String,
    @SerialName("V8-Version") val v8Version: String? = null,
    @SerialName("WebKit-Version") val webKitVersion: String,
    @SerialName("webSocketDebuggerUrl") val webSocketDebuggerUrl: String,
)

/**
 * Targets are the parts of the browser that the Chrome DevTools Protocol can interact with.
 * This includes for instance pages, serviceworkers and extensions.
 *
 * When a client wants to interact with a target using CDP, it has to first attach to the target using
 * [ChromeDPTarget.attach]. This will establish a protocol session to the given target.
 * The client can then interact with the target using the [ChromeTargetSession].
 */
@Serializable
data class ChromeDPTarget(
    val id: String,
    val title: String,
    val type: String,
    val description: String,
    val devtoolsFrontendUrl: String,
    val webSocketDebuggerUrl: String,
) {
    /**
     * Attaches to this target via a new web socket connection to this target's debugger URL.
     * This establishes a new protocol session to this target.
     */
    suspend fun attach(webSocketClient: WebSocketClient = DEFAULT_WEBSOCKET_CLIENT): ChromeTargetSession =
        webSocketClient.connectToChrome(webSocketDebuggerUrl).attachTo(id)
}

private suspend fun WebSocketClient.connectToChrome(debuggerUrl: String): ChromeBrowserSession {
    val connection = connect(debuggerUrl).chromeDp()
    return ChromeBrowserSession(connection)
}
