@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.0")

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.net.http.HttpClient
import java.net.http.HttpRequest

// Sonatype

// Auth request
// POST https://oss.sonatype.org/service/siesta/wonderland/authenticate
// Body: {u: "the base64 user", p: "the base64 password"}
// Response: { "t" : "the auth token" }

// Key request
// GET https://oss.sonatype.org/service/siesta/usertoken/current?_dc=1603467064109
// Response: {
//  "nameCode" : "qRSOFLEZ",
//  "passCode" : "vcLryPBdB8TCT9yy6OJGECVgZKkEYVUbkSsFNP9JZYIV",
//  "created" : "2017-02-06T20:50:11.977+0000"
//}

@Serializable
data class AuthRequest(
    @SerialName("u") val user: String,
    @SerialName("p") val password: String,
)

@Serializable
data class AuthResponse(
    @SerialName("t") val token: String,
)

@Serializable
data class KeyResponse(
    val nameCode: String,
    val passCode: String,
    val created: String,
)

class OssSonatypeClient(val httpClient: HttpClient) {
    suspend fun getKey(): KeyResponse {
        httpClient.send(HttpRequest.newBuilder("https://oss.sonatype.org/service/siesta/usertoken/current?_dc=1603467064109"))
    }
}
