package com.sahsenvar.kmapper.bignumber

import com.sahsenvar.kmapper.converter.MapTypeConverter
import com.sahsenvar.kmapper.converter.UnsupportedDirection
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

/** [Double] -> [java.math.BigDecimal] via valueOf (widening only; lossy reverse unsupported). */
object DoubleJavaBigDecimalConverter : MapTypeConverter<Double, JBigDecimal>(Double::class, JBigDecimal::class) {
    override fun convertTo(source: Double): JBigDecimal = JBigDecimal.valueOf(source)

    @UnsupportedDirection("BigDecimal -> Double loses precision (binary floating point cannot represent most decimals); convert explicitly if intended.")
    override fun convertFrom(target: JBigDecimal): Double = unsupported()
}

/** [Long] -> [java.math.BigInteger] (widening only; overflowing reverse unsupported). */
object LongJavaBigIntegerConverter : MapTypeConverter<Long, JBigInteger>(Long::class, JBigInteger::class) {
    override fun convertTo(source: Long): JBigInteger = JBigInteger.valueOf(source)

    @UnsupportedDirection("BigInteger -> Long can overflow and previously truncated SILENTLY; write a custom converter if your domain guarantees the range.")
    override fun convertFrom(target: JBigInteger): Long = unsupported()
}

/** [java.math.BigInteger] -> [java.math.BigDecimal]: lossless promotion (fraction-truncating reverse unsupported). */
object JavaBigIntegerBigDecimalConverter : MapTypeConverter<JBigInteger, JBigDecimal>(JBigInteger::class, JBigDecimal::class) {
    override fun convertTo(source: JBigInteger): JBigDecimal = JBigDecimal(source)

    @UnsupportedDirection("BigDecimal -> BigInteger truncates the fraction; decide floor/round/ceil explicitly.")
    override fun convertFrom(target: JBigDecimal): JBigInteger = unsupported()
}
