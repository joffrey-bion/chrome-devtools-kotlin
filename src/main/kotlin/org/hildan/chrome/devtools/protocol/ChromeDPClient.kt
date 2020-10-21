package org.hildan.chrome.devtools.protocol

import io.ktor.client.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

    suspend fun newTab(url: String): ChromeDPTarget = httpClient.get("$remoteDebugUrl/json/new?$url")

    suspend fun activateTab(targetId: String): String = httpClient.get("$remoteDebugUrl/json/activate/$targetId")

    suspend fun closeTab(targetId: String): String = httpClient.get("$remoteDebugUrl/json/close/$targetId")
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
    suspend fun debugger(): ChromeDPSession {
        return ChromeDPConnection.open(webSocketDebuggerUrl).newSession("")
    }
}
