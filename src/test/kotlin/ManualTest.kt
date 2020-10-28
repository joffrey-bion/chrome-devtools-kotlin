import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hildan.chrome.devtools.domains.dom.GetDocumentRequest
import org.hildan.chrome.devtools.domains.dom.GetOuterHTMLRequest
import org.hildan.chrome.devtools.domains.dom.QuerySelectorRequest
import org.hildan.chrome.devtools.domains.target.CloseTargetRequest
import org.hildan.chrome.devtools.protocol.ChromeDPClient
import org.hildan.chrome.devtools.targets.attachToNewPage
import kotlin.test.Test
import kotlin.test.assertTrue

// Run chrome headless docker container first:
// docker container run -d -p 9222:9222 zenika/alpine-chrome --no-sandbox --remote-debugging-address=0.0.0.0 --remote-debugging-port=9222 about:blank
@Ignore
class ManualTest {

    @Test
    fun test() {
        runBlocking(Dispatchers.Default) {
            val chrome = ChromeDPClient()
            chrome.closeAllTargets()

            val version = chrome.version()
            assertTrue(version.browser.contains("Chrome"))

            val browser = chrome.webSocket()
            val api = browser.attachToNewPage("http://www.google.com")
            api.page.enable()
            api.page.frameStoppedLoading().first()
            delay(500)
            api.dom.enable()
            val doc = api.dom.getDocument(GetDocumentRequest()).root
            val nodeId = api.dom.querySelector(QuerySelectorRequest(doc.nodeId, "#main")).nodeId
            println(nodeId)
            val html = api.dom.getOuterHTML(GetOuterHTMLRequest(nodeId = nodeId))
            println(html)
            browser.target.closeTarget(CloseTargetRequest(targetId = api.targetInfo.targetId))
        }
    }

    @Test
    fun detachedSession_nonTargetDomain_shouldFail() {
        runBlocking(Dispatchers.Default) {
            val chrome = ChromeDPClient()
            chrome.closeAllTargets()
            val target = chrome.newTab("http://www.google.com")
            val api = target.attach()
            println(api.browser.getVersion())
        }
    }
}