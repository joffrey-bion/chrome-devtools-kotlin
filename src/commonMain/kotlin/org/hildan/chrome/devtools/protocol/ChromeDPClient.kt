package org.hildan.chrome.devtools.protocol

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.hildan.chrome.devtools.sessions.*

private val DEFAULT_HTTP_CLIENT by lazy { createHttpClient(overrideHostHeader = false) }

private val DEFAULT_HTTP_CLIENT_WITH_HOST_OVERRIDE by lazy { createHttpClient(overrideHostHeader = true) }

private fun createHttpClient(overrideHostHeader: Boolean) = HttpClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(WebSockets)
    if (overrideHostHeader) {
        install(DefaultRequest) {
            headers["Host"] = "localhost"
        }
    }
}

/**
 * A client using the Chrome Devtools Protocol to communicate with a running Chrome browser via the debugger API.
 *
 * It provides access to the [HTTP endpoints](https://chromedevtools.github.io/devtools-protocol/#endpoints) exposed
 * by the Chrome browser, as well as web socket connections to the browser and its targets to make use of the full
 * Chrome Devtools Protocol API.
 *
 * **Note:** if you already know the browser target's web socket URL, you don't need to create a `ChromeDPClient`.
 * Instead, you can directly use [HttpClient.chromeWebSocket].
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
    /**
     * The Chrome debugger HTTP URL. This will be used to access metadata via HTTP, in order to ultimately get a web
     * socket URL and connect via web socket for a richer API.
     *
     * If you already have the browser's *web socket* debugger URL (which looks like
     * `ws://127.0.0.1:36775/devtools/browser/a292f96c-7332-4ce8-82a9-7411f3bd280a`), use [HttpClient.chromeWebSocket]
     * directly instead of creating a [ChromeDPClient].
     */
    private val remoteDebugUrl: String = "http://localhost:9222",
    /**
     * Enables override of the `Host` header to `localhost` (see section about Host override in [ChromeDPClient] doc).
     */
    private val overrideHostHeader: Boolean = false,
    /**
     * The underlying Ktor [HttpClient] to use, which must have the [WebSockets] plugin installed, as well as the
     * [ContentNegotiation] plugin with Kotlinx Serialization JSON.
     *
     * This parameter should usually be left to its default value, which is a pre-configured client that is reused
     * between instances of [ChromeDPClient].
     * You should only need to override it to work around an issue in the client's configuration/behaviour, or if you
     * want to also reuse your own client here.
     */
    private val httpClient: HttpClient = if (overrideHostHeader) DEFAULT_HTTP_CLIENT_WITH_HOST_OVERRIDE else DEFAULT_HTTP_CLIENT,
) {
    init {
        require(remoteDebugUrl.startsWith("http://") || remoteDebugUrl.startsWith("https://")) {
            "This function is meant to be used with 'http://' or 'https://' URLs, but got $remoteDebugUrl. " +
                "If you already have a web socket URL, use ChromeDP.connect(wsUrl) directly instead."
        }
    }

    /**
     * Fetches the browser version metadata via the debugger's HTTP API.
     */
    suspend fun version(): ChromeVersion =
        httpClient.get("$remoteDebugUrl/json/version").body<ChromeVersion>().fixHost()

    /**
     * Fetches the current Chrome DevTools Protocol definition, as a JSON string.
     */
    suspend fun protocolJson(): String = httpClient.get("$remoteDebugUrl/json/protocol").bodyAsText()

    /**
     * Fetches the list of all available web socket targets (e.g. browser tabs).
     */
    suspend fun targets(): List<ChromeDPTarget> =
        httpClient.get("$remoteDebugUrl/json/list").body<List<ChromeDPTarget>>().map { it.fixHost() }

    /**
     * Opens a new tab, and returns the websocket target data for the new tab.
     */
    @Deprecated(
        message = "Prefer richer API via web socket",
        replaceWith = ReplaceWith(
            expression = "webSocket().newPage().goto(url)",
            imports = [
                "org.hildan.chrome.devtools.sessions.newPage",
                "org.hildan.chrome.devtools.sessions.goto",
            ],
        ),
    )
    suspend fun newTab(url: String = "about:blank"): ChromeDPTarget =
        httpClient.put("$remoteDebugUrl/json/new?$url").body<ChromeDPTarget>().fixHost()

    /**
     * Brings the page identified by the given [targetId] into the foreground (activates a tab).
     */
    suspend fun activateTab(targetId: String): String = httpClient.get("$remoteDebugUrl/json/activate/$targetId").body()

    /**
     * Closes the page identified by [targetId].
     */
    suspend fun closeTab(targetId: String): String = httpClient.get("$remoteDebugUrl/json/close/$targetId").body()

    /**
     * Closes all targets.
     */
    suspend fun closeAllTargets() {
        targets().forEach {
            closeTab(it.id)
        }
    }

    /**
     * Opens a web socket connection to interact with the browser.
     *
     * This method attaches to the default browser target, which creates a root session without session ID.
     * The returned [BrowserSession] thus only provides a limited subset of the possible operations (only the ones
     * applicable to the browser itself). Refer to the documentation of [BrowserSession] to see how to use it to
     * attach to (and interact with) more specific targets.
     *
     * Child sessions of returned `BrowserSession` use the same underlying web socket connection as the initial browser
     * session returned here.
     *
     * Note that the caller of this method is responsible for closing the web socket after use by calling
     * [BrowserSession.close], or using the auto-close capabilities via [BrowserSession.use].
     * Calling [ChildSession.close] or [ChildSession.use] on a derived session doesn't close the underlying web socket
     * connection, to avoid undesirable interactions between child sessions.
     */
    suspend fun webSocket(): BrowserSession {
        val browserDebuggerUrl = version().webSocketDebuggerUrl
        return httpClient.chromeWebSocket(browserDebuggerUrl)
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
    /**
     * The web socket URL to use to attach to the browser target.
     * It is sort of the "root" target that can then be used to connect to pages and other types of targets.
     *
     * The URL contains a unique ID for the browser target, such as:
     * `ws://localhost:9222/devtools/browser/b0b8a4fb-bb17-4359-9533-a8d9f3908bd8`
     */
    @SerialName("webSocketDebuggerUrl") val webSocketDebuggerUrl: String,
)

/**
 * Targets are the parts of the browser that the Chrome DevTools Protocol can interact with.
 * This includes pages, service workers, extensions, and also the browser itself.
 *
 * When a client wants to interact with a target using CDP, it has to first attach to the target.
 * One way to do it is to connect to Chrome via web socket using [ChromeDPClient.webSocket] and then
 * using [BrowserSession.attachToTarget].
 *
 * However, most of the time, targets don't already exist, so it's easier to just create a new page
 * using [BrowserSession.newPage] and then interact with it through the returned [PageSession].
 */
@Serializable
data class ChromeDPTarget(
    val id: String,
    val title: String,
    val type: String,
    val description: String,
    val url: String,
    val devtoolsFrontendUrl: String,
    /**
     * The web socket URL to use with [HttpClient.chromeWebSocket] to connect via the debugger to this target.
     */
    val webSocketDebuggerUrl: String,
    val faviconUrl: String? = null,
)

/**
 * Connects to the Chrome debugger at the given [webSocketDebuggerUrl].
 *
 * This function expects a *web socket* URL (not HTTP). It should be something like:
 * ```
 * ws://localhost:9222/devtools/browser/b0b8a4fb-bb17-4359-9533-a8d9f3908bd8
 * ```
 * If you only have the debugger's HTTP URL at hand (e.g. `http://localhost:9222`), create a [ChromeDPClient] instead,
 * and then connect to the web socket using [ChromeDPClient.webSocket].
 *
 * This [HttpClient] must have the [WebSockets] plugin installed.
 *
 * The returned [BrowserSession] only provides a limited subset of the possible operations, because it is
 * attached to the default *browser* target, not a *page* target.
 * To create a new page/tab, use [BrowserSession.newPage] and then interact with it through the returned [PageSession].
 */
suspend fun HttpClient.chromeWebSocket(webSocketDebuggerUrl: String): BrowserSession =
    webSocketSession(webSocketDebuggerUrl).chromeDp().withSession(sessionId = null).asBrowserSession()
