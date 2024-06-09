package org.hildan.chrome.devtools.sessions

import org.hildan.chrome.devtools.domains.target.*
import org.hildan.chrome.devtools.targets.*

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
 * Such root session doesn't have the most useful APIs because it represents the connection to the browser target.
 *
 * To interact with more interesting targets, such as pages and workers, you need to first attach to that target, which
 * creates a child session of this session, which offers more APIs:
 *
 * * To create a new page/tab, use [BrowserSession.newPage] and then interact with it through the returned
 * [PageSession], which is a child session of the browser session and has many more APIs.
 *
 * * If you want to attach to an already existing target, use the [BrowserSession.target] domain to get information
 * about the target, and then use [BrowserSession.attachToTarget]. Note that you will only get a plain [ChildSession]
 * after this. Use one of the converters to convert this session to the proper type depending on the target type you
 * want to interact with. For example, if you're attaching to a worker, use [ChildSession.asWorkerSession] to get a
 * [WorkerSession], which offers the relevant domains for you.
 *
 */
interface BrowserSession : ChromeSession, BrowserTarget {

    /**
     * Creates a new [ChildSession] attached to the target with the given [targetId].
     * The new session shares the same underlying web socket connection as this [BrowserSession].
     *
     * Note that a [ChildSession] is a generic session that doesn't carry information about the exact target type, and
     * thus doesn't offer very useful APIs. It is meant to be converted to a more specific session type using one of the
     * converter extensions (`ChildSession.as*()`).
     *
     * For example, if you're attaching to a worker, use [ChildSession.asWorkerSession] to get a [WorkerSession],
     * which offers the relevant domains for you.
     *
     * **Note:** if you want to create a NEW page (or tab) and attach to it, use [newPage] instead. It will save you
     * the trouble of creating the target, handling its browser context, and dealing with the return type.
     *
     * @see newPage
     */
    suspend fun attachToTarget(targetId: TargetID): ChildSession

    /**
     * Closes this session and the underlying web socket connection.
     * This effectively **closes all child sessions**, because they're based on the same web socket connection.
     */
    suspend fun close()
}

/**
 * Performs the given operation in this session and closes the web socket connection.
 *
 * Note: This effectively closes all child sessions, because they're based on the same web socket connection.
 */
suspend inline fun <T> BrowserSession.use(block: (BrowserSession) -> T): T = use(block) { close() }

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
    val parent: BrowserSession

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
 * Performs the given operation in this [ChildSession] and closes this session and its children, as well as the
 * corresponding targets.
 *
 * This preserves the underlying web socket connection (of the parent [BrowserSession]), because it could be used by
 * other sessions that are children of the same browser session, but not children of this [ChildSession].
 *
 * If you don't want to close child targets created during this session, use [ChildSession.close] with
 * `keepBrowserContext=true` instead of this helper.
 */
suspend inline fun <S : ChildSession, T> S.use(block: (S) -> T): T = use(block) { close() }

@PublishedApi
internal inline fun <R, T> R.use(block: (R) -> T, close: R.() -> Unit): T {
    var userFailure: Throwable? = null
    try {
        return block(this)
    } catch (t: Throwable) {
        userFailure = t
        throw t
    } finally {
        try {
            close()
        } catch (t: Throwable) {
            // Errors in close() shouldn't hide errors from the user's block, so we throw the user error instead,
            // and add the close error as suppressed.
            if (userFailure != null) {
                userFailure.addSuppressed(t)
                throw userFailure
            }
            throw t
        }
    }
}
