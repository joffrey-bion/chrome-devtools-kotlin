import kotlinx.coroutines.*
import org.hildan.chrome.devtools.*
import org.hildan.chrome.devtools.domains.dom.*
import org.hildan.chrome.devtools.protocol.*
import org.hildan.chrome.devtools.sessions.*
import org.junit.jupiter.api.Test
import org.testcontainers.containers.*
import org.testcontainers.junit.jupiter.*
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.*
import java.time.*
import kotlin.test.*

@Testcontainers
class ZenikaIntegrationTests : LocalIntegrationTestBase() {

    /**
     * A container running the "raw" Chrome with support for the JSON HTTP API of the DevTools protocol (in addition to
     * the web socket API).
     *
     * One must first connect via the HTTP API at `http://localhost:{port}` and then get the web socket URL from there.
     */
    @Container
    var zenikaChrome: GenericContainer<*> = GenericContainer("zenika/alpine-chrome:latest")
        .withStartupTimeout(Duration.ofMinutes(5)) // sometimes more than the default 2 minutes on CI
        .withExposedPorts(9222)
        .withAccessToHost(true)
        .withCommand("--no-sandbox --remote-debugging-address=0.0.0.0 --remote-debugging-port=9222 about:blank")
        .withCopyFileToContainer(MountableFile.forClasspathResource("/test-server-pages/"), "/test-server-pages/")

    override val httpUrl: String
        get() = "http://${zenikaChrome.host}:${zenikaChrome.getMappedPort(9222)}"

    // the WS URL is not known in advance and needs to be queried first via the HTTP API, hence the HTTP URL here
    override val wsConnectUrl: String
        get() = "http://${zenikaChrome.host}:${zenikaChrome.getMappedPort(9222)}"

    @Ignore("The Zenika container seems out of date and still treats cookiePartitionKey as a string instead of object")
    override fun missingExpiresInCookie() {
    }

    @OptIn(LegacyChromeTargetHttpApi::class)
    @Test
    fun httpTabEndpoints_newTabWithCustomUrl() = runTestWithRealTime {
        val chrome = chromeHttp()

        val googleTab = chrome.newTab(url = "https://www.google.com")
        assertEquals("https://www.google.com", googleTab.url.trimEnd('/'))

        val targets = chrome.targets()
        assertTrue(
            actual = targets.any { it.url.trimEnd('/') == "https://www.google.com" },
            message = "the google.com page target should be listed, got:\n${targets.joinToString("\n")}",
        )

        chrome.closeTab(googleTab.id)
        delay(100) // wait for the tab to actually close (fails on CI otherwise)

        val targetsAfterClose = chrome.targets()
        assertTrue(
            actual = targetsAfterClose.none { it.url.trimEnd('/') == "https://www.google.com" },
            message = "the google.com page target should be closed, got:\n${targetsAfterClose.joinToString("\n")}",
        )
    }

    @OptIn(ExperimentalChromeApi::class)
    @Test
    fun parallelPages() = runTestWithRealTime {
        withResourceServerForTestcontainers { baseUrl ->
            chromeWebSocket().use { browser ->
                // we want all coroutines to finish before we close the browser session
                withContext(Dispatchers.IO) {
                    repeat(20) {
                        launch {
                            browser.newPage().use { page ->
                                page.goto("$baseUrl/test-server-pages/basic.html")
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
    }
}
