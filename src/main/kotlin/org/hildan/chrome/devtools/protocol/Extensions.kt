package org.hildan.chrome.devtools.protocol

import kotlinx.coroutines.flow.first
import org.hildan.chrome.devtools.ChromeApi
import org.hildan.chrome.devtools.ExperimentalChromeApi
import org.hildan.chrome.devtools.domains.browser.BrowserContextID
import org.hildan.chrome.devtools.domains.target.*
import org.hildan.chrome.devtools.domains.target.events.TargetEvent

/**
 * Creates a new [ChromeApi] instance attached to the target with the given [targetId].
 * The new [ChromeApi] shares the same underlying web socket connection.
 */
@OptIn(ExperimentalChromeApi::class)
public suspend fun ChromeApi.attachTo(targetId: TargetID, browserContextID: BrowserContextID? = null): ChromeApi {
    val sessionId = target.attachToTarget(AttachToTargetRequest(targetId = targetId, flatten = true)).sessionId
    return ChromeDPSession(session.connection, sessionId, targetId, browserContextID).api()
}

@OptIn(ExperimentalChromeApi::class)
public suspend fun ChromeApi.detach(): TargetEvent.DetachedFromTargetEvent {
    val detachedEvents = target.detachedFromTarget()
    target.detachFromTarget(DetachFromTargetRequest(targetId = session.targetId, sessionId = session.sessionId))
    return detachedEvents.first()
}

internal fun ChromeDPSession.api() = ChromeApi(this)

/**
 * Creates and attaches to a new target with given [url] and viewport [width] and [height].
 * This action doesn't affect this [ChromeApi], but creates a new one.
 * The underlying web socket connection is reused for both the old and new sessions.
 *
 * If [incognito] is true, than new target is created in separate browser context (think of it as incognito window).
 */
@OptIn(ExperimentalChromeApi::class)
public suspend fun ChromeApi.attachToNewTarget(
    url: String,
    incognito: Boolean = true,
    width: Int = 1024,
    height: Int = 768,
): ChromeApi {
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

public suspend fun ChromeApi.close() {
    closeTarget()
    session.close()
}

@OptIn(ExperimentalChromeApi::class)
private suspend fun ChromeApi.closeTarget() {
    if (session.targetId != null) {
        target.closeTarget(CloseTargetRequest(session.targetId))
    }
    // FIXME do we really need this given the "disposeOnDetach=true" used at creation?
    val browserContextID = session.browserContextId
    if (!browserContextID.isNullOrEmpty()) {
        target.disposeBrowserContext(DisposeBrowserContextRequest(browserContextID))
    }
}
