import org.testcontainers.containers.*
import org.testcontainers.junit.jupiter.*
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.*
import kotlin.test.Ignore

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

    override val wsConnectUrl: String
        get() = "http://localhost:${zenikaChrome.firstMappedPort}"

    @Ignore("The Zenika container seems out of data and still treats cookiePartitionKey as a string instead of object")
    override fun missingExpiresInCookie() {
    }
}
