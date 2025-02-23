package org.hildan.chrome.devtools

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import org.hildan.chrome.devtools.protocol.*
import org.hildan.chrome.devtools.sessions.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Connects to the Chrome debugger at the given [wsOrHttpUrl] (either web socket or HTTP), opening a [BrowserSession].
 *
 * Note that `this` [HttpClient] must have the [WebSockets] plugin installed, even if [wsOrHttpUrl] is an HTTP URL.
 *
 * * if [wsOrHttpUrl] is a `ws://` or `wss://` URL, this function directly connects to the browser target via the web socket.
 *   It should be something like `ws://localhost:9222/devtools/browser/b0b8a4fb-bb17-4359-9533-a8d9f3908bd8`.
 *   If you're using services like [Browserless's Docker image](https://docs.browserless.io/baas/docker/quickstart),
 *   you might have a simpler URL like `ws://localhost:3000` or `ws://localhost:3000?token=6R0W53R135510`.
 *
 * * if [wsOrHttpUrl] is an `http://` or `https://` URL, this function finds the web socket debugger URL via
 *   the HTTP API, and then uses it to open the web socket to the Chrome debugger.
 *
 * If you have access to the web socket URL, it is preferable to use that one to avoid the extra hop.
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
 * @param wsOrHttpUrl the web socket URL to use to connect, or the HTTP URL to use to find the web socket URL
 * @param sessionContext a custom [CoroutineContext] for the coroutines used in the Chrome session to process events
 */
suspend fun HttpClient.connectChromeDebugger(
    wsOrHttpUrl: String,
    sessionContext: CoroutineContext = EmptyCoroutineContext,
): BrowserSession {
    val wsUrl = when {
        wsOrHttpUrl.startsWith("ws://") || wsOrHttpUrl.startsWith("wss://") -> wsOrHttpUrl
        wsOrHttpUrl.startsWith("http://") || wsOrHttpUrl.startsWith("https://") -> {
            ChromeDP.httpApi(wsOrHttpUrl, httpClient = this).version().webSocketDebuggerUrl
        }
        else -> throw IllegalArgumentException("Unsupported URL scheme in $wsOrHttpUrl (please use ws, wss, http, or https)")
    }
    return chromeWebSocket(wsUrl, sessionContext)
}

/**
 * Connects to the Chrome debugger at the given [webSocketDebuggerUrl].
 *
 * Note that `this` [HttpClient] must have the [WebSockets] plugin installed.
 *
 * This function expects a *web socket* URL (not HTTP). It should be something like
 * ```
 * ws://localhost:9222/devtools/browser/b0b8a4fb-bb17-4359-9533-a8d9f3908bd8
 * ```
 * If you're using services like [Browserless's Docker image](https://docs.browserless.io/baas/docker/quickstart),
 * you might have a simpler URL like `ws://localhost:3000` or `ws://localhost:3000?token=6R0W53R135510`.
 *
 * If you only have the debugger's HTTP URL at hand (e.g. `http://localhost:9222`), use [ChromeDP.httpApi] instead,
 * and then connect to the web socket using [ChromeDPHttpApi.webSocket].
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
 */
internal suspend fun HttpClient.chromeWebSocket(
    webSocketDebuggerUrl: String,
    sessionContext: CoroutineContext = EmptyCoroutineContext,
): BrowserSession {
    require(webSocketDebuggerUrl.startsWith("ws://") || webSocketDebuggerUrl.startsWith("wss://")) {
        "The web socket API requires a 'ws://' or 'wss://' URL, but got $webSocketDebuggerUrl."
    }
    val webSocketSession = webSocketSession(webSocketDebuggerUrl)
    return try {
        webSocketSession.chromeDp(sessionContext).withSession(sessionId = null).asBrowserSession()
    } catch (e: Exception) {
        // the caller won't have the opportunity to clean this up if any of these conversion/wrapping operations fails
        webSocketSession.close()
        throw e
    }
}
