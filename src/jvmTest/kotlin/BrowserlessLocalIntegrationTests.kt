import org.testcontainers.containers.*
import org.testcontainers.junit.jupiter.*
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.*
import kotlin.test.*

@Testcontainers
class BrowserlessLocalIntegrationTests : IntegrationTestBase() {

    /**
     * A container running the Browserless with Chromium support.
     * It is meant to be used mostly with the web socket API, which is accessible directly at `ws://localhost:{port}`
     * (no need for an intermediate HTTP call).
     *
     * It provides a bridge to the JSON HTTP API of the DevTools protocol as well, but only for a subset of the
     * endpoints. See [Browser REST APIs](https://docs.browserless.io/open-api#tag/Browser-REST-APIs) in the docs.
     *
     * Also, there is [a bug](https://github.com/browserless/browserless/issues/4566) with the `/json/new` endpoint.
     */
    @Container
    var browserlessChromium: GenericContainer<*> = GenericContainer("ghcr.io/browserless/chromium:latest")
        .withExposedPorts(3000)
        .withCopyFileToContainer(MountableFile.forClasspathResource("/test-server-pages/"), "/test-server-pages/")

    override val httpUrl: String
        get() = "http://localhost:${browserlessChromium.firstMappedPort}"

    override val wsConnectUrl: String
        get() = "ws://localhost:${browserlessChromium.firstMappedPort}"

    @Ignore("The /json/new endpoint doesn't work with the HTTP API of Browserless: " +
                "https://github.com/browserless/browserless/issues/4566")
    override fun httpTabEndpoints() {
    }
}
