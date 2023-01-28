import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import org.hildan.chrome.devtools.domains.backgroundservice.ClearEventsRequest
import org.hildan.chrome.devtools.domains.backgroundservice.ServiceName
import org.hildan.chrome.devtools.domains.cachestorage.RequestCacheNamesRequest
import org.hildan.chrome.devtools.domains.dom.*
import org.hildan.chrome.devtools.domains.domdebugger.DOMBreakpointType
import org.hildan.chrome.devtools.domains.domdebugger.SetDOMBreakpointRequest
import org.hildan.chrome.devtools.domains.runtime.evaluateJs
import org.hildan.chrome.devtools.domains.storage.GetCookiesRequest
import org.hildan.chrome.devtools.domains.target.GetTargetsRequest
import org.hildan.chrome.devtools.protocol.ChromeDPClient
import org.hildan.chrome.devtools.protocol.ExperimentalChromeApi
import org.hildan.chrome.devtools.targets.*
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.IOException
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@Testcontainers
class IntegrationTests {

    @Container
    var chromeContainer: GenericContainer<*> = GenericContainer("zenika/alpine-chrome")
        .withExposedPorts(9222)
        .withCommand("--no-sandbox --remote-debugging-address=0.0.0.0 --remote-debugging-port=9222 about:blank")

    private fun chromeDpClient(): ChromeDPClient {
        val chromeDebuggerPort = chromeContainer.firstMappedPort
        return ChromeDPClient("http://localhost:$chromeDebuggerPort")
    }

