package com.sahsenvar.kmapper.okio

import com.sahsenvar.kmapper.converter.MapTypeConverter
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.Path
import okio.Path.Companion.toPath

/** [String] ↔ [okio.ByteString] via UTF-8 encoding. */
object StringByteStringConverter : MapTypeConverter<String, ByteString>(String::class, ByteString::class) {
    override fun convertToNonNull(value: String): ByteString = value.encodeUtf8()

    override fun convertFromNonNull(value: ByteString): String = value.utf8()
}

/** [ByteArray] ↔ [okio.ByteString]. */
object ByteArrayByteStringConverter : MapTypeConverter<ByteArray, ByteString>(ByteArray::class, ByteString::class) {
    override fun convertToNonNull(value: ByteArray): ByteString = value.toByteString()

    override fun convertFromNonNull(value: ByteString): ByteArray = value.toByteArray()
}

/** [String] ↔ [okio.Path]. */
object StringPathConverter : MapTypeConverter<String, Path>(String::class, Path::class) {
    override fun convertToNonNull(value: String): Path = value.toPath()

    override fun convertFromNonNull(value: Path): String = value.toString()
}
