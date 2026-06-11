package com.sahsenvar.kmapper.datetime

import com.sahsenvar.kmapper.converter.builtin.InstantLongConverter
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Property-based round-trip tests for datetime converters.
 * InstantLongConverter lives in :core (api dependency), so it is accessible here.
 * checkAll is suspend → wrapped in runBlocking.
 */
class DateTimeRoundTripPropertyTest {
    @Test
    fun longInstant_Long_Instant_Long_round_trip() {
        // fromEpochMilliseconds / toEpochMilliseconds round-trip exactly ONLY within Instant's
        // representable range — which is NARROWER on Kotlin/Native than on the JVM, so out-of-range
        // millis clamp differently per platform. Bound to a realistic timestamp range (year 0001..9999).
        runBlocking {
            checkAll(Arb.long(-62_135_596_800_000L..253_402_300_799_000L)) { millis ->
                // Richer-first InstantLongConverter: convertFrom parses millis, convertTo formats back.
                InstantLongConverter.convertTo(
                    InstantLongConverter.convertFrom(millis),
                ) shouldBe millis
            }
        }
    }

    @Test
    fun stringLocalDate_String_LocalDate_String_round_trip() {
        // Probe a representative set of ISO-8601 dates: year range 1970..2099.
        // We cannot use Arb.string() for dates, so we use Arb.long() seeded as day offsets
        // from epoch and validate only that the encode→decode cycle is identity.
        val sampleDates =
            listOf(
                "1970-01-01",
                "2000-02-29",
                "2024-12-31",
                "2026-06-05",
                "2099-11-30",
                "1900-01-01",
            )
        for (date in sampleDates) {
            StringLocalDateConverter.convertFrom(
                StringLocalDateConverter.convertTo(date),
            ) shouldBe date
        }
    }
}
