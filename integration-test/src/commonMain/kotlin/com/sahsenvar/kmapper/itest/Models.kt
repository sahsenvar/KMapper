package com.sahsenvar.kmapper.itest

import com.sahsenvar.kmapper.MappableEnum
import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.annotations.MapTo
import com.sahsenvar.kmapper.arrow.NonEmptyListWrapper
import com.sahsenvar.kmapper.datetime.StringLocalDateConverter
import com.sahsenvar.kmapper.immutable.PersistentListWrapper
import arrow.core.NonEmptyList
import kotlinx.collections.immutable.PersistentList
import kotlinx.datetime.LocalDate

enum class Status(override val wireValue: String) : MappableEnum<String> {
    ACTIVE("active"),
    BANNED("banned"),
}

data class TagD(val name: String)

data class UserD(
    val id: String,
    val joined: LocalDate,
    val status: Status,
    val tags: PersistentList<TagD>,
    val roles: NonEmptyList<String>,
)

@KMapperConfig(converters = [StringLocalDateConverter::class], wrappers = [PersistentListWrapper::class, NonEmptyListWrapper::class])
object ItestMapperConfig

@MapTo(TagD::class)
data class TagR(val name: String)

@MapTo(UserD::class)
data class UserR(
    val id: String?,         // nullable → non-null (RequiredFieldMissing path)
    val joined: String,      // String → LocalDate (scalar converter via @KMapperConfig)
    val status: String,      // String → Status enum (MappableEnum)
    val tags: List<TagR>,    // List → PersistentList<TagD> (immutable add-on + nested element)
    val roles: List<String>, // List → NonEmptyList<String> (arrow add-on)
)
