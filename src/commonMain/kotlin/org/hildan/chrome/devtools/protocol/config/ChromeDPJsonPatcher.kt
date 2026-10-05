package org.hildan.chrome.devtools.protocol.config

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonNull

/**
 * A way to pre- or post-process JSON elements to compensate when debugger servers deviate from the protocol, or
 * when the protocol definitions don't match reality.
 */
interface ChromeDPJsonPatcher {

    /**
     * Transforms the raw JSON [params] of an instance of command of type [methodName] before serializing it.
     */
    fun patchCommand(methodName: String, params: JsonElement): JsonElement = params

    /**
     * Transforms the raw JSON [payload] received in response of [methodName] before deserializing it.
     */
    fun patchResponse(methodName: String, payload: JsonElement): JsonElement = payload

    /**
     * Transforms the raw JSON [payload] of a received event before deserializing it.
     */
    fun patchEvent(eventName: String, payload: JsonElement): JsonElement = payload
}

class ChromeDPJsonPatcherBuilder internal constructor() {
    internal val eventTransformations = mutableMapOf<String, (params: JsonElement) -> JsonElement>()
    internal val commandTransformations = mutableMapOf<String, (params: JsonElement) -> JsonElement>()
    internal val responseTransformations = mutableMapOf<String, (params: JsonElement) -> JsonElement>()

    /**
     * Adds a transformation that changes the payload of all received events of type [eventName] before they are
     * deserialized.
     *
     * If this function is called multiple times with the same [eventName], the given [transform]s are combined as a
     * sequence of transformations.
     */
    fun transformEvents(eventName: String, transform: (payload: JsonElement) -> JsonElement) {
        eventTransformations[eventName] = eventTransformations[eventName]?.then(transform) ?: transform
    }

    /**
     * Adds a transformation that changes the payload of all commands of type [methodName] before they are
     * serialized and sent on the wire.
     *
     * If this function is called multiple times with the same [methodName], the given [transform]s are combined as a
     * sequence of transformations.
     */
    fun transformCommands(methodName: String, transform: (params: JsonElement) -> JsonElement) {
        commandTransformations[methodName] = commandTransformations[methodName]?.then(transform) ?: transform
    }

    /**
     * Adds a transformation that changes the payload of all received responses to commands of type [methodName] before
     * they are deserialized.
     *
     * If this function is called multiple times with the same [methodName], the given [transform]s are combined as a
     * sequence of transformations.
     */
    fun transformResponses(methodName: String, transform: (payload: JsonElement) -> JsonElement) {
        responseTransformations[methodName] = responseTransformations[methodName]?.then(transform) ?: transform
    }

    private fun ((JsonElement) -> JsonElement).then(other: (JsonElement) -> JsonElement): (JsonElement) -> JsonElement =
        { element -> other.invoke(this.invoke(element)) }

    internal fun build(): ChromeDPJsonPatcher = object : ChromeDPJsonPatcher {
        override fun patchEvent(eventName: String, payload: JsonElement): JsonElement =
            eventTransformations[eventName]?.invoke(payload) ?: payload

        override fun patchCommand(methodName: String, params: JsonElement): JsonElement =
            commandTransformations[methodName]?.invoke(params) ?: params

        override fun patchResponse(methodName: String, payload: JsonElement): JsonElement =
            responseTransformations[methodName]?.invoke(payload) ?: payload
    }
}