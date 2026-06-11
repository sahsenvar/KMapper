package com.sahsenvar.kmapper.datetime

import com.sahsenvar.kmapper.converter.MapTypeConverter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

// NOTE: Instant converters (InstantStringConverter, InstantLongConverter) already live in
// core's builtin package — do NOT duplicate them here.

/**
 * Converts between ISO-8601 [String] and [LocalDate].
 *
 * S=String is legacy ordering from before core adopted the richer-first convention
 * (richer-first would put the richer [LocalDate] on the S side). Reordering these
 * pairs is parked — see docs/converter-redesign.md.
 */
object StringLocalDateConverter : MapTypeConverter<String, LocalDate>(String::class, LocalDate::class) {
    override fun convertTo(source: String): LocalDate = LocalDate.parse(source)

    override fun convertFrom(target: LocalDate): String = target.toString()
}

/**
 * Converts between ISO-8601 [String] and [LocalDateTime].
 */
object StringLocalDateTimeConverter : MapTypeConverter<String, LocalDateTime>(String::class, LocalDateTime::class) {
    override fun convertTo(source: String): LocalDateTime = LocalDateTime.parse(source)

    override fun convertFrom(target: LocalDateTime): String = target.toString()
}

/**
 * Converts between ISO-8601 [String] and [LocalTime].
 */
object StringLocalTimeConverter : MapTypeConverter<String, LocalTime>(String::class, LocalTime::class) {
    override fun convertTo(source: String): LocalTime = LocalTime.parse(source)

    override fun convertFrom(target: LocalTime): String = target.toString()
}
