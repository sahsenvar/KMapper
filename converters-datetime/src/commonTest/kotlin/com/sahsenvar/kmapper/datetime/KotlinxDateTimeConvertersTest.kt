package com.sahsenvar.kmapper.datetime

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class KotlinxDateTimeConvertersTest {
    // StringLocalDateConverter
    @Test
    fun localDate_parseFromString() {
        val s = "2026-06-04"
        assertEquals(LocalDate(2026, 6, 4), StringLocalDateConverter.convertToNonNull(s))
    }

    @Test
    fun localDate_formatToString() {
        assertEquals("2026-06-04", StringLocalDateConverter.convertFromNonNull(LocalDate(2026, 6, 4)))
    }

    @Test
    fun localDate_roundTrip() {
        val s = "2026-01-31"
        assertEquals(s, StringLocalDateConverter.convertFromNonNull(StringLocalDateConverter.convertToNonNull(s)))
    }

    // StringLocalDateTimeConverter
    @Test
    fun localDateTime_parseFromString() {
        val s = "2026-06-04T10:15:30"
        assertEquals(LocalDateTime(2026, 6, 4, 10, 15, 30), StringLocalDateTimeConverter.convertToNonNull(s))
    }

    @Test
    fun localDateTime_formatToString() {
        assertEquals("2026-06-04T10:15:30", StringLocalDateTimeConverter.convertFromNonNull(LocalDateTime(2026, 6, 4, 10, 15, 30)))
    }

    @Test
    fun localDateTime_roundTrip() {
        val s = "2026-12-31T23:59:59"
        assertEquals(s, StringLocalDateTimeConverter.convertFromNonNull(StringLocalDateTimeConverter.convertToNonNull(s)))
    }

    // StringLocalTimeConverter
    @Test
    fun localTime_parseFromString() {
        val s = "10:15:30"
        assertEquals(LocalTime(10, 15, 30), StringLocalTimeConverter.convertToNonNull(s))
    }

    @Test
    fun localTime_formatToString() {
        assertEquals("10:15:30", StringLocalTimeConverter.convertFromNonNull(LocalTime(10, 15, 30)))
    }

    @Test
    fun localTime_roundTrip() {
        val s = "23:59:59"
        assertEquals(s, StringLocalTimeConverter.convertFromNonNull(StringLocalTimeConverter.convertToNonNull(s)))
    }

    // Nullable wrappers (convertTo / convertFrom from MapTypeConverter)
    @Test
    fun localDate_nullable_null() {
        assertEquals(null, StringLocalDateConverter.convertTo(null))
        assertEquals(null, StringLocalDateConverter.convertFrom(null))
    }

    @Test
    fun localDate_nullable_nonNull() {
        val s = "2026-06-04"
        assertEquals(LocalDate(2026, 6, 4), StringLocalDateConverter.convertTo(s))
        assertEquals(s, StringLocalDateConverter.convertFrom(LocalDate(2026, 6, 4)))
    }
}
