package org.hildan.chrome.devtools.protocol

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlin.reflect.*

/**
 * A serializer for forward-compatible enum types.
 */
abstract class FCEnumSerializer<FC : Any>(fcClass: KClass<FC>) : KSerializer<FC> {

    override val descriptor = PrimitiveSerialDescriptor(
        serialName = fcClass.simpleName ?: error("Cannot create serializer for anonymous class"),
        kind = PrimitiveKind.STRING,
    )

    override fun deserialize(decoder: Decoder): FC = fromCode(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: FC) {
        encoder.encodeString(codeOf(value))
    }

    abstract fun fromCode(code: String): FC
    abstract fun codeOf(value: FC): String
}
