package com.sahsenvar.kmapper.itest

import arrow.core.None
import arrow.core.Some
import com.sahsenvar.kmapper.MappingException
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test

class EndToEndMappingTest {
    private fun valid() = UserR(
        id = "42",
        joined = "2026-06-04",
        status = "active",
        tags = listOf(TagR("kotlin")),
        roles = listOf("admin", "user"),
    )

    @Test
    fun `full happy-path mapping`() {
        val domain = valid().toUserDResult().getOrThrow()
        domain.id shouldBe "42"
        domain.joined shouldBe LocalDate(2026, 6, 4)
        domain.status shouldBe Status.ACTIVE
        domain.tags.map { it.name } shouldContainExactly listOf("kotlin")
        domain.roles.toList() shouldBe listOf("admin", "user")
    }

    @Test
    fun `null required id fails with RequiredFieldMissing carrying the field path`() {
        val outcome = valid().copy(id = null).toUserDResult()
        outcome.isFailure shouldBe true
        val exception = outcome.exceptionOrNull().shouldBeInstanceOf<MappingException.RequiredFieldMissing>()
        exception.path shouldBe "id"
    }

    @Test
    fun `unknown enum value fails with UnknownEnumValue carrying the field path`() {
        val outcome = valid().copy(status = "???").toUserDResult()
        outcome.isFailure shouldBe true
        val exception = outcome.exceptionOrNull().shouldBeInstanceOf<MappingException.UnknownEnumValue>()
        exception.path shouldBe "status"
        exception.value shouldBe "???"
    }

    @Test
    fun `empty roles fails with EmptyCollection carrying the field path`() {
        val outcome = valid().copy(roles = emptyList()).toUserDResult()
        outcome.isFailure shouldBe true
        val exception = outcome.exceptionOrNull().shouldBeInstanceOf<MappingException.EmptyCollection>()
        exception.path shouldBe "roles"
        exception.detail shouldBe "NonEmptyList source was empty"
    }

    @Test
    fun `malformed date fails with TypeConversionFailed carrying the field path`() {
        val outcome = valid().copy(joined = "not-a-date").toUserDResult()
        outcome.isFailure shouldBe true
        val exception = outcome.exceptionOrNull().shouldBeInstanceOf<MappingException.TypeConversionFailed>()
        exception.path shouldBe "joined"
    }

    // ─── Arrow Option<T> wrap tests (spec §6.8) ─────────────────────────────

    @Test
    fun `Option wrap — Some and None`() {
        val some = OptionSource("abc", TagR("tag1")).toOptionTargetResult().getOrThrow()
        some.maybeId shouldBe Some("abc")
        some.maybeTag shouldBe Some(TagD("tag1"))

        val none = OptionSource(null, null).toOptionTargetResult().getOrThrow()
        none.maybeId shouldBe None
        none.maybeTag shouldBe None
    }
}
