package org.hildan.chrome.devtools.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
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
internal sealed class InboundFrame

/**
 * A polymorphic deserializer that picks the correct deserializer for the specific response or event frames based on
 * the presence of the `id` field in the JSON.
 */
internal object InboundFrameSerializer : JsonContentPolymorphicSerializer<InboundFrame>(InboundFrame::class) {
    // Implementation as defined by:
    // https://github.com/aslushnikov/getting-started-with-cdp/blob/master/README.md#protocol-fundamentals
    override fun selectDeserializer(element: JsonElement): KSerializer<out InboundFrame> {
        val id = element.jsonObject["id"]
        return if (id != null && id !is JsonNull) ResponseFrame.serializer() else EventFrame.serializer()
    }
}

/**
 * An event frame, received when events are enabled for a domain.
 */
@Serializable
internal data class EventFrame(
    /** The event name. */
    @SerialName("method")
    val eventName: String,

    /** The payload of this event. */
    @SerialName("params")
    val payload: JsonElement,

    /** Session ID of the target concerned by this event. */
    val sessionId: SessionID? = null,
) : InboundFrame()

/**
 * A frame received as a response to a request.
 */
@Serializable
internal data class ResponseFrame(
    /** The ID of the request that triggered this response. */
    val id: Long,

    /** Response payload. */
    val result: JsonElement? = null,

    /** Error data. Only non-null if an error occurred during request processing. */
    val error: RequestError? = null,

    /** Session ID of the target concerned by this event. */
    val sessionId: SessionID? = null,
) : InboundFrame() {

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
