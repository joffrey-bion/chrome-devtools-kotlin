package org.hildan.chrome.devtools.protocol.preprocessing

import org.hildan.chrome.devtools.protocol.json.*
import org.hildan.chrome.devtools.protocol.names.*

// Workaround for https://github.com/ChromeDevTools/devtools-protocol/issues/244
fun JsonDomain.pullNestedEnumsToTopLevel(): JsonDomain = with(EnumFixer()) {
    pullNestedEnumsAsTopLevelTypes()
}

private class EnumFixer {

    val extraEnums = mutableListOf<InferredExtraEnumType>()

    fun JsonDomain.pullNestedEnumsAsTopLevelTypes(): JsonDomain {
        val domainNaming = DomainNaming(domain)
        val fixedTypes = types.map { it.replaceNestedEnumsWithReferences(domainNaming) }
        val fixedCommands = commands.map { it.replaceNestedEnumsWithReferences(domainNaming) }
        val fixedEvents = events.map { it.replaceNestedEnumsWithReferences(domainNaming) }

        return copy(
            commands = fixedCommands,
            events = fixedEvents,
            types = fixedTypes + extraEnums.deduplicate().map { it.toTypeDeclaration() },
        )
    }

    private fun JsonDomainType.replaceNestedEnumsWithReferences(domainNaming: DomainNaming): JsonDomainType {
        val thisType = DomainTypeNaming(declaredName = id, domain = domainNaming)
        return copy(properties = properties.map {
            it.replaceNestedEnumsWithReferences(
                containingType = thisType,
                containerIsDeprecated = deprecated,
                containerIsExperimental = experimental,
            )
        })
    }

    private fun JsonDomainCommand.replaceNestedEnumsWithReferences(domainNaming: DomainNaming): JsonDomainCommand {
        val commandNaming = CommandNaming(commandName = name, domain = domainNaming)
        return copy(
            parameters = parameters.map {
                it.replaceNestedEnumsWithReferences(
                    containingType = commandNaming,
                    containerIsDeprecated = deprecated,
                    containerIsExperimental = experimental,
                )
            },
            returns = returns.map {
                it.replaceNestedEnumsWithReferences(
                    containingType = commandNaming,
                    containerIsDeprecated = deprecated,
                    containerIsExperimental = experimental,
                )
            },
        )
    }

    private fun JsonDomainEvent.replaceNestedEnumsWithReferences(domainNaming: DomainNaming): JsonDomainEvent {
        val commandNaming = EventNaming(eventName = name, domain = domainNaming)
        return copy(parameters = parameters.map {
            it.replaceNestedEnumsWithReferences(
                containingType = commandNaming,
                containerIsDeprecated = deprecated,
                containerIsExperimental = experimental,
            )
        })
    }

    private fun JsonDomainParameter.replaceNestedEnumsWithReferences(
        containingType: NamingConvention,
        containerIsDeprecated: Boolean,
        containerIsExperimental: Boolean,
    ): JsonDomainParameter {
        if (type == "string" && enum != null && enum!!.isNotEmpty()) {
            val inferredName = containingType.inferParamClass(name) // TODO add domain?
            extraEnums.add(
                InferredExtraEnumType(
                    inferredName = inferredName,
                    sources = listOf(InferenceSource(name, containingType)),
                    enumValues = enum!!,
                    nonExhaustive = isNonExhaustiveEnum,
                    deprecated = deprecated,
                    experimental = experimental,
                    allContainersAreDeprecated = containerIsDeprecated,
                    allContainersAreExperimental = containerIsExperimental,
                )
            )
            return copy(
                type = null,
                enum = null,
                `$ref` = inferredName,
            )
        }
        return this
    }
}

private fun NamingConvention.inferParamClass(paramName: String) = when(this) {
    is CommandNaming -> inferParamClass(paramName)
    is DomainTypeNaming -> inferParamClass(paramName)
    is EventNaming -> inferParamClass(paramName)
}

private fun DomainTypeNaming.inferParamClass(paramName: String): String =
    "${declaredName.capitalize()}${paramName.capitalize()}".correctedName()

private fun CommandNaming.inferParamClass(paramName: String): String {
    val commandWithoutVerb = methodName.dropWhile { it.isLowerCase() }
    val capitalizedParamName = paramName.capitalize()
    val inferredName = if (commandWithoutVerb.endsWith(capitalizedParamName)) {
        commandWithoutVerb // we don't want repetition as is "DownloadBehaviorBehavior" or "WebLifecycleStateState"
    } else {
        "$commandWithoutVerb$capitalizedParamName"
    }
    return inferredName.correctedName()
}

