package org.hildan.chrome.devtools.protocol

import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import org.hildan.chrome.devtools.ChromeBrowserSession
import org.hildan.chrome.devtools.ChromeTargetSession
import org.hildan.chrome.devtools.ExperimentalChromeApi
import org.hildan.chrome.devtools.domains.browser.BrowserContextID
import org.hildan.chrome.devtools.domains.target.*

/**
 * Creates a new [ChromeTargetSession] attached to the target with the given [targetId].
 * The new session shares the same underlying web socket connection as this [ChromeBrowserSession].
 */
@OptIn(ExperimentalChromeApi::class)
public suspend fun ChromeBrowserSession.attachTo(targetId: TargetID, browserContextID: BrowserContextID? = null): ChromeTargetSession {
    println("Attaching to target $targetId")
    val attachedEvents = target.attachedToTarget()
    val sessionId = target.attachToTarget(AttachToTargetRequest(targetId = targetId, flatten = true)).sessionId
    println("Attached to target $targetId, session = $sessionId")
    val event = attachedEvents.filter { it.sessionId == sessionId }.first()
    println("Received attached event to target $targetId, session = $sessionId: $event")
    return ChromeTargetSession(ChromeDPSession(session.connection, sessionId, targetId, browserContextID))
}

/**
 * Creates and attaches to a new target with given [url] and viewport [width] and [height].
 * This action doesn't affect this [ChromeBrowserSession]; instead, it creates a [ChromeTargetSession].
 * The underlying web socket connection is reused for both the old and new sessions.
 *
 * If [incognito] is true, than new target is created in separate browser context (think of it as incognito window).
 */
@OptIn(ExperimentalChromeApi::class)
public suspend fun ChromeBrowserSession.attachToNewTarget(
    url: String,
    incognito: Boolean = true,
    width: Int = 1024,
    height: Int = 768,
): ChromeTargetSession {
    val browserContextId = when (incognito) {
        true -> target.createBrowserContext(CreateBrowserContextRequest(disposeOnDetach = true)).browserContextId
        false -> null
    }

    val targetId = target.createTarget(
        CreateTargetRequest(
            url = url,
            browserContextId = browserContextId,
            height = height,
            width = width,
            background = true,
        )
    ).targetId

    return attachTo(targetId, browserContextId)
}

public suspend fun ChromeBrowserSession.close() {
    closeTarget()
    print("Closing connection... ")
    session.close()
    println("Done.")
}

@OptIn(ExperimentalChromeApi::class)
private suspend fun ChromeBrowserSession.closeTarget() {
    if (session.targetId != null) {
        println("Closing target ${session.targetId}... ")
        target.closeTarget(CloseTargetRequest(targetId = session.targetId))
        println("Done.")
    }
    // FIXME do we really need this given the "disposeOnDetach=true" used at creation?
    val browserContextID = session.browserContextId
    if (!browserContextID.isNullOrEmpty()) {
        println("Disposing browser context ${session.browserContextId}... ")
        target.disposeBrowserContext(DisposeBrowserContextRequest(browserContextID))
        println("Done.")
    }
}
