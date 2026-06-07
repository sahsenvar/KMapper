package com.sahsenvar.kmapper.converter.builtin

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlin.test.Test

class DateTimeBuiltinConvertersTest {
    // ---- StringInstantConverter ----

    @Test fun `StringInstant round-trip ISO-8601`() {
        val iso = "2024-01-15T12:00:00Z"
        val instant = StringInstantConverter.convertToNonNull(iso)
        instant shouldBe Instant.parse(iso)
        StringInstantConverter.convertFromNonNull(instant) shouldBe instant.toString()
    }

    @Test fun `StringInstant null passthrough`() {
        StringInstantConverter.convertTo(null).shouldBeNull()
        StringInstantConverter.convertFrom(null).shouldBeNull()
    }

    @Test fun `StringInstant malformed string throws`() {
        shouldThrow<Throwable> { StringInstantConverter.convertToNonNull("not-a-date") }
    }

    // ---- LongInstantConverter ----

    @Test fun `LongInstant round-trip epoch millis`() {
        val millis = 1700000000000L
        val instant = LongInstantConverter.convertToNonNull(millis)
        instant shouldBe Instant.fromEpochMilliseconds(millis)
        LongInstantConverter.convertFromNonNull(instant) shouldBe millis
    }

    @Test fun `LongInstant epoch zero`() {
        val instant = LongInstantConverter.convertToNonNull(0L)
        instant shouldBe Instant.fromEpochMilliseconds(0L)
        LongInstantConverter.convertFromNonNull(instant) shouldBe 0L
    }

    @Test fun `LongInstant null passthrough`() {
        LongInstantConverter.convertTo(null).shouldBeNull()
        LongInstantConverter.convertFrom(null).shouldBeNull()
    }
}
