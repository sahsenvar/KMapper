package com.sahsenvar.kmapper.uri

import com.sahsenvar.kmapper.MappingException
import com.sahsenvar.kmapper.converter.MapTypeConverter
import platform.Foundation.NSURL

/**
 * [String] ↔ [NSURL].
 *
 * [NSURL.URLWithString] returns null for malformed URLs; in that case
 * [convertToNonNull] throws [MappingException.TypeConversionFailed].
 *
 * Round-trip caveat: NSURL normalizes URLs (e.g. adds trailing slash to bare hosts).
 * Tests must use pre-normalized URLs to pass a true round-trip check.
 */
object NsUrlStringConverter : MapTypeConverter<String, NSURL>(String::class, NSURL::class) {
    override fun convertToNonNull(value: String): NSURL = NSURL.URLWithString(value)
        ?: throw MappingException.TypeConversionFailed(
            "",
            "String",
            "NSURL",
            IllegalArgumentException("NSURL.URLWithString returned null for: $value"),
        )

    override fun convertFromNonNull(value: NSURL): String = value.absoluteString ?: value.path ?: ""
}
