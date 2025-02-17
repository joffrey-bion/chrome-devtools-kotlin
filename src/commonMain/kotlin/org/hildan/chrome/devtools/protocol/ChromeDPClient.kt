package org.hildan.chrome.devtools.protocol

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.hildan.chrome.devtools.*
import org.hildan.chrome.devtools.chromeWebSocket
import org.hildan.chrome.devtools.sessions.*

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
 * Chrome doesn't accept a `Host` header that is not an IP nor `localhost`, but in some environments the URL has to
 * have a named host (e.g. docker services in a docker swarm, communicating using service names).
 *
 * Enabling [overrideHostHeader] works around this problem by setting the `Host` header to "localhost" in the HTTP
 * requests to the Chrome debugger. It also restores the original host in URLs that are returned by Chrome
 * (Chrome uses the `Host` header to build the URLs that it returns, so they would contain `localhost` otherwise).
 */
@OptIn(LegacyChromeTargetHttpApi::class) // for backwards compatibility
@Deprecated(
    message = "The entrypoint API has be reworked to favor the web socket connection approach. " +
        "Use ChromeDP.connect() instead to connect to the debugger. " +
        "Please check the docs and the KDoc of ChromeDP for more info. " +
        "Moreover, the ChromeDPClient concrete type shouldn't be referenced as-is anymore, and will be hidden or " +
        "removed in a future version. If you still need to access similar HTTP API methods, please use the " +
        "ChromeDPHttpApi interface instead.",
    replaceWith = ReplaceWith("ChromeDPHttpApi", "org.hildan.chrome.devtools.protocol.ChromeDPHttpApi"),
    level = DeprecationLevel.WARNING,
)
class ChromeDPClient
@Deprecated(
    message = "The entrypoint API has be reworked to favor the web socket connection approach. " +
        "Use ChromeDP.connect() instead to connect to the debugger. " +
        "Please check the docs and the KDoc of ChromeDP for more info. " +
        "Moreover, the ChromeDPClient concrete type shouldn't be referenced as-is anymore, and will be hidden or " +
        "removed in a future version. If you still need to access similar HTTP API methods, please use the " +
        "ChromeDPHttpApi interface instead.",
    replaceWith = ReplaceWith(
        expression = "ChromeDP.httpApi(remoteDebugUrl, overrideHostHeader)",
        imports = [ "org.hildan.chrome.devtools.ChromeDP" ],
    ),
    level = DeprecationLevel.WARNING,
)
constructor(
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
    private val httpClient: HttpClient = ChromeDP.defaultHttpClient,
) : ChromeDPHttpApi {
    init {
        require(remoteDebugUrl.startsWith("http://") || remoteDebugUrl.startsWith("https://")) {
            "This function is meant to be used with 'http://' or 'https://' URLs, but got $remoteDebugUrl. " +
                "If you already have a web socket URL, use ChromeDP.connect(wsUrl) directly instead."
        }
    }

    override suspend fun version(): ChromeVersion = httpGet("/json/version").body<ChromeVersion>().fixHost()

    override suspend fun protocolJson(): String = httpGet("/json/protocol").bodyAsText()

    override suspend fun targets(): List<ChromeDPTarget> = httpGet("/json/list").body<List<ChromeDPTarget>>().map { it.fixHost() }

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
    override suspend fun newTab(url: String): ChromeDPTarget {
        // The /json/new endpoint takes a target URL instead of the query, it's not a real query parameter.
        // The hack that follows allows to append this weird parameter after a potentially existing query.
        val encodedTargetUrl = url.encodeURLParameter(spaceToPlus = false)
        val urlWithEndpoint = URLBuilder(remoteDebugUrl).apply { encodedPath += "/json/new" }.build()
        val urlStr = urlWithEndpoint.toString()
        val jsonNewUrl =  if (urlWithEndpoint.encodedQuery.isEmpty()) {
            "${urlStr}?${encodedTargetUrl}"
        } else {
            "${urlStr}&${encodedTargetUrl}"
        }
        return httpClient.put(jsonNewUrl) {
            if (overrideHostHeader) {
                headers["Host"] = "localhost"
            }
        }.body<ChromeDPTarget>().fixHost()
    }

    override suspend fun activateTab(targetId: String): String = httpGet("/json/activate/$targetId").body()

    override suspend fun closeTab(targetId: String): String = httpGet("/json/close/$targetId").body()

    override suspend fun webSocket(): BrowserSession {
        val browserDebuggerUrl = version().webSocketDebuggerUrl
        return httpClient.chromeWebSocket(browserDebuggerUrl)
    }

    private suspend fun httpGet(endpoint: String): HttpResponse = httpClient.get {
        url.takeFrom(remoteDebugUrl)
        url.encodedPath += endpoint
        if (overrideHostHeader) {
            headers["Host"] = "localhost"
        }
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
 * Connects to the Chrome debugger at the given [webSocketDebuggerUrl].
 *
 * Note that `this` [HttpClient] must have the [WebSockets] plugin installed.
 *
 * This function expects a *web socket* URL (not HTTP). It should be something like:
 * ```
 * ws://localhost:9222/devtools/browser/b0b8a4fb-bb17-4359-9533-a8d9f3908bd8
 * ```
 * If you're using services like [Browserless's Docker image](https://docs.browserless.io/baas/docker/quickstart),
 * you might have a simpler URL like `ws://localhost:3000` or `ws://localhost:3000?token=6R0W53R135510`.
 *
 * If you only have the debugger's HTTP URL at hand (e.g. `http://localhost:9222`), create a [ChromeDPHttpApi] instead,
 * and then connect to the web socket using [ChromeDPClient.webSocket].
 *
 * The returned [BrowserSession] only provides a limited subset of the possible operations, because it is
 * attached to the default *browser* target, not a *page* target.
 * To create a new page (tab), use [newPage] and then interact with it through the returned [PageSession].
 * Refer to the documentation of [BrowserSession] for more info.
 *
 * The caller of this method is responsible for closing the web socket after use by calling [BrowserSession.close],
 * or using the auto-close capabilities via [use].
 * Because all child sessions of the returned [BrowserSession] use the same underlying web socket connection,
 * calling [ChildSession.close] or [use] on a derived session doesn't close the connection (to avoid undesirable
 * interactions between child sessions).
 *
 * @see ChromeDP
 * @see connectChromeDebugger
 */
@Deprecated(
    message = "The entrypoint API has been reworked to simplify the direct web socket connection approach. " +
        "Use ChromeDP.connect() instead to connect to the debugger. " +
        "Please check the docs and the KDoc of ChromeDP for more info. " +
        "This extension will be removed in a future version of chrome-devtools-kotlin. " +
        "If you need a custom HttpClient, use HttpClient.connectChromeDebugger(url) instead.",
    replaceWith = ReplaceWith("this.connectChromeDebugger(webSocketDebuggerUrl)", "org.hildan.chrome.devtools.connectChromeDebugger"),
    level = DeprecationLevel.WARNING,
)
suspend fun HttpClient.chromeWebSocket(webSocketDebuggerUrl: String): BrowserSession =
    webSocketSession(webSocketDebuggerUrl).chromeDp().withSession(sessionId = null).asBrowserSession()
