import org.hildan.chrome.devtools.domains.dom.*
import org.hildan.chrome.devtools.sessions.*
import org.junit.jupiter.api.Test
import kotlin.test.*

abstract class LocalIntegrationTestBase : IntegrationTestBase() {

    private suspend fun PageSession.gotoTestPageResource(resourcePath: String) {
        goto("file:///test-server-pages/$resourcePath")
    }

    @Test
    fun attributesAccess() {
        runBlockingWithTimeout {
            chromeWebSocket().use { browser ->
                browser.newPage().use { page ->
                    page.gotoTestPageResource("select.html")

                    val nodeId = page.dom.findNodeBySelector("select[name=pets] option[selected]")
                    assertNull(nodeId, "No option is selected in this <select>")

                    val attributes1 = page.dom.getTypedAttributes("select[name=pets] option[selected]")
                    assertNull(attributes1, "No option is selected in this <select>")

                    val attributes2 = page.dom.getTypedAttributes("select[name=pets-selected] option[selected]")
                    assertNotNull(attributes2, "There should be a selected option")
                    assertEquals(true, attributes2.selected)
                    assertEquals("cat", attributes2.value)
                    val value = page.dom.getAttributeValue("select[name=pets-selected] option[selected]", "value")
                    assertEquals("cat", value)
                    // Attributes without value (e.g. "selected" in <option name="x" selected />) are returned as empty
                    // strings by the protocol.
                    val selected = page.dom.getAttributeValue("select[name=pets-selected] option[selected]", "selected")
                    assertEquals("", selected)

                    val absentValue = page.dom.getAttributeValue("select[name=pets-selected-without-value] option[selected]", "value")
                    assertNull(absentValue, "There is no 'value' attribute in this select option")
                }
            }
        }
    }
}