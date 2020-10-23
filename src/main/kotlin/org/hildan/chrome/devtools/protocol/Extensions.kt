package org.hildan.chrome.devtools.protocol

import org.hildan.chrome.devtools.ExperimentalChromeApi
import org.hildan.chrome.devtools.domains.browser.BrowserContextID
import org.hildan.chrome.devtools.domains.target.*

/**
 * Creates a new [ChromeTargetSession] attached to the target with the given [targetId].
 * The new session shares the same underlying web socket connection as this [ChromeBrowserSession].
 */
@OptIn(ExperimentalChromeApi::class)
public suspend fun ChromeBrowserSession.attachTo(
    targetId: TargetID,
    browserContextID: BrowserContextID? = null
): ChromeTargetSession {
    val sessionId = target.attachToTarget(AttachToTargetRequest(targetId = targetId, flatten = true)).sessionId
    return ChromeTargetSession(connection, sessionId, this, targetId, browserContextID)
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
    background: Boolean = false,
): ChromeTargetSession {
    val browserContextId = when (incognito) {
        true -> target.createBrowserContext(CreateBrowserContextRequest(disposeOnDetach = background)).browserContextId
        false -> null
    }

    val targetId = target.createTarget(
        CreateTargetRequest(
            url = url,
            browserContextId = browserContextId,
            height = height,
            width = width,
            background = background,
        )
    ).targetId

    return attachTo(targetId, browserContextId)
}

@OptIn(ExperimentalChromeApi::class)
private suspend fun ChromeTargetSession.closeTarget() {
    println("Closing target ${targetId}... ")
    parent.target.closeTarget(CloseTargetRequest(targetId = targetId))
    println("Done.")

    // FIXME do we really need this given the "disposeOnDetach=true" used at creation?
    if (!browserContextId.isNullOrEmpty()) {
        println("Disposing browser context ${browserContextId}... ")
        parent.target.disposeBrowserContext(DisposeBrowserContextRequest(browserContextId))
        println("Done.")
    }
}
