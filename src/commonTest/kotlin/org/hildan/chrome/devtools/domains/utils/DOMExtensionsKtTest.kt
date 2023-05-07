package org.hildan.chrome.devtools.domains.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class DOMExtensionsKtTest {

    @Test
    fun toViewport() {
        val quad = listOf(
            1.0, 2.0, // top left
            4.0, 2.0, // top right
            4.0, 3.0, // bottom right
            1.0, 3.0, // bottom left
        )
        val viewport = quad.toViewport()
        assertEquals(1.0, viewport.x)
        assertEquals(2.0, viewport.y)
        assertEquals(1.0, viewport.height)
        assertEquals(3.0, viewport.width)
    }
}
