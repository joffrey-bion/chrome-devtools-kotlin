package org.hildan.chrome.devtools.protocol

import io.ktor.client.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hildan.chrome.devtools.ChromeApi
import org.hildan.chrome.devtools.domains.target.TargetID

private fun ktorClientWithJson() = HttpClient {
    install(JsonFeature) {
        serializer = KotlinxSerializer(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
    }
}

class ChromeDPClient(
    private val remoteDebugUrl: String = "http://localhost:9222",
    private val httpClient: HttpClient = ktorClientWithJson()
) {
    suspend fun version(): ChromeVersion = httpClient.get("$remoteDebugUrl/json/version")

    suspend fun targets(): List<ChromeDPTarget> = httpClient.get("$remoteDebugUrl/json/list")

    suspend fun protocolJson(): String = httpClient.get("$remoteDebugUrl/json/protocol")

    suspend fun newTab(url: String = "about:blank"): ChromeDPTarget = httpClient.get("$remoteDebugUrl/json/new?$url")

    suspend fun activateTab(targetId: String): String = httpClient.get("$remoteDebugUrl/json/activate/$targetId")

    suspend fun closeTab(targetId: String): String = httpClient.get("$remoteDebugUrl/json/close/$targetId")

    suspend fun detachedWebSocketDebugger(): ChromeApi {
        val browserDebuggerUrl = version().webSocketDebuggerUrl
        return ChromeDPConnection.open(browserDebuggerUrl).detachedSession().api()
    }
}

@Serializable
data class ChromeVersion(
    @SerialName("Browser") val browser: String,
    @SerialName("Protocol-Version") val protocolVersion: String,
    @SerialName("User-Agent") val userAgent: String,
    @SerialName("V8-Version") val v8Version: String? = null,
    @SerialName("WebKit-Version") val webKitVersion: String,
    @SerialName("webSocketDebuggerUrl") val webSocketDebuggerUrl: String,
)

@Serializable
data class ChromeDPTarget(
    val id: String,
    val title: String,
    val type: String,
    val description: String,
    val devtoolsFrontendUrl: String,
    val webSocketDebuggerUrl: String,
) {
    suspend fun attach(): ChromeApi {
        val connection = ChromeDPConnection.open(webSocketDebuggerUrl)
        return connection.detachedSession().attach(id).api()
    }

    suspend inline fun <T> use(block: (ChromeApi) -> T): T {
        val api = attach()
        try {
            return block(api)
        } finally {
            api.close()
        }
    }
}

suspend fun ChromeApi.attach(targetId: TargetID): ChromeApi = session.attach(targetId).api()
