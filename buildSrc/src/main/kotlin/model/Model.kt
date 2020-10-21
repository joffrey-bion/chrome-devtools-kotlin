package org.hildan.chrome.devtools.build.model

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import org.hildan.chrome.devtools.build.generator.ExternalDeclarations
import org.hildan.chrome.devtools.build.json.ArrayItemDescriptor
import org.hildan.chrome.devtools.build.json.JsonDomain
import org.hildan.chrome.devtools.build.json.JsonDomainCommand
import org.hildan.chrome.devtools.build.json.JsonDomainEvent
import org.hildan.chrome.devtools.build.json.JsonDomainParameter
import org.hildan.chrome.devtools.build.json.JsonDomainType
import kotlin.reflect.KClass

typealias DomainName = String
// inline class DomainName(private val value: String)

fun DomainName.asVariableName() = when {
    this[1].isLowerCase() -> decapitalize()
    all { it.isUpperCase() } -> toLowerCase()
    else -> {
        // This handles domains starting with acronyms (DOM, CSS...) by lowercasing the whole acronym
        val firstLowercaseIndex = indexOfFirst { it.isLowerCase() }
        substring(0, firstLowercaseIndex - 1).toLowerCase() + substring(firstLowercaseIndex - 1)
    }
}

val DomainName.packageName
    get() = "${ExternalDeclarations.rootPackageName}.domains.${toLowerCase()}"

fun DomainName.asClassName() = ClassName(packageName, "${this}Domain")

// Distinct events sub-package to avoid conflicts with domain types
val DomainName.eventsPackageName
    get() = "$packageName.events"

private val DomainName.eventsParentClassName
    get() = ClassName(eventsPackageName, "${this}Event")

data class ChromeDPDomain(
    val name: DomainName,
    val description: String? = null,
    val deprecated: Boolean = false,
    val experimental: Boolean = false,
    val dependencies: List<String> = emptyList(),
    val types: List<DomainTypeDeclaration> = emptyList(),
    val commands: List<ChromeDPCommand> = emptyList(),
    val events: List<ChromeDPEvent> = emptyList()
) {
    val packageName = name.packageName

    val eventsPackageName = name.eventsPackageName

    val eventsParentClassName = name.eventsParentClassName
}

fun sanitize(domain: JsonDomain): ChromeDPDomain = ChromeDPDomain(name = domain.domain,
    description = domain.description,
    deprecated = domain.deprecated,
    experimental = domain.experimental,
    dependencies = domain.dependencies,
    types = domain.types.map { it.toTypeDeclaration(domain.domain) },
    commands = domain.commands.map { it.toCommand(domain.domain) },
    events = domain.events.map { it.toEvent(domain.domain) }
)

data class DomainTypeDeclaration(
    val name: String,
    val description: String? = null,
    val deprecated: Boolean = false,
    val experimental: Boolean = false,
    val type: ChromeDPType
)

private fun JsonDomainType.toTypeDeclaration(domainName: DomainName): DomainTypeDeclaration =
    DomainTypeDeclaration(
        name = id,
        description = description,
        deprecated = deprecated,
        experimental = experimental,
        type = ChromeDPType.of(
            type = type,
            properties = properties,
            enum = enum,
            items = items,
            domainName = domainName
        )
    )

data class ChromeDPParameter(
    val name: String,
    val description: String? = null,
    val optional: Boolean = false,
    val deprecated: Boolean = false,
    val experimental: Boolean = false,
    val type: ChromeDPType
)

private fun JsonDomainParameter.toParameter(domainName: DomainName): ChromeDPParameter = ChromeDPParameter(
    name = name,
    description = description,
    optional = optional,
    deprecated = deprecated,
    experimental = experimental,
    type = ChromeDPType.of(type = type, enum = enum, items = items, ref = `$ref`, domainName = domainName)
)

