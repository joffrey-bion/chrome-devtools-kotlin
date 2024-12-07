package org.hildan.chrome.devtools.protocol.names

import com.squareup.kotlinpoet.ClassName

/**
 * The name of the synthetic enum entry used to represent deserialized values that are not defined in the protocol.
 */
// Note: 'unknown' and 'undefined' already exist in some of the enums, so we want to keep this one different
const val UndefinedEnumEntryName = "NotDefinedInProtocol"

@JvmInline
value class DomainNaming(
    val domainName: String,
) {
    val filename
        get() = "${domainName}Domain"

    val packageName
        get() = "$ROOT_PACKAGE_NAME.domains.${domainName.lowercase()}"

    val domainClassName
        get() = ClassName(packageName, "${domainName}Domain")

    val typesFilename
        get() = "${domainName}Types"

    val eventsFilename
        get() = "${domainName}Events"

    // Distinct events sub-package to avoid conflicts with domain types
    val eventsPackageName
        get() = "$packageName.events"

    val eventsParentClassName
        get() = ClassName(eventsPackageName, "${this}Event")

    val targetFieldName
        get() = when {
            domainName[1].isLowerCase() -> domainName.replaceFirstChar { it.lowercase() }
            domainName.all { it.isUpperCase() } -> domainName.lowercase()
            else -> {
                // This handles domains starting with acronyms (DOM, CSS...) by lowercasing the whole acronym
                val firstLowercaseIndex = domainName.indexOfFirst { it.isLowerCase() }
                domainName.substring(0, firstLowercaseIndex - 1).lowercase() + domainName.substring(
                    firstLowercaseIndex - 1)
            }
        }

    override fun toString(): String = domainName
}

sealed class NamingConvention

data class DomainTypeNaming(
    val declaredName: String,
    val domain: DomainNaming,
) : NamingConvention() {
    val packageName = domain.packageName
    val className = ClassName(packageName, declaredName)
}

data class CommandNaming(
    val commandName: String,
    val domain: DomainNaming,
) : NamingConvention() {
    val fullCommandName = "${domain.domainName}.$commandName"
    val methodName = commandName
    val inputTypeName = ClassName(domain.packageName, "${commandName.capitalize()}Request")
    val inputTypeBuilderName = inputTypeName.nestedClass("Builder")
    val outputTypeName = ClassName(domain.packageName, "${commandName.capitalize()}Response")
}

data class EventNaming(
    val eventName: String,
    val domain: DomainNaming,
) : NamingConvention() {
    val fullEventName = "${domain.domainName}.${eventName}"
    val flowMethodName = "${eventName}Events"
    val legacyMethodName = eventName
    val eventTypeName = domain.eventsParentClassName.nestedClass(eventName.capitalize())
}

private fun String.capitalize() = replaceFirstChar { it.titlecase() }
