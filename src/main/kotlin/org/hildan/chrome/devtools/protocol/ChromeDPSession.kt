package org.hildan.chrome.devtools.protocol

import kotlinx.coroutines.flow.filter
import kotlinx.serialization.json.JsonElement
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
    val connection: ChromeDPConnection,
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
    fun events() = connection.events().filter { it.sessionId == sessionId }

    /**
     * Closes the underlying web socket connection, effectively closing every session based on the same web socket
     * connection.
     */
    suspend fun closeWebSocket() {
        connection.close()
    }
}
