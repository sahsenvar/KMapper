package com.sahsenvar.kmapper.bignumber

import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun doubleJavaBigDecimal_toDouble() {
        val bd = JBigDecimal.valueOf(3.14)
        assertEquals(3.14, DoubleJavaBigDecimalConverter.convertFrom(bd), 0.0001)
    }

    @Test
    fun doubleJavaBigDecimal_roundTrip() {
        val d = 42.0
        assertEquals(d, DoubleJavaBigDecimalConverter.convertFrom(DoubleJavaBigDecimalConverter.convertTo(d)), 0.0001)
    }

    // LongJavaBigIntegerConverter
    @Test
    fun longJavaBigInteger_fromLong() {
        assertEquals(JBigInteger.valueOf(Long.MAX_VALUE), LongJavaBigIntegerConverter.convertTo(Long.MAX_VALUE))
    }

    @Test
    fun longJavaBigInteger_toLong() {
        assertEquals(1_000_000_000L, LongJavaBigIntegerConverter.convertFrom(JBigInteger.valueOf(1_000_000_000L)))
    }

    @Test
    fun longJavaBigInteger_roundTrip() {
        val v = 123456789L
        assertEquals(v, LongJavaBigIntegerConverter.convertFrom(LongJavaBigIntegerConverter.convertTo(v)))
    }

    // JavaBigIntegerBigDecimalConverter
    @Test
    fun javaBigIntegerBigDecimal_fromBigInteger() {
        val bi = JBigInteger.valueOf(9876543210L)
        assertEquals(JBigDecimal(bi), JavaBigIntegerBigDecimalConverter.convertTo(bi))
    }

    @Test
    fun javaBigIntegerBigDecimal_toBigInteger() {
        val bi = JBigInteger.valueOf(100L)
        val bd = JBigDecimal(bi)
        assertEquals(bi, JavaBigIntegerBigDecimalConverter.convertFrom(bd))
    }

    @Test
    fun javaBigIntegerBigDecimal_roundTrip() {
        val bi = JBigInteger("123456789012345678901234567890")
        assertEquals(bi, JavaBigIntegerBigDecimalConverter.convertFrom(JavaBigIntegerBigDecimalConverter.convertTo(bi)))
    }
}
