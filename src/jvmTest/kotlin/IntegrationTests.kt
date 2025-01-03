import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.hildan.chrome.devtools.domains.backgroundservice.ServiceName
import org.hildan.chrome.devtools.domains.dom.*
import org.hildan.chrome.devtools.domains.domdebugger.DOMBreakpointType
import org.hildan.chrome.devtools.domains.runtime.evaluateJs
import org.hildan.chrome.devtools.protocol.ChromeDPClient
import org.hildan.chrome.devtools.protocol.ExperimentalChromeApi
import org.hildan.chrome.devtools.protocol.RequestNotSentException
import org.hildan.chrome.devtools.protocol.json.*
import org.hildan.chrome.devtools.sessions.*
import org.hildan.chrome.devtools.sessions.use
import org.hildan.chrome.devtools.targets.*
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.*
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Testcontainers
class IntegrationTests {

    @Container
    var chromeContainer: GenericContainer<*> = GenericContainer("zenika/alpine-chrome")
        .withExposedPorts(9222)
        .withCommand("--no-sandbox --remote-debugging-address=0.0.0.0 --remote-debugging-port=9222 about:blank")
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("/test-server-pages/"),
            "/test-server-pages/"
        )

    private fun chromeDpClient(): ChromeDPClient {
        val chromeDebuggerPort = chromeContainer.firstMappedPort
        return ChromeDPClient("http://localhost:$chromeDebuggerPort")
    }

    private suspend fun PageSession.gotoTestPageResource(resourcePath: String) {
        goto("file:///test-server-pages/$resourcePath")
    }
    
    @Test
    fun httpEndpoints_meta() {
        runBlockingWithTimeout {
            val chrome = chromeDpClient()

            val version = chrome.version()
            assertTrue(version.browser.contains("Chrome"))
            assertTrue(version.userAgent.contains("HeadlessChrome"))
            assertTrue(version.webSocketDebuggerUrl.startsWith("ws://localhost"))

            val protocolJson = chrome.protocolJson()
            assertTrue(protocolJson.isNotEmpty(), "the JSON definition of the protocol should not be empty")

            val targets = chrome.targets()
            assertTrue(targets.isNotEmpty(), "there should be at least the about:blank target")

            @Suppress("DEPRECATION") // the point is to test this deprecated API
            val googleTab = chrome.newTab("https://www.google.com")
            assertEquals("https://www.google.com", googleTab.url.trimEnd('/'))
            chrome.closeTab(googleTab.id)
        }
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun webSocket_basic() {
        runBlockingWithTimeout {
            val chrome = chromeDpClient()

            chrome.webSocket().use { browser ->
                val pageSession = browser.newPage()
                val targetId = pageSession.metaData.targetId

                pageSession.use { page ->
                    page.goto("http://www.google.com")

                    assertEquals("Google", page.target.getTargetInfo().targetInfo.title)

                    assertTrue(chrome.targets().any { it.id == targetId }, "the new target should be listed")

                    val nodeId = withTimeoutOrNull(5.seconds) {
                        page.dom.awaitNodeBySelector("form[action='/search']")
                    }
                    assertNotNull(nodeId, "timed out while waiting for DOM node with attribute: form[action='/search']")

                    val getOuterHTMLResponse = page.dom.getOuterHTML(GetOuterHTMLRequest(nodeId = nodeId))
                    assertTrue(getOuterHTMLResponse.outerHTML.contains("<input name=\"source\""))
                }
                assertTrue(chrome.targets().none { it.id == targetId }, "the new target should be closed (not listed)")
            }
        }
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun sessionThrowsIOExceptionIfAlreadyClosed() {
        runBlockingWithTimeout {
            val chrome = chromeDpClient()

            val browser = chrome.webSocket()
            val session = browser.newPage()
            session.goto("http://www.google.com")

            browser.close()

            assertFailsWith<RequestNotSentException> {
                session.target.getTargetInfo().targetInfo
            }
        }
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun pageSession_goto() {
        runBlockingWithTimeout {
            chromeDpClient().webSocket().use { browser ->
                browser.newPage().use { page ->
                    page.goto("https://kotlinlang.org/")
                    assertEquals("Kotlin Programming Language", page.target.getTargetInfo().targetInfo.title)

                    page.goto("http://www.google.com")
                    assertEquals("Google", page.target.getTargetInfo().targetInfo.title)

                    val nodeId = withTimeoutOrNull(5.seconds) {
                        page.dom.awaitNodeBySelector("form[action='/search']")
                    }
                    assertNotNull(nodeId, "timed out while waiting for DOM node with attribute: form[action='/search']")
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
                withContext(Dispatchers.IO) {
                    repeat(4) {
                        browser.newPage().use { page ->
                            page.goto("http://www.google.com")
                            page.runtime.getHeapUsage()
                            val docRoot = page.dom.getDocumentRootNodeId()
                            page.dom.describeNode(DescribeNodeRequest(docRoot, depth = 2))
                            page.storage.getCookies()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun page_getTargets() {
        runBlockingWithTimeout {
            chromeDpClient().webSocket().use { browser ->
                browser.newPage().use { page ->
                    page.goto("http://www.google.com")
                    val targets = page.target.getTargets().targetInfos
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
            val client = chromeDpClient()
            val descriptor = Json.decodeFromString<ChromeProtocolDescriptor>(client.protocolJson())

            val knownUnsupportedDomains = setOf(
                "ApplicationCache", // was removed in tip-of-tree, but still supported by the container
            )
            val actualSupportedDomains = descriptor.domains
                .filterNot { it.domain in knownUnsupportedDomains}
                .map { it.domain }
                .toSet()
            val domainsDiff = actualSupportedDomains - knownUnsupportedDomains - AllDomainsTarget.supportedDomains
            if (domainsDiff.isNotEmpty()) {
                fail("The library should support all domains that the ${chromeContainer.dockerImageName} container" +
                         "actually exposes (apart from $knownUnsupportedDomains), but it's missing: ${domainsDiff.sorted()}")
            }

            client.webSocket().use { browser ->
                browser.newPage().use { page ->
                    page.accessibility.enable()
                    page.animation.enable()
                    page.backgroundService.clearEvents(ServiceName.backgroundFetch)
                    page.browser.getVersion()
                    // Commenting this one out until the issue is better understood
                    // https://github.com/joffrey-bion/chrome-devtools-kotlin/issues/233
                    //page.cacheStorage.requestCacheNames(RequestCacheNamesRequest("google.com"))
                    page.css.getMediaQueries()
                    page.database.enable()
                    page.debugger.disable()
                    page.deviceOrientation.clearDeviceOrientationOverride()
                    page.domDebugger.setDOMBreakpoint(
                        nodeId = page.dom.getDocumentRootNodeId(),
                        type = DOMBreakpointType.attributeModified,
                    )
                    page.domSnapshot.enable()
                    page.domStorage.enable()
                    page.fetch.disable()
                    @Suppress("DEPRECATION") // it's the only working function
                    page.headlessExperimental.enable()
                    page.heapProfiler.enable()
                    page.indexedDB.enable()
                    page.layerTree.enable()
                    page.performance.disable()
                    page.profiler.disable()
                    page.runtime.enable()

                    // We cannot replace this schema Domain call by an HTTP call to /json/protocol, because
                    // the protocol JSON contains the list of all domains, not just for the page target type.
                    @Suppress("DEPRECATION")
                    val actualPageDomains = page.schema.getDomains().domains.map { it.name }.toSet()

                    val pageDomainsDiff = actualPageDomains - knownUnsupportedDomains - PageTarget.supportedDomains
                    if (pageDomainsDiff.isNotEmpty()) {
                        fail("PageSession should support all domains that the ${chromeContainer.dockerImageName} " +
                                 "container actually exposes (apart from $knownUnsupportedDomains), but it's missing: ${pageDomainsDiff.sorted()}")
                    }
                }
            }
        }
    }

    @Serializable
    data class Person(val firstName: String, val lastName: String)

    @Test
    fun runtime_evaluateJs() {
        runBlockingWithTimeout {
            chromeDpClient().webSocket().use { browser ->
                browser.newPage().use { page ->
                    assertEquals(42, page.runtime.evaluateJs<Int>("42"))
                    assertEquals(
                        42 to "test",
                        page.runtime.evaluateJs<Pair<Int, String>>("""eval({first: 42, second: "test"})""")
                    )
                    assertEquals(
                        Person("Bob", "Lee Swagger"),
                        page.runtime.evaluateJs<Person>("""eval({firstName: "Bob", lastName: "Lee Swagger"})""")
                    )
                }
            }
        }
    }

    @Test
    fun attributesAccess() {
        runBlockingWithTimeout {
            chromeDpClient().webSocket().use { browser ->
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

private fun runBlockingWithTimeout(block: suspend CoroutineScope.() -> Unit) = runBlocking {
    withTimeout(1.minutes, block)
}
