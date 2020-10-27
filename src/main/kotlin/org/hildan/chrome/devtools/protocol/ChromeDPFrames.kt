package org.hildan.chrome.devtools.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hildan.chrome.devtools.domains.target.SessionID

/**
 * A request frame which can be sent to the server, as defined
 * [in the protocol's README](https://github.com/aslushnikov/getting-started-with-cdp/blob/master/README.md#protocol-fundamentals)
 */
@Serializable
data class RequestFrame(
    /** Request id, must be unique in the current session. */
    val id: Long,

    /** Protocol method (e.g. Page.navigateTo). */
    val method: String,

    /** Request params (if any). */
    val params: JsonElement? = null,

    /** Session ID for Target's flatten mode requests (see [http://crbug.com/991325](http://crbug.com/991325)). */
    val sessionId: String? = null,
)

/**
 * A generic inbound frame received from the server. It can represent responses to requests, or server-initiated events.
 */
@Serializable
internal data class InboundFrame(
    /** The ID of the request that triggered this response (only present on responses, not on events). */
    val id: Long? = null,

    /** Response result (when responding to a request). */
    val result: JsonElement? = null,

    /**
     * Error data.
     * Only present if this frame represents a response, and an error occurred during request processing.
     */
    val error: RequestError? = null,

    /** Event name. */
    val method: String? = null,

    /** Event params (only when representing an event). */
    val params: JsonElement? = null,

    /** Session ID of the target concerned by this event. */
    val sessionId: SessionID? = null,
) {
    /** True if this is an event (as opposed to a response to a request). */
    // Implementation as defined by:
    // https://github.com/aslushnikov/getting-started-with-cdp/blob/master/README.md#protocol-fundamentals
    fun isEvent(): Boolean = id == null

    /** Checks if this frame is a response to the given request. */
    fun matchesRequest(request: RequestFrame): Boolean = id == request.id && sessionId == request.sessionId
}

/**
 * Represents protocol error.
 */
@Serializable
data class RequestError(
    /** Error code. */
    val code: Long,

    /** Error message. */
    val message: String,

    /** Associated error data. */
    val data: JsonElement? = null
)
