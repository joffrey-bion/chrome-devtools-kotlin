package org.hildan.chrome.devtools.targets

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.hildan.chrome.devtools.domains.page.NavigateRequest
import org.hildan.chrome.devtools.domains.page.NavigateResponse
import org.hildan.chrome.devtools.domains.target.*
import org.hildan.chrome.devtools.domains.target.events.TargetEvent
import org.hildan.chrome.devtools.protocol.ExperimentalChromeApi
import org.hildan.chrome.devtools.protocol.withSession

private val pageLikeTargetTypes = listOf(TargetTypeNames.page, TargetTypeNames.iFrame)

/**
 * Creates a new [ChromePageSession] attached to the `page` or `iframe` target with the given [targetId].
 * The new session shares the same underlying web socket connection as this [ChromeBrowserSession].
 *
 * If the given ID corresponds to a target that is not a `page` or `iframe`, an exception is thrown.
 */
@OptIn(ExperimentalChromeApi::class)
suspend fun ChromeBrowserSession.attachToPage(targetId: TargetID): ChromePageSession {
    // We use the "flatten" mode because it's required by our implementation of the protocol
    // (namely, we specify sessionId as part of the request frames directly, see RequestFrame)
    val sessionId = target.attachToTarget(targetId = targetId) { flatten = true }.sessionId
    val targetInfo = target.getTargetInfo { this.targetId = targetId }.targetInfo
    if (targetInfo.type !in pageLikeTargetTypes) {
        error("Cannot initiate a page session with target of type ${targetInfo.type} (target ID: $targetId)")
    }
    val metaData = ChromePageMetaData(targetId, targetInfo.browserContextId)
    return ChromePageSession(session.connection.withSession(sessionId), this, metaData)
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
        true -> target.createBrowserContext { disposeOnDetach = true }.browserContextId
        false -> null
    }

    val targetId = target.createTarget(url) {
        this.browserContextId = browserContextId
        this.height = height
        this.width = width
        this.background = background
    }.targetId

    return attachToPage(targetId)
}

/**
 * Creates and attaches to a new page (tab) initially navigated to the given [url].
 * Suspends until the `frameStoppedLoading` event is fired.
 * The underlying web socket connection of this [ChromeBrowserSession] is reused for the new [ChromePageSession].
 *
 * If [incognito] is true, the new target is created in a separate browser context (think of it as incognito window).
 *
 * You can use [width] and [height] to specify the viewport dimensions in DIP (Chrome Headless only).
 *
 * If [background] is true, the new tab will be created in the background (Chrome only).
 *
 * This function throws [NavigationFailed] if the navigation response has an error, instead of waiting forever for an
 * event that will never come.
 */
suspend fun ChromeBrowserSession.attachToNewPageAndAwaitPageLoad(
    url: String,
    incognito: Boolean = true,
    width: Int = 1024,
    height: Int = 768,
    background: Boolean = false,
): ChromePageSession {
    val session = attachToNewPage("about:blank", incognito, width, height, background)
    session.navigateAndAwaitPageLoad(url)
    return session
}

/**
 * Navigates the current page to the provided [url], and suspends until the corresponding `frameStoppedLoading` event
 * is received.
 *
 * This function throws [NavigationFailed] if the navigation response has an error, instead of waiting forever for an
 * event that will never come.
 */
suspend fun ChromePageSession.navigateAndAwaitPageLoad(
    url: String,
    optionalArgs: NavigateRequest.Builder.() -> Unit = {},
) {
    navigateAndAwaitPageLoad(NavigateRequest.Builder(url = url).apply(optionalArgs).build())
}

/**
 * Navigates the current page according to the provided [navigateRequest], and suspends until the corresponding
 * `frameStoppedLoading` event is received.
 *
 * This function throws [NavigationFailed] if the navigation response has an error, instead of waiting forever for an
 * event that will never come.
 */
@OptIn(ExperimentalChromeApi::class)
suspend fun ChromePageSession.navigateAndAwaitPageLoad(navigateRequest: NavigateRequest) {
    page.enable()
    coroutineScope {
        val events = page.frameStoppedLoadingEvents()
        val stoppedLoadingEvent = async(start = CoroutineStart.UNDISPATCHED) {
            events.first { it.frameId == metaData.targetId }
        }
        val response = page.navigate(navigateRequest)
        if (response.errorText != null) {
            throw NavigationFailed(navigateRequest, response)
        }
        stoppedLoadingEvent.await()
    }
}

/**
 * Thrown to indicate that the navigation to a page has failed.
 */
class NavigationFailed(
    val request: NavigateRequest,
    val response: NavigateResponse,
) : Exception("Navigation to ${request.url} has failed: ${response.errorText}")

/**
 * Finds page targets that were opened by this page.
 */
suspend fun ChromePageSession.childPages(): List<TargetInfo> {
    val thisTargetId = metaData.targetId
    return target.getTargets().targetInfos.filter { it.type == TargetTypeNames.page && it.openerId == thisTargetId }
}

/**
 * Performs the given operation in this session and closes the web socket connection.
 *
 * Note: This effectively closes every session based on the same web socket connection.
 */
suspend inline fun <T> ChromeBrowserSession.use(block: (ChromeBrowserSession) -> T): T {
    try {
        return block(this)
    } finally {
        close()
    }
}

/**
 * Performs the given operation in this session and closes the target.
 *
 * This preserves the underlying web socket connection (of the parent browser session), because it could be used by
 * other page sessions.
 */
suspend inline fun <T> ChromePageSession.use(block: (ChromePageSession) -> T): T {
    try {
        return block(this)
    } finally {
        close()
    }
}

/**
 * Retrieves information about this session's page target.
 */
@ExperimentalChromeApi
suspend fun ChromePageSession.getTargetInfo(): TargetInfo = target.getTargetInfo().targetInfo

/**
 * Watches the available targets in this browser.
 */
suspend fun ChromeBrowserSession.watchTargetsIn(coroutineScope: CoroutineScope): StateFlow<Map<TargetID, TargetInfo>> {
    val targetsFlow = MutableStateFlow(emptyMap<TargetID, TargetInfo>())

    target.events().onEach { targetsFlow.value = targetsFlow.value.updatedBy(it) }.launchIn(coroutineScope)

    // triggers target info events
    target.setDiscoverTargets(discover = true)
    return targetsFlow
}

@OptIn(ExperimentalChromeApi::class)
private fun Map<TargetID, TargetInfo>.updatedBy(event: TargetEvent): Map<TargetID, TargetInfo> = when (event) {
    is TargetEvent.TargetCreated -> this + (event.targetInfo.targetId to event.targetInfo)
    is TargetEvent.TargetInfoChanged -> this + (event.targetInfo.targetId to event.targetInfo)
    is TargetEvent.TargetDestroyed -> this - event.targetId
    is TargetEvent.TargetCrashed -> this - event.targetId
    is TargetEvent.AttachedToTarget, //
    is TargetEvent.DetachedFromTarget, //
    is TargetEvent.ReceivedMessageFromTarget -> this // irrelevant events
}