    @Test
    fun httpEndpoints_meta() {
        runBlockingWithTimeout {
            val chrome = chromeDpClient()

            val version = chrome.version()
            assertTrue(version.browser.contains("Chrome"))
            assertTrue(version.userAgent.contains("HeadlessChrome"))
            assertTrue(version.webSocketDebuggerUrl.startsWith("ws://localhost"))

            val targets = chrome.targets()
            assertTrue(targets.isNotEmpty(), "there should be at least the about:blank target")

            val protocolJson = chrome.protocolJson()
            assertTrue(protocolJson.isNotEmpty(), "the JSON definition of the protocol should not be empty")
        }
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun webSocket_basic() {
        runBlockingWithTimeout {
            val chrome = chromeDpClient()

            val browser = chrome.webSocket()
            val session = browser.attachToNewPageAndAwaitPageLoad("http://www.google.com")
            val targetId = session.metaData.targetId

            assertEquals("Google", session.getTargetInfo().title)

            assertTrue(chrome.targets().any { it.id == targetId }, "the new target should be listed")

            val nodeId = withTimeoutOrNull(1000) {
                session.dom.awaitNodeBySelector("form[action='/search']")
            }
            assertNotNull(nodeId)

            val getOuterHTMLResponse = session.dom.getOuterHTML(GetOuterHTMLRequest(nodeId = nodeId))
            assertTrue(getOuterHTMLResponse.outerHTML.contains("<input name=\"source\""))

            session.close()
            assertTrue(chrome.targets().none { it.id == targetId }, "the new target should be closed (not listed)")

            browser.close()
        }
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun sessionThrowsIOExceptionIfAlreadyClosed() {
        runBlockingWithTimeout {
            val chrome = chromeDpClient()

            val browser = chrome.webSocket()
            val session = browser.attachToNewPageAndAwaitPageLoad("http://www.google.com")

            browser.close()

            assertFailsWith<IOException> {
                session.getTargetInfo()
            }
        }
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun pageSession_navigateAndAwaitPageLoad() {
        runBlockingWithTimeout {
            chromeDpClient().webSocket().use { browser ->
                browser.attachToNewPageAndAwaitPageLoad("https://kotlinlang.org/").use { page ->

                    assertEquals("Kotlin Programming Language", page.getTargetInfo().title)

                    page.navigateAndAwaitPageLoad("http://www.google.com")
                    assertEquals("Google", page.getTargetInfo().title)

                    val nodeId = withTimeoutOrNull(1000) {
                        page.dom.awaitNodeBySelector("form[action='/search']")
                    }
                    assertNotNull(nodeId)
                }
            }
        }
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun test_parallelPages() {
        runBlockingWithTimeout {
            chromeDpClient().webSocket().use { browser ->
                // we want all coroutines to finish before we close the browser session
                coroutineScope {
                    repeat(16) {
                        launch(Dispatchers.IO) {
                            browser.attachToNewPageAndAwaitPageLoad("http://www.google.com").use { page ->
                                page.runtime.getHeapUsage()
                                val docRoot = page.dom.getDocumentRootNodeId()
                                page.dom.describeNode(DescribeNodeRequest(docRoot, depth = 2))
                                page.storage.getCookies(GetCookiesRequest())
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun page_getTargets() {
        runBlockingWithTimeout {
            chromeDpClient().webSocket().use { browser ->
                browser.attachToNewPageAndAwaitPageLoad("http://google.com").use { page ->
                    val targets = page.target.getTargets(GetTargetsRequest()).targetInfos
                    val targetInfo = targets.first { it.targetId == page.metaData.targetId }
                    assertEquals("page", targetInfo.type)
                    assertTrue(targetInfo.attached)
                    assertTrue(targetInfo.url.contains("www.google.com")) // redirected
                }
            }
        }
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun supportedDomains() {
        runBlockingWithTimeout {
            chromeDpClient().webSocket().use { browser ->
                browser.attachToNewPage().use { page ->

                    // the following usages should not fail
                    page.cacheStorage.requestCacheNames(RequestCacheNamesRequest("google.com"))
                    page.backgroundService.clearEvents(ClearEventsRequest(ServiceName.backgroundFetch))
                    page.browser.getVersion()
                    page.css.getMediaQueries()
                    page.database.enable()
                    page.debugger.disable()
                    page.deviceOrientation.clearDeviceOrientationOverride()
                    page.domDebugger.setDOMBreakpoint(SetDOMBreakpointRequest(
                        nodeId = page.dom.getDocumentRootNodeId(),
                        type = DOMBreakpointType.`attribute-modified`,
                    ))
                    page.domSnapshot.enable()
                    page.domStorage.enable()
                    page.fetch.disable()
                    page.headlessExperimental.enable()
                    page.heapProfiler.enable()
                    page.indexedDB.enable()
                    page.layerTree.enable()
                    page.performance.disable()
                    page.profiler.disable()

                    val supportedByCode = RenderFrameTarget.supportedDomains.toSet()
                    // We can replace this schema Domain call by an HTTP call to /json/protocol
                    // The Kotlin definitions of that JSON from the buildSrc should work for this,
                    // but we need a way to share them between test and buildSrc
                    val supportedByServer = page.schema.getDomains().domains.map { it.name }.toSet()

                    val knownUnsupportedDomains = setOf(
                        "ApplicationCache", // was removed in tip-of-tree, but still supported by the server
                    )
                    val onlyInServer = supportedByServer - knownUnsupportedDomains - supportedByCode
                    assertEquals(emptySet(),
                        onlyInServer,
                        "The library should support all domains that the zenika/alpine-chrome container actually " +
                            "exposes (apart from $knownUnsupportedDomains)")
                }
            }
        }
    }

    @Serializable
    data class Person(val firstName: String, val lastName: String)

    @Test
    fun runtime_evaluateJs() {
        runBlockingWithTimeout {

            val browser = chromeDpClient().webSocket()
            val page = browser.attachToNewPageAndAwaitPageLoad("http://google.com")
            assertEquals(42, page.runtime.evaluateJs<Int>("42"))
            assertEquals(
                42 to "test", page.runtime.evaluateJs<Pair<Int, String>>("""eval({first: 42, second: "test"})""")
            )
            assertEquals(
                Person("Bob", "Lee Swagger"),
                page.runtime.evaluateJs<Person>("""eval({firstName: "Bob", lastName: "Lee Swagger"})""")
            )
            page.close()
            browser.close()
        }
    }

    @Test
    fun getAttributes_selectedWithoutValue() {
        runBlockingWithTimeout {
            chromeDpClient().webSocket().use { browser ->
                browser.attachToNewPageAndAwaitPageLoad("https://www.htmlquick.com/reference/tags/select.html").use { page ->

                    val nodeId = page.dom.findNodeBySelector("select[name=carbrand] option[selected]")
                    val attributes1 = page.dom.getTypedAttributes(nodeId!!)
                    assertEquals(true, attributes1.selected)

                    val attributes2 = page.dom.getTypedAttributes("select[name=carbrand] option[selected]")!!
                    assertEquals(true, attributes2.selected)

                    // Attributes without value (e.g. "selected" in <option selected />) are returned as empty strings by the protocol.
                    val selected = page.dom.getAttributeValue("select[name=carbrand] option[selected]", "selected")!!
                    assertEquals("", selected)
                }
            }
        }
    }
}

private fun runBlockingWithTimeout(block: suspend CoroutineScope.() -> Unit) = runBlocking {
    withTimeout(20.seconds, block)
}
