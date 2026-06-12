package com.sahsenvar.kmapper.converter.builtin

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.datetime.Instant

class InstantConvertersTest :
    FunSpec({
        test("ISO round-trip") {
            val iso = "2026-06-08T00:00:00Z"
            InstantStringConverter.convertTo(InstantStringConverter.convertFrom(iso)) shouldBe iso
        }

        context("malformed ISO throws") {
            withData(
                nameFn = { "rejects '$it'" },
                "not-a-date",
                "",
                "2026-13-45T99:99:99Z",
                "2026-06-08",
            ) { malformed ->
                shouldThrow<IllegalArgumentException> { InstantStringConverter.convertFrom(malformed) }
            }
        }

        test("epoch millis round-trip (property, within the Instant range so no clamping)") {
            // Instant.fromEpochMilliseconds clamps out-of-range values, so the property holds
            // only inside the representable range; +/-10^15 ms (~31,688 years) is safely inside.
            checkAll(Arb.long(-1_000_000_000_000_000L..1_000_000_000_000_000L)) { epochMillis ->
                InstantLongConverter.convertTo(InstantLongConverter.convertFrom(epochMillis)) shouldBe epochMillis
            }
        }

        test("boundary instant: epoch zero") {
            InstantLongConverter.convertFrom(0L) shouldBe Instant.fromEpochMilliseconds(0)
            InstantStringConverter.convertTo(Instant.fromEpochMilliseconds(0)) shouldBe "1970-01-01T00:00:00Z"
            InstantLongConverter.convertTo(Instant.fromEpochMilliseconds(0)) shouldBe 0L
        }

        test("negative epoch millis (pre-1970) survive the round-trip") {
            InstantLongConverter.convertTo(InstantLongConverter.convertFrom(-1L)) shouldBe -1L
        }
    })
