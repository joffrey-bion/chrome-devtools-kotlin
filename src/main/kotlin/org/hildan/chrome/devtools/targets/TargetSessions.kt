package org.hildan.chrome.devtools.targets

import org.hildan.chrome.devtools.domains.browser.BrowserContextID
import org.hildan.chrome.devtools.domains.target.*
import org.hildan.chrome.devtools.protocol.ChromeDPSession
import org.hildan.chrome.devtools.protocol.ExperimentalChromeApi

/**
 * A protocol session, created when attached to a target.
 */
sealed class AbstractTargetSession(
    internal val session: ChromeDPSession,
    internal open val targetImplementation: SimpleTarget = SimpleTarget(session),
) {
    /**
     * Gives access to all domains of the protocol, regardless of the type of this session.
     *
     * This should only be used as a workaround when a domain is missing in the current typed API, but is known to be
     * available.
     *
     * If you need to use this method, please also open an issue so that the properly typed API is added:
     * https://github.com/joffrey-bion/chrome-devtools-kotlin/issues
     */
    fun unsafe(): AllDomainsTarget = targetImplementation

    /**
     * Closes the underlying web socket connection, effectively closing every session based on the same web socket
     * connection.
     */
    suspend fun closeWebSocket() {
        session.close()
    }
}

/**
 * A browser session, usually created when initially connecting to the browser's debugger.
 */
class ChromeBrowserSession internal constructor(
    session: ChromeDPSession,
    override val targetImplementation: SimpleTarget = SimpleTarget(session),
) : AbstractTargetSession(session), BrowserTarget by targetImplementation {

    /**
     * Closes this session and the underlying web socket connection.
     * This effectively closes every session based on the same web socket connection.
     */
    suspend fun close() {
        closeWebSocket()
    }
}

@OptIn(ExperimentalChromeApi::class)
data class ChromePageMetaData(
    val targetId: TargetID,
    val browserContextId: BrowserContextID? = null
)

/**
 * A page session, usually created when attaching to a page from the root browser session.
 */
class ChromePageSession internal constructor(
    session: ChromeDPSession,
    /**
     * The parent session which created this page target.
     *
     * This is described in the
     * [session hierarchy section](https://github.com/aslushnikov/getting-started-with-cdp/blob/master/README.md#session-hierarchy)
     * in the "getting started" guide.
     */
    val parent: ChromeBrowserSession,
    /**
     * Info about the underlying page target.
     */
    val metaData: ChromePageMetaData,
    override val targetImplementation: SimpleTarget = SimpleTarget(session),
) : AbstractTargetSession(session), RenderFrameTarget by targetImplementation {

    /**
     * Detaches from this page session, leaving the tab open.
     *
     * This preserves the underlying web socket connection (of the parent browser session), because it could be used
     * by other page sessions.
     */
    @OptIn(ExperimentalChromeApi::class)
    suspend fun detach() {
        parent.target.detachFromTarget(DetachFromTargetRequest(sessionId = session.sessionId))
    }

    /**
     * Closes this page session.
     *
     * This only closes the corresponding tab, but preserves the underlying web socket connection (of the parent
     * browser session), because it could be used by other page sessions.
     *
     * If [keepBrowserContext] is true, the browser context of this page session will be preserved, which means
     * that other tabs that were opened from this page session will not be force-closed.
     */
    @OptIn(ExperimentalChromeApi::class)
    suspend fun close(keepBrowserContext: Boolean = false) {
        parent.target.closeTarget(CloseTargetRequest(targetId = metaData.targetId))

        if (!keepBrowserContext && !metaData.browserContextId.isNullOrEmpty()) {
            parent.target.disposeBrowserContext(DisposeBrowserContextRequest(metaData.browserContextId))
        }
    }
}
