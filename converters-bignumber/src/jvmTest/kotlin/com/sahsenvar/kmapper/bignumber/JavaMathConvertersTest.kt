package com.sahsenvar.kmapper.bignumber

import com.sahsenvar.kmapper.MappingException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.math.BigDecimal as JBigDecimal
import java.math.BigInteger as JBigInteger

class JavaMathConvertersTest {
    // StringJavaBigDecimalConverter
    @Test
    fun stringJavaBigDecimal_parseFromString() {
        assertEquals(JBigDecimal("123.456"), StringJavaBigDecimalConverter.convertTo("123.456"))
    }

    @Test
    fun stringJavaBigDecimal_formatToString() {
        assertEquals("123.456", StringJavaBigDecimalConverter.convertFrom(JBigDecimal("123.456")))
    }

    @Test
    fun stringJavaBigDecimal_roundTrip() {
        val s = "9999999999999999.12345"
        assertEquals(s, StringJavaBigDecimalConverter.convertFrom(StringJavaBigDecimalConverter.convertTo(s)))
    }

    @Test
    fun stringJavaBigDecimal_noScientificNotation() {
        // toPlainString must not produce scientific notation
        val s = "0.000001"
        val result = StringJavaBigDecimalConverter.convertFrom(StringJavaBigDecimalConverter.convertTo(s))
        assertEquals(s, result)
    }

    // StringJavaBigIntegerConverter
    @Test
    fun stringJavaBigInteger_parseFromString() {
        assertEquals(JBigInteger("12345678901234567890"), StringJavaBigIntegerConverter.convertTo("12345678901234567890"))
    }

    @Test
    fun stringJavaBigInteger_formatToString() {
        assertEquals("12345678901234567890", StringJavaBigIntegerConverter.convertFrom(JBigInteger("12345678901234567890")))
    }

    @Test
    fun stringJavaBigInteger_roundTrip() {
        val s = "99999999999999999999999999"
        assertEquals(s, StringJavaBigIntegerConverter.convertFrom(StringJavaBigIntegerConverter.convertTo(s)))
    }

    // DoubleJavaBigDecimalConverter
    @Test
    fun doubleJavaBigDecimal_fromDouble() {
        assertEquals(JBigDecimal.valueOf(1.5), DoubleJavaBigDecimalConverter.convertTo(1.5))
    }

    @Test
    fun doubleJavaBigDecimal_narrowingToDoubleIsRefused() {
        // BigDecimal -> Double can silently lose precision, so the direction is @UnsupportedDirection.
        assertFailsWith<MappingException.UnsupportedConversion> {
            DoubleJavaBigDecimalConverter.convertFrom(JBigDecimal.valueOf(3.14))
        }
    }

    @Test
    fun doubleJavaBigDecimal_wideningHandlesExtremes() {
        assertEquals(JBigDecimal.valueOf(Double.MAX_VALUE), DoubleJavaBigDecimalConverter.convertTo(Double.MAX_VALUE))
        assertEquals(JBigDecimal.valueOf(-Double.MAX_VALUE), DoubleJavaBigDecimalConverter.convertTo(-Double.MAX_VALUE))
    }

    // LongJavaBigIntegerConverter
    @Test
    fun longJavaBigInteger_fromLong() {
        assertEquals(JBigInteger.valueOf(Long.MAX_VALUE), LongJavaBigIntegerConverter.convertTo(Long.MAX_VALUE))
    }

    @Test
    fun longJavaBigInteger_narrowingToLongIsRefused() {
        // BigInteger -> Long can overflow (previously truncated SILENTLY), so the direction is refused.
        assertFailsWith<MappingException.UnsupportedConversion> {
            LongJavaBigIntegerConverter.convertFrom(JBigInteger.valueOf(1_000_000_000L))
        }
    }

    @Test
    fun longJavaBigInteger_wideningHandlesBoundaries() {
        assertEquals(JBigInteger.valueOf(Long.MIN_VALUE), LongJavaBigIntegerConverter.convertTo(Long.MIN_VALUE))
        assertEquals(JBigInteger.ZERO, LongJavaBigIntegerConverter.convertTo(0L))
    }

    // JavaBigIntegerBigDecimalConverter
    @Test
    fun javaBigIntegerBigDecimal_fromBigInteger() {
        val bi = JBigInteger.valueOf(9876543210L)
        assertEquals(JBigDecimal(bi), JavaBigIntegerBigDecimalConverter.convertTo(bi))
    }

    @Test
    fun javaBigIntegerBigDecimal_narrowingToBigIntegerIsRefused() {
        // BigDecimal -> BigInteger silently drops any fractional part, so the direction is refused.
        assertFailsWith<MappingException.UnsupportedConversion> {
            JavaBigIntegerBigDecimalConverter.convertFrom(JBigDecimal(JBigInteger.valueOf(100L)))
        }
    }

    @Test
    fun javaBigIntegerBigDecimal_wideningPreservesHugeValues() {
        val bi = JBigInteger("123456789012345678901234567890")
        assertEquals(JBigDecimal(bi), JavaBigIntegerBigDecimalConverter.convertTo(bi))
    }
}
