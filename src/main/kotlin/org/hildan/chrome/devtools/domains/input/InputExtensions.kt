package org.hildan.chrome.devtools.domains.input

import kotlinx.coroutines.delay

/**
 * Simulates a mouse click on the given [x] and [y] coordinates.
 * This uses a `mousePressed` and `mouseReleased` event in quick succession.
 *
 * The current tab doesn't need to be focused.
 * If this click opens a new tab, that new tab may become focused, but this session still targets the old tab.
 */
suspend fun InputDomain.dispatchMouseClick(
    x: Double,
    y: Double,
    clickDurationMillis: Long = 100,
    button: MouseButton = MouseButton.left
) {
    dispatchMouseEvent(
        type = MouseEventType.mousePressed,
        x = x,
        y = y,
    ) {
        this.button = button
        this.clickCount = 1
    }
    delay(clickDurationMillis)
    dispatchMouseEvent(
        type = MouseEventType.mouseReleased,
        x = x,
        y = y,
    ) {
        this.button = button
    }
}
