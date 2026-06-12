package com.sahsenvar.kmapper.converter.builtin

import com.sahsenvar.kmapper.converter.MapTypeConverter
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** [Instant] <-> ISO-8601 [String]: format via toString, parse via [Instant.parse]. */
object InstantStringConverter : MapTypeConverter<Instant, String>(Instant::class, String::class) {
    override fun convertTo(source: Instant): String = source.toString()

    override fun convertFrom(target: String): Instant = Instant.parse(target)
}

/** [Instant] <-> epoch-milliseconds [Long]. Sub-millisecond precision truncates on the way out. */
object InstantLongConverter : MapTypeConverter<Instant, Long>(Instant::class, Long::class) {
    override fun convertTo(source: Instant): Long = source.toEpochMilliseconds()

    override fun convertFrom(target: Long): Instant = Instant.fromEpochMilliseconds(target)
}

/** [LocalDate] <-> ISO-8601 [String] ("2026-06-12"). Parse throws on malformed input. */
object LocalDateStringConverter : MapTypeConverter<LocalDate, String>(LocalDate::class, String::class) {
    override fun convertTo(source: LocalDate): String = source.toString()

    override fun convertFrom(target: String): LocalDate = LocalDate.parse(target)
}

/** [LocalDateTime] <-> ISO-8601 [String] ("2026-06-12T09:30:00"). Parse throws on malformed input. */
object LocalDateTimeStringConverter : MapTypeConverter<LocalDateTime, String>(LocalDateTime::class, String::class) {
    override fun convertTo(source: LocalDateTime): String = source.toString()

    override fun convertFrom(target: String): LocalDateTime = LocalDateTime.parse(target)
}

/** [LocalTime] <-> ISO-8601 [String] ("09:30:00"). Parse throws on malformed input. */
object LocalTimeStringConverter : MapTypeConverter<LocalTime, String>(LocalTime::class, String::class) {
    override fun convertTo(source: LocalTime): String = source.toString()

    override fun convertFrom(target: String): LocalTime = LocalTime.parse(target)
}
