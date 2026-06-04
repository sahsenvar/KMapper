package com.sahsenvar.kmapper.datetime

import kotlinx.datetime.Instant as KInstant
import kotlinx.datetime.LocalDate as KLocalDate
import java.time.Instant as JInstant
import java.time.LocalDate as JLocalDate
import java.time.LocalDateTime as JLocalDateTime
import java.time.LocalTime as JLocalTime
import java.time.OffsetDateTime as JOffsetDateTime
import java.time.ZonedDateTime as JZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class JavaTimeConvertersTest {

    // StringJavaInstantConverter
    @Test
    fun javaInstant_parseFromString() {
        val s = "2026-06-04T10:15:30Z"
        assertEquals(JInstant.parse(s), StringJavaInstantConverter.convertToNonNull(s))
    }

    @Test
    fun javaInstant_formatToString() {
        val instant = JInstant.parse("2026-06-04T10:15:30Z")
        assertEquals("2026-06-04T10:15:30Z", StringJavaInstantConverter.convertFromNonNull(instant))
    }

    @Test
    fun javaInstant_roundTrip() {
        val s = "2026-06-04T00:00:00Z"
        assertEquals(s, StringJavaInstantConverter.convertFromNonNull(StringJavaInstantConverter.convertToNonNull(s)))
    }

    // LongJavaInstantConverter
    @Test
    fun longJavaInstant_toInstant() {
        val millis = 1_000_000_000_000L
        assertEquals(JInstant.ofEpochMilli(millis), LongJavaInstantConverter.convertToNonNull(millis))
    }

    @Test
    fun longJavaInstant_toMillis() {
        val millis = 1_000_000_000_000L
        assertEquals(millis, LongJavaInstantConverter.convertFromNonNull(JInstant.ofEpochMilli(millis)))
    }

    @Test
    fun longJavaInstant_roundTrip() {
        val millis = 1_748_000_000_000L
        assertEquals(millis, LongJavaInstantConverter.convertFromNonNull(LongJavaInstantConverter.convertToNonNull(millis)))
    }

    // StringJavaLocalDateConverter
    @Test
    fun javaLocalDate_parseFromString() {
        assertEquals(JLocalDate.of(2026, 6, 4), StringJavaLocalDateConverter.convertToNonNull("2026-06-04"))
    }

    @Test
    fun javaLocalDate_formatToString() {
        assertEquals("2026-06-04", StringJavaLocalDateConverter.convertFromNonNull(JLocalDate.of(2026, 6, 4)))
    }

    @Test
    fun javaLocalDate_roundTrip() {
        val s = "2026-01-31"
        assertEquals(s, StringJavaLocalDateConverter.convertFromNonNull(StringJavaLocalDateConverter.convertToNonNull(s)))
    }

    // StringJavaLocalDateTimeConverter
    @Test
    fun javaLocalDateTime_parseFromString() {
        assertEquals(JLocalDateTime.of(2026, 6, 4, 10, 15, 30), StringJavaLocalDateTimeConverter.convertToNonNull("2026-06-04T10:15:30"))
    }

    @Test
    fun javaLocalDateTime_roundTrip() {
        val s = "2026-12-31T23:59:59"
        assertEquals(s, StringJavaLocalDateTimeConverter.convertFromNonNull(StringJavaLocalDateTimeConverter.convertToNonNull(s)))
    }

    // StringJavaLocalTimeConverter
    @Test
    fun javaLocalTime_parseFromString() {
        assertEquals(JLocalTime.of(10, 15, 30), StringJavaLocalTimeConverter.convertToNonNull("10:15:30"))
    }

    @Test
    fun javaLocalTime_roundTrip() {
        val s = "23:59:59"
        assertEquals(s, StringJavaLocalTimeConverter.convertFromNonNull(StringJavaLocalTimeConverter.convertToNonNull(s)))
    }

    // StringJavaZonedDateTimeConverter
    @Test
    fun javaZonedDateTime_roundTrip() {
        val s = "2026-06-04T10:15:30+02:00[Europe/Paris]"
        assertEquals(
            StringJavaZonedDateTimeConverter.convertToNonNull(s),
            StringJavaZonedDateTimeConverter.convertToNonNull(
                StringJavaZonedDateTimeConverter.convertFromNonNull(
                    StringJavaZonedDateTimeConverter.convertToNonNull(s)
                )
            )
        )
    }

    // StringJavaOffsetDateTimeConverter
    @Test
    fun javaOffsetDateTime_parseFromString() {
        val s = "2026-06-04T10:15:30+02:00"
        assertEquals(JOffsetDateTime.parse(s), StringJavaOffsetDateTimeConverter.convertToNonNull(s))
    }

    @Test
    fun javaOffsetDateTime_roundTrip() {
        val s = "2026-06-04T10:15:30+02:00"
        assertEquals(s, StringJavaOffsetDateTimeConverter.convertFromNonNull(StringJavaOffsetDateTimeConverter.convertToNonNull(s)))
    }

    // KotlinJavaInstantConverter (bridge)
    @Test
    fun kotlinJavaInstant_toJava() {
        val kInstant = KInstant.parse("2026-06-04T10:15:30Z")
        val jInstant = JInstant.parse("2026-06-04T10:15:30Z")
        assertEquals(jInstant, KotlinJavaInstantConverter.convertToNonNull(kInstant))
    }

    @Test
    fun kotlinJavaInstant_toKotlin() {
        val kInstant = KInstant.parse("2026-06-04T10:15:30Z")
        val jInstant = JInstant.parse("2026-06-04T10:15:30Z")
        assertEquals(kInstant, KotlinJavaInstantConverter.convertFromNonNull(jInstant))
    }

    @Test
    fun kotlinJavaInstant_roundTrip() {
        val kInstant = KInstant.parse("2026-06-04T00:00:00Z")
        assertEquals(kInstant, KotlinJavaInstantConverter.convertFromNonNull(KotlinJavaInstantConverter.convertToNonNull(kInstant)))
    }

    // KotlinJavaLocalDateConverter (bridge)
    @Test
    fun kotlinJavaLocalDate_toJava() {
        val kDate = KLocalDate(2026, 6, 4)
        val jDate = JLocalDate.of(2026, 6, 4)
        assertEquals(jDate, KotlinJavaLocalDateConverter.convertToNonNull(kDate))
    }

    @Test
    fun kotlinJavaLocalDate_toKotlin() {
        val kDate = KLocalDate(2026, 6, 4)
        val jDate = JLocalDate.of(2026, 6, 4)
        assertEquals(kDate, KotlinJavaLocalDateConverter.convertFromNonNull(jDate))
    }

    @Test
    fun kotlinJavaLocalDate_roundTrip() {
        val kDate = KLocalDate(2026, 1, 31)
        assertEquals(kDate, KotlinJavaLocalDateConverter.convertFromNonNull(KotlinJavaLocalDateConverter.convertToNonNull(kDate)))
    }

    // Nullable wrapper sanity check (from MapTypeConverter.convertTo/From)
    @Test
    fun javaInstant_nullable_null() {
        assertEquals(null, StringJavaInstantConverter.convertTo(null))
        assertEquals(null, StringJavaInstantConverter.convertFrom(null))
    }

    @Test
    fun kotlinJavaInstant_bridge_nullable_null() {
        assertEquals(null, KotlinJavaInstantConverter.convertTo(null))
        assertEquals(null, KotlinJavaInstantConverter.convertFrom(null))
    }
}
