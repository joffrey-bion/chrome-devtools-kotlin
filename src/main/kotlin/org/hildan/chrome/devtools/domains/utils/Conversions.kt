package org.hildan.chrome.devtools.domains.utils

import org.hildan.chrome.devtools.domains.dom.Quad
import org.hildan.chrome.devtools.domains.dom.Rect
import org.hildan.chrome.devtools.domains.page.Viewport

/**
 * Converts this [Quad] into a [Viewport] for screenshot captures.
 */
fun Quad.toViewport(): Viewport = Viewport(
    x = this[0],
    y = this[1],
    width = this[4] - this[0],
    height = this[5] - this[1],
    scale = 1.0,
)

internal data class Point(val x: Double, val y: Double)

internal val Quad.center: Point
    get() = Point(
        x = middle(start = this[4], end = this[0]),
        y = middle(start = this[5], end = this[1]),
    )

private fun middle(start: Double, end: Double) = start + (end - start) / 2
