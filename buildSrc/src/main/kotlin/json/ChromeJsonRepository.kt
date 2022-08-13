package org.hildan.chrome.devtools.build.json

import java.net.URL

internal object ChromeJsonRepository {

    private const val rawFilesBaseUrl = "https://raw.githubusercontent.com/ChromeDevTools/devtools-protocol"

    fun descriptorUrls(revision: String) = JsonDescriptors(
        browserProtocolUrl = rawContentUrl(revision, "json/browser_protocol.json"),
        jsProtocolUrl = rawContentUrl(revision, "json/js_protocol.json"),
    )

    fun fetchProtocolNpmVersion(revision: String): String {
        val packageJson = rawContentUrl(revision, "package.json").readText()
        return Regex(""""version"\s*:\s*"([^"]+)"""").find(packageJson)?.groupValues?.get(1) ?: "not-found"
    }

    private fun rawContentUrl(revision: String, pathInRepo: String) = URL("$rawFilesBaseUrl/$revision/$pathInRepo")
}

data class JsonDescriptors(
    val browserProtocolUrl: URL,
    val jsProtocolUrl: URL,
) {
    val all = listOf(browserProtocolUrl, jsProtocolUrl)
}
