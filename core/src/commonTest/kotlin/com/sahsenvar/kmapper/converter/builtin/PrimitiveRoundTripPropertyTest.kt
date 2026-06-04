package com.sahsenvar.kmapper.converter.builtin

import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.boolean
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Property-based round-trip tests for primitive converters.
 * checkAll is suspend → wrapped in runBlocking (kotlinx-coroutines-core is a
 * transitive dep of kotest-property and has iOS targets).
 */
class PrimitiveRoundTripPropertyTest {

    @Test
    fun `StringIntConverter Int round-trip String-Int-String`() {
        runBlocking {
            checkAll(Arb.int()) { n ->
                StringIntConverter.convertToNonNull(n.toString()) shouldBe n
            }
        }
    }

    @Test
    fun `StringIntConverter String round-trip Int-String-Int`() {
        // convertFromNonNull: Int → String, then convertToNonNull: String → Int
        runBlocking {
            checkAll(Arb.int()) { n ->
                StringIntConverter.convertToNonNull(StringIntConverter.convertFromNonNull(n)) shouldBe n
            }
        }
    }

    @Test
    fun `StringLongConverter Long round-trip`() {
        runBlocking {
            checkAll(Arb.long()) { n ->
                StringLongConverter.convertToNonNull(n.toString()) shouldBe n
            }
        }
    }

    @Test
    fun `StringDoubleConverter Double round-trip`() {
        // includeNaNs=false: NaN and Infinity don't round-trip through String→Double→String identity
        runBlocking {
            checkAll(Arb.double(includeNaNs = false)) { n ->
                StringDoubleConverter.convertToNonNull(n.toString()) shouldBe n
            }
        }
    }

    @Test
    fun `StringFloatConverter Float round-trip`() {
        runBlocking {
            checkAll(Arb.float(includeNaNs = false)) { n ->
                StringFloatConverter.convertToNonNull(n.toString()) shouldBe n
            }
        }
    }

    @Test
    fun `StringBooleanConverter Boolean round-trip`() {
        runBlocking {
            checkAll(Arb.boolean()) { b ->
                StringBooleanConverter.convertToNonNull(b.toString()) shouldBe b
            }
        }
    }

    @Test
    fun `IntLongConverter Int-Long-Int round-trip`() {
        runBlocking {
            checkAll(Arb.int()) { n ->
                IntLongConverter.convertFromNonNull(IntLongConverter.convertToNonNull(n)) shouldBe n
            }
        }
    }
}
