package org.hildan.chrome.devtools.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.enums.EnumEntries

/**
 * A forward-compatible enum that allows manipulating a type-safe set of values, but also possibly unknown future
 * values. Unknown values still allow to
 */
@Serializable(with = FCEnumSerializer::class)
sealed interface FCEnum<E : Enum<E>> {

    data class Known<E : Enum<E>>(val value: E) : FCEnum<E>

    data class Unknown<E : Enum<E>>(val value: String) : FCEnum<E>
}

@OptIn(ExperimentalContracts::class)
fun <E : Enum<E>> FCEnum<E>.knownOrNull(): E? {
    contract {
        returnsNotNull() implies (this@knownOrNull is FCEnum.Known<E>)
        returns(null) implies (this@knownOrNull is FCEnum.Unknown)
    }
    return (this as? FCEnum.Known<E>)?.value
}

fun <E : Enum<E>> FCEnum<E>.knownOrThrow(): E = knownOrNull() ?: error("Unknown enum value '$value'")

internal class FCEnumSerializer<E : Enum<E>> : KSerializer<FCEnum<E>> {

    override val descriptor = enumSerializer.descriptor

    override fun deserialize(decoder: Decoder): FCEnum<E> {
        val code = decoder.decodeSerializableValue(enumSerializer)
        val valueForCode = enumValuesByCode[code]
        return if (valueForCode == null) {
            CodifiedEnum.Unknown(code)
        } else {
            CodifiedEnum.Known(valueForCode)
        }
    }

    override fun serialize(encoder: Encoder, value: FCEnum<E>) {
        when (value) {
            is CodifiedEnum.Known -> encoder.encodeSerializableValue(codeSerializer, value.value.code)
            is CodifiedEnum.Unknown -> encoder.encodeSerializableValue(codeSerializer, value.value)
        }
    }
}

class CodifiedEnumSerializer<T, C>(
    enumValues: Array<T>,
    private val codeSerializer: KSerializer<C>
) : KSerializer<CodifiedEnum<T, C>> where T : Enum<T>, T : Codified<C> {

    override val descriptor = codeSerializer.descriptor

    private val enumValuesByCode = enumValues.associateBy { it.code }

    override fun deserialize(decoder: Decoder): CodifiedEnum<T, C> {
        val code = decoder.decodeSerializableValue(codeSerializer)
        val valueForCode = enumValuesByCode[code]
        return if (valueForCode == null) {
            CodifiedEnum.Unknown(code)
        } else {
            CodifiedEnum.Known(valueForCode)
        }
    }

    override fun serialize(encoder: Encoder, value: CodifiedEnum<T, C>) {
        when (value) {
            is CodifiedEnum.Known -> encoder.encodeSerializableValue(codeSerializer, value.value.code)
            is CodifiedEnum.Unknown -> encoder.encodeSerializableValue(codeSerializer, value.value)
        }
    }
}

inline fun <reified T> codifiedEnumSerializer(): KSerializer<FCEnum<T, String>>
    where T : Enum<T>, T : Codified<String> =
    codifiedEnumSerializer(String.serializer())

inline fun <reified T, C> codifiedEnumSerializer(codeSerializer: KSerializer<C>): KSerializer<CodifiedEnum<T, C>>
    where T : Enum<T>, T : Codified<C> =
    CodifiedEnumSerializer(enumValues(), codeSerializer)