package com.sahsenvar.kmapper.itest

import com.sahsenvar.kmapper.MappingException
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ValidationMappingTest {
    // ─── @ValidateFrom: validated on source field BEFORE mapping ─────────────

    @Test
    fun `valid ContactR maps successfully`() {
        val d = ContactR(name = "Alice", label = "work").toContactD()
        d.name shouldBe "Alice"
        d.label shouldBe "work"
    }

    @Test
    fun `blank name throws ValidationFailed from ValidateFrom`() {
        val ex =
            assertFailsWith<MappingException.ValidationFailed> {
                ContactR(name = "   ", label = "ok").toContactD()
            }
        ex.field shouldBe "name"
    }

    @Test
    fun `empty name throws ValidationFailed from ValidateFrom`() {
        val ex =
            assertFailsWith<MappingException.ValidationFailed> {
                ContactR(name = "", label = null).toContactD()
            }
        ex.field shouldBe "name"
    }

    // ─── @ValidateTo: validated on produced value AFTER mapping ──────────────

    @Test
    fun `blank label throws ValidationFailed from ValidateTo`() {
        val ex =
            assertFailsWith<MappingException.ValidationFailed> {
                ContactR(name = "Bob", label = "  ").toContactD()
            }
        ex.field shouldBe "label"
    }

    @Test
    fun `null label skips ValidateTo because null is not passed to validator`() {
        // null is never passed to the validator; mapping should succeed
        val d = ContactR(name = "Bob", label = null).toContactD()
        d.name shouldBe "Bob"
        d.label shouldBe null
    }

    // ─── ValidationFailed carries field + reason ─────────────────────────────

    @Test
    fun `ValidationFailed message contains field name and reason`() {
        val ex =
            assertFailsWith<MappingException.ValidationFailed> {
                ContactR(name = "", label = null).toContactD()
            }
        ex.field shouldBe "name"
        ex.reason shouldBe "must not be blank"
        (ex.message ?: "").contains("name") shouldBe true
    }
}
