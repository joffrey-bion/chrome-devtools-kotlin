import org.testcontainers.containers.*
import org.testcontainers.junit.jupiter.*
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.*
import java.time.Duration

@Testcontainers
class BrowserlessLocalIntegrationTests : LocalIntegrationTestBase() {

    /**
     * A container running Browserless with Chromium support.
     * It is meant to be used mostly with the web socket API, which is accessible directly at `ws://localhost:{port}`
     * (no need for an intermediate HTTP call).
     *
     * It provides a bridge to the HTTP endpoints `/json/protocol` and `/json/version` of the DevTools protocol as well,
     * but not for endpoints related to tab manipulation.
     * The `/json/list` and `/json/new` endpoints are just mocked to allow some clients to get the WS URL, but they
     * don't actually reflect the reality or affect the browser's state.
     * See [Browser REST APIs](https://docs.browserless.io/open-api#tag/Browser-REST-APIs) in the docs.
     */
    @Container
    var browserlessChromium: GenericContainer<*> = GenericContainer("ghcr.io/browserless/chromium:latest")
        .withStartupTimeout(Duration.ofMinutes(5)) // sometimes more than the default 2 minutes on CI
        .withExposedPorts(3000)
        .withAccessToHost(true)

    override val httpUrl: String
        get() = "http://${browserlessChromium.host}:${browserlessChromium.getMappedPort(3000)}"

    override val wsConnectUrl: String
        get() = "ws://${browserlessChromium.host}:${browserlessChromium.getMappedPort(3000)}"
}
