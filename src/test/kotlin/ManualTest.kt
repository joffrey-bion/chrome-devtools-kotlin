import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.hildan.chrome.devtools.domains.dom.GetOuterHTMLRequest
import org.hildan.chrome.devtools.domains.dom.findNodeBySelector
import org.hildan.chrome.devtools.domains.page.events.PageEvent
import org.hildan.chrome.devtools.domains.page.navigateAndWaitLoading
import org.hildan.chrome.devtools.domains.runtime.evaluateJs
import org.hildan.chrome.devtools.protocol.ChromeDPClient
import org.hildan.chrome.devtools.protocol.ExperimentalChromeApi
import org.hildan.chrome.devtools.targets.attachToNewPage
import org.hildan.chrome.devtools.targets.use
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Run chrome headless docker container first:
// docker container run -d -p 9222:9222 zenika/alpine-chrome --no-sandbox --remote-debugging-address=0.0.0.0 --remote-debugging-port=9222 about:blank
@Ignore
class ManualTest {

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun test() {
        runBlocking {
            val chrome = ChromeDPClient()
            chrome.closeAllTargets()

            val version = chrome.version()
            assertTrue(version.browser.contains("Chrome"))

            val browser = chrome.webSocket()
            browser.attachToNewPage("about:blank").use { page ->
                page.page.navigateAndWaitLoading("http://www.google.com")
                page.dom.enable()

                val pageJob = page.page.events()
                    .onEach { println("Page event: ${it::class.simpleName}") }
                    .launchIn(this)
                val domJob = page.dom.events()
                    .onEach { println("DOM event: ${it::class.simpleName}") }
                    .launchIn(this)

                val nodeId = page.dom.findNodeBySelector("#main")
                println(nodeId)
                val html = page.dom.getOuterHTML(GetOuterHTMLRequest(nodeId = nodeId))
                println(html)

                domJob.cancel()
                pageJob.cancel()
            }
            chrome.closeAllTargets()
        }
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun test_parallelPages() {
        runBlocking {
            val chrome = ChromeDPClient()
            chrome.closeAllTargets()

            val browser = chrome.webSocket()
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
            chrome.closeAllTargets()
        }
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun page_getTargets() {
        runBlocking {
            val chrome = ChromeDPClient()
            chrome.closeAllTargets()
            val browser = chrome.webSocket()
            val page = browser.attachToNewPage("http://google.com")
            println(page.targetInfo)
            page.page.enable()
            page.page.events()
                .onEach { println(it) }
                .takeWhile { it !is PageEvent.FrameStoppedLoadingEvent }
                .launchIn(this)
            page.page.frameStoppedLoading().first()

            val targets = page.target.getTargets().targetInfos
            assertEquals(1, targets.size) // only page targets are present
            assertEquals("page", targets[0].type)
            assertTrue(targets[0].attached)
            assertTrue(targets[0].url.contains("www.google.com")) // redirected
            println(targets)
        }
    }

    @Test
    fun runtime_evaluateJs() {
        runBlocking {
            @Serializable
            data class Person(val firstName: String, val lastName: String)

            val chrome = ChromeDPClient()
            chrome.closeAllTargets()
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
        }
    }
}