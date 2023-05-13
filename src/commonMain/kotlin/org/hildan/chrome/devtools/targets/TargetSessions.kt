package org.hildan.chrome.devtools.targets

import org.hildan.chrome.devtools.domains.target.*

/**
 * A Chrome DevTools debugging session, created when attaching to a target.
 */
interface ChromeSession {

    /**
     * Gives access to all domains of the protocol, regardless of the type of this session.
     *
     * This should only be used as a workaround when a domain is missing in the current typed API, but is known to be
     * available.
     *
     * If you need to use this method, please also open an issue so that the properly typed API is added:
     * https://github.com/joffrey-bion/chrome-devtools-kotlin/issues
     */
    fun unsafe(): AllDomainsTarget

    /**
     * Closes the underlying web socket connection, effectively closing every session based on the same web socket
     * connection.
     */
    suspend fun closeWebSocket()
}

/**
 * A browser session. This is the root session created when initially connecting to the browser's debugger.
 *
 * The root browser session doesn't have a session ID.
 */
interface ChromeBrowserSession : ChromeSession, BrowserTarget {

    /**
     * Creates a new [ChildSession] attached to the target with the given [targetId].
     * The new session shares the same underlying web socket connection as this [ChromeBrowserSession].
     */
    suspend fun attachToTarget(targetId: TargetID): ChildSession

    /**
     * Closes this session and the underlying web socket connection.
     * This effectively closes every session based on the same web socket connection.
     */
    suspend fun close()
}

/**
 * Info about a session and its underlying target.
 */
interface SessionMetaData {

    /**
     * The ID of this session.
     */
    val sessionId: SessionID

    /**
     * The ID of the attached target.
     */
    val targetId: TargetID

    /**
     * The type of the attached target.
     */
    val targetType: String
}

/**
 * A target session that is a child of a browser session, usually created when attaching to a target from the root
 * browser session.
 */
interface ChildSession : ChromeSession {

    /**
     * The parent browser session that created this target session.
     *
     * This is described in the
     * [session hierarchy section](https://github.com/aslushnikov/getting-started-with-cdp/blob/master/README.md#session-hierarchy)
     * in the "getting started" guide.
     */
    val parent: ChromeBrowserSession

    /**
     * Info about this session and its underlying target.
     */
    val metaData: SessionMetaData

    /**
     * Detaches this session from its target, effectively closing this session and all its child sessions, but without
     * closing the corresponding target (this leaves the tab open in the case of a page session).
     *
     * This preserves the underlying web socket connection (of the parent browser session), because it could be used
     * by other page sessions.
     */
    suspend fun detach()

    /**
     * Closes this session and its target. If the target is a page, the tab gets closed.
     *
     * This only closes the corresponding tab, but preserves the underlying web socket connection (of the parent
     * browser session), because it could be used by other page sessions.
     *
     * If [keepBrowserContext] is true, the browser context of this page session will be preserved, which means
     * that other tabs that were opened from this page session will not be force-closed.
     */
    suspend fun close(keepBrowserContext: Boolean = false)
}

/**
 * A page session, usually created when attaching to a page from the root browser session.
 */
interface ChromePageSession : ChildSession, RenderFrameTarget
