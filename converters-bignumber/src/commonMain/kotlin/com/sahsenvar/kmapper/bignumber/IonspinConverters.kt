package com.sahsenvar.kmapper.bignumber

import com.sahsenvar.kmapper.converter.MapTypeConverter
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger

// ---------------------------------------------------------------------------
// ionspin BigDecimal / BigInteger scalar converters (commonMain)
// Naming convention: String/primitive side is S → "String<X>Converter".
//
// API notes (verified against ionspin bignum 0.3.10):
//   BigDecimal companion: parseString(String), fromDouble(Double), fromBigInteger(BigInteger)
//   BigDecimal instance:  toBigInteger(), toStringExpanded(), doubleValue(exactRequired: Boolean)
//   BigInteger companion: parseString(String, base: Int), fromLong(Long), fromInt(Int)
//   BigInteger instance:  longValue(exactRequired: Boolean), intValue(exactRequired: Boolean)
//
// NOTE: There is NO toPlainString() on ionspin BigDecimal — use toStringExpanded() instead.
//       doubleValue/longValue/intValue require an explicit exactRequired Boolean param.
// ---------------------------------------------------------------------------

/** ISO decimal [String] ↔ ionspin [BigDecimal]. Uses base-10 parsing and expanded notation. */
object StringBigDecimalConverter : MapTypeConverter<String, BigDecimal>(String::class, BigDecimal::class) {
    override fun convertToNonNull(value: String): BigDecimal = BigDecimal.parseString(value)
    override fun convertFromNonNull(value: BigDecimal): String = value.toStringExpanded()
}

/** Decimal [String] ↔ ionspin [BigInteger]. Parses in base 10. */
object StringBigIntegerConverter : MapTypeConverter<String, BigInteger>(String::class, BigInteger::class) {
    override fun convertToNonNull(value: String): BigInteger = BigInteger.parseString(value, 10)
    override fun convertFromNonNull(value: BigInteger): String = value.toString()
}

/**
 * [Double] ↔ ionspin [BigDecimal].
 * convertFromNonNull uses exactRequired=false so values like 1.5 are returned as 1.5 not an error.
 */
object DoubleBigDecimalConverter : MapTypeConverter<Double, BigDecimal>(Double::class, BigDecimal::class) {
    override fun convertToNonNull(value: Double): BigDecimal = BigDecimal.fromDouble(value)
    override fun convertFromNonNull(value: BigDecimal): Double = value.doubleValue(exactRequired = false)
}

/**
 * [Long] ↔ ionspin [BigInteger].
 * convertFromNonNull uses exactRequired=false to allow values that do not fit exactly in Long range
 * to be truncated rather than throw, matching lenient conversion semantics.
 */
object LongBigIntegerConverter : MapTypeConverter<Long, BigInteger>(Long::class, BigInteger::class) {
    override fun convertToNonNull(value: Long): BigInteger = BigInteger.fromLong(value)
    override fun convertFromNonNull(value: BigInteger): Long = value.longValue(exactRequired = false)
}

/**
 * [Int] ↔ ionspin [BigInteger].
 * convertFromNonNull uses exactRequired=false for lenient truncation.
 */
object IntBigIntegerConverter : MapTypeConverter<Int, BigInteger>(Int::class, BigInteger::class) {
    override fun convertToNonNull(value: Int): BigInteger = BigInteger.fromInt(value)
    override fun convertFromNonNull(value: BigInteger): Int = value.intValue(exactRequired = false)
}

/** ionspin [BigInteger] ↔ ionspin [BigDecimal]. Lossless integer-to-decimal promotion. */
object BigIntegerBigDecimalConverter : MapTypeConverter<BigInteger, BigDecimal>(BigInteger::class, BigDecimal::class) {
    override fun convertToNonNull(value: BigInteger): BigDecimal = BigDecimal.fromBigInteger(value)
    override fun convertFromNonNull(value: BigDecimal): BigInteger = value.toBigInteger()
}
