import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hildan.chrome.devtools.extensions.clickOnElement
import org.hildan.chrome.devtools.protocol.ChromeDPClient
import org.hildan.chrome.devtools.targets.attachToNewPageAndAwaitPageLoad
import org.hildan.chrome.devtools.targets.attachToPage
import org.hildan.chrome.devtools.targets.childPages
import java.nio.file.Paths

// Run an actual browser first:
// "C:\Program Files\Google\Chrome\Application\chrome.exe" --remote-debugging-port=9222 --user-data-dir=C:\remote-profile

fun main(): Unit = runBlocking {
    println(Paths.get("../resources/child.html").toAbsolutePath())
//    val chromeClient = ChromeDPClient()
//    val browser = chromeClient.webSocket()
//
//    println("Connected to browser")
//    delay(1000)
//
//    val page = browser.attachToNewPageAndAwaitPageLoad(
//        url = "file://C:\\Projects\\chrome-devtools-kotlin\\src\\test\\resources\\page.html"
//    )
//    println("Navigated to Google")
//    delay(5000)
//
//    page.clickOnElement(selector = "a")
//    println("Clicked on link")
//    delay(3000)
//
//    val newTarget = page.childPages().first()
//    println(newTarget)
//
//    delay(2000)
//    val page2 = browser.attachToPage(newTarget.targetId)
//    println("Attached to child page")
//
//    delay(1000)
//    page2.close(keepBrowserContext = true)
//    println("Closed to child page")
//
//    delay(3000)
//    page.close(keepBrowserContext = true)
//    println("Closed page")
//    delay(3000)
//    browser.close()
//    println("Closed browser")
}
