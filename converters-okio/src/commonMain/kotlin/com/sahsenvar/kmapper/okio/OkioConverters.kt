package com.sahsenvar.kmapper.okio

import com.sahsenvar.kmapper.converter.MapTypeConverter
import okio.ByteString
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
