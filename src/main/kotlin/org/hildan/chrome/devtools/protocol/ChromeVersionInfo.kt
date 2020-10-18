package org.hildan.chrome.devtools.protocol

import io.ktor.client.HttpClient
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

data class ChromeVersionInfo(
    @SerialName("Browser") val browser: String,
    @SerialName("Protocol-Version") val protocolVersion: String,
    @SerialName("User-Agent") val userAgent: String,
    @SerialName("V8-Version") val v8Version: String? = null,
    @SerialName("WebKit-Version") val webKitVersion: String,
    @SerialName("webSocketDebuggerUrl") val webSocketDebuggerUrl: String,
) {
    companion object {
        suspend fun fetch(chromeAddress: String) = usingLenientHttpClient { client ->
            client.get<ChromeVersionInfo>("http://$chromeAddress/json/version")
        }
    }
}

private inline fun <T> usingLenientHttpClient(block: (HttpClient) -> T): T {
    val http = HttpClient {
        install(JsonFeature) {
            serializer = KotlinxSerializer(Json { ignoreUnknownKeys = true })
        }
    }
    return http.use { block(it) }
}
