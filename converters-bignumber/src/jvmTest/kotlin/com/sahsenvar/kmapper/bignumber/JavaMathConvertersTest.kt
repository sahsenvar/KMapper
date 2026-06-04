package com.sahsenvar.kmapper.bignumber

import java.math.BigDecimal as JBigDecimal
import java.math.BigInteger as JBigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class JavaMathConvertersTest {

    // StringJavaBigDecimalConverter
    @Test
    fun stringJavaBigDecimal_parseFromString() {
        assertEquals(JBigDecimal("123.456"), StringJavaBigDecimalConverter.convertToNonNull("123.456"))
    }

    @Test
    fun stringJavaBigDecimal_formatToString() {
        assertEquals("123.456", StringJavaBigDecimalConverter.convertFromNonNull(JBigDecimal("123.456")))
    }

    @Test
    fun stringJavaBigDecimal_roundTrip() {
        val s = "9999999999999999.12345"
        assertEquals(s, StringJavaBigDecimalConverter.convertFromNonNull(StringJavaBigDecimalConverter.convertToNonNull(s)))
    }

    @Test
    fun stringJavaBigDecimal_noScientificNotation() {
        // toPlainString must not produce scientific notation
        val s = "0.000001"
        val result = StringJavaBigDecimalConverter.convertFromNonNull(StringJavaBigDecimalConverter.convertToNonNull(s))
        assertEquals(s, result)
    }

    // StringJavaBigIntegerConverter
    @Test
    fun stringJavaBigInteger_parseFromString() {
        assertEquals(JBigInteger("12345678901234567890"), StringJavaBigIntegerConverter.convertToNonNull("12345678901234567890"))
    }

    @Test
    fun stringJavaBigInteger_formatToString() {
        assertEquals("12345678901234567890", StringJavaBigIntegerConverter.convertFromNonNull(JBigInteger("12345678901234567890")))
    }

    @Test
    fun stringJavaBigInteger_roundTrip() {
        val s = "99999999999999999999999999"
        assertEquals(s, StringJavaBigIntegerConverter.convertFromNonNull(StringJavaBigIntegerConverter.convertToNonNull(s)))
    }

    // DoubleJavaBigDecimalConverter
    @Test
    fun doubleJavaBigDecimal_fromDouble() {
        assertEquals(JBigDecimal.valueOf(1.5), DoubleJavaBigDecimalConverter.convertToNonNull(1.5))
    }

    @Test
    fun doubleJavaBigDecimal_toDouble() {
        val bd = JBigDecimal.valueOf(3.14)
        assertEquals(3.14, DoubleJavaBigDecimalConverter.convertFromNonNull(bd), 0.0001)
    }

    @Test
    fun doubleJavaBigDecimal_roundTrip() {
        val d = 42.0
        assertEquals(d, DoubleJavaBigDecimalConverter.convertFromNonNull(DoubleJavaBigDecimalConverter.convertToNonNull(d)), 0.0001)
    }

    // LongJavaBigIntegerConverter
    @Test
    fun longJavaBigInteger_fromLong() {
        assertEquals(JBigInteger.valueOf(Long.MAX_VALUE), LongJavaBigIntegerConverter.convertToNonNull(Long.MAX_VALUE))
    }

    @Test
    fun longJavaBigInteger_toLong() {
        assertEquals(1_000_000_000L, LongJavaBigIntegerConverter.convertFromNonNull(JBigInteger.valueOf(1_000_000_000L)))
    }

    @Test
    fun longJavaBigInteger_roundTrip() {
        val v = 123456789L
        assertEquals(v, LongJavaBigIntegerConverter.convertFromNonNull(LongJavaBigIntegerConverter.convertToNonNull(v)))
    }

    // JavaBigIntegerBigDecimalConverter
    @Test
    fun javaBigIntegerBigDecimal_fromBigInteger() {
        val bi = JBigInteger.valueOf(9876543210L)
        assertEquals(JBigDecimal(bi), JavaBigIntegerBigDecimalConverter.convertToNonNull(bi))
    }

    @Test
    fun javaBigIntegerBigDecimal_toBigInteger() {
        val bi = JBigInteger.valueOf(100L)
        val bd = JBigDecimal(bi)
        assertEquals(bi, JavaBigIntegerBigDecimalConverter.convertFromNonNull(bd))
    }

    @Test
    fun javaBigIntegerBigDecimal_roundTrip() {
        val bi = JBigInteger("123456789012345678901234567890")
        assertEquals(bi, JavaBigIntegerBigDecimalConverter.convertFromNonNull(JavaBigIntegerBigDecimalConverter.convertToNonNull(bi)))
    }

    // Nullable wrapper sanity checks
    @Test
    fun stringJavaBigDecimal_nullable_null() {
        assertEquals(null, StringJavaBigDecimalConverter.convertTo(null))
        assertEquals(null, StringJavaBigDecimalConverter.convertFrom(null))
    }

    @Test
    fun longJavaBigInteger_nullable_null() {
        assertEquals(null, LongJavaBigIntegerConverter.convertTo(null))
        assertEquals(null, LongJavaBigIntegerConverter.convertFrom(null))
    }
}
