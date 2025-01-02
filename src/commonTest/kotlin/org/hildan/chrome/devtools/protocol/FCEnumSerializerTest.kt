package org.hildan.chrome.devtools.protocol

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.hildan.chrome.devtools.domains.accessibility.*
import org.hildan.chrome.devtools.domains.bluetoothemulation.*
import kotlin.test.*

class FCEnumSerializerTest {

    @Test
    fun deserializesKnownValues() {
        assertEquals(AXPropertyName.url, Json.decodeFromString<AXPropertyName>("\"url\""))
        assertEquals(AXPropertyName.level, Json.decodeFromString<AXPropertyName>("\"level\""))
        assertEquals(AXPropertyName.hiddenRoot, Json.decodeFromString<AXPropertyName>("\"hiddenRoot\""))
    }

    @Test
    fun deserializesKnownValues_withDashes() {
        assertEquals(CentralState.poweredOn, Json.decodeFromString<CentralState>("\"powered-on\""))
        assertEquals(CentralState.poweredOff, Json.decodeFromString<CentralState>("\"powered-off\""))
    }

    @Test
    fun deserializesUnknownValues() {
        assertEquals(AXPropertyName.NotDefinedInProtocol("notRendered"), Json.decodeFromString<AXPropertyName>("\"notRendered\""))
        assertEquals(AXPropertyName.NotDefinedInProtocol("uninteresting"), Json.decodeFromString<AXPropertyName>("\"uninteresting\""))
    }

    @Test
    fun serializesKnownValues() {
        assertEquals("\"url\"", Json.encodeToString(AXPropertyName.url))
        assertEquals("\"level\"", Json.encodeToString(AXPropertyName.level))
        assertEquals("\"hiddenRoot\"", Json.encodeToString(AXPropertyName.hiddenRoot))
    }

    @Test
    fun serializesKnownValues_withDashes() {
        assertEquals("\"powered-on\"", Json.encodeToString(CentralState.poweredOn))
        assertEquals("\"powered-off\"", Json.encodeToString(CentralState.poweredOff))
    }

    @Test
    fun serializesUnknownValues() {
        assertEquals("\"notRendered\"", Json.encodeToString<AXPropertyName>(AXPropertyName.NotDefinedInProtocol("notRendered")))
        assertEquals("\"uninteresting\"", Json.encodeToString(AXPropertyName.NotDefinedInProtocol("uninteresting")))
        assertEquals("\"totallyInexistentStuff\"", Json.encodeToString(AXPropertyName.NotDefinedInProtocol("totallyInexistentStuff")))
    }
}
