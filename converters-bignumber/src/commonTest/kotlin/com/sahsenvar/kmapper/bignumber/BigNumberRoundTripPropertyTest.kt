package com.sahsenvar.kmapper.bignumber

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Property-based round-trip tests for ionspin big-number converters.
 * checkAll is suspend → wrapped in runBlocking.
 */
class BigNumberRoundTripPropertyTest {
    @Test
    fun longBigInteger_widening_preserves_the_value() {
        // BigInteger -> Long is refused (@UnsupportedDirection: overflow), so no round-trip exists;
        // the property that remains is that the widening direction is value-preserving.
        runBlocking {
            checkAll(Arb.long()) { n ->
                LongBigIntegerConverter.convertTo(n) shouldBe BigInteger.fromLong(n)
            }
        }
    }

    @Test
    fun stringBigInteger_String_BigInteger_String_round_trip() {
        runBlocking {
            checkAll(Arb.long()) { n ->
                val decimalString = n.toString()
                StringBigIntegerConverter.convertFrom(
                    StringBigIntegerConverter.convertTo(decimalString),
                ) shouldBe decimalString
            }
        }
    }

    @Test
    fun stringBigDecimal_BigDecimal_String_BigDecimal_round_trip() {
        // Start from Long so the generated BigDecimal is always a whole number with no
        // ambiguous trailing zeros, making the toStringExpanded → parseString identity exact.
        runBlocking {
            checkAll(Arb.long()) { n ->
                val original = BigDecimal.fromLong(n)
                StringBigDecimalConverter.convertTo(
                    StringBigDecimalConverter.convertFrom(original),
                ) shouldBe original
            }
        }
    }
}
