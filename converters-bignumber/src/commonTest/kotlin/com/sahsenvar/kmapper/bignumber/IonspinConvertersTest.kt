package com.sahsenvar.kmapper.bignumber

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.sahsenvar.kmapper.MappingException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun doubleBigDecimal_narrowingToDoubleIsRefused() {
        // BigDecimal -> Double can silently lose precision, so the direction is @UnsupportedDirection.
        val failure =
            assertFailsWith<MappingException.UnsupportedConversion> {
                DoubleBigDecimalConverter.convertFrom(BigDecimal.fromDouble(3.14))
            }
        assertTrue("unsupported" in failure.message.orEmpty(), "Expected guidance in '${failure.message}'")
    }

    @Test
    fun doubleBigDecimal_wideningHandlesExtremes() {
        assertEquals(BigDecimal.fromDouble(Double.MAX_VALUE), DoubleBigDecimalConverter.convertTo(Double.MAX_VALUE))
        assertEquals(BigDecimal.fromDouble(-Double.MAX_VALUE), DoubleBigDecimalConverter.convertTo(-Double.MAX_VALUE))
    }

    // LongBigIntegerConverter
    @Test
    fun longBigInteger_fromLong() {
        assertEquals(BigInteger.fromLong(Long.MAX_VALUE), LongBigIntegerConverter.convertTo(Long.MAX_VALUE))
    }

    @Test
    fun longBigInteger_narrowingToLongIsRefused() {
        // BigInteger -> Long can overflow (previously truncated SILENTLY), so the direction is refused.
        assertFailsWith<MappingException.UnsupportedConversion> {
            LongBigIntegerConverter.convertFrom(BigInteger.fromLong(1_000_000_000L))
        }
    }

    @Test
    fun longBigInteger_wideningHandlesBoundaries() {
        assertEquals(BigInteger.fromLong(Long.MIN_VALUE), LongBigIntegerConverter.convertTo(Long.MIN_VALUE))
        assertEquals(BigInteger.fromLong(0L), LongBigIntegerConverter.convertTo(0L))
    }

    // IntBigIntegerConverter
    @Test
    fun intBigInteger_fromInt() {
        assertEquals(BigInteger.fromInt(Int.MAX_VALUE), IntBigIntegerConverter.convertTo(Int.MAX_VALUE))
    }

    @Test
    fun intBigInteger_narrowingToIntIsRefused() {
        // BigInteger -> Int can overflow (previously truncated SILENTLY), so the direction is refused.
        assertFailsWith<MappingException.UnsupportedConversion> {
            IntBigIntegerConverter.convertFrom(BigInteger.fromInt(42))
        }
    }

    @Test
    fun intBigInteger_wideningHandlesBoundaries() {
        assertEquals(BigInteger.fromInt(Int.MIN_VALUE), IntBigIntegerConverter.convertTo(Int.MIN_VALUE))
        assertEquals(BigInteger.fromInt(-1), IntBigIntegerConverter.convertTo(-1))
    }

    // BigIntegerBigDecimalConverter
    @Test
    fun bigIntegerBigDecimal_fromBigInteger() {
        val bi = BigInteger.fromLong(9876543210L)
        val bd = BigIntegerBigDecimalConverter.convertTo(bi)
        assertEquals(BigDecimal.fromBigInteger(bi), bd)
    }

    @Test
    fun bigIntegerBigDecimal_narrowingToBigIntegerIsRefused() {
        // BigDecimal -> BigInteger silently drops any fractional part, so the direction is refused.
        assertFailsWith<MappingException.UnsupportedConversion> {
            BigIntegerBigDecimalConverter.convertFrom(BigDecimal.fromBigInteger(BigInteger.fromLong(100L)))
        }
    }

    @Test
    fun bigIntegerBigDecimal_wideningPreservesHugeValues() {
        val bi = BigInteger.parseString("123456789012345678901234567890", 10)
        assertEquals(BigDecimal.fromBigInteger(bi), BigIntegerBigDecimalConverter.convertTo(bi))
    }
}
