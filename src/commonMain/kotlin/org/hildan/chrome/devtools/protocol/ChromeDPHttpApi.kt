package org.hildan.chrome.devtools.protocol

import kotlinx.serialization.*
import org.hildan.chrome.devtools.*
import org.hildan.chrome.devtools.sessions.*

@RequiresOptIn(
    message = "This HTTP JSON endpoint is superseded by the richer web socket API, and might not be supported by " +
        "every headless browser. Consider using the web socket instead."
)
annotation class LegacyChromeTargetHttpApi

/**
 * An API to interact with Chrome's [JSON HTTP endpoints](https://chromedevtools.github.io/devtools-protocol/#endpoints).
 *
 * Use [ChromeDP.httpApi] to get an instance of this API.
 *
 * These endpoints should mostly be used to query metadata about the protocol.
 * Prefer the richer web socket API to interact with the browser using the Chrome DevTools Protocol.
 */
interface ChromeDPHttpApi {

    /**
     * Fetches the browser version metadata via the debugger's HTTP API.
     */
    suspend fun version(): ChromeVersion

    /**
     * Fetches the current Chrome DevTools Protocol definition, as a JSON string.
     */
    suspend fun protocolJson(): String

    /**
     * Fetches the list of all available web socket targets (e.g. browser tabs).
     */
    @LegacyChromeTargetHttpApi
    suspend fun targets(): List<ChromeDPTarget>

    /**
     * Opens a new tab, and returns the websocket target data for the new tab.
     */
    @LegacyChromeTargetHttpApi
    suspend fun newTab(url: String = "about:blank"): ChromeDPTarget

    /**
     * Brings the page identified by the given [targetId] into the foreground (activates a tab).
     */
    @LegacyChromeTargetHttpApi
    suspend fun activateTab(targetId: String): String

    /**
     * Closes the page identified by [targetId].
     */
    @LegacyChromeTargetHttpApi
    suspend fun closeTab(targetId: String): String

    /**
     * Closes all targets.
     */
    @LegacyChromeTargetHttpApi
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
    suspend fun webSocket(): BrowserSession
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
     * The web socket URL to use with [chromeWebSocket] to connect via the debugger to this target.
     */
    val webSocketDebuggerUrl: String,
    val faviconUrl: String? = null,
)
