import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hildan.chrome.devtools.domains.target.GetTargetsRequest
import org.hildan.chrome.devtools.extensions.clickOnElement
import org.hildan.chrome.devtools.protocol.ChromeDPClient
import org.hildan.chrome.devtools.targets.attachToNewPageAndAwaitPageLoad
import org.hildan.chrome.devtools.targets.attachToPage
import org.hildan.chrome.devtools.targets.childPages
import java.nio.file.Paths

// Run an actual browser first:
// "C:\Program Files\Google\Chrome\Application\chrome.exe" --remote-debugging-port=9222 --user-data-dir=C:\remote-profile

fun main(): Unit = runBlocking {
    testCrossOriginIFrame()
}

private suspend fun testCrossOriginIFrame() {
    println(Paths.get("../resources/page-with-cross-origin-iframe.html").toAbsolutePath())
    val chromeClient = ChromeDPClient()
    val browserSession = chromeClient.webSocket()

    println("Connected to browser")
    delay(1000)

    val page = browserSession.attachToNewPageAndAwaitPageLoad(url = "file://C:\\Projects\\chrome-devtools-kotlin\\src\\test\\resources\\page-with-cross-origin-iframe.html")
    println("Navigated to page")

    val targets = browserSession.target.getTargets(GetTargetsRequest())
    println("Targets:\n${targets.targetInfos}")
    delay(1000)

    val iFrameTarget = targets.targetInfos.first { it.type == "iframe" }
    val iFrameSession = browserSession.attachToPage(iFrameTarget.targetId)

    delay(3000)

    iFrameSession.clickOnElement(selector = "a")
    println("Clicked on link")
    delay(3000)

    val newTarget = page.childPages().firstOrNull()
    println(newTarget)

    delay(1000)
    iFrameSession.close(keepBrowserContext = true)
    println("Closed to child page, but also the containing page in the process")

    delay(3000)
    browserSession.close()
    println("Closed browser connection")
}

private suspend fun testChildPage() {
    println(Paths.get("../resources/child.html").toAbsolutePath())
    val browserSession = ChromeDPClient().webSocket()

    println("Connected to browser")
    delay(1000)

    val page = browserSession.attachToNewPageAndAwaitPageLoad(url = "file://C:\\Projects\\chrome-devtools-kotlin\\src\\test\\resources\\page.html")
    println("Navigated to Google")
    delay(5000)

    page.clickOnElement(selector = "a")
    println("Clicked on link")
    delay(3000)

    val newTarget = page.childPages().first()
    println(newTarget)

    delay(2000)
    val page2 = browserSession.attachToPage(newTarget.targetId)
    println("Attached to child page")

    delay(1000)
    page2.close(keepBrowserContext = true)
    println("Closed to child page")

    delay(3000)
    page.close(keepBrowserContext = true)
    println("Closed page")
    delay(3000)
    browserSession.close()
    println("Closed browser connection")
}
