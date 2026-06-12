package com.sahsenvar.kmapper.converter.builtin

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

class KotlinxDateTimeConvertersTest :
    FunSpec({
        context("LocalDate <-> String (ISO-8601)") {
            withData(
                "2026-06-12" to LocalDate(2026, 6, 12),
                "1970-01-01" to LocalDate(1970, 1, 1),
                "0001-01-01" to LocalDate(1, 1, 1), //       far past boundary
                "9999-12-31" to LocalDate(9999, 12, 31), //  far future boundary
                "2024-02-29" to LocalDate(2024, 2, 29), //   leap day
            ) { (iso, date) ->
                LocalDateStringConverter.convertFrom(iso) shouldBe date
                LocalDateStringConverter.convertTo(date) shouldBe iso
            }

            withData(
                nameFn = { "rejects '$it'" },
                "not-a-date",
                "2026-13-01",
                "2026-02-30",
                "2025-02-29",
                "12/06/2026",
                "",
                " 2026-06-12 ",
            ) { malformed ->
                shouldThrow<IllegalArgumentException> { LocalDateStringConverter.convertFrom(malformed) }
            }

            test("round-trip property: parse(format(date)) == date") {
                checkAll(Arb.int(1..9999), Arb.int(1..12), Arb.int(1..28)) { year, month, day ->
                    val date = LocalDate(year, month, day)
                    LocalDateStringConverter.convertFrom(LocalDateStringConverter.convertTo(date)) shouldBe date
                }
            }
        }

        context("LocalDateTime <-> String (ISO-8601)") {
            test("round-trips a representative value") {
                val dateTime = LocalDateTime(2026, 6, 12, 9, 30, 15)
                val iso = LocalDateTimeStringConverter.convertTo(dateTime)
                LocalDateTimeStringConverter.convertFrom(iso) shouldBe dateTime
            }
            test("rejects a zone-carrying instant string (LocalDateTime has no zone)") {
                shouldThrow<IllegalArgumentException> {
                    LocalDateTimeStringConverter.convertFrom("2026-06-12T09:30:00Z")
                }
            }
        }

        context("LocalTime <-> String (ISO-8601)") {
            withData(
                "09:30" to LocalTime(9, 30),
                "00:00" to LocalTime(0, 0), //         midnight boundary
                "23:59:59" to LocalTime(23, 59, 59), // end-of-day boundary
            ) { (iso, time) ->
                LocalTimeStringConverter.convertFrom(iso) shouldBe time
            }
            test("rejects out-of-range and malformed input") {
                shouldThrow<IllegalArgumentException> { LocalTimeStringConverter.convertFrom("24:00") }
                shouldThrow<IllegalArgumentException> { LocalTimeStringConverter.convertFrom("9:30 AM") }
            }
        }
    })
