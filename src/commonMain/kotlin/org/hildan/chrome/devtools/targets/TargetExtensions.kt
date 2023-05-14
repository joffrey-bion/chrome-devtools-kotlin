package org.hildan.chrome.devtools.targets

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.hildan.chrome.devtools.domains.page.*
import org.hildan.chrome.devtools.domains.target.*
import org.hildan.chrome.devtools.protocol.*
import org.hildan.chrome.devtools.sessions.*
import org.hildan.chrome.devtools.sessions.childPages as childPages_moved
import org.hildan.chrome.devtools.sessions.use as use_moved

@Deprecated(
    message = "This function will be removed in a future release",
    replaceWith = ReplaceWith(
        expression = "attachToTarget(targetId).asPageSession()",
        imports = ["org.hildan.chrome.devtools.sessions.asPageSession"],
    ),
)
suspend fun BrowserSession.attachToPage(targetId: TargetID): PageSession =
    attachToTarget(targetId).asPageSession()

@Deprecated(
    message = "This function will be removed in a future release, please use newPage() instead. " +
            "If you need to navigate to a specific URL, use newPage() and then goto(url)."
)
suspend fun BrowserSession.attachToNewPage(
    url: String = "about:blank",
    incognito: Boolean = true,
    width: Int = 1024,
    height: Int = 768,
    background: Boolean = false,
): PageSession = newPage {
    this.incognito = incognito
    this.width = width
    this.height = height
    this.background = background
}.apply {
    goto(url) {
        waitFor() // no events, complete immediately
    }
}

@Deprecated("This function will be removed in a future release, please use newPage() and then goto(url) instead.")
suspend fun BrowserSession.attachToNewPageAndAwaitPageLoad(
    url: String,
    incognito: Boolean = true,
    width: Int = 1024,
    height: Int = 768,
    background: Boolean = false,
): PageSession = newPage {
    this.incognito = incognito
    this.width = width
    this.height = height
    this.background = background
}.apply {
    goto(url) {
        waitFor(GotoCompletionEvent.FrameStoppedLoading)
    }
}

@Suppress("DeprecatedCallableAddReplaceWith", "DEPRECATION")
@Deprecated("This function will be removed in a future release, please use goto() instead.")
suspend fun PageSession.navigateAndAwaitPageLoad(
    url: String,
    optionalArgs: NavigateRequest.Builder.() -> Unit = {},
) {
    navigateAndAwaitPageLoad(NavigateRequest.Builder(url = url).apply(optionalArgs).build())
}

@Deprecated("This function will be removed in a future release, please use goto() instead.")
@OptIn(ExperimentalChromeApi::class)
suspend fun PageSession.navigateAndAwaitPageLoad(navigateRequest: NavigateRequest) {
    goto(navigateRequest.url) {
        waitFor(GotoCompletionEvent.FrameStoppedLoading)
        transitionType = navigateRequest.transitionType
        referrer = navigateRequest.referrer
        referrerPolicy = navigateRequest.referrerPolicy
        frameId = navigateRequest.frameId
    }
}

@Deprecated(
    message = "This type moved to the sessions package",
    replaceWith = ReplaceWith(
        expression = "NavigationFailed",
        imports = ["org.hildan.chrome.devtools.sessions.NavigationFailed"],
    ),
)
typealias NavigationFailed = org.hildan.chrome.devtools.sessions.NavigationFailed

@Deprecated(
    message = "This extension moved to the sessions package",
    replaceWith = ReplaceWith(
        expression = "this.childPages()",
        imports = ["org.hildan.chrome.devtools.sessions.childPages"],
    ),
)
suspend fun PageSession.childPages(): List<TargetInfo> = childPages_moved()

@Deprecated(
    message = "This extension moved to the sessions package",
    replaceWith = ReplaceWith(
        expression = "this.use(block)",
        imports = ["org.hildan.chrome.devtools.sessions.use"],
    ),
)
suspend inline fun <T> BrowserSession.use(block: (BrowserSession) -> T): T = use_moved(block)

@Deprecated(
    message = "This extension moved to the sessions package",
    replaceWith = ReplaceWith(
        expression = "this.use(block)",
        imports = ["org.hildan.chrome.devtools.sessions.use"],
    ),
)
suspend inline fun <T> PageSession.use(block: (PageSession) -> T): T = use_moved(block)

@Deprecated(
    message = "This extension will be removed in a future version. " +
            "If you have a use case for it, please open an issue on GitHub.",
    replaceWith = ReplaceWith("this.target.getTargetInfo().targetInfo"),
)
@ExperimentalChromeApi
suspend fun PageSession.getTargetInfo(): TargetInfo = target.getTargetInfo().targetInfo

@Deprecated(
    message = "This extension will be removed in a future version. ",
    replaceWith = ReplaceWith(
        expression = "this.target.allTargetsFlow().stateIn(coroutineScope)",
        imports = [
            "org.hildan.chrome.devtools.domains.target.allTargetsFlow",
            "kotlinx.coroutines.flow.stateIn",
        ],
    ),
)
suspend fun BrowserSession.watchTargetsIn(coroutineScope: CoroutineScope): StateFlow<Map<TargetID, TargetInfo>> =
    target.allTargetsFlow().stateIn(coroutineScope)
