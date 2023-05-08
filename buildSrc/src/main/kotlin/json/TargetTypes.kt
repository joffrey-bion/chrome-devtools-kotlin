package org.hildan.chrome.devtools.build.json

import kotlinx.serialization.Serializable
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.nio.file.Path

// Data for this is read from protocol/target_types.json, which is manually extracted from:
// https://source.chromium.org/search?q=%22session-%3EAddHandler%22%20f:devtools&ss=chromium

@Serializable
data class TargetType(
    val name: String,
    val supportedDomains: List<String>
) {
    companion object {
        fun parseJson(path: Path): List<TargetType> = Json.decodeFromString(path.toFile().readText())
    }
}
