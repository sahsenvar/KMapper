package com.sahsenvar.kmapper.datetime

import kotlin.test.Test
import kotlin.test.assertEquals
import java.time.Instant as JInstant
import java.time.LocalDate as JLocalDate
import java.time.LocalDateTime as JLocalDateTime
import java.time.LocalTime as JLocalTime
import java.time.OffsetDateTime as JOffsetDateTime
import kotlinx.datetime.Instant as KInstant
import kotlinx.datetime.LocalDate as KLocalDate

class JavaTimeConvertersTest {
    // StringJavaInstantConverter
    @Test
    fun javaInstant_parseFromString() {
        val s = "2026-06-04T10:15:30Z"
        assertEquals(JInstant.parse(s), StringJavaInstantConverter.convertTo(s))
    }

    @Test
    fun javaInstant_formatToString() {
        val instant = JInstant.parse("2026-06-04T10:15:30Z")
        assertEquals("2026-06-04T10:15:30Z", StringJavaInstantConverter.convertFrom(instant))
    }

    @Test
    fun javaInstant_roundTrip() {
        val s = "2026-06-04T00:00:00Z"
        assertEquals(s, StringJavaInstantConverter.convertFrom(StringJavaInstantConverter.convertTo(s)))
    }

    // LongJavaInstantConverter
    @Test
    fun longJavaInstant_toInstant() {
        val millis = 1_000_000_000_000L
        assertEquals(JInstant.ofEpochMilli(millis), LongJavaInstantConverter.convertTo(millis))
    }

    @Test
    fun longJavaInstant_toMillis() {
        val millis = 1_000_000_000_000L
        assertEquals(millis, LongJavaInstantConverter.convertFrom(JInstant.ofEpochMilli(millis)))
    }

    @Test
    fun longJavaInstant_roundTrip() {
        val millis = 1_748_000_000_000L
        assertEquals(millis, LongJavaInstantConverter.convertFrom(LongJavaInstantConverter.convertTo(millis)))
    }

    // StringJavaLocalDateConverter
    @Test
    fun javaLocalDate_parseFromString() {
        assertEquals(JLocalDate.of(2026, 6, 4), StringJavaLocalDateConverter.convertTo("2026-06-04"))
    }

    @Test
    fun javaLocalDate_formatToString() {
        assertEquals("2026-06-04", StringJavaLocalDateConverter.convertFrom(JLocalDate.of(2026, 6, 4)))
    }

    @Test
    fun javaLocalDate_roundTrip() {
        val s = "2026-01-31"
        assertEquals(s, StringJavaLocalDateConverter.convertFrom(StringJavaLocalDateConverter.convertTo(s)))
    }

    // StringJavaLocalDateTimeConverter
    @Test
    fun javaLocalDateTime_parseFromString() {
        assertEquals(JLocalDateTime.of(2026, 6, 4, 10, 15, 30), StringJavaLocalDateTimeConverter.convertTo("2026-06-04T10:15:30"))
    }

    @Test
    fun javaLocalDateTime_roundTrip() {
        val s = "2026-12-31T23:59:59"
        assertEquals(s, StringJavaLocalDateTimeConverter.convertFrom(StringJavaLocalDateTimeConverter.convertTo(s)))
    }

    // StringJavaLocalTimeConverter
    @Test
    fun javaLocalTime_parseFromString() {
        assertEquals(JLocalTime.of(10, 15, 30), StringJavaLocalTimeConverter.convertTo("10:15:30"))
    }

    @Test
    fun javaLocalTime_roundTrip() {
        val s = "23:59:59"
        assertEquals(s, StringJavaLocalTimeConverter.convertFrom(StringJavaLocalTimeConverter.convertTo(s)))
    }

    // StringJavaZonedDateTimeConverter
    @Test
    fun javaZonedDateTime_roundTrip() {
        val s = "2026-06-04T10:15:30+02:00[Europe/Paris]"
        assertEquals(
            StringJavaZonedDateTimeConverter.convertTo(s),
            StringJavaZonedDateTimeConverter.convertTo(
                StringJavaZonedDateTimeConverter.convertFrom(
                    StringJavaZonedDateTimeConverter.convertTo(s),
                ),
            ),
        )
    }

    // StringJavaOffsetDateTimeConverter
    @Test
    fun javaOffsetDateTime_parseFromString() {
        val s = "2026-06-04T10:15:30+02:00"
        assertEquals(JOffsetDateTime.parse(s), StringJavaOffsetDateTimeConverter.convertTo(s))
    }

    @Test
    fun javaOffsetDateTime_roundTrip() {
        val s = "2026-06-04T10:15:30+02:00"
        assertEquals(s, StringJavaOffsetDateTimeConverter.convertFrom(StringJavaOffsetDateTimeConverter.convertTo(s)))
    }

    // KotlinJavaInstantConverter (bridge)
    @Test
    fun kotlinJavaInstant_toJava() {
        val kInstant = KInstant.parse("2026-06-04T10:15:30Z")
        val jInstant = JInstant.parse("2026-06-04T10:15:30Z")
        assertEquals(jInstant, KotlinJavaInstantConverter.convertTo(kInstant))
    }

    @Test
    fun kotlinJavaInstant_toKotlin() {
        val kInstant = KInstant.parse("2026-06-04T10:15:30Z")
        val jInstant = JInstant.parse("2026-06-04T10:15:30Z")
        assertEquals(kInstant, KotlinJavaInstantConverter.convertFrom(jInstant))
    }

    @Test
    fun kotlinJavaInstant_roundTrip() {
        val kInstant = KInstant.parse("2026-06-04T00:00:00Z")
        assertEquals(kInstant, KotlinJavaInstantConverter.convertFrom(KotlinJavaInstantConverter.convertTo(kInstant)))
    }

    // KotlinJavaLocalDateConverter (bridge)
    @Test
    fun kotlinJavaLocalDate_toJava() {
        val kDate = KLocalDate(2026, 6, 4)
        val jDate = JLocalDate.of(2026, 6, 4)
        assertEquals(jDate, KotlinJavaLocalDateConverter.convertTo(kDate))
    }

    @Test
    fun kotlinJavaLocalDate_toKotlin() {
        val kDate = KLocalDate(2026, 6, 4)
        val jDate = JLocalDate.of(2026, 6, 4)
        assertEquals(kDate, KotlinJavaLocalDateConverter.convertFrom(jDate))
    }

    @Test
    fun kotlinJavaLocalDate_roundTrip() {
        val kDate = KLocalDate(2026, 1, 31)
        assertEquals(kDate, KotlinJavaLocalDateConverter.convertFrom(KotlinJavaLocalDateConverter.convertTo(kDate)))
    }
}
