package com.sahsenvar.kmapper.uri

import android.net.Uri
import com.sahsenvar.kmapper.converter.MapTypeConverter

/** [String] ↔ [android.net.Uri]. Uses [Uri.parse] for parsing. */
object AndroidStringUriConverter : MapTypeConverter<String, Uri>(String::class, Uri::class) {
    override fun convertTo(source: String): Uri = Uri.parse(source)

    override fun convertFrom(target: Uri): String = target.toString()
}
