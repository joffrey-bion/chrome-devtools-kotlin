package org.hildan.chrome.devtools.protocol.json

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.nio.file.Path

// Data for this is read from protocol/target_types.json, which is manually extracted from Chromium's source.
// The doc on individual properties explains how they can be found in the source.

/**
 * A target type in the general sense of "a Chromium devtools_agent_host implementation".
 * 
 * This is different from the protocol's notion of "target type". The `TargetInfo.type` field
 * [in the protocol definition](https://chromedevtools.github.io/devtools-protocol/tot/Target/#type-TargetInfo) is a
 * more fine-grained segregation of targets. Each Chromium target type implementation can actually support multiple 
 * protocol target types (see [supportedCdpTargets]). The possible protocol target types are actually defined in terms
 * of Chromium's implementation instead of being formally specified (I have stopped losing my mind over it).
 *
 * In addition to that, each Chromium target type implementation supports a different set of protocol domains, and this
 * is NOT part of the protocol definition (alas!).
 * 
 * In Chromium's code, multiple `<name>_devtools_agent_host.cc` files correspond to these Chromium target types, and 
 * they register different sets of domains, and can handle different protocol target types. We have to read the sources
 * to know which domains and which target types are supported.
 */
@Serializable
data class TargetType(
    /**
     * The Chromium source file defining this target type.
     */
    val chromiumSourceFile: String,
    /**
     * The name of this target type in the Kotlin representation of chrome-devtools-kotlin.
     */
    val kotlinName: String,
    /**
     * Target type names (as returned by the protocol) supported by this Chromium target type.
     *
     * This is inferred from usages of the `const char DevToolsAgentHost`
     * [constants in Chromium's source](https://source.chromium.org/chromium/chromium/src/+/main:content/browser/devtools/devtools_agent_host_impl.cc;l=126-140)
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
