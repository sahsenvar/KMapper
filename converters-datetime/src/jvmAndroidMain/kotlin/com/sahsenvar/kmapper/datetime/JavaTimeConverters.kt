package com.sahsenvar.kmapper.datetime

import com.sahsenvar.kmapper.converter.MapTypeConverter
import kotlinx.datetime.Instant as KInstant
import kotlinx.datetime.LocalDate as KLocalDate
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toKotlinLocalDate
import java.time.Instant as JInstant
import java.time.LocalDate as JLocalDate
import java.time.LocalDateTime as JLocalDateTime
import java.time.LocalTime as JLocalTime
import java.time.OffsetDateTime as JOffsetDateTime
import java.time.ZonedDateTime as JZonedDateTime

// ---------------------------------------------------------------------------
// Pure java.time scalar converters (String ↔ java.time, Long ↔ java.time.Instant)
// Naming convention: Java-prefixed to disambiguate from kotlinx-datetime converters.
// ---------------------------------------------------------------------------

/** ISO-8601 [String] ↔ [java.time.Instant] */
object StringJavaInstantConverter : MapTypeConverter<String, JInstant>(String::class, JInstant::class) {
    override fun convertToNonNull(value: String): JInstant = JInstant.parse(value)
    override fun convertFromNonNull(value: JInstant): String = value.toString()
}

/** epoch-milli [Long] ↔ [java.time.Instant] */
object LongJavaInstantConverter : MapTypeConverter<Long, JInstant>(Long::class, JInstant::class) {
    override fun convertToNonNull(value: Long): JInstant = JInstant.ofEpochMilli(value)
    override fun convertFromNonNull(value: JInstant): Long = value.toEpochMilli()
}

/** ISO-8601 [String] ↔ [java.time.LocalDate] */
object StringJavaLocalDateConverter : MapTypeConverter<String, JLocalDate>(String::class, JLocalDate::class) {
    override fun convertToNonNull(value: String): JLocalDate = JLocalDate.parse(value)
    override fun convertFromNonNull(value: JLocalDate): String = value.toString()
}

/** ISO-8601 [String] ↔ [java.time.LocalDateTime] */
object StringJavaLocalDateTimeConverter : MapTypeConverter<String, JLocalDateTime>(String::class, JLocalDateTime::class) {
    override fun convertToNonNull(value: String): JLocalDateTime = JLocalDateTime.parse(value)
    override fun convertFromNonNull(value: JLocalDateTime): String = value.toString()
}

/** ISO-8601 [String] ↔ [java.time.LocalTime] */
object StringJavaLocalTimeConverter : MapTypeConverter<String, JLocalTime>(String::class, JLocalTime::class) {
    override fun convertToNonNull(value: String): JLocalTime = JLocalTime.parse(value)
    override fun convertFromNonNull(value: JLocalTime): String = value.toString()
}

/** ISO-8601 [String] ↔ [java.time.ZonedDateTime] */
object StringJavaZonedDateTimeConverter : MapTypeConverter<String, JZonedDateTime>(String::class, JZonedDateTime::class) {
    override fun convertToNonNull(value: String): JZonedDateTime = JZonedDateTime.parse(value)
    override fun convertFromNonNull(value: JZonedDateTime): String = value.toString()
}

/** ISO-8601 [String] ↔ [java.time.OffsetDateTime] */
object StringJavaOffsetDateTimeConverter : MapTypeConverter<String, JOffsetDateTime>(String::class, JOffsetDateTime::class) {
    override fun convertToNonNull(value: String): JOffsetDateTime = JOffsetDateTime.parse(value)
    override fun convertFromNonNull(value: JOffsetDateTime): String = value.toString()
}

// ---------------------------------------------------------------------------
// Bridge converters: kotlinx.datetime ↔ java.time
// Uses kotlinx-datetime JVM interop extensions from kotlinx.datetime.ConvertersKt.
// ---------------------------------------------------------------------------

/** [kotlinx.datetime.Instant] ↔ [java.time.Instant] via kotlinx-datetime JVM bridges */
object KotlinJavaInstantConverter : MapTypeConverter<KInstant, JInstant>(KInstant::class, JInstant::class) {
    override fun convertToNonNull(value: KInstant): JInstant = value.toJavaInstant()
    override fun convertFromNonNull(value: JInstant): KInstant = value.toKotlinInstant()
}

/** [kotlinx.datetime.LocalDate] ↔ [java.time.LocalDate] via kotlinx-datetime JVM bridges */
object KotlinJavaLocalDateConverter : MapTypeConverter<KLocalDate, JLocalDate>(KLocalDate::class, JLocalDate::class) {
    override fun convertToNonNull(value: KLocalDate): JLocalDate = value.toJavaLocalDate()
    override fun convertFromNonNull(value: JLocalDate): KLocalDate = value.toKotlinLocalDate()
}
