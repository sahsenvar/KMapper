package com.sahsenvar.kmapper.converter.builtin

import com.sahsenvar.kmapper.converter.MapTypeConverter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * [Duration] <-> ISO-8601 [String] ("PT1H30M"). Format via [Duration.toIsoString], parse via
 * [Duration.parseIsoString] — strict ISO only; Kotlin's lenient "1h 30m" form is rejected
 * (wire formats want one canonical representation).
 */
object DurationStringConverter : MapTypeConverter<Duration, String>(Duration::class, String::class) {
    override fun convertTo(source: Duration): String = source.toIsoString()

    override fun convertFrom(target: String): Duration = Duration.parseIsoString(target)
}

/**
 * [Duration] <-> whole-milliseconds [Long]. Sub-millisecond precision truncates on the way
 * out — the same trade-off as [InstantLongConverter] (millis are the de-facto wire unit).
 */
object DurationLongConverter : MapTypeConverter<Duration, Long>(Duration::class, Long::class) {
    override fun convertTo(source: Duration): Long = source.inWholeMilliseconds

    override fun convertFrom(target: Long): Duration = target.milliseconds
}
