package org.hildan.chrome.devtools.protocol.json

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.nio.file.Path

/**
 * A target type in the general sense of "a Chromium devtools_agent_host implementation".
 * 
 * This is different from the protocol's notion of "target type". The `TargetInfo.type` field
 * [in the protocol definition](https://chromedevtools.github.io/devtools-protocol/tot/Target/#type-TargetInfo) is a
 * more fine-grained segregation of targets. Each Chromium target type implementation can actually support multiple 
 * protocol target types (see [supportedCdpTargets]).
 *
 * In addition to that, each Chromium target type implementation supports a different set of protocol domains, and this
 * information is not available in the protocol definition (alas!).
 * 
 * In Chromium's code, multiple `<name>_devtools_agent_host.cc` files correspond to these Chromium target types, and 
 * they register different sets of domains, and can handle different protocol target types. We have to read the sources
 * to know which domains and which target types are supported (see protocol/README.md).
 */
@Serializable
data class TargetType(
    /**
     * The name chosen for this target type in the Kotlin representation of chrome-devtools-kotlin.
     */
    val kotlinName: String,
    /**
     * The class in Chromium sources defining this target type.
     */
    val chromiumAgentHostType: String,
    /**
     * Target type names (as returned by the protocol in `TargetInfo.type`) supported by this Chromium target type.
     *
     * This is inferred from usages of the `const char DevToolsAgentHost`
     * [constants in Chromium's source](https://source.chromium.org/chromium/chromium/src/+/main:content/browser/devtools/devtools_agent_host_impl.cc?q=%22const%20char%20DevToolsAgentHost::kType%22)
     */
    val supportedCdpTargets: List<String>,
    /**
     * The domains supported by this target type, as found in Chromium's sources.
     * 
     * This is based on the [registration of domain handlers in Chromium's source](https://source.chromium.org/search?q=%22session-%3ECreateAndAddHandler%22%20f:devtools&ss=chromium).
     */
    val supportedDomainsInChromium: List<String>,
    /**
     * Additional domains that are effectively supported by this target type, despite not being found in Chromium
     * sources. They are demonstrably present based on integration tests with the zenika/alpine-chrome Docker image.
     */
    val additionalSupportedDomains: List<String> = emptyList(),
) {
    /**
     * The domains supported by this target type.
     */
    val supportedDomains = supportedDomainsInChromium + additionalSupportedDomains
    
    companion object {
        fun parseJson(path: Path): List<TargetType> = Json.decodeFromString(path.toFile().readText())
    }
}
