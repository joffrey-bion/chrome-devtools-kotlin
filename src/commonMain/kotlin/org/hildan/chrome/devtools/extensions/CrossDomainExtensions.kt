package org.hildan.chrome.devtools.extensions

import org.hildan.chrome.devtools.domains.dom.CssSelector
import org.hildan.chrome.devtools.domains.dom.getBoxModel
import org.hildan.chrome.devtools.domains.input.MouseButton
import org.hildan.chrome.devtools.domains.input.dispatchMouseClick
import org.hildan.chrome.devtools.domains.utils.center
import org.hildan.chrome.devtools.targets.ChromePageSession

/**
 * Finds a DOM element via the given [selector], and simulates a click event on it based on its padding box.
 * The current tab doesn't need to be focused.
 *
 * If this click opens a new tab, that new tab may become focused, but this session still targets the old tab.
 */
suspend fun ChromePageSession.clickOnElement(
    selector: CssSelector,
    clickDurationMillis: Long = 100,
    mouseButton: MouseButton = MouseButton.left
) {
    val box = dom.getBoxModel(selector) ?: error("Cannot click on element, no node found using selector '$selector'")
    val elementCenter = box.content.center

    input.dispatchMouseClick(
        x = elementCenter.x,
        y = elementCenter.y,
        clickDurationMillis = clickDurationMillis,
        button = mouseButton,
    )
}
