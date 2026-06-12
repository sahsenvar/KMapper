package com.sahsenvar.kmapper.converter.builtin

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class DurationConvertersTest :
    FunSpec({
        context("Duration <-> ISO-8601 String") {
            withData(
                nameFn = { it.second },
                (1.hours + 30.minutes) to "PT1H30M",
                90.seconds to "PT1M30S",
                Duration.ZERO to "PT0S",
                (-15).minutes to "-PT15M", //  negative durations are legal ISO
            ) { (duration, iso) ->
                DurationStringConverter.convertTo(duration) shouldBe iso
                DurationStringConverter.convertFrom(iso) shouldBe duration
            }

            withData(
                nameFn = { "rejects '$it'" },
                "1h 30m", //  Kotlin's lenient format is NOT accepted — ISO only, one canonical form
                "90",
                "",
                "PT",
                "P1D2H", // malformed/incomplete
            ) { malformed ->
                shouldThrow<IllegalArgumentException> { DurationStringConverter.convertFrom(malformed) }
            }

            test("round-trip property over whole-millisecond durations") {
                checkAll(Arb.long(-1_000_000_000L..1_000_000_000L)) { millis ->
                    val duration = millis.milliseconds
                    DurationStringConverter.convertFrom(DurationStringConverter.convertTo(duration)) shouldBe duration
                }
            }
        }

        context("Duration <-> whole-milliseconds Long") {
            test("round-trip property") {
                checkAll(Arb.long(-1_000_000_000_000L..1_000_000_000_000L)) { millis ->
                    DurationLongConverter.convertTo(DurationLongConverter.convertFrom(millis)) shouldBe millis
                }
            }
            test("sub-millisecond precision truncates on the way out (documented trade-off)") {
                DurationLongConverter.convertTo(1500.nanoseconds) shouldBe 0L
                DurationLongConverter.convertTo(1.milliseconds + 999.nanoseconds) shouldBe 1L
            }
        }
    })
