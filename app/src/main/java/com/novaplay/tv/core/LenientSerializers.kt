package com.novaplay.tv.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

// Xtream panels return dirty JSON: numbers as strings ("num":"3"), strings as numbers,
// empty strings for missing values. These serializers accept any primitive shape.

private fun Decoder.primitiveOrNull(): JsonPrimitive? =
    (this as? JsonDecoder)?.decodeJsonElement() as? JsonPrimitive

object LenientInt : KSerializer<Int> {
    override val descriptor = PrimitiveSerialDescriptor("LenientInt", PrimitiveKind.INT)
    override fun deserialize(decoder: Decoder): Int {
        val p = decoder.primitiveOrNull() ?: return 0
        return p.intOrNull ?: p.doubleOrNull?.toInt() ?: 0
    }

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

object LenientLong : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor("LenientLong", PrimitiveKind.LONG)
    override fun deserialize(decoder: Decoder): Long {
        val p = decoder.primitiveOrNull() ?: return 0L
        return p.longOrNull ?: p.doubleOrNull?.toLong() ?: 0L
    }

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

object LenientLongOrNull : KSerializer<Long?> {
    override val descriptor = PrimitiveSerialDescriptor("LenientLongOrNull", PrimitiveKind.LONG)
    override fun deserialize(decoder: Decoder): Long? {
        val p = decoder.primitiveOrNull() ?: return null
        return p.longOrNull ?: p.doubleOrNull?.toLong()
    }

    override fun serialize(encoder: Encoder, value: Long?) =
        if (value == null) encoder.encodeNull() else encoder.encodeLong(value)
}

object LenientDoubleOrNull : KSerializer<Double?> {
    override val descriptor = PrimitiveSerialDescriptor("LenientDoubleOrNull", PrimitiveKind.DOUBLE)
    override fun deserialize(decoder: Decoder): Double? =
        decoder.primitiveOrNull()?.doubleOrNull

    override fun serialize(encoder: Encoder, value: Double?) =
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
}

// backdrop_path arrives as a string on some panels and an array of strings on
// others; take the first usable value either way.
object LenientFirstStringOrNull : KSerializer<String?> {
    override val descriptor = PrimitiveSerialDescriptor("LenientFirstStringOrNull", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String? {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return null
        val primitive = when (element) {
            is JsonPrimitive -> element
            is JsonArray -> element.firstOrNull() as? JsonPrimitive
            else -> null
        } ?: return null
        return primitive.content.takeIf { it.isNotBlank() && it != "null" }
    }

    override fun serialize(encoder: Encoder, value: String?) =
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
}

object LenientStringOrNull : KSerializer<String?> {
    override val descriptor = PrimitiveSerialDescriptor("LenientStringOrNull", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String? {
        val p = decoder.primitiveOrNull() ?: return null
        return p.content.takeIf { it.isNotBlank() && it != "null" }
    }

    override fun serialize(encoder: Encoder, value: String?) =
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
}
