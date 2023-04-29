package org.hildan.chrome.devtools.domains.dom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DOMAttributesKtTest {

    @Test
    fun basicStringAttributes() {
        val attrsList = listOf("href", "http://www.google.com", "title", "My Tooltip")
        val attrs = attrsList.asDOMAttributes()
        assertEquals("http://www.google.com", attrs.href)
        assertEquals("My Tooltip", attrs.title)
    }

    @Test
    fun missingAttributes() {
        val attrsList = listOf("title", "My Tooltip")
        val attrs = attrsList.asDOMAttributes()
        assertNull(attrs.href)
        assertNull(attrs.`class`)
        assertEquals("My Tooltip", attrs.title)
    }

    @Test
    fun convertedAttributes() {
        val attrsList = listOf("width", "150", "selected", "false", "height", "320")
        val attrs = attrsList.asDOMAttributes()
        assertEquals(false, attrs.selected)
        assertEquals(150, attrs.width)
        assertEquals(320, attrs.height)
    }

    @Test
    fun attributesWithoutValue() {
        val attrsList = listOf("selected", "", "title", "Bob")
        val attrs = attrsList.asDOMAttributes()
        assertEquals(true, attrs.selected)
        assertEquals("Bob", attrs.title)
    }
}