private fun EventNaming.inferParamClass(paramName: String): String =
    "${eventName.capitalize()}${paramName.capitalize()}".correctedName()

private fun String.correctedName() = correctedWeirdNames[this] ?: this

private val correctedWeirdNames = mapOf(
    "EmitTouchEventsForMouseConfiguration" to "TouchEventsConfiguration",
    "InstrumentationBreakpointInstrumentation" to "BreakpointInstrumentation",
    "ScreenshotParamsFormat" to "ImageFormat",
    "ToLocationTargetCallFrames" to "TargetCallFrames",
    "ToPDFTransferMode" to "TransferMode",
    "TouchFromMouseEventType" to "MouseEventType",
    "TrustTokenParamsRefreshPolicy" to "TrustTokenRefreshPolicy",
)

private data class InferredExtraEnumType(
    val inferredName: String,
    val sources: List<InferenceSource>,
    val enumValues: List<String>,
    val nonExhaustive: Boolean,
    val deprecated: Boolean,
    val experimental: Boolean,
    val allContainersAreDeprecated: Boolean,
    val allContainersAreExperimental: Boolean,
) {
    val generatedDescription = "This enum doesn't have a proper description because it was generated from " +
        "${if (sources.size > 1) "inline declarations" else "an inline declaration"}. " +
        "Its name was inferred based on the place${if (sources.size > 1) "s" else ""} where it is used:\n" +
        " - ${sources.joinToString("\n - ") { it.locationDescription }}"
}

private data class InferenceSource(
    val paramName: String,
    val context: NamingConvention,
) {
    val locationDescription = when (context) {
        is DomainTypeNaming -> "the property '$paramName' of the type '${context.declaredName}'"
        is CommandNaming -> "the parameter '$paramName' of the command '${context.fullCommandName}'"
        is EventNaming -> "the parameter '$paramName' of the event '${context.fullEventName}'"
    }
}

private fun List<InferredExtraEnumType>.deduplicate(): List<InferredExtraEnumType> =
    groupBy { it.inferredName }.map { (_, homonyms) -> homonyms.merge() }

private fun List<InferredExtraEnumType>.merge(): InferredExtraEnumType = reduce { t1, t2 ->
    require(t1.enumValues == t2.enumValues) {
        "Multiple generated enums named '${t1.inferredName}', but their values differ: ${t1.enumValues} VS ${t2.enumValues}"
    }
    require(t1.deprecated == t2.deprecated) {
        "Multiple generated enums named '${t1.inferredName}', but their deprecated statuses differ. Sources:\n" +
            "  - [1] ${t1.sources.joinToString("\n  - [1] ") { "${it.locationDescription} (deprecated = ${t1.deprecated})" }}\n" +
            "  - [2] ${t2.sources.joinToString("\n  - [2] ") { "${it.locationDescription} (deprecated = ${t2.deprecated})" }}"
    }
    require(t1.experimental == t2.experimental) {
        "Multiple generated enums named '${t1.inferredName}', but their experimental statuses differ. Sources:\n" +
            "  - [1] ${t1.sources.joinToString("\n  - [1] ") { "${it.locationDescription} (experimental = ${t1.experimental})" }}\n" +
            "  - [2] ${t2.sources.joinToString("\n  - [2] ") { "${it.locationDescription} (experimental = ${t2.experimental})" }}"
    }
    t1.copy(
        sources = (t1.sources + t2.sources).distinct(),
        deprecated = t1.deprecated,
        experimental = t1.experimental,
        // If all containing declarations are deprecated, we can consider the enum deprecated too (even if not marked as
        // such). But if some containers aren't, it means the enum is still valid/maintained and just happens to be used
        // in a deprecated declaration.
        allContainersAreDeprecated = t1.allContainersAreDeprecated && t2.allContainersAreDeprecated,
        // If all containing declarations are experimental, we can consider the enum experimental too (even if not
        // marked as such). But if some containers aren't, it means the enum is stable and just happens to be used in
        // an experimental declaration.
        allContainersAreExperimental = t1.allContainersAreExperimental && t2.allContainersAreExperimental,
    )
}

private fun InferredExtraEnumType.toTypeDeclaration(): JsonDomainType = JsonDomainType(
    id = inferredName,
    description = generatedDescription,
    deprecated = deprecated || allContainersAreDeprecated,
    experimental = experimental || allContainersAreExperimental,
    type = "string",
    properties = emptyList(),
    enum = enumValues,
    items = null,
    isNonExhaustiveEnum = nonExhaustive,
)

private fun String.capitalize() = replaceFirstChar { it.titlecase() }
