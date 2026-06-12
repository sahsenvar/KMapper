package com.sahsenvar.kmapper.bignumber

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.sahsenvar.kmapper.converter.MapTypeConverter
import com.sahsenvar.kmapper.converter.UnsupportedDirection

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

/** [Double] -> ionspin [BigDecimal] (widening only; the reverse is intentionally unsupported). */
object DoubleBigDecimalConverter : MapTypeConverter<Double, BigDecimal>(Double::class, BigDecimal::class) {
    override fun convertTo(source: Double): BigDecimal = BigDecimal.fromDouble(source)

    @UnsupportedDirection("BigDecimal -> Double loses precision (binary floating point cannot represent most decimals); convert explicitly if intended.")
    override fun convertFrom(target: BigDecimal): Double = unsupported()
}

/** [Long] -> ionspin [BigInteger] (widening only; the reverse is intentionally unsupported). */
object LongBigIntegerConverter : MapTypeConverter<Long, BigInteger>(Long::class, BigInteger::class) {
    override fun convertTo(source: Long): BigInteger = BigInteger.fromLong(source)

    @UnsupportedDirection("BigInteger -> Long can overflow and previously truncated SILENTLY; write a custom converter if your domain guarantees the range.")
    override fun convertFrom(target: BigInteger): Long = unsupported()
}

/** [Int] -> ionspin [BigInteger] (widening only; the reverse is intentionally unsupported). */
object IntBigIntegerConverter : MapTypeConverter<Int, BigInteger>(Int::class, BigInteger::class) {
    override fun convertTo(source: Int): BigInteger = BigInteger.fromInt(source)

    @UnsupportedDirection("BigInteger -> Int can overflow and previously truncated SILENTLY; write a custom converter if your domain guarantees the range.")
    override fun convertFrom(target: BigInteger): Int = unsupported()
}

/** ionspin [BigInteger] -> ionspin [BigDecimal]: lossless promotion (fraction-truncating reverse unsupported). */
object BigIntegerBigDecimalConverter : MapTypeConverter<BigInteger, BigDecimal>(BigInteger::class, BigDecimal::class) {
    override fun convertTo(source: BigInteger): BigDecimal = BigDecimal.fromBigInteger(source)

    @UnsupportedDirection("BigDecimal -> BigInteger truncates the fraction; decide floor/round/ceil explicitly.")
    override fun convertFrom(target: BigDecimal): BigInteger = unsupported()
}
