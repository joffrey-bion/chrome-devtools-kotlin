package org.hildan.chrome.devtools.targets

import org.hildan.chrome.devtools.domains.target.CloseTargetRequest
import org.hildan.chrome.devtools.domains.target.DisposeBrowserContextRequest
import org.hildan.chrome.devtools.domains.target.TargetInfo
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
    val targetInfo: TargetInfo,
    override val targetImplementation: SimpleTarget = SimpleTarget(session),
) : AbstractTargetSession(session), RenderFrameTarget by targetImplementation {

    /**
     * Closes this page session.
     *
     * This only closes the corresponding tab, but preserves the underlying web socket connection (of the parent
     * browser session), because it could be used by other page sessions.
     */
    @OptIn(ExperimentalChromeApi::class)
    suspend fun close() {
        parent.target.closeTarget(CloseTargetRequest(targetId = targetInfo.targetId))

        // FIXME do we really need this given the "disposeOnDetach=true" used at creation?
        if (!targetInfo.browserContextId.isNullOrEmpty()) {
            parent.target.disposeBrowserContext(DisposeBrowserContextRequest(targetInfo.browserContextId))
        }
    }
}
