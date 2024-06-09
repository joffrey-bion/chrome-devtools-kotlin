package org.hildan.chrome.devtools.domains.input

import kotlinx.coroutines.delay
import kotlin.time.*
import kotlin.time.Duration.Companion.milliseconds

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
    clickDuration: Duration = 100.milliseconds,
    button: MouseButton = MouseButton.left,
) {
    dispatchMouseEvent(
        type = MouseEventType.mousePressed,
        x = x,
        y = y,
    ) {
        this.button = button
        this.clickCount = 1
    }
    delay(clickDuration)
    dispatchMouseEvent(
        type = MouseEventType.mouseReleased,
        x = x,
        y = y,
    ) {
        this.button = button
    }
}

/**
 * Simulates a mouse click on the given [x] and [y] coordinates.
 * This uses a `mousePressed` and `mouseReleased` event in quick succession.
 *
 * The current tab doesn't need to be focused.
 * If this click opens a new tab, that new tab may become focused, but this session still targets the old tab.
 */
@Deprecated(
    message = "Use dispatchMouseClick with a Duration type for clickDuration.",
    replaceWith = ReplaceWith(
        expression = "this.dispatchMouseClick(x, y, clickDurationMillis.milliseconds, button)",
        imports = ["kotlin.time.Duration.Companion.milliseconds"],
    ),
)
suspend fun InputDomain.dispatchMouseClick(
    x: Double,
    y: Double,
    clickDurationMillis: Long,
    button: MouseButton = MouseButton.left,
) {
    dispatchMouseClick(x, y, clickDurationMillis.milliseconds, button)
}
