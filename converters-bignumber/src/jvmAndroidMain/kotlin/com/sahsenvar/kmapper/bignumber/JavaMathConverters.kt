package com.sahsenvar.kmapper.bignumber

import com.sahsenvar.kmapper.converter.MapTypeConverter
import java.math.BigDecimal as JBigDecimal
import java.math.BigInteger as JBigInteger

// ---------------------------------------------------------------------------
// java.math BigDecimal / BigInteger scalar converters (jvmAndroidMain)
// "Java"-prefixed to disambiguate from ionspin counterparts.
// ---------------------------------------------------------------------------

/** ISO decimal [String] ↔ [java.math.BigDecimal]. */
object StringJavaBigDecimalConverter : MapTypeConverter<String, JBigDecimal>(String::class, JBigDecimal::class) {
    override fun convertToNonNull(value: String): JBigDecimal = JBigDecimal(value)

    override fun convertFromNonNull(value: JBigDecimal): String = value.toPlainString()
}

/** Decimal [String] ↔ [java.math.BigInteger]. */
object StringJavaBigIntegerConverter : MapTypeConverter<String, JBigInteger>(String::class, JBigInteger::class) {
    override fun convertToNonNull(value: String): JBigInteger = JBigInteger(value)

    override fun convertFromNonNull(value: JBigInteger): String = value.toString()
}

/** [Double] ↔ [java.math.BigDecimal]. Uses valueOf for reliable decimal representation. */
object DoubleJavaBigDecimalConverter : MapTypeConverter<Double, JBigDecimal>(Double::class, JBigDecimal::class) {
    override fun convertToNonNull(value: Double): JBigDecimal = JBigDecimal.valueOf(value)

    override fun convertFromNonNull(value: JBigDecimal): Double = value.toDouble()
}

/** [Long] ↔ [java.math.BigInteger]. */
object LongJavaBigIntegerConverter : MapTypeConverter<Long, JBigInteger>(Long::class, JBigInteger::class) {
    override fun convertToNonNull(value: Long): JBigInteger = JBigInteger.valueOf(value)

    override fun convertFromNonNull(value: JBigInteger): Long = value.toLong()
}

/** [java.math.BigInteger] ↔ [java.math.BigDecimal]. Lossless integer-to-decimal promotion. */
object JavaBigIntegerBigDecimalConverter : MapTypeConverter<JBigInteger, JBigDecimal>(JBigInteger::class, JBigDecimal::class) {
    override fun convertToNonNull(value: JBigInteger): JBigDecimal = JBigDecimal(value)

    override fun convertFromNonNull(value: JBigDecimal): JBigInteger = value.toBigInteger()
}
