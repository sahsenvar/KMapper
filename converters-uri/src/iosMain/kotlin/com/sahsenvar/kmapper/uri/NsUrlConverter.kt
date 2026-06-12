package com.sahsenvar.kmapper.uri

import com.sahsenvar.kmapper.MappingException
import com.sahsenvar.kmapper.converter.MapTypeConverter
import platform.Foundation.NSURL

/**
 * [String] ↔ [NSURL].
 *
 * [NSURL.URLWithString] returns null for malformed URLs; in that case
 * [convertTo] throws [MappingException.TypeConversionFailed].
 *
 * Round-trip caveat: NSURL normalizes URLs (e.g. adds trailing slash to bare hosts).
 * Tests must use pre-normalized URLs to pass a true round-trip check.
 */
object NsUrlStringConverter : MapTypeConverter<String, NSURL>(String::class, NSURL::class) {
    override fun convertTo(source: String): NSURL = NSURL.URLWithString(source)
        ?: throw MappingException.TypeConversionFailed(
            path = "",
            from = "String",
            to = "NSURL",
            cause = IllegalArgumentException("NSURL.URLWithString returned null for: $source"),
        )

    override fun convertFrom(target: NSURL): String = target.absoluteString ?: target.path ?: ""
}
