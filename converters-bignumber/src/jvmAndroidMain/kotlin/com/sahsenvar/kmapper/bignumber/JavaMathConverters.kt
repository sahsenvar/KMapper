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
    override fun convertTo(source: String): JBigDecimal = JBigDecimal(source)

    override fun convertFrom(target: JBigDecimal): String = target.toPlainString()
}

/** Decimal [String] ↔ [java.math.BigInteger]. */
object StringJavaBigIntegerConverter : MapTypeConverter<String, JBigInteger>(String::class, JBigInteger::class) {
    override fun convertTo(source: String): JBigInteger = JBigInteger(source)

    override fun convertFrom(target: JBigInteger): String = target.toString()
}

/** [Double] ↔ [java.math.BigDecimal]. Uses valueOf for reliable decimal representation. */
object DoubleJavaBigDecimalConverter : MapTypeConverter<Double, JBigDecimal>(Double::class, JBigDecimal::class) {
    override fun convertTo(source: Double): JBigDecimal = JBigDecimal.valueOf(source)

    override fun convertFrom(target: JBigDecimal): Double = target.toDouble()
}

/** [Long] ↔ [java.math.BigInteger]. */
object LongJavaBigIntegerConverter : MapTypeConverter<Long, JBigInteger>(Long::class, JBigInteger::class) {
    override fun convertTo(source: Long): JBigInteger = JBigInteger.valueOf(source)

    override fun convertFrom(target: JBigInteger): Long = target.toLong()
}

/** [java.math.BigInteger] ↔ [java.math.BigDecimal]. Lossless integer-to-decimal promotion. */
object JavaBigIntegerBigDecimalConverter : MapTypeConverter<JBigInteger, JBigDecimal>(JBigInteger::class, JBigDecimal::class) {
    override fun convertTo(source: JBigInteger): JBigDecimal = JBigDecimal(source)

    override fun convertFrom(target: JBigDecimal): JBigInteger = target.toBigInteger()
}
