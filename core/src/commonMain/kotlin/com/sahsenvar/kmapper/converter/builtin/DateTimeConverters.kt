package com.sahsenvar.kmapper.converter.builtin

import com.sahsenvar.kmapper.converter.MapTypeConverter
import kotlinx.datetime.Instant

object StringInstantConverter : MapTypeConverter<String, Instant>(String::class, Instant::class) {
    override fun convertToNonNull(value: String): Instant = Instant.parse(value)

    override fun convertFromNonNull(value: Instant): String = value.toString()
}

object LongInstantConverter : MapTypeConverter<Long, Instant>(Long::class, Instant::class) {
    override fun convertToNonNull(value: Long): Instant = Instant.fromEpochMilliseconds(value)

    override fun convertFromNonNull(value: Instant): Long = value.toEpochMilliseconds()
}
