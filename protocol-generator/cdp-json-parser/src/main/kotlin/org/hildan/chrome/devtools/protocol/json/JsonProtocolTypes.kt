package org.hildan.chrome.devtools.protocol.json

import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class ChromeProtocolDescriptor(
    val version: ChromeProtocolVersion,
    val domains: List<JsonDomain>
) {
    companion object {
        fun fromJson(json: String): ChromeProtocolDescriptor = Json.decodeFromString(json)
    }
}

@Serializable
data class ChromeProtocolVersion(
    val major: Int,
    val minor: Int
)

@Serializable
data class JsonDomain(
    val domain: String,
    val description: String? = null,
    val deprecated: Boolean = false,
    val experimental: Boolean = false,
    val dependencies: List<String> = emptyList(),
    val types: List<JsonDomainType> = emptyList(),
    val commands: List<JsonDomainCommand> = emptyList(),
    val events: List<JsonDomainEvent> = emptyList()
)

@Serializable
data class JsonDomainType(
    val id: String,
    val description: String? = null,
    val deprecated: Boolean = false,
    val experimental: Boolean = false,
    val type: String,
    val properties: List<JsonDomainParameter> = emptyList(), // only for type="object"?
    val enum: List<String>? = null, // only for type="string"?
    val items: ArrayItemDescriptor? = null // only for type="array"?
    // add maxItems/minItems? present in the go generator but never appear in the actual protocol JSONs
)

@Serializable
data class JsonDomainParameter(
    val name: String,
    val description: String? = null,
    val type: String? = null, // null if $ref is present, string (even for enum), integer, boolean, array, object
    val optional: Boolean = false,
    val deprecated: Boolean = false,
    val experimental: Boolean = false,
    val enum: List<String>? = null, // only for type="string"?
    val items: ArrayItemDescriptor? = null, // only for type="array"?
    val `$ref`: String? = null
)

@Serializable
data class ArrayItemDescriptor(
    val type: String? = null, // null if $ref is present, string (even for enum), integer, boolean, array?, object
    val enum: List<String>? = null, // only for type="string"?
    val `$ref`: String? = null
)

@Serializable
data class JsonDomainCommand(
    val name: String,
    val description: String? = null,
    val deprecated: Boolean = false,
    val experimental: Boolean = false,
    val redirect: String? = null,
    val parameters: List<JsonDomainParameter> = emptyList(),
    val returns: List<JsonDomainParameter> = emptyList()
)

@Serializable
data class JsonDomainEvent(
    val name: String,
    val description: String? = null,
    val deprecated: Boolean = false,
    val experimental: Boolean = false,
    val parameters: List<JsonDomainParameter> = emptyList()
)
