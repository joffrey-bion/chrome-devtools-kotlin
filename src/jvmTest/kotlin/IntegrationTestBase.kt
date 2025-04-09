import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.hildan.chrome.devtools.domains.backgroundservice.*
import org.hildan.chrome.devtools.domains.dom.*
import org.hildan.chrome.devtools.domains.domdebugger.*
import org.hildan.chrome.devtools.domains.runtime.*
import org.hildan.chrome.devtools.extensions.clickOnElement
import org.hildan.chrome.devtools.protocol.*
import org.hildan.chrome.devtools.protocol.json.*
import org.hildan.chrome.devtools.sessions.*
import org.hildan.chrome.devtools.targets.*
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val httpClientWithWs = HttpClient { install(WebSockets) }

abstract class IntegrationTestBase {

    /**
     * Must be HTTP, it's used for HTTP JSON API usage.
     */
    protected abstract val httpUrl: String

    /**
     * Can be HTTP or WS, it's used for direct web socket connection.
     */
    protected abstract val wsConnectUrl: String

    private val knownUnsupportedDomains = setOf(
        "ApplicationCache", // was removed in tip-of-tree, but still supported by the latest Chrome
        "Database",         // was removed in tip-of-tree, but still supported by the latest Chrome
    )

    protected fun chromeHttp(): ChromeDPClient = ChromeDPClient(httpUrl)

    protected suspend fun chromeWebSocket(): BrowserSession =
        if (wsConnectUrl.startsWith("http")) {
            // We enable overrideHostHeader not really to override the host header per se, but rather because the
            // Browserless container's /json/version endpoint returns a web socket URL with IP 0.0.0.0 instead of
            // localhost, leading to a connection refused error.
            // Enabling overrideHostHeader replaces the IP with the original 'localhost' host, which makes it work.
            ChromeDPClient(wsConnectUrl).webSocket()
        } else {
            httpClientWithWs.chromeWebSocket(wsConnectUrl)
        }

    @Test
    fun httpMetadataEndpoints() {
        runBlockingWithTimeout {
            val chrome = chromeHttp()

            val version = chrome.version()
            assertTrue(version.browser.contains("Chrome"))
            assertTrue(version.userAgent.contains("HeadlessChrome"))
            assertTrue(version.webSocketDebuggerUrl.startsWith("ws://"), "the debugger URL should start with ws://, but was: ${version.webSocketDebuggerUrl}")
            println("Chrome version: $version")

            val protocolJson = chrome.protocolJson()
            assertTrue(protocolJson.isNotEmpty(), "the JSON definition of the protocol should not be empty")
            val descriptor = Json.decodeFromString<ChromeProtocolDescriptor>(protocolJson)
            println("Chrome protocol JSON version: ${descriptor.version}")
        }
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun basicFlow_remote() {
        runBlockingWithTimeout {
            chromeWebSocket().use { browser ->
                val pageSession = browser.newPage()
                val targetId = pageSession.metaData.targetId

                pageSession.use { page ->
                    page.goto("http://www.google.com")

                    assertEquals("Google", page.target.getTargetInfo().targetInfo.title)

                    assertTrue(browser.target.getTargets().targetInfos.any { it.targetId == targetId }, "the new target should be listed")

                    val nodeId = withTimeoutOrNull(5.seconds) {
                        page.dom.awaitNodeBySelector("form[action='/search']")
                    }
                    assertNotNull(nodeId, "timed out while waiting for DOM node with attribute: form[action='/search']")

                    val getOuterHTMLResponse = page.dom.getOuterHTML(GetOuterHTMLRequest(nodeId = nodeId))
                    assertTrue(getOuterHTMLResponse.outerHTML.contains("<input name=\"source\""))
                }
                assertTrue(browser.target.getTargets().targetInfos.none { it.targetId == targetId }, "the new target should be closed (not listed)")
            }
        }
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun supportedDomains_all() {
        runBlockingWithTimeout {
            val client = chromeHttp()
            val descriptor = Json.decodeFromString<ChromeProtocolDescriptor>(client.protocolJson())

            val actualSupportedDomains = descriptor.domains
                .filterNot { it.domain in knownUnsupportedDomains}
                .map { it.domain }
                .toSet()
            val domainsDiff = actualSupportedDomains - knownUnsupportedDomains - AllDomainsTarget.supportedDomains
            if (domainsDiff.isNotEmpty()) {
                fail("The library should support all domains that the server actually exposes (apart from " +
                         "$knownUnsupportedDomains), but it's missing: ${domainsDiff.sorted()}")
            }
        }
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun supportedDomains_pageTarget() {
        runBlockingWithTimeout {
            chromeWebSocket().use { browser ->
                browser.newPage().use { page ->
                    page.accessibility.enable()
                    page.animation.enable()
                    page.backgroundService.clearEvents(ServiceName.backgroundFetch)
                    page.browser.getVersion()
                    // Commenting this one out until the issue is better understood
                    // https://github.com/joffrey-bion/chrome-devtools-kotlin/issues/233
                    //page.cacheStorage.requestCacheNames(RequestCacheNamesRequest("google.com"))
                    page.css.getMediaQueries()
                    page.debugger.disable()
                    page.deviceOrientation.clearDeviceOrientationOverride()
                    page.domDebugger.setDOMBreakpoint(
                        nodeId = page.dom.getDocumentRootNodeId(),
                        type = DOMBreakpointType.attributeModified,
                    )
                    page.domSnapshot.enable()
                    page.domStorage.enable()
                    page.fetch.disable()
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
                        fail("PageSession should support all domains that the server actually exposes (apart from " +
                                 "$knownUnsupportedDomains), but it's missing: ${pageDomainsDiff.sorted()}")
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
            chromeWebSocket().use { browser ->
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

    @OptIn(ExperimentalChromeApi::class)
    @Test
    open fun missingExpiresInCookie() {
        runBlockingWithTimeout {
            chromeWebSocket().use { browser ->
                browser.newPage().use { page ->
                    page.goto("https://x.com")
                    page.network.enable()
                    coroutineScope {
                        launch {
                            // ensures we don't crash on deserialization
                            page.network.responseReceivedExtraInfoEvents().first()
                        }
                        page.dom.awaitNodeBySelector("a[href=\"/login\"]")
                        page.clickOnElement("a[href=\"/login\"]")
                    }
                }
            }
        }
    }

    protected fun runBlockingWithTimeout(block: suspend CoroutineScope.() -> Unit) = runBlocking {
        withTimeout(1.minutes, block)
    }
}
