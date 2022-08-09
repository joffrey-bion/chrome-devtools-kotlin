package org.hildan.chrome.devtools.protocol

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

private val json = Json { ignoreUnknownKeys = true }

/**
 * Sends a request with the given [methodName] and [requestParams] object, and suspends until the response is received.
 *
 * The request payload is serialized, and the response payload is deserialized, using serializers inferred from their
 * reified types.
 */
internal suspend inline fun <reified I, reified O> ChromeDPSession.request(methodName: String, requestParams: I?): O =
    request(methodName, requestParams, serializer = serializer(), deserializer = serializer())

/**
 * Sends a request with the given [methodName] and [requestParams] object, and suspends until the response is received.
 *
 * The request payload is serialized using [serializer], and the response payload is deserialized using [deserializer].
 */
internal suspend fun <I, O> ChromeDPSession.request(
    methodName: String,
    requestParams: I?,
    serializer: SerializationStrategy<I>,
    deserializer: DeserializationStrategy<O>,
): O {
    val jsonParams = requestParams?.let { json.encodeToJsonElement(serializer, it) }
    val response = request(methodName, jsonParams)
    return json.decodeFromJsonElement(deserializer, response.payload)
}

/**
 * Subscribes to events of the given [eventName], converting their payload to instances of [E].
 */
internal inline fun <reified E> ChromeDPSession.typedEvents(eventName: String): Flow<E> = typedEvents(eventName,
    serializer())

/**
 * Subscribes to events of the given [eventName], converting their payload to instances of [E] using [deserializer].
 */
internal fun <E> ChromeDPSession.typedEvents(eventName: String, deserializer: DeserializationStrategy<E>): Flow<E> = events()
    .filter { it.eventName == eventName }
    .map { it.decodePayload(deserializer) }

/**
 * Subscribes to events whose names are in the provided [deserializers] map, converting their payload to subclasses
 * of [E] using the corresponding deserializer in the map.
 */
internal fun <E> ChromeDPSession.typedEvents(deserializers: Map<String, DeserializationStrategy<out E>>): Flow<E> = events()
    .mapNotNull { f -> deserializers[f.eventName]?.let { f.decodePayload(it) } }

private fun <T> EventFrame.decodePayload(deserializer: DeserializationStrategy<T>): T =
    json.decodeFromJsonElement(deserializer, payload)
