package com.sahsenvar.kmapper.okio

import com.sahsenvar.kmapper.converter.MapTypeConverter
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.Path
import okio.Path.Companion.toPath

/** [String] ↔ [okio.ByteString] via UTF-8 encoding. */
object StringByteStringConverter : MapTypeConverter<String, ByteString>(String::class, ByteString::class) {
    override fun convertTo(source: String): ByteString = source.encodeUtf8()

    override fun convertFrom(target: ByteString): String = target.utf8()
}

/** [ByteArray] ↔ [okio.ByteString]. */
object ByteArrayByteStringConverter : MapTypeConverter<ByteArray, ByteString>(ByteArray::class, ByteString::class) {
    override fun convertTo(source: ByteArray): ByteString = source.toByteString()

    override fun convertFrom(target: ByteString): ByteArray = target.toByteArray()
}

/** [String] ↔ [okio.Path]. */
object StringPathConverter : MapTypeConverter<String, Path>(String::class, Path::class) {
    override fun convertTo(source: String): Path = source.toPath()

    override fun convertFrom(target: Path): String = target.toString()
}

/**
 * [String] ↔ [okio.ByteString] via Base64 (RFC 4648).
 *
 * Same type pair as [StringByteStringConverter] (UTF-8), so it is an *alternate*:
 * auto-discovery resolves one converter per type pair. Select this one either per-field
 * with `@ConvertWith(Base64ByteStringConverter::class)`, or register it in your
 * `@KMapperConfig(converters = [...])` *instead of* the UTF-8 converter.
 *
 * Decoding accepts both the standard and URL-safe alphabets and rejects malformed
 * input with [IllegalArgumentException]. Encoding uses the standard alphabet with padding.
 */
object Base64ByteStringConverter : MapTypeConverter<String, ByteString>(String::class, ByteString::class) {
    override fun convertTo(source: String): ByteString = requireNotNull(source.decodeBase64()) { "Not a valid Base64 string: '$source'" }

    override fun convertFrom(target: ByteString): String = target.base64()
}

/**
 * [String] ↔ [okio.ByteString] via URL-safe Base64 (RFC 4648 §5, `-` and `_` instead of `+` and `/`).
 *
 * Same type pair as [StringByteStringConverter] — see [Base64ByteStringConverter] for how to
 * select an alternate converter. Decoding accepts both alphabets; encoding emits the URL-safe one.
 */
object Base64UrlByteStringConverter : MapTypeConverter<String, ByteString>(String::class, ByteString::class) {
    override fun convertTo(source: String): ByteString = requireNotNull(source.decodeBase64()) { "Not a valid Base64 string: '$source'" }

    override fun convertFrom(target: ByteString): String = target.base64Url()
}

/**
 * [String] ↔ [okio.ByteString] via hexadecimal encoding.
 *
 * Same type pair as [StringByteStringConverter] — see [Base64ByteStringConverter] for how to
 * select an alternate converter. Decoding accepts upper- and lower-case digits and rejects
 * odd-length or non-hex input with [IllegalArgumentException]; encoding emits lower-case.
 */
object HexByteStringConverter : MapTypeConverter<String, ByteString>(String::class, ByteString::class) {
    override fun convertTo(source: String): ByteString = source.decodeHex()

    override fun convertFrom(target: ByteString): String = target.hex()
}
