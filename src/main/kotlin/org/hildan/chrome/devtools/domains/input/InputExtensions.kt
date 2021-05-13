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
        DispatchMouseEventRequest(
            type = "mousePressed",
            x = x,
            y = y,
            button = button,
            clickCount = 1,
        )
    )
    delay(clickDurationMillis)
    dispatchMouseEvent(
        DispatchMouseEventRequest(
            type = "mouseReleased",
            x = x,
            y = y,
            button = button,
        )
    )
}
