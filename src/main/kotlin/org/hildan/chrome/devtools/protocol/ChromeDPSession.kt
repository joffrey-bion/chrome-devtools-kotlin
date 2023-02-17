package org.hildan.chrome.devtools.protocol

import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.hildan.chrome.devtools.domains.inspector.events.*
import org.hildan.chrome.devtools.domains.target.events.*
import org.hildan.chrome.devtools.domains.target.SessionID
import java.util.concurrent.atomic.AtomicLong

/**
 * Creates a [ChromeDPSession] backed by this connection to handle session-scoped request IDs and filter events of
 * the session with the given [sessionId]. The session ID may be null to represent the root browser sessions.
 */
internal fun ChromeDPConnection.withSession(sessionId: SessionID?) = ChromeDPSession(this, sessionId)

/**
 * A wrapper around a [ChromeDPConnection] to handle session-scoped request IDs and filter events of a specific session.
 */
internal class ChromeDPSession(
    /**
     * The underlying connection to Chrome.
     */
    val connection: ChromeDPConnection,
    /**
     * The ID of this session, or null if this is the root browser session.
     */
    val sessionId: SessionID?,
) {
    /**
     * Ids must be unique at least within a session.
     */
    private val nextRequestId = AtomicLong(0)

    /**
     * Sends a request with the given [methodName] and [params], and suspends until the response is received.
     */
    suspend fun request(methodName: String, params: JsonElement?): ResponseFrame {
        val request = RequestFrame(
            id = nextRequestId.incrementAndGet(),
            method = methodName,
            params = params,
            sessionId = sessionId,
        )
        return connection.request(request)
    }

    /**
     * Subscribes to all events tied to this session.
     */
    fun events() = connection.events()
        .filter { it.sessionId == sessionId }
        .onEach {
            // We throw to immediately stop collectors when a target will not respond (instead of hanging).
            // Note that Inspector.targetCrashed events are received even without InspectorDomain.enable() call.
            if (it.eventName in crashEventNames) {
                throw TargetCrashedException(it.sessionId, it.eventName, it.payload)
            }
        }

    /**
     * Closes the underlying web socket connection, effectively closing every session based on the same web socket
     * connection.
     */
    suspend fun closeWebSocket() {
        connection.close()
    }
}

private val crashEventNames = setOf("Inspector.targetCrashed", "Target.targetCrashed")

/**
 * An exception thrown when an [InspectorEvent.TargetCrashed] or [TargetEvent.TargetCrashed] is received.
 */
@Suppress("CanBeParameter", "MemberVisibilityCanBePrivate")
class TargetCrashedException(
    /**
     * The session ID of the target that crashed, or null if it is the root browser target.
     */
    val sessionId: SessionID?,
    /**
     * The name of the event that triggered this exception.
     */
    val crashEventName: String,
    /**
     * The payload of the crash event that triggered this exception
     */
    val crashEventPayload: JsonElement,
) : Exception(buildTargetCrashedMessage(sessionId, crashEventName, crashEventPayload))

private fun buildTargetCrashedMessage(sessionId: SessionID?, crashEventName: String, payload: JsonElement): String {
    val payloadText = when (payload) {
        is JsonNull -> null
        is JsonPrimitive -> if (payload.isString) "\"${payload.content}\"" else payload.content
        is JsonObject -> if (payload.size > 0) payload.toString() else null
        is JsonArray -> if (payload.size > 0) payload.toString() else null
    }
    val payloadInfo = if (payloadText == null) "without payload." else "with payload: $payloadText"
    val eventInfo = "Received event '$crashEventName' $payloadInfo"
    return if (sessionId == null) {
        "The browser target has crashed. $eventInfo"
    } else {
        "The target with session ID $sessionId has crashed. $eventInfo"
    }
}
