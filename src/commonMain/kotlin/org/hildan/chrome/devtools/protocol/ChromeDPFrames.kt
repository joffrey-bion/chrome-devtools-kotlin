package org.hildan.chrome.devtools.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.hildan.chrome.devtools.domains.target.SessionID

/**
 * [Json] serializer to use for Chrome DP frames.
 */
internal val chromeDpJson = Json {
    // frame payloads can evolve, and we shouldn't fail hard on deserialization when this happens
    ignoreUnknownKeys = true
}

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
 * General interface represent both real incoming .
 */
internal sealed interface InboundFrameOrError

/**
 * Represents errors in the incoming frames flow.
 */
internal class InboundFramesConnectionError(val cause: Throwable) : InboundFrameOrError

/**
 * A generic inbound frame received from the server. It can represent responses to requests, or server-initiated events.
 */
internal sealed class InboundFrame : InboundFrameOrError {
    /** The session ID of the target concerned by this event. */
    abstract val sessionId: SessionID?
}

/**
 * A polymorphic deserializer that picks the correct deserializer for the specific response or event frames based on
 * the JSON structure.
 */
internal object InboundFrameSerializer : JsonContentPolymorphicSerializer<InboundFrame>(InboundFrame::class) {
    // Events are distinguished from responses to requests by the absence of the 'id' field, as defined by:
    // https://github.com/aslushnikov/getting-started-with-cdp/blob/master/README.md#protocol-fundamentals
    // Successful and error responses are distinguished by the presence of either the 'result' or 'error' field.
    // Exactly one of these 2 fields must be present, see https://www.jsonrpc.org/specification#response_object
    override fun selectDeserializer(element: JsonElement): KSerializer<out InboundFrame> {
        val id = element.jsonObject["id"]
        val error = element.jsonObject["error"]
        return when {
            id == null || id is JsonNull -> EventFrame.serializer()
            error != null && error !is JsonNull -> ErrorFrame.serializer()
            else -> ResponseFrame.serializer()
        }
    }
}

/**
 * An event frame, received when events are enabled for a domain.
 */
@Serializable
internal data class EventFrame(
    /** The event name of this event (e.g. "DOM.documentUpdated"). */
    @SerialName("method") val eventName: String,

    /** The payload of this event. */
    @SerialName("params") val payload: JsonElement,

    override val sessionId: SessionID? = null,
) : InboundFrame()

/**
 * A frame received as a response to a request.
 */
@Serializable
internal sealed class ResultFrame : InboundFrame() {
    /** The ID of the request that triggered this response. */
    abstract val id: Long
}

/**
 * A successful response to a request.
 */
@Serializable
internal data class ResponseFrame(
    override val id: Long,
    override val sessionId: SessionID? = null,
    @SerialName("result") val payload: JsonElement,
) : ResultFrame()

/**
 * A frame received when there was an error processing the corresponding request.
 */
@Serializable
internal data class ErrorFrame(
    override val id: Long,
    override val sessionId: SessionID? = null,
    val error: RequestError,
) : ResultFrame()

/**
 * Data about an error that occurred when processing a request.
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
