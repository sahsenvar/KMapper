package com.sahsenvar.kmapper.itest

import com.sahsenvar.kmapper.MappingException
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class ValidationMappingTest {
    // ─── @Validate on SOURCE field (ContactR.name): fires BEFORE mapping ─────

    @Test
    fun `valid ContactR maps successfully`() {
        val domain = ContactR(name = "Alice", label = "work").toContactDResult().getOrThrow()
        domain.name shouldBe "Alice"
        domain.label shouldBe "work"
    }

    @Test
    fun `blank name fails with ValidationFailed from source-field Validate`() {
        val outcome = ContactR(name = "   ", label = "ok").toContactDResult()
        outcome.isFailure shouldBe true
        val exception = outcome.exceptionOrNull().shouldBeInstanceOf<MappingException.ValidationFailed>()
        exception.path shouldBe "name"
    }

    @Test
    fun `empty name fails with ValidationFailed from source-field Validate`() {
        val outcome = ContactR(name = "", label = null).toContactDResult()
        outcome.isFailure shouldBe true
        val exception = outcome.exceptionOrNull().shouldBeInstanceOf<MappingException.ValidationFailed>()
        exception.path shouldBe "name"
    }

    // ─── @Validate on TARGET field (ContactD.label): fires AFTER mapping ─────

    @Test
    fun `blank label fails with ValidationFailed from target-field Validate`() {
        val outcome = ContactR(name = "Bob", label = "  ").toContactDResult()
        outcome.isFailure shouldBe true
        val exception = outcome.exceptionOrNull().shouldBeInstanceOf<MappingException.ValidationFailed>()
        exception.path shouldBe "label"
    }

    @Test
    fun `null label skips target-field Validate because null is not passed to validator`() {
        // null is never passed to the validator; mapping should succeed
        val domain = ContactR(name = "Bob", label = null).toContactDResult().getOrThrow()
        domain.name shouldBe "Bob"
        domain.label shouldBe null
    }

    // ─── ValidationFailed carries field path + reason ─────────────────────────

    @Test
    fun `ValidationFailed message contains field path and reason`() {
        val outcome = ContactR(name = "", label = null).toContactDResult()
        outcome.isFailure shouldBe true
        val exception = outcome.exceptionOrNull().shouldBeInstanceOf<MappingException.ValidationFailed>()
        exception.path shouldBe "name"
        exception.reason shouldBe "must not be blank"
        (exception.message ?: "") shouldContain "Validation failed for 'name': must not be blank"
    }
}
