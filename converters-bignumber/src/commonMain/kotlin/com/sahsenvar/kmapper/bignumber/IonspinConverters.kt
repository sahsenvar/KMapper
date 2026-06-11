package com.sahsenvar.kmapper.bignumber

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.sahsenvar.kmapper.converter.MapTypeConverter

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
    override fun convertTo(source: String): BigDecimal = BigDecimal.parseString(source)

    override fun convertFrom(target: BigDecimal): String = target.toStringExpanded()
}

/** Decimal [String] ↔ ionspin [BigInteger]. Parses in base 10. */
object StringBigIntegerConverter : MapTypeConverter<String, BigInteger>(String::class, BigInteger::class) {
    override fun convertTo(source: String): BigInteger = BigInteger.parseString(source, 10)

    override fun convertFrom(target: BigInteger): String = target.toString()
}

/**
 * [Double] ↔ ionspin [BigDecimal].
 * convertFrom uses exactRequired=false so values like 1.5 are returned as 1.5 not an error.
 */
object DoubleBigDecimalConverter : MapTypeConverter<Double, BigDecimal>(Double::class, BigDecimal::class) {
    override fun convertTo(source: Double): BigDecimal = BigDecimal.fromDouble(source)

    override fun convertFrom(target: BigDecimal): Double = target.doubleValue(exactRequired = false)
}

/**
 * [Long] ↔ ionspin [BigInteger].
 * convertFrom uses exactRequired=false to allow values that do not fit exactly in Long range
 * to be truncated rather than throw, matching lenient conversion semantics.
 */
object LongBigIntegerConverter : MapTypeConverter<Long, BigInteger>(Long::class, BigInteger::class) {
    override fun convertTo(source: Long): BigInteger = BigInteger.fromLong(source)

    override fun convertFrom(target: BigInteger): Long = target.longValue(exactRequired = false)
}

/**
 * [Int] ↔ ionspin [BigInteger].
 * convertFrom uses exactRequired=false for lenient truncation.
 */
object IntBigIntegerConverter : MapTypeConverter<Int, BigInteger>(Int::class, BigInteger::class) {
    override fun convertTo(source: Int): BigInteger = BigInteger.fromInt(source)

    override fun convertFrom(target: BigInteger): Int = target.intValue(exactRequired = false)
}

/** ionspin [BigInteger] ↔ ionspin [BigDecimal]. Lossless integer-to-decimal promotion. */
object BigIntegerBigDecimalConverter : MapTypeConverter<BigInteger, BigDecimal>(BigInteger::class, BigDecimal::class) {
    override fun convertTo(source: BigInteger): BigDecimal = BigDecimal.fromBigInteger(source)

    override fun convertFrom(target: BigDecimal): BigInteger = target.toBigInteger()
}
