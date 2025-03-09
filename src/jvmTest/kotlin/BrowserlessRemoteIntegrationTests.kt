import org.junit.jupiter.api.Disabled

@Disabled("Getting HTTP 429 even before reaching the quota")
class BrowserlessRemoteIntegrationTests : IntegrationTestBase() {

    private val token = System.getenv("BROWSERLESS_API_TOKEN")
        ?: error("BROWSERLESS_API_TOKEN environment variable is missing")

    override val httpUrl: String
        get() = "https://production-lon.browserless.io?token=${token}"

    override val wsConnectUrl: String
        get() = "wss://production-lon.browserless.io?token=${token}"
}
