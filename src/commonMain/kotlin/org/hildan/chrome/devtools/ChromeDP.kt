package org.hildan.chrome.devtools

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import org.hildan.chrome.devtools.protocol.*
import org.hildan.chrome.devtools.sessions.*
import kotlin.coroutines.*

/**
 * This is the entry point of the interactions with a Chrome browser.
 *
 * First, make sure a Chrome browser is already running externally.
 * You should then have either an HTTP or a web socket URL to connect to the debugger.
 * Use [connect] to connect to the remote debugger and get a [BrowserSession].
 * Check out the KDoc of these declarations for more info.
 *
 * If you instead want to use the [HTTP JSON endpoints](https://chromedevtools.github.io/devtools-protocol/#endpoints)
 * to query metadata about the browser, use the [httpApi] method.
 * Note that it is not recommended to use the returned [ChromeDPHttpApi] to manage targets, because not all headless
 * browsers support these endpoints (and they are superseded by the web socket API).
 *
 * @see connectChromeDebugger
 */
object ChromeDP {

    internal val defaultHttpClient by lazy {
        HttpClient {
            expectSuccess = true

            install(UserAgent) {
                agent = "Chrome DevTools Kotlin"
            }

            install(WebSockets)

            // for HTTP /json/* endpoints
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    /**
     * Connects to the Chrome debugger at the given [wsOrHttpUrl] (either web socket or HTTP),
     * opening a [BrowserSession].
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
     * @param wsOrHttpUrl the HTTP or web socket URL of the Chrome debugger.
     *
     * @param sessionContext a custom [CoroutineContext] for the coroutines used in the Chrome session to process events
     *
     * @param configureClient Adds extra configuration to the default [HttpClient] used to connect to the debugger's
     *                        web socket. If you need to reuse an existing [HttpClient] entirely, use the
     *                        `HttpClient.connectChromeDebugger` extension instead.
     */
    suspend fun connect(
        wsOrHttpUrl: String,
        sessionContext: CoroutineContext = EmptyCoroutineContext,
        configureClient: (HttpClientConfig<*>.() -> Unit)? = null,
    ): BrowserSession {
        val httpClient = if (configureClient == null) {
            defaultHttpClient
        } else {
            defaultHttpClient.config(configureClient)
        }
        return httpClient.connectChromeDebugger(wsOrHttpUrl, sessionContext)
    }

    /**
     * Creates a client that uses Chrome's [JSON HTTP endpoints](https://chromedevtools.github.io/devtools-protocol/#endpoints)
     * to query metadata about the browser or the protocol, and to list, create and close targets (e.g. tabs).
     *
     * Avoid using the returned [ChromeDPHttpApi] to manage targets (list/open/close tabs).
     * Not all headless browsers support these endpoints, and they are superseded by the web socket API.
     * Prefer the web socket API by using [connect] instead.
     *
     * @param httpUrl the remote debugger's HTTP URL (not web socket).
     *      It should be something like `http://localhost:9222`.
     *
     * @param overrideHostHeader Overrides the `Host` header in HTTP requests to `localhost`, and restores the original
     *      host in the URLs returned by Chrome's endpoints.
     *      Chrome doesn't accept a `Host` header that is not an IP nor `localhost`, but in some environments the URL
     *      has to have a named host (e.g. docker services in a docker swarm, communicating using service names).
     *
     * @param configureClient Adds extra configuration to the default [HttpClient][io.ktor.client.HttpClient] used to
     *      interact with the Chrome HTTP API.
     */
    fun httpApi(
        httpUrl: String,
        overrideHostHeader: Boolean = false,
        configureClient: HttpClientConfig<*>.() -> Unit,
    ): ChromeDPHttpApi = httpApi(httpUrl, overrideHostHeader, defaultHttpClient.config(configureClient))

    /**
     * Creates a client that uses Chrome's [JSON HTTP endpoints](https://chromedevtools.github.io/devtools-protocol/#endpoints)
     * to query metadata about the browser or the protocol, and to list, create and close targets (e.g. tabs).
     *
     * Avoid using the returned [ChromeDPHttpApi] to manage targets (list/open/close tabs).
     * Not all headless browsers support these endpoints, and they are superseded by the web socket API.
     * Prefer the web socket API by using [connect] instead.
     *
     * @param httpUrl the remote debugger's HTTP URL (not web socket).
     *                It should be something like `http://localhost:9222`.
     *
     * @param overrideHostHeader Overrides the `Host` header in HTTP requests to `localhost`, and restores the original
     *      host in the URLs returned by Chrome's endpoints.
     *      Chrome doesn't accept a `Host` header that is not an IP nor `localhost`, but in some environments the URL
     *      has to have a named host (e.g. docker services in a docker swarm, communicating using service names).
     *
     * @param httpClient the Ktor client to use to interact with the Chrome HTTP API.
     */
    fun httpApi(
        httpUrl: String,
        overrideHostHeader: Boolean = false,
        httpClient: HttpClient = defaultHttpClient,
    ): ChromeDPHttpApi {
        require(httpUrl.startsWith("http://") || httpUrl.startsWith("https://")) {
            if (httpUrl.startsWith("ws://") || httpUrl.startsWith("wss://")) {
                "The HTTP API cannot be used with a web socket URL (got $httpUrl), use an http:// or https:// URL " +
                    "instead. Alternatively, pass the web socket URL to ChromeDP.connect() to interact with the " +
                    "browser via the web socket directly using the Chrome DevTools Protocol."
            } else {
                "The HTTP API requires 'http://' or 'https://' URLs, but got $httpUrl."
            }
        }
        @Suppress("DEPRECATION_ERROR") // TODO remove this suppress when the ChromeDPClient class is made internal
        return ChromeDPClient(
            remoteDebugUrl = httpUrl,
            overrideHostHeader = overrideHostHeader,
            httpClient = httpClient,
        )
    }
}
