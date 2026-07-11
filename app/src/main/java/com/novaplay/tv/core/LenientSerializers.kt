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

// Reads the raw element when the decoder is JSON-backed; null for arrays/objects/non-JSON.
private fun Decoder.primitiveOrNull(): JsonPrimitive? =
    (this as? JsonDecoder)?.decodeJsonElement() as? JsonPrimitive

/** Int field that may arrive as a number, quoted number, or float. */
object LenientInt : KSerializer<Int> {
    override val descriptor = PrimitiveSerialDescriptor("LenientInt", PrimitiveKind.INT)
    // Unparseable or missing values collapse to 0 so one dirty field cannot fail the row.
    override fun deserialize(decoder: Decoder): Int {
        val p = decoder.primitiveOrNull() ?: return 0
        return p.intOrNull ?: p.doubleOrNull?.toInt() ?: 0
    }

    // Output is always a plain number; leniency is only needed on input.
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

/** Long variant of [LenientInt] for ids and timestamps. */
object LenientLong : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor("LenientLong", PrimitiveKind.LONG)
    // Tries a long first, then a float shape truncated to long; defaults to 0L.
    override fun deserialize(decoder: Decoder): Long {
        val p = decoder.primitiveOrNull() ?: return 0L
        return p.longOrNull ?: p.doubleOrNull?.toLong() ?: 0L
    }

    // Always emits a plain number.
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

/** Nullable Long: absent or unparseable input stays null instead of collapsing to 0. */
object LenientLongOrNull : KSerializer<Long?> {
    override val descriptor = PrimitiveSerialDescriptor("LenientLongOrNull", PrimitiveKind.LONG)
    // null preserves "unknown", letting callers distinguish a missing timestamp from epoch 0.
    override fun deserialize(decoder: Decoder): Long? {
        val p = decoder.primitiveOrNull() ?: return null
        return p.longOrNull ?: p.doubleOrNull?.toLong()
    }

    // Round-trips null explicitly; numbers as plain longs.
    override fun serialize(encoder: Encoder, value: Long?) =
        if (value == null) encoder.encodeNull() else encoder.encodeLong(value)
}

/** Nullable Double for values like ratings that arrive as 7.5, "7.5", or "". */
object LenientDoubleOrNull : KSerializer<Double?> {
    override val descriptor = PrimitiveSerialDescriptor("LenientDoubleOrNull", PrimitiveKind.DOUBLE)
    // Any primitive that parses as a double; everything else is null.
    override fun deserialize(decoder: Decoder): Double? =
        decoder.primitiveOrNull()?.doubleOrNull

    // Emits null or a plain double.
    override fun serialize(encoder: Encoder, value: Double?) =
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
}

// backdrop_path arrives as a string on some panels and an array of strings on
// others; take the first usable value either way.
object LenientFirstStringOrNull : KSerializer<String?> {
    override val descriptor = PrimitiveSerialDescriptor("LenientFirstStringOrNull", PrimitiveKind.STRING)
    // Unwraps string-or-array-of-strings; blank content and the literal "null" count as absent.
    override fun deserialize(decoder: Decoder): String? {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return null
        val primitive = when (element) {
            is JsonPrimitive -> element
            is JsonArray -> element.firstOrNull() as? JsonPrimitive
            else -> null
        } ?: return null
        return primitive.content.takeIf { it.isNotBlank() && it != "null" }
    }

    // Always writes a single string (or null); the array shape is input-only.
    override fun serialize(encoder: Encoder, value: String?) =
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
}

/** Nullable String: numbers are stringified, blank content and the literal "null" become null. */
object LenientStringOrNull : KSerializer<String?> {
    override val descriptor = PrimitiveSerialDescriptor("LenientStringOrNull", PrimitiveKind.STRING)
    // Takes the primitive's raw content, so numeric ids survive as text.
    override fun deserialize(decoder: Decoder): String? {
        val p = decoder.primitiveOrNull() ?: return null
        return p.content.takeIf { it.isNotBlank() && it != "null" }
    }

    // Plain string or explicit null.
    override fun serialize(encoder: Encoder, value: String?) =
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
}
