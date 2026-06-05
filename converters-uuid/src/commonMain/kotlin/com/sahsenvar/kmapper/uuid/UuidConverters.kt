@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.sahsenvar.kmapper.uuid

import com.sahsenvar.kmapper.converter.MapTypeConverter
import kotlin.uuid.Uuid

/** [String] ↔ [kotlin.uuid.Uuid]. Parses RFC-4122 UUID strings in both directions. */
object StringUuidConverter : MapTypeConverter<String, Uuid>(String::class, Uuid::class) {
    override fun convertToNonNull(value: String): Uuid = Uuid.parse(value)
    override fun convertFromNonNull(value: Uuid): String = value.toString()
}
