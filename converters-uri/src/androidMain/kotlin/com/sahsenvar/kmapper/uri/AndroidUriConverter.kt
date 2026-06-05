package com.sahsenvar.kmapper.uri

import android.net.Uri
import com.sahsenvar.kmapper.converter.MapTypeConverter

/** [String] ↔ [android.net.Uri]. Uses [Uri.parse] for parsing. */
object AndroidStringUriConverter : MapTypeConverter<String, Uri>(String::class, Uri::class) {
    override fun convertToNonNull(value: String): Uri = Uri.parse(value)
    override fun convertFromNonNull(value: Uri): String = value.toString()
}
