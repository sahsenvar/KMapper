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
        val d = valid().toUserD()
        d.id shouldBe "42"
        d.joined shouldBe LocalDate(2026, 6, 4)
        d.status shouldBe Status.ACTIVE
        d.tags.map { it.name } shouldContainExactly listOf("kotlin")
        d.roles.toList() shouldBe listOf("admin", "user")
    }

    @Test
    fun `null required id throws RequiredFieldMissing`() {
        assertFailsWith<MappingException.RequiredFieldMissing> {
            valid().copy(id = null).toUserD()
        }
    }

    @Test
    fun `unknown enum value throws UnknownEnumValue`() {
        assertFailsWith<MappingException.UnknownEnumValue> {
            valid().copy(status = "???").toUserD()
        }
    }

    @Test
    fun `empty roles throws EmptyCollection`() {
        assertFailsWith<MappingException.EmptyCollection> {
            valid().copy(roles = emptyList()).toUserD()
        }
    }

    @Test
    fun `malformed date throws TypeConversionFailed`() {
        assertFailsWith<MappingException.TypeConversionFailed> {
            valid().copy(joined = "not-a-date").toUserD()
        }
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
