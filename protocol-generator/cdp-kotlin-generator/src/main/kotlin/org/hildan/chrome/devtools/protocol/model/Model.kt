package org.hildan.chrome.devtools.protocol.model

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.hildan.chrome.devtools.protocol.json.*
import org.hildan.chrome.devtools.protocol.names.*
import kotlin.reflect.KClass

data class ChromeDPDomain(
    val names: DomainNaming,
    val description: String? = null,
    val deprecated: Boolean = false,
    val experimental: Boolean = false,
    val dependencies: List<String> = emptyList(),
    val types: List<DomainTypeDeclaration> = emptyList(),
    val commands: List<ChromeDPCommand> = emptyList(),
    val events: List<ChromeDPEvent> = emptyList(),
) {
    val docUrl = DocUrls.domain(names.domainName)
}

data class DomainTypeDeclaration(
    val names: DomainTypeNaming,
    val description: String? = null,
    val deprecated: Boolean = false,
    val experimental: Boolean = false,
    val type: ChromeDPType,
) {
    val docUrl = DocUrls.type(names.domain.domainName, names.declaredName)
}

data class ChromeDPCommand(
    val names: CommandNaming,
    val description: String? = null,
    val deprecated: Boolean = false,
    val experimental: Boolean = false,
    val redirect: String? = null,
    val parameters: List<ChromeDPParameter> = emptyList(),
    val returns: List<ChromeDPParameter> = emptyList(),
) {
    val docUrl = DocUrls.command(names.domain.domainName, names.methodName)
}

data class ChromeDPEvent(
    val names: EventNaming,
    val description: String? = null,
    val deprecated: Boolean = false,
    val experimental: Boolean = false,
    val parameters: List<ChromeDPParameter> = emptyList(),
) {
    val docUrl = DocUrls.event(names.domain.domainName, names.eventName)
}

data class ChromeDPParameter(
    val name: String,
    val description: String? = null,
    val deprecated: Boolean = false,
    val optional: Boolean = false,
    val experimental: Boolean = false,
    val type: TypeName,
)

fun JsonDomain.toChromeDPDomain(): ChromeDPDomain = ChromeDPDomain(
    names = DomainNaming(domain),
    description = description,
    deprecated = deprecated,
    experimental = experimental,
    dependencies = dependencies,
    types = types.map { it.toDomainTypeDeclaration(DomainNaming(domain)) },
    commands = commands.map { it.toCommand(DomainNaming(domain)) },
    events = events.map { it.toEvent(DomainNaming(domain)) },
)

private fun JsonDomainType.toDomainTypeDeclaration(domain: DomainNaming) = DomainTypeDeclaration(
    names = DomainTypeNaming(id, domain),
    description = description,
    deprecated = deprecated,
    experimental = experimental,
    type = ChromeDPType.of(
        type = type,
        properties = properties,
        enumValues = enum,
        arrayItemType = items,
        ref = null,
        domain = domain,
    ),
)

private fun JsonDomainCommand.toCommand(domain: DomainNaming): ChromeDPCommand = ChromeDPCommand(
    names = CommandNaming(name, domain),
    description = description,
    deprecated = deprecated,
    experimental = experimental,
    redirect = redirect,
    parameters = parameters.map { it.toParameter(domain) },
    returns = returns.map { it.toParameter(domain) },
)

private fun JsonDomainEvent.toEvent(domain: DomainNaming): ChromeDPEvent = ChromeDPEvent(
    names = EventNaming(name, domain),
    description = description,
    deprecated = deprecated,
    experimental = experimental,
    parameters = parameters.map { it.toParameter(domain) },
)

private fun JsonDomainParameter.toParameter(domain: DomainNaming): ChromeDPParameter {
    val type = ChromeDPType.of(
        type = type,
        properties = emptyList(),
        enumValues = enum,
        arrayItemType = items,
        ref = `$ref`,
        domain = domain,
    )
    val namedRef = (type as? ChromeDPType.NamedRef) ?: error("Nested enums or objects are not allowed in parameters")

    // we make experimental fields nullable too because they might not be present in the JSON
    val typeName = if (optional || experimental) namedRef.typeName.copy(nullable = true) else namedRef.typeName
    return ChromeDPParameter(
        name = name,
        description = description,
        deprecated = deprecated,
        optional = optional,
        experimental = experimental,
        type = typeName,
    )
}

sealed class ChromeDPType {

    sealed class NamedRef : ChromeDPType() {
        abstract val typeName: TypeName

        data class Primitive<T : Any>(val type: KClass<T>) : NamedRef() {
            override val typeName: TypeName = type.asTypeName()
        }

        data class Array(val itemType: NamedRef) : NamedRef() {
            override val typeName: TypeName = LIST.parameterizedBy(itemType.typeName)
        }

        data class Reference(override val typeName: TypeName) : NamedRef()

        object DynamicValue : NamedRef() {
            override val typeName: TypeName = JsonElement::class.asClassName()
        }

        object DynamicObject : NamedRef() {
            override val typeName: TypeName = JsonObject::class.asClassName()
        }
    }

    data class Enum(val enumValues: List<String>) : ChromeDPType()

    data class Object(val properties: List<ChromeDPParameter>) : ChromeDPType()

    companion object {
        fun of(
            type: String?,
            properties: List<JsonDomainParameter>,
            enumValues: List<String>?,
            arrayItemType: ArrayItemDescriptor?,
            ref: String?,
            domain: DomainNaming
        ) = when (type) {
            "any" -> NamedRef.DynamicValue
            "boolean" -> NamedRef.Primitive(Boolean::class)
            "integer" -> NamedRef.Primitive(Int::class)
            "number" -> NamedRef.Primitive(Double::class)
            "string" -> when {
                enumValues != null -> Enum(enumValues)
                else -> NamedRef.Primitive(String::class)
            }
            "array" -> NamedRef.Array(
                itemType = ofArrayItem(arrayItemType ?: error("Missing 'items' property on array type"), domain),
            )
            "object" -> if (properties.isEmpty()) {
                NamedRef.DynamicObject
            } else {
                Object(properties.map { it.toParameter(domain) })
            }
            null -> reference(ref ?: error("Either 'type' or '\$ref' should be present"), domain)
            else -> error("Unknown kind of type '$type'")
        }

        private fun ofArrayItem(arrayItemDescriptor: ArrayItemDescriptor, domain: DomainNaming): NamedRef = of(
            type = arrayItemDescriptor.type,
            properties = emptyList(),
            enumValues = arrayItemDescriptor.enum,
            arrayItemType = null,
            ref = arrayItemDescriptor.`$ref`,
            domain = domain,
        ) as? NamedRef ?: error("Array item type is not a named ref")

        private fun reference(ref: String, domain: DomainNaming): NamedRef.Reference {
            val actualDomain = if (ref.contains(".")) DomainNaming(ref.substringBefore('.')) else domain
            val typeName = ref.substringAfter(".")
            return NamedRef.Reference(ClassName(actualDomain.packageName, typeName))
        }
    }
}
