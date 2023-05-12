package org.hildan.chrome.devtools.build.json

import kotlinx.serialization.Serializable
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.nio.file.Path

// Data for this is read from protocol/target_types.json, which is manually extracted from Chromium's source.
// The doc on individual properties explains how they can be found in the source.

@Serializable
data class TargetType(
    /**
     * The name of this target type, corresponding to the prefix of the agent host type in Chromium's code.
     * `<name>_devtools_agent_host.cc`
     */
    val name: String,
    /**
     * Target type names (as returned by the protocol) supported by this target type.
     *
     * This is inferred from usages of these
     * [constants in Chromium's source](https://source.chromium.org/chromium/chromium/src/+/main:content/browser/devtools/devtools_agent_host_impl.cc;l=66-75)
     */
    val supportedCdpTargets: List<String>,
    /**
     * The domains supported by this target type.
     *
     * This is inferred from the
     * [handler declarations in Chromium's source](https://source.chromium.org/search?q=%22session-%3ECreateAndAddHandler%22%20f:devtools&ss=chromium).
     */
    val supportedDomains: List<String>,
) {
    companion object {
        fun parseJson(path: Path): List<TargetType> = Json.decodeFromString(path.toFile().readText())
    }
}
