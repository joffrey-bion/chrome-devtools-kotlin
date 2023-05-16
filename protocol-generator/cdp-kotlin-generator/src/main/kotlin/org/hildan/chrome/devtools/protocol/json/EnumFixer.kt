package org.hildan.chrome.devtools.protocol.json

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
            types = fixedTypes + extraEnums.deduplicate().map { it.toTypeDeclaration() }
        )
    }

    private fun JsonDomainType.replaceNestedEnumsWithReferences(domainNaming: DomainNaming): JsonDomainType {
        val thisType = DomainTypeNaming(declaredName = id, domain = domainNaming)
        return copy(properties = properties.map { it.replaceNestedEnumsWithReferences(thisType) })
    }

    private fun JsonDomainCommand.replaceNestedEnumsWithReferences(domainNaming: DomainNaming): JsonDomainCommand {
        val commandNaming = CommandNaming(commandName = name, domain = domainNaming)
        return copy(
            parameters = parameters.map { it.replaceNestedEnumsWithReferences(commandNaming) },
            returns = returns.map { it.replaceNestedEnumsWithReferences(commandNaming) },
        )
    }

    private fun JsonDomainEvent.replaceNestedEnumsWithReferences(domainNaming: DomainNaming): JsonDomainEvent {
        val commandNaming = EventNaming(eventName = name, domain = domainNaming)
        return copy(parameters = parameters.map { it.replaceNestedEnumsWithReferences(commandNaming) })
    }

    private fun JsonDomainParameter.replaceNestedEnumsWithReferences(containingType: NamingConvention): JsonDomainParameter {
        if (type == "string" && enum != null && enum!!.isNotEmpty()) {
            val inferredName = containingType.inferParamClass(name) // TODO add domain?
            extraEnums.add(
                InferredExtraEnumType(
                    inferredName = inferredName,
                    sources = listOf(InferenceSource(name, containingType)),
                    enumValues = enum!!,
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
) {
    val generatedDescription = "This enum doesn't have a proper description because it was generated from " +
        "${if (sources.size > 1) "" else "an "}inline declaration${if (sources.size > 1) "s" else ""}. " +
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
    t1.copy(sources = (t1.sources + t2.sources).distinct())
}

private fun InferredExtraEnumType.toTypeDeclaration(): JsonDomainType = JsonDomainType(
    id = inferredName,
    description = generatedDescription,
    deprecated = false,
    experimental = false,
    type = "string",
    properties = emptyList(),
    enum = enumValues,
    items = null,
)