data class ChromeDPCommand(
    val name: String,
    val domainName: DomainName,
    val description: String? = null,
    val deprecated: Boolean = false,
    val experimental: Boolean = false,
    val redirect: String? = null,
    val parameters: List<ChromeDPParameter> = emptyList(),
    val returns: List<ChromeDPParameter> = emptyList()
) {
    val inputTypeName: String
        get() = "${name.capitalize()}Request"
    val outputTypeName: String
        get() = "${name.capitalize()}Response"
}

private fun JsonDomainCommand.toCommand(domainName: DomainName) = ChromeDPCommand(
    name = name,
    domainName = domainName,
    description = description,
    deprecated = deprecated,
    experimental = experimental,
    redirect = redirect,
    parameters = parameters.map { it.toParameter(domainName) },
    returns = returns.map { it.toParameter(domainName) }
)

data class ChromeDPEvent(
    val name: String,
    val domainName: DomainName,
    val description: String? = null,
    val deprecated: Boolean = false,
    val experimental: Boolean = false,
    val parameters: List<ChromeDPParameter> = emptyList()
) {
    val eventTypeName
        get() = domainName.eventsParentClassName.nestedClass("${name.capitalize()}Event")
}

private fun JsonDomainEvent.toEvent(domainName: DomainName) = ChromeDPEvent(
    name = name,
    domainName = domainName,
    description = description,
    deprecated = deprecated,
    experimental = experimental,
    parameters = parameters.map { it.toParameter(domainName) }
)

sealed class ChromeDPType {

    abstract fun toTypeName(rootPackageName: String): TypeName

    object Unknown : ChromeDPType() {
        override fun toTypeName(rootPackageName: String): TypeName = ExternalDeclarations.jsonElementClass
    }

    data class Primitive<T : Any>(val type: KClass<T>) : ChromeDPType() {
        override fun toTypeName(rootPackageName: String): TypeName = type.asTypeName()
    }

    data class Reference(val typeName: String, val domainName: String) : ChromeDPType() {
        override fun toTypeName(rootPackageName: String): TypeName = ClassName(domainName.packageName, typeName)
    }

    data class Enum(val enumValues: List<String>, val domainName: String) : ChromeDPType() {
        override fun toTypeName(rootPackageName: String): TypeName {
            // enums don't have to be declared types, but without names they have to be just strings
            return String::class.asTypeName()
        }
    }

    data class Array(val itemType: ChromeDPType) : ChromeDPType() {
        override fun toTypeName(rootPackageName: String): TypeName =
            ClassName("kotlin.collections", "List").parameterizedBy(itemType.toTypeName(rootPackageName))
    }

    data class Object(val properties: List<ChromeDPParameter>, val domainName: String) : ChromeDPType() {
        override fun toTypeName(rootPackageName: String): TypeName = error("No proper name for object type")
    }

    companion object {
        fun of(
            type: String?,
            properties: List<JsonDomainParameter> = emptyList(),
            enum: List<String>? = null,
            items: ArrayItemDescriptor? = null,
            ref: String? = null,
            domainName: String
        ) = when (type) {
            "string" -> if (enum != null) Enum(enum, domainName) else Primitive(String::class)
            "boolean" -> Primitive(Boolean::class)
            "integer" -> Primitive(Int::class)
            "number" -> Primitive(Double::class)
            "any" -> Unknown
            "array" -> Array(items?.toChromeDPType(domainName) ?: error("Missing 'items' property on array type"))
            "object" -> if (properties.isEmpty()) {
                Unknown
            } else {
                Object(properties.map { it.toParameter(domainName) }, domainName)
            }
            null -> reference(ref ?: error("Either 'type' or '\$ref' should be present"), domainName)
            else -> error("Unknown kind of type '$type'")
        }

        private fun reference(ref: String, domainName: String): Reference {
            val domain = if (ref.contains(".")) ref.substringBefore('.') else domainName
            val typeName = if (ref.contains(".")) ref.substringAfter(".") else ref
            return Reference(typeName, domain)
        }
    }
}

private fun ArrayItemDescriptor.toChromeDPType(domainName: String): ChromeDPType =
    ChromeDPType.of(type = type, enum = enum, ref = `$ref`, domainName = domainName)
