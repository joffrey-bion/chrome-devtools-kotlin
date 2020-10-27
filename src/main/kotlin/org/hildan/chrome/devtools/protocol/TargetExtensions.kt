package org.hildan.chrome.devtools.protocol

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.hildan.chrome.devtools.ExperimentalChromeApi
import org.hildan.chrome.devtools.domains.browser.BrowserContextID
import org.hildan.chrome.devtools.domains.target.*
import org.hildan.chrome.devtools.domains.target.events.TargetEvent

/**
 * Attaches to this target via a new web socket connection, and performs the given operation before closing the
 * connection.
 */
public suspend inline fun <T> ChromeDPTarget.use(block: (ChromeTargetSession) -> T): T {
    val api = attach()
    try {
        return block(api)
    } finally {
        api.close()
    }
}

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
 * Creates and attaches to a new page (tab) initially navigated to the given [url].
 * The underlying web socket connection of this [ChromeBrowserSession] is reused for the new [ChromeTargetSession].
 *
 * If [incognito] is true, the new target is created in a separate browser context (think of it as incognito window).
 *
 * You can use [width] and [height] to specify the viewport dimensions in DIP (Chrome Headless only).
 *
 * If [background] is true, the new tab will be created in the background (Chrome only).
 */
@OptIn(ExperimentalChromeApi::class)
public suspend fun ChromeBrowserSession.attachToNewPage(
    url: String,
    incognito: Boolean = true,
    width: Int = 1024,
    height: Int = 768,
    background: Boolean = false,
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
            background = background,
        )
    ).targetId

    return attachTo(targetId, browserContextId)
}

// TODO expose this? or wait until we understand better what this does?
@OptIn(ExperimentalChromeApi::class)
private suspend fun ChromeTargetSession.closeTarget() {
    parent.target.closeTarget(CloseTargetRequest(targetId = targetId))

    // FIXME do we really need this given the "disposeOnDetach=true" used at creation?
    if (!browserContextId.isNullOrEmpty()) {
        parent.target.disposeBrowserContext(DisposeBrowserContextRequest(browserContextId))
    }
}

/**
 * Watches the available targets in this browser.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun ChromeBrowserSession.watchTargetsIn(coroutineScope: CoroutineScope): StateFlow<Map<TargetID, TargetInfo>> {
    val targetsFlow = MutableStateFlow(emptyMap<TargetID, TargetInfo>())

    target.events().onEach { targetsFlow.value = targetsFlow.value.updatedBy(it) }.launchIn(coroutineScope)

    coroutineScope.launch {
        // triggers target info events
        target.setDiscoverTargets(SetDiscoverTargetsRequest(discover = true))
    }
    return targetsFlow
}

private fun Map<TargetID, TargetInfo>.updatedBy(event: TargetEvent): Map<TargetID, TargetInfo> = when (event) {
    is TargetEvent.TargetCreatedEvent -> this + (event.targetInfo.targetId to event.targetInfo)
    is TargetEvent.TargetInfoChangedEvent -> this + (event.targetInfo.targetId to event.targetInfo)
    is TargetEvent.TargetDestroyedEvent -> this - event.targetId
    is TargetEvent.TargetCrashedEvent -> this - event.targetId
    is TargetEvent.AttachedToTargetEvent, is TargetEvent.DetachedFromTargetEvent, is TargetEvent.ReceivedMessageFromTargetEvent -> this // irrelevant events
}