package org.hildan.chrome.devtools.sessions

import org.hildan.chrome.devtools.protocol.*

/**
 * Creates a new page and attaches to it, then returns the new child [PageSession].
 * The underlying web socket connection of this [BrowserSession] is reused for the new child [PageSession].
 *
 * Use the [configure] parameter to further configure the properties of the new page.
 *
 * By default, the new page is created in a new isolated browser context (think of it as incognito mode).
 * This can be disabled via the [configure] parameter by setting [NewPageConfigBuilder.incognito] to false.
 */
@OptIn(ExperimentalChromeApi::class)
suspend fun BrowserSession.newPage(configure: NewPageConfigBuilder.() -> Unit = {}): PageSession {
    val config = NewPageConfigBuilder().apply(configure)

    val browserContextId = when (config.incognito) {
        true -> target.createBrowserContext { disposeOnDetach = true }.browserContextId
        false -> null
    }

    val targetId = target.createTarget(url = "about:blank") {
        this.browserContextId = browserContextId
        width = config.width
        height = config.height
        newWindow = config.newWindow
        background = config.background
    }.targetId

    return attachToTarget(targetId).asPageSession()
}

/**
 * Defines properties that can be customized when creating a new page.
 */
class NewPageConfigBuilder internal constructor() {
    /**
     * If true, the new target is created in a separate browser context (think of it as incognito window).
     */
    var incognito: Boolean = true

    /**
     * Frame width in DIP (headless chrome only).
     */
    var width: Int? = null

    /**
     * Frame height in DIP (headless chrome only).
     */
    var height: Int? = null

    /**
     * Whether to create a new Window or Tab (chrome-only, false by default).
     */
    var newWindow: Boolean? = null

    /**
     * Whether to create the target in background or foreground (chrome-only, false by default).
     */
    var background: Boolean? = null
}
