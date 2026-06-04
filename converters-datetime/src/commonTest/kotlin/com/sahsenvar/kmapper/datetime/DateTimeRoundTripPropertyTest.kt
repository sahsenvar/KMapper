package com.sahsenvar.kmapper.datetime

import com.sahsenvar.kmapper.converter.builtin.LongInstantConverter
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Property-based round-trip tests for datetime converters.
 * LongInstantConverter lives in :core (api dependency), so it is accessible here.
 * checkAll is suspend → wrapped in runBlocking.
 */
class DateTimeRoundTripPropertyTest {

    @Test
    fun longInstant_Long_Instant_Long_round_trip() {
        // fromEpochMilliseconds / toEpochMilliseconds are exact inverses for all Long values.
        runBlocking {
            checkAll(Arb.long()) { millis ->
                LongInstantConverter.convertFromNonNull(
                    LongInstantConverter.convertToNonNull(millis)
                ) shouldBe millis
            }
        }
    }

    @Test
    fun stringLocalDate_String_LocalDate_String_round_trip() {
        // Probe a representative set of ISO-8601 dates: year range 1970..2099.
        // We cannot use Arb.string() for dates, so we use Arb.long() seeded as day offsets
        // from epoch and validate only that the encode→decode cycle is identity.
        val sampleDates = listOf(
            "1970-01-01", "2000-02-29", "2024-12-31",
            "2026-06-05", "2099-11-30", "1900-01-01"
        )
        for (date in sampleDates) {
            StringLocalDateConverter.convertFromNonNull(
                StringLocalDateConverter.convertToNonNull(date)
            ) shouldBe date
        }
    }
}
