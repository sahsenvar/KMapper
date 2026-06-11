package com.sahsenvar.kmapper.converter.builtin

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll

class StringConvertersTest :
    FunSpec({
        context("format (convertTo) is total") {
            withData(
                nameFn = { it.first },
                Triple("Int zero", "0" as Any, IntStringConverter.convertTo(0) as Any),
                Triple("Int negative", "-123", IntStringConverter.convertTo(-123)),
                Triple("Int MAX", "2147483647", IntStringConverter.convertTo(Int.MAX_VALUE)),
                Triple("Int MIN", "-2147483648", IntStringConverter.convertTo(Int.MIN_VALUE)),
                Triple("Long MAX", "9223372036854775807", LongStringConverter.convertTo(Long.MAX_VALUE)),
                Triple("Long MIN", "-9223372036854775808", LongStringConverter.convertTo(Long.MIN_VALUE)),
                Triple("Byte MAX", "127", ByteStringConverter.convertTo(Byte.MAX_VALUE)),
                Triple("Short MIN", "-32768", ShortStringConverter.convertTo(Short.MIN_VALUE)),
                Triple("Float decimal", "1.5", FloatStringConverter.convertTo(1.5f)),
                Triple("Double decimal", "1.5", DoubleStringConverter.convertTo(1.5)),
                Triple("Double NaN", "NaN", DoubleStringConverter.convertTo(Double.NaN)),
                Triple("Double +inf", "Infinity", DoubleStringConverter.convertTo(Double.POSITIVE_INFINITY)),
                Triple("Boolean true", "true", BooleanStringConverter.convertTo(true)),
                Triple("Boolean false", "false", BooleanStringConverter.convertTo(false)),
            ) { (_, expected, actual) -> actual shouldBe expected }
        }

        context("parse (convertFrom) accepts valid input incl. boundaries") {
            withData(
                nameFn = { it.first },
                Triple("Int leading zeros", 7 as Any, IntStringConverter.convertFrom("007") as Any),
                Triple("Int explicit plus", 5, IntStringConverter.convertFrom("+5")),
                Triple("Int MIN", Int.MIN_VALUE, IntStringConverter.convertFrom("-2147483648")),
                Triple("Int MAX", Int.MAX_VALUE, IntStringConverter.convertFrom("2147483647")),
                Triple("Long MAX", Long.MAX_VALUE, LongStringConverter.convertFrom("9223372036854775807")),
                Triple("Long MIN", Long.MIN_VALUE, LongStringConverter.convertFrom("-9223372036854775808")),
                Triple("Byte MAX", 127.toByte(), ByteStringConverter.convertFrom("127")),
                Triple("Byte MIN", (-128).toByte(), ByteStringConverter.convertFrom("-128")),
                Triple("Short MAX", Short.MAX_VALUE, ShortStringConverter.convertFrom("32767")),
                Triple("Float decimal", 1.5f, FloatStringConverter.convertFrom("1.5")),
                Triple("Double decimal", 1.5, DoubleStringConverter.convertFrom("1.5")),
                Triple("Boolean true", true, BooleanStringConverter.convertFrom("true")),
                Triple("Boolean false", false, BooleanStringConverter.convertFrom("false")),
            ) { (_, expected, actual) -> actual shouldBe expected }

            test("Double NaN parses (IEEE NaN != NaN, so assert via isNaN)") {
                DoubleStringConverter.convertFrom("NaN").isNaN() shouldBe true
                FloatStringConverter.convertFrom("NaN").isNaN() shouldBe true
            }
        }

        context("parse rejects malformed / out-of-range / wrong case") {
            withData(
                nameFn = { it.first },
                "int overflow" to { IntStringConverter.convertFrom("2147483648") as Any },
                "int underflow" to { IntStringConverter.convertFrom("-2147483649") },
                "long overflow" to { LongStringConverter.convertFrom("9223372036854775808") },
                "byte overflow" to { ByteStringConverter.convertFrom("128") },
                "short overflow" to { ShortStringConverter.convertFrom("32768") },
                "alpha" to { IntStringConverter.convertFrom("abc") },
                "empty" to { IntStringConverter.convertFrom("") },
                "decimal into int" to { IntStringConverter.convertFrom("1.5") },
                "padded" to { IntStringConverter.convertFrom(" 5 ") },
                "float garbage" to { FloatStringConverter.convertFrom("1.5x") },
                "double empty" to { DoubleStringConverter.convertFrom("") },
            ) { (_, parseCall) -> shouldThrow<NumberFormatException> { parseCall() } }

            withData(
                nameFn = { "boolean '$it'" },
                "TRUE",
                "True",
                "FALSE",
                "yes",
                "1",
                "",
                " true ",
            ) { malformed ->
                shouldThrow<IllegalArgumentException> { BooleanStringConverter.convertFrom(malformed) }
            }
        }

        context("round-trip property: parse(format(x)) == x") {
            test("Byte") {
                checkAll<Byte> { value -> ByteStringConverter.convertFrom(ByteStringConverter.convertTo(value)) shouldBe value }
            }
            test("Short") {
                checkAll<Short> { value -> ShortStringConverter.convertFrom(ShortStringConverter.convertTo(value)) shouldBe value }
            }
            test("Int") {
                checkAll<Int> { value -> IntStringConverter.convertFrom(IntStringConverter.convertTo(value)) shouldBe value }
            }
            test("Long") {
                checkAll<Long> { value -> LongStringConverter.convertFrom(LongStringConverter.convertTo(value)) shouldBe value }
            }
            test("Float") {
                // Kotest float matchers follow IEEE semantics (NaN != NaN), so guard NaN explicitly.
                checkAll<Float> { value ->
                    val roundTripped = FloatStringConverter.convertFrom(FloatStringConverter.convertTo(value))
                    if (value.isNaN()) roundTripped.isNaN() shouldBe true else roundTripped shouldBe value
                }
            }
            test("Double") {
                checkAll<Double> { value ->
                    val roundTripped = DoubleStringConverter.convertFrom(DoubleStringConverter.convertTo(value))
                    if (value.isNaN()) roundTripped.isNaN() shouldBe true else roundTripped shouldBe value
                }
            }
            test("Boolean") {
                checkAll<Boolean> { value -> BooleanStringConverter.convertFrom(BooleanStringConverter.convertTo(value)) shouldBe value }
            }
        }
    })
