package org.hildan.chrome.devtools.build.json

import kotlinx.serialization.Serializable
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.builtins.list
import kotlinx.serialization.json.Json
import java.nio.file.Path

// Data for this is read from protocol/target_types.json, which is manually extracted from:
// https://source.chromium.org/search?q=%22session-%3EAddHandler%22%20f:devtools&ss=chromium

const val ALL_DOMAINS_TARGET = "AllDomains"

@Serializable
data class TargetType(
    val name: String,
    val supportedDomains: List<String>
) {
    companion object {
        @OptIn(UnstableDefault::class)
        fun parseJson(path: Path): List<TargetType> = Json.parse(serializer().list, path.toFile().readText())
    }
}
