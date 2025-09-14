import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.hildan.chrome.devtools.*
import org.hildan.chrome.devtools.domains.accessibility.*
import org.hildan.chrome.devtools.domains.dom.*
import org.hildan.chrome.devtools.domains.runtime.RemoteObjectSubtype
import org.hildan.chrome.devtools.protocol.*
import org.hildan.chrome.devtools.sessions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

abstract class LocalIntegrationTestBase : IntegrationTestBase() {

    companion object {
        @RegisterExtension
        val resourceServer = TestResourcesServerExtension()
    }

    @BeforeEach
    fun register() {
        Testcontainers.exposeHostPorts(resourceServer.port)
    }

    protected suspend fun PageSession.gotoTestPageResource(resourcePath: String) {
        goto("http://host.testcontainers.internal:${resourceServer.port}/$resourcePath")
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun basicFlow_fileScheme() = runTestWithRealTime {
        chromeWebSocket().use { browser ->
            val pageSession = browser.newPage()
            val targetId = pageSession.metaData.targetId

            pageSession.use { page ->
                page.gotoTestPageResource("basic.html")
                assertEquals("Basic tab title", page.target.getTargetInfo().targetInfo.title)
                assertTrue(browser.hasTarget(targetId), "the new target should be listed")
            }
            assertFalse(browser.hasTarget(targetId), "the new target should be closed (not listed)")
        }
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun basicFlow_httpScheme() = runTestWithRealTime {
        chromeWebSocket().use { browser ->
            val pageSession = browser.newPage()
            val targetId = pageSession.metaData.targetId

            pageSession.use { page ->
                page.gotoTestPageResource("basic.html")
                assertEquals("Basic tab title", page.target.getTargetInfo().targetInfo.title)
                assertTrue(browser.hasTarget(targetId), "the new target should be listed")
            }
            assertFalse(browser.hasTarget(targetId), "the new target should be closed (not listed)")
        }
    }

    @Test
    fun page_getTargets_fileScheme() = runTestWithRealTime {
        chromeWebSocket().use { browser ->
            browser.newPage().use { page ->
                page.gotoTestPageResource("basic.html")
                val targets = page.target.getTargets().targetInfos
                val targetInfo = targets.first { it.targetId == page.metaData.targetId }
                assertEquals("page", targetInfo.type)
                assertTrue(targetInfo.attached)
                assertTrue(targetInfo.url.contains("basic.html"))
            }
        }
    }

    @Test
    fun page_getTargets_httpScheme() = runTestWithRealTime {
        chromeWebSocket().use { browser ->
            browser.newPage().use { page ->
                page.gotoTestPageResource("basic.html")
                val targets = page.target.getTargets().targetInfos
                val targetInfo = targets.first { it.targetId == page.metaData.targetId }
                assertEquals("page", targetInfo.type)
                assertTrue(targetInfo.attached)
                assertTrue(targetInfo.url.contains("basic.html"))
            }
        }
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun page_goto() = runTestWithRealTime {
        chromeWebSocket().use { browser ->
            browser.newPage().use { page ->
                page.gotoTestPageResource("basic.html")
                assertEquals("Basic tab title", page.target.getTargetInfo().targetInfo.title)

                page.gotoTestPageResource("other.html")
                assertEquals("Other tab title", page.target.getTargetInfo().targetInfo.title)
                val nodeId = withTimeoutOrNull(5.seconds) {
                    page.dom.awaitNodeBySelector("p[class='some-p-class']")
                }
                assertNotNull(
                    nodeId,
                    "timed out while waiting for DOM node with attribute: p[class='some-p-class']"
                )

                val getOuterHTMLResponse = page.dom.getOuterHTML(GetOuterHTMLRequest(nodeId = nodeId))
                assertTrue(getOuterHTMLResponse.outerHTML.contains("<p class=\"some-p-class\">"))
            }
        }
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun sessionThrowsIOExceptionIfAlreadyClosed() = runTestWithRealTime {
        val browser = chromeWebSocket()
        val session = browser.newPage()
        session.gotoTestPageResource("basic.html")

        browser.close()

        assertFailsWith<RequestNotSentException> {
            session.target.getTargetInfo().targetInfo
        }
    }

    @Test
    fun attributesAccess() = runTestWithRealTime {
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

                val absentValue =
                    page.dom.getAttributeValue("select[name=pets-selected-without-value] option[selected]", "value")
                assertNull(absentValue, "There is no 'value' attribute in this select option")
            }
        }
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun test_deserialization_unknown_enum() = runTestWithRealTime {
        chromeWebSocket().use { browser ->
            browser.newPage().use { page ->
                page.gotoTestPageResource("basic.html")
                val tree = page.accessibility.getFullAXTree() // just test that this doesn't fail

                assertTrue("we are no longer testing that unknown AXPropertyName values are deserialized as NotDefinedInProtocol") {
                    tree.nodes.any { n ->
                        n.properties.anyUndefinedName() || n.ignoredReasons.anyUndefinedName()
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun test_deserialization_RemoteObject_subtype_enum() = runTestWithRealTime {
        chromeWebSocket().use { browser ->
            browser.newPage().use { page ->
                page.runtime.enable()
                val deferredEvent = async(start = CoroutineStart.UNDISPATCHED) {
                    page.runtime.consoleAPICalledEvents().first()
                }
                page.gotoTestPageResource("trustedtype.html")
                val event = deferredEvent.await()
                // proof that we have to provide this subtype as enum value
                assertEquals(RemoteObjectSubtype.trustedtype, event.args.first().subtype)
            }
        }
    }

    private fun List<AXProperty>?.anyUndefinedName(): Boolean =
        this != null && this.any { it.name is AXPropertyName.NotDefinedInProtocol }
}