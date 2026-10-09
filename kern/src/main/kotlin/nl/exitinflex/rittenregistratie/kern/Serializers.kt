package nl.exitinflex.rittenregistratie.kern

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * ISO-8601 als opslagformaat: leesbaar in een back-upbestand en zonder
 * tijdzonevalkuilen terug te lezen.
 */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object LocalDateNullableSerializer : KSerializer<LocalDate?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDateNullable", PrimitiveKind.STRING).nullable
    override fun serialize(encoder: Encoder, value: LocalDate?) =
        if (value == null) encoder.encodeNull() else encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate? =
        if (decoder.decodeNotNullMark()) LocalDate.parse(decoder.decodeString()) else decoder.decodeNull()
}

object LocalTimeNullableSerializer : KSerializer<LocalTime?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalTimeNullable", PrimitiveKind.STRING).nullable
    override fun serialize(encoder: Encoder, value: LocalTime?) =
        if (value == null) encoder.encodeNull() else encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalTime? =
        if (decoder.decodeNotNullMark()) LocalTime.parse(decoder.decodeString()) else decoder.decodeNull()
}

object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

object InstantNullableSerializer : KSerializer<Instant?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("InstantNullable", PrimitiveKind.STRING).nullable
    override fun serialize(encoder: Encoder, value: Instant?) =
        if (value == null) encoder.encodeNull() else encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant? =
        if (decoder.decodeNotNullMark()) Instant.parse(decoder.decodeString()) else decoder.decodeNull()
}
