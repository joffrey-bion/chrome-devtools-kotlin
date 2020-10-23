import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.hildan.chrome.devtools.domains.dom.GetDocumentRequest
import org.hildan.chrome.devtools.domains.dom.GetOuterHTMLRequest
import org.hildan.chrome.devtools.domains.dom.QuerySelectorRequest
import org.hildan.chrome.devtools.domains.target.CloseTargetRequest
import org.hildan.chrome.devtools.protocol.ChromeDPClient
import kotlin.test.Ignore
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
            val version = chrome.version()
            assertTrue(version.browser.contains("Chrome"))
            val targets = chrome.targets()
            targets.forEach { chrome.closeTab(it.id) }
            val target = chrome.newTab("http://www.google.com")
            val api = target.attach()
            api.dom.enable()
            val doc = api.dom.getDocument(GetDocumentRequest()).root
            val nodeId = api.dom.querySelector(QuerySelectorRequest(doc.nodeId, "#main")).nodeId
            println(nodeId)
            val html = api.dom.getOuterHTML(GetOuterHTMLRequest(nodeId = nodeId))
            println(html)
            chrome.webSocket().target.closeTarget(CloseTargetRequest(targetId = target.id))
        }
    }

    @Test
    fun detachedSession_nonTargetDomain_shouldFail() {
        runBlocking(Dispatchers.Default) {
            val chrome = ChromeDPClient()
            val version = chrome.version()
            assertTrue(version.browser.contains("Chrome"))
            val targets = chrome.targets()
            targets.forEach { chrome.closeTab(it.id) }
            val target = chrome.newTab("http://www.google.com")
            val api = target.attach()
            println(api.browser.getVersion())
        }
    }
}