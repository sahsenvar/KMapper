package com.sahsenvar.kmapper.bignumber

import com.ionspin.kotlin.bignum.decimal.BigDecimal
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
    fun longBigInteger_Long_BigInteger_Long_round_trip() {
        runBlocking {
            checkAll(Arb.long()) { n ->
                LongBigIntegerConverter.convertFromNonNull(
                    LongBigIntegerConverter.convertToNonNull(n)
                ) shouldBe n
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
                StringBigDecimalConverter.convertToNonNull(
                    StringBigDecimalConverter.convertFromNonNull(original)
                ) shouldBe original
            }
        }
    }
}
