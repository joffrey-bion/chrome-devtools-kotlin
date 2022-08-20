package org.hildan.chrome.devtools.build.names

import com.squareup.kotlinpoet.ClassName

inline class DomainNaming(
    val domainName: String,
) {
    val filename
        get() = "${domainName}Domain"

    val packageName
        get() = "$ROOT_PACKAGE_NAME.domains.${domainName.toLowerCase()}"

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
            domainName[1].isLowerCase() -> domainName.decapitalize()
            domainName.all { it.isUpperCase() } -> domainName.toLowerCase()
            else -> {
                // This handles domains starting with acronyms (DOM, CSS...) by lowercasing the whole acronym
                val firstLowercaseIndex = domainName.indexOfFirst { it.isLowerCase() }
                domainName.substring(0, firstLowercaseIndex - 1).toLowerCase() + domainName.substring(
                    firstLowercaseIndex - 1)
            }
        }

    override fun toString(): String = domainName
}

sealed class NamingConvention

data class DomainTypeNaming(
    val declaredName: String,
    val domain: DomainNaming,
) : NamingConvention()

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
