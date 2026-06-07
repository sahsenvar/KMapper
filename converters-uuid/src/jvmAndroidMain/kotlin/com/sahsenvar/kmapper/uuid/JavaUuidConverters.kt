@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.sahsenvar.kmapper.uuid

import com.sahsenvar.kmapper.converter.MapTypeConverter
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/** [String] ↔ [java.util.UUID]. Parses RFC-4122 UUID strings via [UUID.fromString]. */
object JavaStringUuidConverter : MapTypeConverter<String, UUID>(String::class, UUID::class) {
    override fun convertToNonNull(value: String): UUID = UUID.fromString(value)

    override fun convertFromNonNull(value: UUID): String = value.toString()
}

/** [kotlin.uuid.Uuid] ↔ [java.util.UUID] bridge via stdlib extensions. */
object KotlinJavaUuidConverter : MapTypeConverter<Uuid, UUID>(Uuid::class, UUID::class) {
    override fun convertToNonNull(value: Uuid): UUID = value.toJavaUuid()

    override fun convertFromNonNull(value: UUID): Uuid = value.toKotlinUuid()
}
