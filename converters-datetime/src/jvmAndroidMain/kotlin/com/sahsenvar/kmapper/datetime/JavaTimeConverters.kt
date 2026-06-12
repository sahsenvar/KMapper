package com.sahsenvar.kmapper.datetime

import com.sahsenvar.kmapper.converter.MapTypeConverter
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toKotlinLocalDate
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration
import java.time.Duration as JDuration
import java.time.Instant as JInstant
import java.time.LocalDate as JLocalDate
import java.time.LocalDateTime as JLocalDateTime
import java.time.LocalTime as JLocalTime
import java.time.OffsetDateTime as JOffsetDateTime
import java.time.ZonedDateTime as JZonedDateTime
import kotlin.time.Duration as KDuration
import kotlinx.datetime.Instant as KInstant
import kotlinx.datetime.LocalDate as KLocalDate

// ---------------------------------------------------------------------------
// Pure java.time scalar converters (String ↔ java.time, Long ↔ java.time.Instant)
// Naming convention: Java-prefixed to disambiguate from kotlinx-datetime converters.
// ---------------------------------------------------------------------------

/** ISO-8601 [String] ↔ [java.time.Instant] */
object StringJavaInstantConverter : MapTypeConverter<String, JInstant>(String::class, JInstant::class) {
    override fun convertTo(source: String): JInstant = JInstant.parse(source)

    override fun convertFrom(target: JInstant): String = target.toString()
}

/** epoch-milli [Long] ↔ [java.time.Instant] */
object LongJavaInstantConverter : MapTypeConverter<Long, JInstant>(Long::class, JInstant::class) {
    override fun convertTo(source: Long): JInstant = JInstant.ofEpochMilli(source)

    override fun convertFrom(target: JInstant): Long = target.toEpochMilli()
}

/** ISO-8601 [String] ↔ [java.time.LocalDate] */
object StringJavaLocalDateConverter : MapTypeConverter<String, JLocalDate>(String::class, JLocalDate::class) {
    override fun convertTo(source: String): JLocalDate = JLocalDate.parse(source)

    override fun convertFrom(target: JLocalDate): String = target.toString()
}

/** ISO-8601 [String] ↔ [java.time.LocalDateTime] */
object StringJavaLocalDateTimeConverter : MapTypeConverter<String, JLocalDateTime>(String::class, JLocalDateTime::class) {
    override fun convertTo(source: String): JLocalDateTime = JLocalDateTime.parse(source)

    override fun convertFrom(target: JLocalDateTime): String = target.toString()
}

/** ISO-8601 [String] ↔ [java.time.LocalTime] */
object StringJavaLocalTimeConverter : MapTypeConverter<String, JLocalTime>(String::class, JLocalTime::class) {
    override fun convertTo(source: String): JLocalTime = JLocalTime.parse(source)

    override fun convertFrom(target: JLocalTime): String = target.toString()
}

/** ISO-8601 [String] ↔ [java.time.ZonedDateTime] */
object StringJavaZonedDateTimeConverter : MapTypeConverter<String, JZonedDateTime>(String::class, JZonedDateTime::class) {
    override fun convertTo(source: String): JZonedDateTime = JZonedDateTime.parse(source)

    override fun convertFrom(target: JZonedDateTime): String = target.toString()
}

/** ISO-8601 [String] ↔ [java.time.OffsetDateTime] */
object StringJavaOffsetDateTimeConverter : MapTypeConverter<String, JOffsetDateTime>(String::class, JOffsetDateTime::class) {
    override fun convertTo(source: String): JOffsetDateTime = JOffsetDateTime.parse(source)

    override fun convertFrom(target: JOffsetDateTime): String = target.toString()
}

// ---------------------------------------------------------------------------
// Bridge converters: kotlinx.datetime ↔ java.time
// Uses kotlinx-datetime JVM interop extensions from kotlinx.datetime.ConvertersKt.
// ---------------------------------------------------------------------------

/** [kotlinx.datetime.Instant] ↔ [java.time.Instant] via kotlinx-datetime JVM bridges */
object KotlinJavaInstantConverter : MapTypeConverter<KInstant, JInstant>(KInstant::class, JInstant::class) {
    override fun convertTo(source: KInstant): JInstant = source.toJavaInstant()

    override fun convertFrom(target: JInstant): KInstant = target.toKotlinInstant()
}

/** [kotlinx.datetime.LocalDate] ↔ [java.time.LocalDate] via kotlinx-datetime JVM bridges */
object KotlinJavaLocalDateConverter : MapTypeConverter<KLocalDate, JLocalDate>(KLocalDate::class, JLocalDate::class) {
    override fun convertTo(source: KLocalDate): JLocalDate = source.toJavaLocalDate()

    override fun convertFrom(target: JLocalDate): KLocalDate = target.toKotlinLocalDate()
}

/**
 * ISO-8601 [String] ↔ [java.time.Duration] (e.g. `"PT1H30M"`).
 *
 * Note: `kotlin.time.Duration` ↔ `String` is a core built-in ([com.sahsenvar.kmapper.converter.builtin.DurationStringConverter]);
 * this converter covers models that use the java.time type directly.
 */
object StringJavaDurationConverter : MapTypeConverter<String, JDuration>(String::class, JDuration::class) {
    override fun convertTo(source: String): JDuration = JDuration.parse(source)

    override fun convertFrom(target: JDuration): String = target.toString()
}

/**
 * [kotlin.time.Duration] ↔ [java.time.Duration] via the stdlib JVM bridges.
 *
 * Kotlin → Java is exact for all finite durations. Java → Kotlin is exact up to
 * nanosecond precision within ±146 years; beyond that range kotlin.time stores
 * milliseconds, so sub-millisecond detail of extreme java durations is dropped.
 */
object KotlinJavaDurationConverter : MapTypeConverter<KDuration, JDuration>(KDuration::class, JDuration::class) {
    override fun convertTo(source: KDuration): JDuration = source.toJavaDuration()

    override fun convertFrom(target: JDuration): KDuration = target.toKotlinDuration()
}
