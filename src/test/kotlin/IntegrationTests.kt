import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.hildan.chrome.devtools.domains.dom.*
import org.hildan.chrome.devtools.domains.page.events.PageEvent
import org.hildan.chrome.devtools.domains.runtime.evaluateJs
import org.hildan.chrome.devtools.protocol.ChromeDPClient
import org.hildan.chrome.devtools.protocol.ExperimentalChromeApi
import org.hildan.chrome.devtools.targets.*
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.*

// workaround for https://github.com/testcontainers/testcontainers-java/issues/318
class KGenericContainer(imageName: String) : GenericContainer<KGenericContainer>(imageName)

@Testcontainers
class IntegrationTests {

    @Container
    var chromeContainer: KGenericContainer = KGenericContainer("zenika/alpine-chrome")
        .withExposedPorts(9222)
        .withCommand("--no-sandbox --remote-debugging-address=0.0.0.0 --remote-debugging-port=9222 about:blank")

    private fun chromeDpClient(): ChromeDPClient {
        val chromeDebuggerPort = chromeContainer.firstMappedPort
        return ChromeDPClient("http://localhost:$chromeDebuggerPort")
    }

    @Test
    fun httpEndpoints_meta() {
        runBlocking {
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

    @Test
    fun webSocket_basic() {
        runBlocking {
            val chrome = chromeDpClient()

            val browser = chrome.webSocket()
            val session = browser.attachToNewPageAndAwaitPageLoad("http://www.google.com")
            val targetId = session.metaData.targetId

            assertEquals("Google", session.getTargetInfo().title)

            assertTrue(chrome.targets().any { it.id == targetId }, "the new target should be listed")

            val nodeId = session.dom.findNodeBySelector("#main")
            assertNotNull(nodeId)

            val getOuterHTMLResponse = session.dom.getOuterHTML(GetOuterHTMLRequest(nodeId = nodeId))
            assertTrue(getOuterHTMLResponse.outerHTML.contains("<div class=\"content\""))

            session.close()
            assertTrue(chrome.targets().none { it.id == targetId }, "the new target should be closed (not listed)")

            browser.close()
        }
    }

    @Test
    fun pageSession_navigateAndAwaitPageLoad() {
        runBlocking {
            val chrome = chromeDpClient()

            chrome.webSocket().use { browser ->
                browser.attachToNewPageAndAwaitPageLoad("about:blank").use { page ->

                    assertEquals("about:blank", page.getTargetInfo().title)

                    page.navigateAndAwaitPageLoad("http://www.google.com")
                    assertEquals("Google", page.getTargetInfo().title)

                    val nodeId = page.dom.findNodeBySelector("#main")
                    assertNotNull(nodeId)

                    val html = page.dom.getOuterHTML(GetOuterHTMLRequest(nodeId = nodeId))
                    println(html)
                }
            }
        }
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun test_parallelPages() {
        runBlocking {
            val chrome = chromeDpClient()

            val browser = chrome.webSocket()
            coroutineScope {
                launch {
                    browser.attachToNewPage("http://www.google.com").use { page ->
                        val heapUsage = page.runtime.getHeapUsage()
                        println(heapUsage)
                        delay(500)
                        println("almost done 1")
                    }
                }
                launch {
                    browser.attachToNewPage("http://www.github.com").use { page ->
                        println(page.browser.getVersion())
                        val heapUsage = page.runtime.getHeapUsage()
                        println(heapUsage)
                        println("almost done 2")
                    }
                }
            }
            browser.close()
        }
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun page_getTargets() {
        runBlocking {
            val chrome = chromeDpClient()
            val browser = chrome.webSocket()
            val page = browser.attachToNewPage("http://google.com")
            println(page.metaData)
            page.page.enable()
            page.page.events()
                .onEach { println(it) }
                .takeWhile { it !is PageEvent.FrameStoppedLoadingEvent }
                .launchIn(this)
            page.page.frameStoppedLoading().first()

            val targets = page.target.getTargets().targetInfos
            val targetInfo = targets.first { it.targetId == page.metaData.targetId }
            assertEquals("page", targetInfo.type)
            assertTrue(targetInfo.attached)
            assertTrue(targetInfo.url.contains("www.google.com")) // redirected
            println(targets)
            page.close()
            browser.close()
        }
    }

    @Test
    fun runtime_evaluateJs() {
        runBlocking {
            @Serializable
            data class Person(val firstName: String, val lastName: String)

            val chrome = chromeDpClient()
            val browser = chrome.webSocket()
            val page = browser.attachToNewPage("http://google.com")
            assertEquals(42, page.runtime.evaluateJs<Int>("42"))
            assertEquals(
                42 to "test",
                page.runtime.evaluateJs<Pair<Int, String>>("""eval({first: 42, second: "test"})""")
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
        runBlocking {
            chromeDpClient().webSocket().use { browser ->
                browser.attachToNewPageAndAwaitPageLoad("https://www.htmlquick.com/reference/tags/select.html").use { page ->

                    val nodeId = page.dom.findNodeBySelector("select[name=carbrand] option[selected]")
                    val attributes1 = page.dom.getAttributes(nodeId!!)
                    assertEquals(true, attributes1.selected)

                    val attributes2 = page.dom.getAttributes("select[name=carbrand] option[selected]")!!
                    assertEquals(true, attributes2.selected)

                    // Attributes without value (e.g. "selected" in <option selected />) are returned as empty strings by the protocol.
                    val selected = page.dom.getAttributeValue("select[name=carbrand] option[selected]", "selected")!!
                    assertEquals("", selected)
                }
            }
        }
    }
}