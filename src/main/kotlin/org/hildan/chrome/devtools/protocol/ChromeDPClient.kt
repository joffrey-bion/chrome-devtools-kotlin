package org.hildan.chrome.devtools.protocol

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hildan.chrome.devtools.targets.ChromeBrowserSession
import org.hildan.chrome.devtools.targets.ChromePageSession
import org.hildan.chrome.devtools.targets.attachToPage
import org.hildan.krossbow.websocket.WebSocketClient
import org.hildan.krossbow.websocket.defaultWebSocketClient
import kotlinx.serialization.json.Json as KxJson

private fun createHttpClient(block: HttpClientConfig<*>.() -> Unit) = HttpClient {
    install(JsonFeature) {
        serializer = KotlinxSerializer(KxJson { ignoreUnknownKeys = true })
    }
    block()
}

private val DEFAULT_WEBSOCKET_CLIENT by lazy { defaultWebSocketClient() }

/**
 * A Chrome Devtools Protocol client.
 *
 * It provides access to the basic HTTP endpoints exposed by the Chrome browser, as well as web socket connections to
 * the browser and its targets to make use of the full Chrome Devtools Protocol API.
 *
 * ## Host override
 *
 * Chrome doesn't accept a `Host` header that is not an IP nor `localhost`, but in some environments it might be hard
 * to provide this (e.g. docker services in a docker swarm, communicating using service names).
 *
 * To work around this problem, simply set [overrideHostHeader] to true.
 * This overrides the `Host` header to "localhost" in the HTTP requests to the Chrome debugger to make it happy, and
 * also replaces the host in subsequent web socket URLs (returned by Chrome) by the initial host provided in
 * [remoteDebugUrl].
 * This is necessary because Chrome uses the `Host` header to build these URLs, and it would be incorrect to keep this.
 */
class ChromeDPClient(
    private val remoteDebugUrl: String = "http://localhost:9222",
    private val webSocketClient: WebSocketClient = DEFAULT_WEBSOCKET_CLIENT,
    private val overrideHostHeader: Boolean = false,
    configureHttpClient: HttpClientConfig<*>.() -> Unit = {},
) {
    private val httpClient: HttpClient = createHttpClient {
        if (overrideHostHeader) {
            install(DefaultRequest) {
                headers["Host"] = "localhost"
            }
        }
        configureHttpClient()
    }

    /** Browser version metadata. */
    suspend fun version(): ChromeVersion = httpClient.get<ChromeVersion>("$remoteDebugUrl/json/version").fixHost()

    /** The current devtools protocol definition, as a JSON string. */
    suspend fun protocolJson(): String = httpClient.get("$remoteDebugUrl/json/protocol")

    /** A list of all available websocket targets (e.g. browser tabs). */
    suspend fun targets(): List<ChromeDPTarget> {
        val targets = httpClient.get<List<ChromeDPTarget>>("$remoteDebugUrl/json/list")
        if (overrideHostHeader) {
            return targets.map { it.fixHost() }
        }
        return targets
    }

    /** Opens a new tab. Responds with the websocket target data for the new tab. */
    @Deprecated(message = "Prefer richer API via web socket", replaceWith = ReplaceWith("webSocket().attachToNewPage(url)"))
    suspend fun newTab(url: String = "about:blank"): ChromeDPTarget =
        httpClient.get<ChromeDPTarget>("$remoteDebugUrl/json/new?$url").fixHost()

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
     * Opens a web socket connection to interact with the browser target (root session, without session ID).
     *
     * The returned [ChromeBrowserSession] only provides a limited subset of the possible operations, because it is
     * attached to the default *browser* target, not a *page* target.
     * To attach to a specific target using the same underlying web socket connection, call
     * [ChromeBrowserSession.attachToPage] or
     * [ChromeBrowserSession.attachToNewPage][org.hildan.chrome.devtools.targets.attachToNewPage].
     */
    suspend fun webSocket(): ChromeBrowserSession {
        val browserDebuggerUrl = version().webSocketDebuggerUrl
        return webSocketClient.connectToChrome(browserDebuggerUrl)
    }

    private fun ChromeVersion.fixHost() = when {
        overrideHostHeader -> copy(webSocketDebuggerUrl = webSocketDebuggerUrl.fixHost())
        else -> this
    }

    private fun ChromeDPTarget.fixHost() = when {
        overrideHostHeader -> copy(webSocketDebuggerUrl = webSocketDebuggerUrl.fixHost())
        else -> this
    }

    private fun String.fixHost(): String = when {
        overrideHostHeader -> URLBuilder(this).apply {
            val url = Url(remoteDebugUrl)
            host = url.host
            port = url.port
        }.buildString()
        else -> this
    }
}

/**
 * Browser version information retrieved via the debugger API.
 */
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
 * This includes for instance pages, serviceworkers and extensions (and also the browser itself).
 *
 * When a client wants to interact with a target using CDP, it has to first attach to the target using
 * [ChromeDPTarget.attach]. This will establish a protocol session to the given target.
 * The client can then interact with the target using the [ChromePageSession].
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
    suspend fun attach(webSocketClient: WebSocketClient = DEFAULT_WEBSOCKET_CLIENT): ChromePageSession =
        webSocketClient.connectToChrome(webSocketDebuggerUrl).attachToPage(id)
}

/**
 * Connects to the Chrome debugger at the given [webSocketDebuggerUrl].
 *
 * The returned [ChromeBrowserSession] only provides a limited subset of the possible operations, because it is
 * attached to the default *browser* target, not a *page* target.
 * To attach to a specific target using the same underlying web socket connection, call
 * [ChromeBrowserSession.attachToPage] or
 * [ChromeBrowserSession.attachToNewPage][org.hildan.chrome.devtools.targets.attachToNewPage].
 */
suspend fun WebSocketClient.connectToChrome(webSocketDebuggerUrl: String): ChromeBrowserSession {
    val connection = connect(webSocketDebuggerUrl).chromeDp()
    return ChromeBrowserSession(ChromeDPSession(connection, null))
}
