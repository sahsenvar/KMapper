package com.sahsenvar.kmapper.converter.builtin

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll

class StringConvertersTest :
    FunSpec({
        context("format (convertTo) is total") {
            // Rows are pure data (name, deferred conversion, expected); the conversion runs
            // inside the test lambda so a throwing converter fails its ONE named row instead
            // of aborting spec construction.
            withData<Triple<String, () -> Any, Any>>(
                nameFn = { it.first },
                Triple("Int zero", { IntStringConverter.convertTo(0) }, "0"),
                Triple("Int negative", { IntStringConverter.convertTo(-123) }, "-123"),
                Triple("Int MAX", { IntStringConverter.convertTo(Int.MAX_VALUE) }, "2147483647"),
                Triple("Int MIN", { IntStringConverter.convertTo(Int.MIN_VALUE) }, "-2147483648"),
                Triple("Long MAX", { LongStringConverter.convertTo(Long.MAX_VALUE) }, "9223372036854775807"),
                Triple("Long MIN", { LongStringConverter.convertTo(Long.MIN_VALUE) }, "-9223372036854775808"),
                Triple("Byte MAX", { ByteStringConverter.convertTo(Byte.MAX_VALUE) }, "127"),
                Triple("Short MIN", { ShortStringConverter.convertTo(Short.MIN_VALUE) }, "-32768"),
                Triple("Float decimal", { FloatStringConverter.convertTo(1.5f) }, "1.5"),
                Triple("Double decimal", { DoubleStringConverter.convertTo(1.5) }, "1.5"),
                Triple("Double NaN", { DoubleStringConverter.convertTo(Double.NaN) }, "NaN"),
                Triple("Double +inf", { DoubleStringConverter.convertTo(Double.POSITIVE_INFINITY) }, "Infinity"),
                Triple("Boolean true", { BooleanStringConverter.convertTo(true) }, "true"),
                Triple("Boolean false", { BooleanStringConverter.convertTo(false) }, "false"),
            ) { (_, conversion, expected) -> conversion() shouldBe expected }
        }

        context("parse (convertFrom) accepts valid input incl. boundaries") {
            withData<Triple<String, () -> Any, Any>>(
                nameFn = { it.first },
                Triple("Int leading zeros", { IntStringConverter.convertFrom("007") }, 7),
                Triple("Int explicit plus", { IntStringConverter.convertFrom("+5") }, 5),
                Triple("Int MIN", { IntStringConverter.convertFrom("-2147483648") }, Int.MIN_VALUE),
                Triple("Int MAX", { IntStringConverter.convertFrom("2147483647") }, Int.MAX_VALUE),
                Triple("Long MAX", { LongStringConverter.convertFrom("9223372036854775807") }, Long.MAX_VALUE),
                Triple("Long MIN", { LongStringConverter.convertFrom("-9223372036854775808") }, Long.MIN_VALUE),
                Triple("Byte MAX", { ByteStringConverter.convertFrom("127") }, 127.toByte()),
                Triple("Byte MIN", { ByteStringConverter.convertFrom("-128") }, (-128).toByte()),
                Triple("Short MAX", { ShortStringConverter.convertFrom("32767") }, Short.MAX_VALUE),
                Triple("Float decimal", { FloatStringConverter.convertFrom("1.5") }, 1.5f),
                Triple("Double decimal", { DoubleStringConverter.convertFrom("1.5") }, 1.5),
                Triple("Boolean true", { BooleanStringConverter.convertFrom("true") }, true),
                Triple("Boolean false", { BooleanStringConverter.convertFrom("false") }, false),
            ) { (_, conversion, expected) -> conversion() shouldBe expected }

            test("Double NaN parses (IEEE NaN != NaN, so assert via isNaN)") {
                DoubleStringConverter.convertFrom("NaN").isNaN() shouldBe true
                FloatStringConverter.convertFrom("NaN").isNaN() shouldBe true
            }
        }

        context("parse rejects malformed / out-of-range / wrong case") {
            withData<Pair<String, () -> Any>>(
                nameFn = { it.first },
                "int overflow" to { IntStringConverter.convertFrom("2147483648") },
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
