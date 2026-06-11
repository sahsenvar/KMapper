package com.sahsenvar.kmapper.bignumber

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IonspinConvertersTest {
    // StringBigDecimalConverter
    @Test
    fun stringBigDecimal_parseFromString() {
        val result = StringBigDecimalConverter.convertTo("123.456")
        assertEquals(BigDecimal.parseString("123.456"), result)
    }

    @Test
    fun stringBigDecimal_formatToString() {
        val bd = BigDecimal.parseString("123.456")
        val result = StringBigDecimalConverter.convertFrom(bd)
        // toStringExpanded returns the decimal without scientific notation
        assertTrue(result.contains("123") && result.contains("456"), "Expected '123.456' in '$result'")
    }

    @Test
    fun stringBigDecimal_roundTrip() {
        val s = "9999999999999999.12345"
        val converted = StringBigDecimalConverter.convertFrom(StringBigDecimalConverter.convertTo(s))
        // Round-trip: re-parse both and compare values
        assertEquals(BigDecimal.parseString(s), BigDecimal.parseString(converted))
    }

    // StringBigIntegerConverter
    @Test
    fun stringBigInteger_parseFromString() {
        assertEquals(BigInteger.parseString("12345678901234567890", 10), StringBigIntegerConverter.convertTo("12345678901234567890"))
    }

    @Test
    fun stringBigInteger_formatToString() {
        val bi = BigInteger.parseString("12345678901234567890", 10)
        assertEquals("12345678901234567890", StringBigIntegerConverter.convertFrom(bi))
    }

    @Test
    fun stringBigInteger_roundTrip() {
        val s = "99999999999999999999999999"
        assertEquals(s, StringBigIntegerConverter.convertFrom(StringBigIntegerConverter.convertTo(s)))
    }

    // DoubleBigDecimalConverter
    @Test
    fun doubleBigDecimal_fromDouble() {
        val bd = DoubleBigDecimalConverter.convertTo(1.5)
        assertEquals(BigDecimal.fromDouble(1.5), bd)
    }

    @Test
    fun doubleBigDecimal_toDouble() {
        val bd = BigDecimal.fromDouble(3.14)
        val result = DoubleBigDecimalConverter.convertFrom(bd)
        assertEquals(3.14, result, 0.0001)
    }

    @Test
    fun doubleBigDecimal_roundTrip() {
        val d = 42.0
        assertEquals(d, DoubleBigDecimalConverter.convertFrom(DoubleBigDecimalConverter.convertTo(d)), 0.0001)
    }

    // LongBigIntegerConverter
    @Test
    fun longBigInteger_fromLong() {
        assertEquals(BigInteger.fromLong(Long.MAX_VALUE), LongBigIntegerConverter.convertTo(Long.MAX_VALUE))
    }

    @Test
    fun longBigInteger_toLong() {
        val bi = BigInteger.fromLong(1_000_000_000L)
        assertEquals(1_000_000_000L, LongBigIntegerConverter.convertFrom(bi))
    }

    @Test
    fun longBigInteger_roundTrip() {
        val v = 123456789L
        assertEquals(v, LongBigIntegerConverter.convertFrom(LongBigIntegerConverter.convertTo(v)))
    }

    // IntBigIntegerConverter
    @Test
    fun intBigInteger_fromInt() {
        assertEquals(BigInteger.fromInt(Int.MAX_VALUE), IntBigIntegerConverter.convertTo(Int.MAX_VALUE))
    }

    @Test
    fun intBigInteger_toInt() {
        val bi = BigInteger.fromInt(42)
        assertEquals(42, IntBigIntegerConverter.convertFrom(bi))
    }

    @Test
    fun intBigInteger_roundTrip() {
        val v = -12345
        assertEquals(v, IntBigIntegerConverter.convertFrom(IntBigIntegerConverter.convertTo(v)))
    }

    // BigIntegerBigDecimalConverter
    @Test
    fun bigIntegerBigDecimal_fromBigInteger() {
        val bi = BigInteger.fromLong(9876543210L)
        val bd = BigIntegerBigDecimalConverter.convertTo(bi)
        assertEquals(BigDecimal.fromBigInteger(bi), bd)
    }

    @Test
    fun bigIntegerBigDecimal_toBigInteger() {
        val bi = BigInteger.fromLong(100L)
        val bd = BigDecimal.fromBigInteger(bi)
        assertEquals(bi, BigIntegerBigDecimalConverter.convertFrom(bd))
    }

    @Test
    fun bigIntegerBigDecimal_roundTrip() {
        val bi = BigInteger.parseString("123456789012345678901234567890", 10)
        assertEquals(bi, BigIntegerBigDecimalConverter.convertFrom(BigIntegerBigDecimalConverter.convertTo(bi)))
    }
}
