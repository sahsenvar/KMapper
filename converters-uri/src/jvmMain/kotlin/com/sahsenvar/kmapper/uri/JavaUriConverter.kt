package com.sahsenvar.kmapper.uri

import com.sahsenvar.kmapper.converter.MapTypeConverter
import java.net.URI

/** [String] ↔ [java.net.URI]. Uses [URI.create] for parsing. */
object JavaStringUriConverter : MapTypeConverter<String, URI>(String::class, URI::class) {
    override fun convertToNonNull(value: String): URI = URI.create(value)
    override fun convertFromNonNull(value: URI): String = value.toString()
}
