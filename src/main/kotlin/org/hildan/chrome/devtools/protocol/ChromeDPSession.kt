package org.hildan.chrome.devtools.protocol

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.hildan.chrome.devtools.ChromeApi
import org.hildan.chrome.devtools.domains.target.AttachToTargetRequest
import org.hildan.chrome.devtools.domains.target.SessionID
import org.hildan.chrome.devtools.domains.target.TargetID

internal fun ChromeDPConnection.detachedSession() = ChromeDPSession(this, null)

internal fun ChromeDPSession.api() = ChromeApi(this)

internal suspend fun ChromeDPSession.attach(targetId: TargetID): ChromeDPSession {
    val sessionId = api().target.attachToTarget(AttachToTargetRequest(targetId = targetId, flatten = true)).sessionId
    return ChromeDPSession(connection, sessionId)
}

class ChromeDPSession internal constructor(
    internal val connection: ChromeDPConnection,
    val sessionId: SessionID?,
) {
    internal suspend inline fun <reified I> request(methodName: String, requestParams: I?): InboundFrame =
        request(methodName, requestParams, serializer())

    internal suspend fun <I> request(methodName: String, requestParams: I?, serializer: SerializationStrategy<I>): InboundFrame {
        val params = requestParams?.let { json.encodeToJsonElement(serializer, it) }
        return connection.request(methodName, params, sessionId)
    }

    suspend inline fun <reified I, reified O : Any> requestForResult(methodName: String, requestParams: I?): O =
        requestForResult(methodName, requestParams, serializer = serializer(), deserializer = serializer())

    /**
     * Sends request and captures response from the stream.
     */
    suspend fun <I, O : Any> requestForResult(
        methodName: String,
        requestParams: I?,
        serializer: SerializationStrategy<I>,
        deserializer: DeserializationStrategy<O>,
    ): O = request(methodName, requestParams, serializer).decodeResponsePayload(deserializer)

    /**
     * Subscribes to events of the given [eventName], converting their payload to instances of [E].
     */
    inline fun <reified E> events(eventName: String): Flow<E> = events(eventName, serializer())

    /**
     * Subscribes to events of the given [eventName], converting their payload to instances of [E] using [deserializer].
     */
    fun <E> events(eventName: String, deserializer: DeserializationStrategy<E>): Flow<E> = connection.events()
        .filter { it.sessionId == sessionId }
        .filter { it.method == eventName }
        .map { it.decodeEventPayload(deserializer) }

    /**
     * Subscribes to events whose names are in the provided [deserializers] map, converting their payload to subclasses
     * of [E] using the corresponding deserializer in the map.
     */
    fun <E> events(deserializers: Map<String, DeserializationStrategy<out E>>): Flow<E> = connection.events()
        .filter { it.sessionId == sessionId }
        .mapNotNull { f -> deserializers[f.method]?.let { f.decodeEventPayload(it) } }

    suspend fun close() {
        connection.close()
    }
}

private val json = Json { ignoreUnknownKeys = true }

private fun <T> InboundFrame.decodeResponsePayload(deserializer: DeserializationStrategy<T>): T =
        json.decodeFromJsonElement(deserializer, result ?: error("Missing result in response"))

private fun <T> InboundFrame.decodeEventPayload(deserializer: DeserializationStrategy<T>): T =
        json.decodeFromJsonElement(deserializer, params ?: error("Missing params field in event"))
