package org.hildan.chrome.devtools.protocol.preprocessing

import org.hildan.chrome.devtools.protocol.json.JsonDomain
import org.hildan.chrome.devtools.protocol.json.JsonDomainParameter
import org.hildan.chrome.devtools.protocol.json.JsonDomainType

/**
 * Pre-processes the list of domains from the JSON definitions to work around some issues in the definitions themselves.
 */
internal fun List<JsonDomain>.preprocessed(): List<JsonDomain> = this
    // Workaround for https://github.com/ChromeDevTools/devtools-protocol/issues/317
    .transformDomainTypeProperty("Network", "Cookie", "expires") { it.copy(optional = true) }
    // Workaround for https://github.com/ChromeDevTools/devtools-protocol/issues/244
    .map { it.pullNestedEnumsToTopLevel() }

private fun List<JsonDomain>.transformDomainTypeProperty(
    domain: String,
    type: String,
    property: String,
    transform: (JsonDomainParameter) -> JsonDomainParameter,
): List<JsonDomain> = transformDomain(domain) { d ->
    d.transformType(type) { t ->
        t.transformProperty(property, transform)
    }
}

private fun List<JsonDomain>.transformDomain(
    name: String,
    transform: (JsonDomain) -> JsonDomain,
): List<JsonDomain> = transformIf({ it.domain == name }) { transform(it) }

private fun JsonDomain.transformType(
    name: String,
    transform: (JsonDomainType) -> JsonDomainType,
): JsonDomain = copy(types = types.transformIf({ it.id == name }) { transform(it) })

private fun JsonDomainType.transformProperty(
    name: String,
    transform: (JsonDomainParameter) -> JsonDomainParameter,
): JsonDomainType = copy(properties = properties.transformIf({ it.name == name }) { transform(it) })

private fun <T> List<T>.transformIf(predicate: (T) -> Boolean, transform: (T) -> T): List<T> =
    map { if (predicate(it)) transform(it) else it }
