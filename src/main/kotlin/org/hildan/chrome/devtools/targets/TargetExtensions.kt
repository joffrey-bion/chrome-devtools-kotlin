package org.hildan.chrome.devtools.targets

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.hildan.chrome.devtools.domains.target.*
import org.hildan.chrome.devtools.domains.target.events.TargetEvent
import org.hildan.chrome.devtools.protocol.ChromeDPSession
import org.hildan.chrome.devtools.protocol.ExperimentalChromeApi

/**
 * Performs the given operation in this session and closes the connection.
 */
suspend inline fun <T, S : AbstractTargetSession> S.use(block: (S) -> T): T {
    try {
        return block(this)
    } finally {
        close()
    }
}

/**
 * Creates a new [ChromePageSession] attached to the page target with the given [targetId].
 * The new session shares the same underlying web socket connection as this [ChromeBrowserSession].
 *
 * If the given ID corresponds to a target that is not a page, an exception is thrown.
 */
@OptIn(ExperimentalChromeApi::class)
suspend fun ChromeBrowserSession.attachToPage(targetId: TargetID): ChromePageSession {
    val sessionId = target.attachToTarget(AttachToTargetRequest(targetId = targetId, flatten = true)).sessionId
    val targetInfo = target.getTargetInfo(GetTargetInfoRequest(targetId = targetId)).targetInfo
    if (targetInfo.type != "page") {
        error("Cannot initiate a page session with target of type ${targetInfo.type} (target ID: $targetId)")
    }
    return ChromePageSession(ChromeDPSession(session.connection, sessionId), this, targetInfo)
}

/**
 * Creates and attaches to a new page (tab) initially navigated to the given [url].
 * The underlying web socket connection of this [ChromeBrowserSession] is reused for the new [ChromePageSession].
 *
 * If [incognito] is true, the new target is created in a separate browser context (think of it as incognito window).
 *
 * You can use [width] and [height] to specify the viewport dimensions in DIP (Chrome Headless only).
 *
 * If [background] is true, the new tab will be created in the background (Chrome only).
 */
@OptIn(ExperimentalChromeApi::class)
suspend fun ChromeBrowserSession.attachToNewPage(
    url: String = "about:blank",
    incognito: Boolean = true,
    width: Int = 1024,
    height: Int = 768,
    background: Boolean = false,
): ChromePageSession {
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

    return attachToPage(targetId)
}

// TODO expose this? or wait until we understand better what this does?
@OptIn(ExperimentalChromeApi::class)
private suspend fun ChromePageSession.closeTarget() {
    parent.target.closeTarget(CloseTargetRequest(targetId = targetInfo.targetId))

    // FIXME do we really need this given the "disposeOnDetach=true" used at creation?
    if (!targetInfo.browserContextId.isNullOrEmpty()) {
        parent.target.disposeBrowserContext(DisposeBrowserContextRequest(targetInfo.browserContextId))
    }
}

/**
 * Watches the available targets in this browser.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun ChromeBrowserSession.watchTargetsIn(coroutineScope: CoroutineScope): StateFlow<Map<TargetID, TargetInfo>> {
    val targetsFlow = MutableStateFlow(emptyMap<TargetID, TargetInfo>())

    target.events().onEach { targetsFlow.value = targetsFlow.value.updatedBy(it) }.launchIn(coroutineScope)

    // triggers target info events
    target.setDiscoverTargets(SetDiscoverTargetsRequest(discover = true))
    return targetsFlow
}

private fun Map<TargetID, TargetInfo>.updatedBy(event: TargetEvent): Map<TargetID, TargetInfo> = when (event) {
    is TargetEvent.TargetCreatedEvent -> this + (event.targetInfo.targetId to event.targetInfo)
    is TargetEvent.TargetInfoChangedEvent -> this + (event.targetInfo.targetId to event.targetInfo)
    is TargetEvent.TargetDestroyedEvent -> this - event.targetId
    is TargetEvent.TargetCrashedEvent -> this - event.targetId
    is TargetEvent.AttachedToTargetEvent, is TargetEvent.DetachedFromTargetEvent, is TargetEvent.ReceivedMessageFromTargetEvent -> this // irrelevant events
}