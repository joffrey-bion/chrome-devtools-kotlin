package org.hildan.chrome.devtools.protocol.preprocessing

import org.hildan.chrome.devtools.protocol.json.JsonDomain
import org.hildan.chrome.devtools.protocol.json.JsonDomainCommand
import org.hildan.chrome.devtools.protocol.json.JsonDomainParameter
import org.hildan.chrome.devtools.protocol.json.JsonDomainType

/**
 * Pre-processes the list of domains from the JSON definitions to work around some issues in the definitions themselves.
 */
internal fun List<JsonDomain>.preprocessed(): List<JsonDomain> = this
    .makeNewExperimentalPropsOptional()
    // Workaround for https://github.com/ChromeDevTools/devtools-protocol/issues/317
    .transformDomainTypeProperty("Network", "Cookie", "expires") { it.copy(optional = true) }
    // Workaround for https://issues.chromium.org/issues/444471169
    // We both add the "trustedType" enum value because we know it pops uo even though it's not in the protocol,
    // but we also mark the enum as non-exhaustive, as suggested by Google folks, because more values may be unknown.
    .transformDomainTypeProperty("Runtime", "RemoteObject", "subtype") {
        it.copy(enum = it.enum.orEmpty() + "trustedtype", isNonExhaustiveEnum = true)
    }
    // Workaround for https://github.com/ChromeDevTools/devtools-protocol/issues/244
    .map { it.pullNestedEnumsToTopLevel() }

/**
 * Modifies some new experimental properties to make them optional, so that serialization doesn't fail on stable Chrome
 * versions.
 *
 * When new properties are added to experimental types in the most recent protocol versions, the stable Chrome doesn't
 * have them yet, and serialization fails because of these missing properties. This also affects tests which are using
 * Docker containers with the latest-ish stable Chrome version.
 */
// NOTE: only add properties that are not already in the latest stable. We don't want to make everything nullable.
private fun List<JsonDomain>.makeNewExperimentalPropsOptional(): List<JsonDomain> =
    transformDomainCommandReturnProp("Runtime", "getHeapUsage", "embedderHeapUsedSize") { it.copy(optional = true) }
    .transformDomainCommandReturnProp("Runtime", "getHeapUsage", "backingStorageSize") { it.copy(optional = true) }

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

private fun List<JsonDomain>.transformDomainCommandReturnProp(
    domain: String,
    command: String,
    property: String,
    transform: (JsonDomainParameter) -> JsonDomainParameter,
): List<JsonDomain> = transformDomain(domain) { d ->
    d.transformCommand(command) { t ->
        t.transformReturnProperty(property, transform)
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

private fun JsonDomain.transformCommand(
    name: String,
    transform: (JsonDomainCommand) -> JsonDomainCommand,
): JsonDomain = copy(commands = commands.transformIf({ it.name == name }) { transform(it) })

private fun JsonDomainCommand.transformReturnProperty(
    name: String,
    transform: (JsonDomainParameter) -> JsonDomainParameter,
): JsonDomainCommand = copy(returns = returns.transformIf({ it.name == name }) { transform(it) })

private fun <T> List<T>.transformIf(predicate: (T) -> Boolean, transform: (T) -> T): List<T> =
    map { if (predicate(it)) transform(it) else it }
