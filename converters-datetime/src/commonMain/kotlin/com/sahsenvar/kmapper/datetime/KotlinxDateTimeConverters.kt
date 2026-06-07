package com.sahsenvar.kmapper.datetime

import com.sahsenvar.kmapper.converter.MapTypeConverter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

// NOTE: Instant converters (StringInstantConverter, LongInstantConverter) already live in
// core's builtin package — do NOT duplicate them here.

/**
 * Converts between ISO-8601 [String] and [LocalDate].
 * S=String matches the core naming convention (primitive/String side is S).
 */
object StringLocalDateConverter : MapTypeConverter<String, LocalDate>(String::class, LocalDate::class) {
    override fun convertToNonNull(value: String): LocalDate = LocalDate.parse(value)

    override fun convertFromNonNull(value: LocalDate): String = value.toString()
}

/**
 * Converts between ISO-8601 [String] and [LocalDateTime].
 */
object StringLocalDateTimeConverter : MapTypeConverter<String, LocalDateTime>(String::class, LocalDateTime::class) {
    override fun convertToNonNull(value: String): LocalDateTime = LocalDateTime.parse(value)

    override fun convertFromNonNull(value: LocalDateTime): String = value.toString()
}

/**
 * Converts between ISO-8601 [String] and [LocalTime].
 */
object StringLocalTimeConverter : MapTypeConverter<String, LocalTime>(String::class, LocalTime::class) {
    override fun convertToNonNull(value: String): LocalTime = LocalTime.parse(value)

    override fun convertFromNonNull(value: LocalTime): String = value.toString()
}
