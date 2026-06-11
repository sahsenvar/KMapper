package com.sahsenvar.kmapper.converter.builtin

import com.sahsenvar.kmapper.converter.MapTypeConverter
import kotlinx.datetime.Instant

/** [Instant] <-> ISO-8601 [String]: format via toString, parse via [Instant.parse]. */
object InstantStringConverter : MapTypeConverter<Instant, String>(Instant::class, String::class) {
    override fun convertTo(source: Instant): String = source.toString()

    override fun convertFrom(target: String): Instant = Instant.parse(target)
}

/** [Instant] <-> epoch-milliseconds [Long]. */
object InstantLongConverter : MapTypeConverter<Instant, Long>(Instant::class, Long::class) {
    override fun convertTo(source: Instant): Long = source.toEpochMilliseconds()

    override fun convertFrom(target: Long): Instant = Instant.fromEpochMilliseconds(target)
}
