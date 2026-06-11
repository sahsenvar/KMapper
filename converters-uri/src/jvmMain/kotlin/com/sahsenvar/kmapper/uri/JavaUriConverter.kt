package com.sahsenvar.kmapper.uri

import com.sahsenvar.kmapper.converter.MapTypeConverter
import java.net.URI

/** [String] ↔ [java.net.URI]. Uses [URI.create] for parsing. */
object JavaStringUriConverter : MapTypeConverter<String, URI>(String::class, URI::class) {
    override fun convertTo(source: String): URI = URI.create(source)

    override fun convertFrom(target: URI): String = target.toString()
}
