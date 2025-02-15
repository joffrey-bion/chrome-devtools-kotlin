import org.hildan.chrome.devtools.protocol.ChromeDPTarget
import org.junit.jupiter.api.Test
import org.testcontainers.containers.*
import org.testcontainers.junit.jupiter.*
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.*
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        .withExposedPorts(9222)
        .withCommand("--no-sandbox --remote-debugging-address=0.0.0.0 --remote-debugging-port=9222 about:blank")
        .withCopyFileToContainer(MountableFile.forClasspathResource("/test-server-pages/"), "/test-server-pages/")

    override val httpUrl: String
        get() = "http://localhost:${zenikaChrome.firstMappedPort}"

    // the WS URL is not known in advance and needs to be queried first via the HTTP API, hence the HTTP URL here
    override val wsConnectUrl: String
        get() = "http://localhost:${zenikaChrome.firstMappedPort}"

    @Ignore("The Zenika container seems out of date and still treats cookiePartitionKey as a string instead of object")
    override fun missingExpiresInCookie() {
    }

    @Suppress("DEPRECATION") // the point is to test this deprecated API
    @Test
    fun httpTabEndpoints_newTabWithCustomUrl() {
        runBlockingWithTimeout {
            val chrome = chromeHttp()

            val googleTab = chrome.newTab(url = "https://www.google.com")
            assertEquals("https://www.google.com", googleTab.url.trimEnd('/'))

            val targets = chrome.targets()
            assertTrue(
                actual = targets.any { it.url.trimEnd('/') == "https://www.google.com" },
                message = "the google.com page target should be listed, got:\n${targets.joinToString("\n")}",
            )

            chrome.closeTab(googleTab.id)

            val targetsAfterClose = chrome.targets()
            assertTrue(
                actual = targetsAfterClose.none { it.url.trimEnd('/') == "https://www.google.com" },
                message = "the google.com page target should be closed, got:\n${targetsAfterClose.joinToString("\n")}",
            )
        }
    }
}
