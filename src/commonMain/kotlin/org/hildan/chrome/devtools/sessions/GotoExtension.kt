package org.hildan.chrome.devtools.sessions

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.hildan.chrome.devtools.domains.page.*
import org.hildan.chrome.devtools.protocol.*

/**
 * Navigates the current page to the provided [url], and suspends until the configured completion events are fired.
 * By default, this function suspends until the [GotoCompletionEvent.Load] event is fired.
 *
 * The completion events and the navigation request can be configured via the [configure] parameter.
 *
 * This function throws [NavigationFailed] if the navigation response has an error, instead of waiting forever for an
 * event that will never come.
 *
 * If there is no failure, and yet the specified events never fire, this function may hang (there is no built-in
 * timeout). You can use the coroutine's built-in [withTimeout] function if you need to stop waiting at some point.
 */
suspend fun PageSession.goto(
    url: String,
    configure: GotoConfigBuilder.() -> Unit = {},
) {
    val config = GotoConfigBuilder().apply(configure)
    val navigateRequest = config.buildNavigateRequest(url)

    page.enable()
    coroutineScope {
        config.completionEvents.forEach { completionEvent ->
            launch(start = CoroutineStart.UNDISPATCHED) {
                completionEvent.await(this@goto, navigateRequest)
            }
        }
        val response = page.navigate(navigateRequest)
        if (response.errorText != null) {
            throw NavigationFailed(navigateRequest, response)
        }
        // Here, coroutineScope will wait for all launched coroutines to complete
    }
}

@OptIn(ExperimentalChromeApi::class)
private fun GotoConfigBuilder.buildNavigateRequest(url: String): NavigateRequest =
    NavigateRequest.Builder(url = url).apply {
        frameId = this@buildNavigateRequest.frameId
        referrer = this@buildNavigateRequest.referrer
        referrerPolicy = this@buildNavigateRequest.referrerPolicy
        transitionType = this@buildNavigateRequest.transitionType
    }.build()

/**
 * Defines properties that can be customized when navigating a page using [goto].
 */
class GotoConfigBuilder internal constructor() {

    /**
     * Referrer URL.
     */
    var referrer: String? = null

    /**
     * Referrer-policy used for the navigation.
     */
    @ExperimentalChromeApi
    var referrerPolicy: ReferrerPolicy? = null

    /**
     * Intended transition type.
     */
    var transitionType: TransitionType? = null

    /**
     * Frame id to navigate. If not specified, navigates the top frame.
     */
    var frameId: FrameId? = null

    internal var completionEvents: List<GotoCompletionEvent> = listOf(GotoCompletionEvent.Load)

    /**
     * Defines the events that [goto] should wait for before considering the navigation complete and resuming.
     *
     * By default, [goto] awaits the [GotoCompletionEvent.Load] event.
     *
     * Calling this function without arguments disable all completion events and makes [goto] complete immediately
     * after the `navigate` request completes (without waiting for anything).
     */
    fun waitFor(vararg completionEvents: GotoCompletionEvent) {
        this.completionEvents = completionEvents.toList()
    }
}

/**
 * An event that a [goto] call can await before considering the navigation complete.
 */
// Using a class instead of enum here in case new events are added and require configuration.
// This abstract class is not sealed so we can safely add new completion event types without breaking user code.
abstract class GotoCompletionEvent {

    internal abstract suspend fun await(session: PageSession, navigateRequest: NavigateRequest)

    /**
     * Fired when the whole page has loaded, including all dependent resources such as images, stylesheets, scripts,
     * and iframes.
     */
    object Load : GotoCompletionEvent() {
        override suspend fun await(session: PageSession, navigateRequest: NavigateRequest) {
            session.page.loadEventFiredEvents().first()
        }
    }

    /**
     * Fired when the specific frame has stopped loading.
     */
    @Suppress("unused")
    object FrameStoppedLoading : GotoCompletionEvent() {
        @OptIn(ExperimentalChromeApi::class)
        override suspend fun await(session: PageSession, navigateRequest: NavigateRequest) {
            session.page.frameStoppedLoadingEvents()
                .first { it.frameId == (navigateRequest.frameId ?: session.metaData.targetId) }
        }
    }

    /**
     * Fired when the browser fully loaded the HTML, and the DOM tree is built, but external resources like pictures
     * and stylesheets may not have been loaded yet.
     */
    @Suppress("unused")
    object DomContentLoaded : GotoCompletionEvent() {
        override suspend fun await(session: PageSession, navigateRequest: NavigateRequest) {
            session.page.domContentEventFiredEvents().first()
        }
    }
}

/**
 * Thrown to indicate that the navigation to a page has failed.
 */
class NavigationFailed(
    val request: NavigateRequest,
    val response: NavigateResponse,
) : Exception("Navigation to ${request.url} has failed: ${response.errorText}")
