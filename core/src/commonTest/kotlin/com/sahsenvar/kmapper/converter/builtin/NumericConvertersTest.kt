package com.sahsenvar.kmapper.converter.builtin

import com.sahsenvar.kmapper.MappingException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll

class NumericConvertersTest :
    FunSpec({
        context("widening converters: poorer -> richer via convertFrom, exact at boundaries") {
            withData(
                nameFn = { it.first },
                // Byte-based pairs
                Triple("ShortByte MAX", ShortByteConverter.convertFrom(Byte.MAX_VALUE) as Any, 127.toShort() as Any),
                Triple("ShortByte MIN", ShortByteConverter.convertFrom(Byte.MIN_VALUE), (-128).toShort()),
                Triple("IntByte MAX", IntByteConverter.convertFrom(Byte.MAX_VALUE), 127),
                Triple("IntByte MIN", IntByteConverter.convertFrom(Byte.MIN_VALUE), -128),
                Triple("LongByte MAX", LongByteConverter.convertFrom(Byte.MAX_VALUE), 127L),
                Triple("LongByte MIN", LongByteConverter.convertFrom(Byte.MIN_VALUE), -128L),
                Triple("FloatByte MAX", FloatByteConverter.convertFrom(Byte.MAX_VALUE), 127f),
                Triple("FloatByte MIN", FloatByteConverter.convertFrom(Byte.MIN_VALUE), -128f),
                Triple("DoubleByte MAX", DoubleByteConverter.convertFrom(Byte.MAX_VALUE), 127.0),
                Triple("DoubleByte MIN", DoubleByteConverter.convertFrom(Byte.MIN_VALUE), -128.0),
                // Short-based pairs
                Triple("IntShort MAX", IntShortConverter.convertFrom(Short.MAX_VALUE), Short.MAX_VALUE.toInt()),
                Triple("IntShort MIN", IntShortConverter.convertFrom(Short.MIN_VALUE), Short.MIN_VALUE.toInt()),
                Triple("LongShort MAX", LongShortConverter.convertFrom(Short.MAX_VALUE), Short.MAX_VALUE.toLong()),
                Triple("LongShort MIN", LongShortConverter.convertFrom(Short.MIN_VALUE), Short.MIN_VALUE.toLong()),
                Triple("FloatShort MAX exact", FloatShortConverter.convertFrom(Short.MAX_VALUE), Short.MAX_VALUE.toFloat()),
                Triple("FloatShort MIN exact", FloatShortConverter.convertFrom(Short.MIN_VALUE), Short.MIN_VALUE.toFloat()),
                Triple("DoubleShort MAX exact", DoubleShortConverter.convertFrom(Short.MAX_VALUE), Short.MAX_VALUE.toDouble()),
                Triple("DoubleShort MIN exact", DoubleShortConverter.convertFrom(Short.MIN_VALUE), Short.MIN_VALUE.toDouble()),
                // Int-based pairs
                Triple("LongInt MAX", LongIntConverter.convertFrom(Int.MAX_VALUE), Int.MAX_VALUE.toLong()),
                Triple("LongInt MIN", LongIntConverter.convertFrom(Int.MIN_VALUE), Int.MIN_VALUE.toLong()),
                Triple("LongInt zero", LongIntConverter.convertFrom(0), 0L),
                Triple("LongInt minus one", LongIntConverter.convertFrom(-1), -1L),
                Triple("DoubleInt MAX exact", DoubleIntConverter.convertFrom(Int.MAX_VALUE), 2147483647.0),
                Triple("DoubleInt MIN exact", DoubleIntConverter.convertFrom(Int.MIN_VALUE), -2147483648.0),
                // Float -> Double pair
                Triple("DoubleFloat MAX exact", DoubleFloatConverter.convertFrom(Float.MAX_VALUE), Float.MAX_VALUE.toDouble()),
                Triple("DoubleFloat smallest positive", DoubleFloatConverter.convertFrom(Float.MIN_VALUE), Float.MIN_VALUE.toDouble()),
                Triple("DoubleFloat +inf", DoubleFloatConverter.convertFrom(Float.POSITIVE_INFINITY), Double.POSITIVE_INFINITY),
                Triple("DoubleFloat -inf", DoubleFloatConverter.convertFrom(Float.NEGATIVE_INFINITY), Double.NEGATIVE_INFINITY),
            ) { (_, actual, expected) -> actual shouldBe expected }

            test("Float NaN survives DoubleFloat widening") {
                DoubleFloatConverter.convertFrom(Float.NaN).isNaN() shouldBe true
            }
        }

        context("widening preserves the value (round-trip property per pair)") {
            test("ShortByte") {
                checkAll<Byte> { value -> ShortByteConverter.convertFrom(value).toByte() shouldBe value }
            }
            test("IntByte") {
                checkAll<Byte> { value -> IntByteConverter.convertFrom(value).toByte() shouldBe value }
            }
            test("LongByte") {
                checkAll<Byte> { value -> LongByteConverter.convertFrom(value).toByte() shouldBe value }
            }
            test("IntShort") {
                checkAll<Short> { value -> IntShortConverter.convertFrom(value).toShort() shouldBe value }
            }
            test("LongShort") {
                checkAll<Short> { value -> LongShortConverter.convertFrom(value).toShort() shouldBe value }
            }
            test("LongInt") {
                checkAll<Int> { value -> LongIntConverter.convertFrom(value).toInt() shouldBe value }
            }
            test("FloatByte") {
                checkAll<Byte> { value -> FloatByteConverter.convertFrom(value).toInt().toByte() shouldBe value }
            }
            test("DoubleByte") {
                checkAll<Byte> { value -> DoubleByteConverter.convertFrom(value).toInt().toByte() shouldBe value }
            }
            test("FloatShort") {
                checkAll<Short> { value -> FloatShortConverter.convertFrom(value).toInt().toShort() shouldBe value }
            }
            test("DoubleShort") {
                checkAll<Short> { value -> DoubleShortConverter.convertFrom(value).toInt().toShort() shouldBe value }
            }
            test("DoubleInt") {
                checkAll<Int> { value -> DoubleIntConverter.convertFrom(value).toInt() shouldBe value }
            }
            test("DoubleFloat") {
                // Kotest float matchers follow IEEE semantics (NaN != NaN), so guard NaN explicitly.
                checkAll<Float> { value ->
                    val widened = DoubleFloatConverter.convertFrom(value)
                    if (value.isNaN()) widened.isNaN() shouldBe true else widened.toFloat() shouldBe value
                }
            }
        }

        context("narrowing direction is UnsupportedConversion for every widening pair") {
            withData(
                nameFn = { it.first },
                "Short->Byte" to { ShortByteConverter.convertTo(5.toShort()) as Any },
                "Int->Byte" to { IntByteConverter.convertTo(5) },
                "Long->Byte" to { LongByteConverter.convertTo(5L) },
                "Int->Short" to { IntShortConverter.convertTo(5) },
                "Long->Short" to { LongShortConverter.convertTo(5L) },
                "Long->Int" to { LongIntConverter.convertTo(5L) },
                "Float->Byte" to { FloatByteConverter.convertTo(5f) },
                "Double->Byte" to { DoubleByteConverter.convertTo(5.0) },
                "Float->Short" to { FloatShortConverter.convertTo(5f) },
                "Double->Short" to { DoubleShortConverter.convertTo(5.0) },
                "Double->Int" to { DoubleIntConverter.convertTo(5.0) },
                "Double->Float" to { DoubleFloatConverter.convertTo(1.0) },
            ) { (_, narrowingCall) ->
                shouldThrow<MappingException.UnsupportedConversion> { narrowingCall() }
            }
        }

        context("X-pairs refuse BOTH directions") {
            withData(
                nameFn = { it.first },
                Triple("FloatInt", { FloatIntConverter.convertTo(1f) as Any }, { FloatIntConverter.convertFrom(1) as Any }),
                Triple("FloatLong", { FloatLongConverter.convertTo(1f) }, { FloatLongConverter.convertFrom(1L) }),
                Triple("DoubleLong", { DoubleLongConverter.convertTo(1.0) }, { DoubleLongConverter.convertFrom(1L) }),
                Triple("ByteBoolean", { ByteBooleanConverter.convertTo(1.toByte()) }, { ByteBooleanConverter.convertFrom(true) }),
                Triple("ShortBoolean", { ShortBooleanConverter.convertTo(1.toShort()) }, { ShortBooleanConverter.convertFrom(true) }),
                Triple("IntBoolean", { IntBooleanConverter.convertTo(1) }, { IntBooleanConverter.convertFrom(true) }),
                Triple("LongBoolean", { LongBooleanConverter.convertTo(1L) }, { LongBooleanConverter.convertFrom(true) }),
                Triple("FloatBoolean", { FloatBooleanConverter.convertTo(1f) }, { FloatBooleanConverter.convertFrom(true) }),
                Triple("DoubleBoolean", { DoubleBooleanConverter.convertTo(1.0) }, { DoubleBooleanConverter.convertFrom(true) }),
            ) { (_, forwardCall, reverseCall) ->
                shouldThrow<MappingException.UnsupportedConversion> { forwardCall() }
                shouldThrow<MappingException.UnsupportedConversion> { reverseCall() }
            }
        }
    })
