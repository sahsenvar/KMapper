package com.sahsenvar.kmapper.itest

import arrow.core.NonEmptyList
import arrow.core.NonEmptySet
import com.sahsenvar.kmapper.MappableEnum
import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.annotations.MapTo
import com.sahsenvar.kmapper.annotations.ValidateFrom
import com.sahsenvar.kmapper.annotations.ValidateTo
import com.sahsenvar.kmapper.arrow.NonEmptyListWrapper
import com.sahsenvar.kmapper.arrow.NonEmptySetWrapper
import com.sahsenvar.kmapper.datetime.StringLocalDateConverter
import com.sahsenvar.kmapper.immutable.PersistentListWrapper
import com.sahsenvar.kmapper.validation.builtin.NotBlankValidator
import kotlinx.collections.immutable.PersistentList
import kotlinx.datetime.LocalDate

// ─── Arrow Option<T> models (Group D, spec §6.8) ───────────────────────────

data class OptionTarget(
    val maybeId: arrow.core.Option<String>, // String? → Option<String>
    val maybeTag: arrow.core.Option<TagD>, // TagR?   → Option<TagD> (nested)
)

@MapTo(OptionTarget::class)
data class OptionSource(
    val maybeId: String?,
    val maybeTag: TagR?,
)

// ─── Existing models ────────────────────────────────────────────────────────

enum class Status(
    override val wireValue: String,
) : MappableEnum<String> {
    ACTIVE("active"),
    BANNED("banned"),
}

data class TagD(
    val name: String,
)

data class UserD(
    val id: String,
    val joined: LocalDate,
    val status: Status,
    val tags: PersistentList<TagD>,
    val roles: NonEmptyList<String>,
)

@KMapperConfig(
    converters = [StringLocalDateConverter::class],
    wrappers = [PersistentListWrapper::class, NonEmptyListWrapper::class, NonEmptySetWrapper::class],
)
object ItestMapperConfig

@MapTo(TagD::class)
data class TagR(
    val name: String,
)

@MapTo(UserD::class)
data class UserR(
    val id: String?, // nullable → non-null (RequiredFieldMissing path)
    val joined: String, // String → LocalDate (scalar converter via @KMapperConfig)
    val status: String, // String → Status enum (MappableEnum)
    val tags: List<TagR>, // List → PersistentList<TagD> (immutable add-on + nested element)
    val roles: List<String>, // List → NonEmptyList<String> (arrow add-on)
)

// ─── Validation E2E models (Group E) ────────────────────────────────────────

data class ContactD(
    val name: String,
    val label: String?,
)

@MapTo(ContactD::class)
data class ContactR(
    @ValidateFrom(NotBlankValidator::class) val name: String, // validated before mapping
    @ValidateTo(NotBlankValidator::class) val label: String?, // validated after mapping (skipped when null)
)

// ─── Map<K,V> E2E models (Group F) ──────────────────────────────────────────

data class AttrD(
    val value: String,
)

@MapTo(AttrD::class)
data class AttrR(
    val value: String,
)

data class CatalogD(
    val attrs: Map<String, AttrD>,
    val meta: Map<String, String>,
)

@MapTo(CatalogD::class)
data class CatalogR(
    val attrs: Map<String, AttrR>, // Map<String,AttrR> → Map<String,AttrD>
    val meta: Map<String, String>, // passthrough
)

// ─── NonEmptySet E2E models (Group G) ──────────────────────────────────────

data class PermissionD(
    val name: String,
)

@MapTo(PermissionD::class)
data class PermissionR(
    val name: String,
)

data class RoleD(
    val permissions: NonEmptySet<PermissionD>,
)

@MapTo(RoleD::class)
data class RoleR(
    val permissions: List<PermissionR>, // List<PermissionR> → NonEmptySet<PermissionD>
)
