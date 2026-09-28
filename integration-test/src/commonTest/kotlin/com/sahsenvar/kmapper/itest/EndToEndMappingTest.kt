package com.sahsenvar.kmapper.itest

import arrow.core.None
import arrow.core.Some
import com.sahsenvar.kmapper.MappingException
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFailsWith

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
        val domain = valid().toUserD()
        domain.id shouldBe "42"
        domain.joined shouldBe LocalDate(2026, 6, 4)
        domain.status shouldBe Status.ACTIVE
        domain.tags.map { it.name } shouldContainExactly listOf("kotlin")
        domain.roles.toList() shouldBe listOf("admin", "user")
    }

    @Test
    fun `null required id fails with RequiredFieldMissing carrying the field path`() {
        val exception =
            assertFailsWith<MappingException.RequiredFieldMissing> {
                valid().copy(id = null).toUserD()
            }
        exception.path shouldBe "id"
    }

    @Test
    fun `unknown enum value fails with UnknownEnumValue carrying the field path`() {
        val exception =
            assertFailsWith<MappingException.UnknownEnumValue> {
                valid().copy(status = "???").toUserD()
            }
        exception.path shouldBe "status"
        exception.value shouldBe "???"
    }

    @Test
    fun `empty roles fails with EmptyCollection carrying the field path`() {
        val exception =
            assertFailsWith<MappingException.EmptyCollection> {
                valid().copy(roles = emptyList()).toUserD()
            }
        exception.path shouldBe "roles"
        exception.detail shouldBe "NonEmptyList source was empty"
    }

    @Test
    fun `malformed date fails with TypeConversionFailed carrying the field path`() {
        val exception =
            assertFailsWith<MappingException.TypeConversionFailed> {
                valid().copy(joined = "not-a-date").toUserD()
            }
        exception.path shouldBe "joined"
    }

    // ─── Arrow Option<T> wrap tests (spec §6.8) ─────────────────────────────

    @Test
    fun `Option wrap — Some and None`() {
        val some = OptionSource("abc", TagR("tag1")).toOptionTarget()
        some.maybeId shouldBe Some("abc")
        some.maybeTag shouldBe Some(TagD("tag1"))

        val none = OptionSource(null, null).toOptionTarget()
        none.maybeId shouldBe None
        none.maybeTag shouldBe None
    }
}
