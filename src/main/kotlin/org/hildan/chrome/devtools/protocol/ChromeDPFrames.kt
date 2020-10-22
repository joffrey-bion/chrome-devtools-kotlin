package org.hildan.chrome.devtools.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hildan.chrome.devtools.domains.target.SessionID

/**
 * Represents a request frame, as defined
 * [in the protocol's README](https://github.com/aslushnikov/getting-started-with-cdp/blob/master/README.md#protocol-fundamentals)
 */
@Serializable
internal data class RequestFrame(
    /**
     * Request id, must be unique.
     */
    val id: Long,

    /**
     * Session ID for Target's flatten mode requests (see [http://crbug.com/991325](http://crbug.com/991325)).
     */
    val sessionId: String? = null,

    /**
     * Protocol method (domain.method_name i.e. Page.navigateTo)
     */
    val method: String,

    /**
     * Request params (if any)
     */
    val params: JsonElement? = null
)

/**
 * Represents generic protocol response.
 */
@Serializable
internal data class InboundFrame(
    /**
     * Response id.
     */
    val id: Long? = null,

    /**
     * Response result (when responding to a request).
     */
    val result: JsonElement? = null,

    /**
     * Request error.
     */
    val error: RequestError? = null,

    /**
     * Event name.
     */
    val method: String? = null,

    /**
     * Response params (when representing an event).
     */
    val params: JsonElement? = null,

    val sessionId: SessionID? = null
) {
    /**
     * Checks if response is event
     */
    fun isEvent(): Boolean = !this.method.isNullOrEmpty()

    /**
     * Checks if response is event
     */
    private fun isResponse(): Boolean = !this.isEvent()

    fun matchesRequest(request: RequestFrame): Boolean = isResponse() &&
        id == request.id &&
        sessionId == request.sessionId
}

/**
 * Represents protocol error.
 */
@Serializable
data class RequestError(
    /**
     * Error code.
     */
    val code: Long,

    /**
     * Error message.
     */
    val message: String,

    /**
     * Associated error data.
     */
    val data: String?
)
