package com.sahsenvar.kmapper.converter.builtin

import com.sahsenvar.kmapper.MappingException
import com.sahsenvar.kmapper.convertOrFail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * FIX 2: IntLongConverter.convertFromNonNull must throw on out-of-range Long values
 * instead of silently truncating / overflowing.
 *
 * convertOrFail (used by generated mappers) wraps any non-MappingException throwable in
 * MappingException.TypeConversionFailed, so out-of-range Long reaches callers as a typed
 * MappingException rather than an IllegalArgumentException.
 */
class IntLongConverterTest {
    // ---- convertToNonNull (Int → Long): always safe, just widen ----

    @Test
    fun `convertToNonNull widens Int to Long without loss`() {
        assertEquals(42L, IntLongConverter.convertToNonNull(42))
        assertEquals(Int.MIN_VALUE.toLong(), IntLongConverter.convertToNonNull(Int.MIN_VALUE))
        assertEquals(Int.MAX_VALUE.toLong(), IntLongConverter.convertToNonNull(Int.MAX_VALUE))
    }

    // ---- convertFromNonNull (Long → Int): in-range succeeds ----

    @Test
    fun `convertFromNonNull converts in-range Long correctly`() {
        assertEquals(0, IntLongConverter.convertFromNonNull(0L))
        assertEquals(42, IntLongConverter.convertFromNonNull(42L))
        assertEquals(Int.MIN_VALUE, IntLongConverter.convertFromNonNull(Int.MIN_VALUE.toLong()))
        assertEquals(Int.MAX_VALUE, IntLongConverter.convertFromNonNull(Int.MAX_VALUE.toLong()))
    }

    @Test
    fun `convertFromNonNull throws IllegalArgumentException for Long above Int MAX_VALUE`() {
        val overflow = Int.MAX_VALUE.toLong() + 1L
        assertFailsWith<IllegalArgumentException> {
            IntLongConverter.convertFromNonNull(overflow)
        }
    }

    @Test
    fun `convertFromNonNull throws IllegalArgumentException for Long below Int MIN_VALUE`() {
        val underflow = Int.MIN_VALUE.toLong() - 1L
        assertFailsWith<IllegalArgumentException> {
            IntLongConverter.convertFromNonNull(underflow)
        }
    }

    // ---- convertOrFail wraps IAE into MappingException.TypeConversionFailed ----

    @Test
    fun `convertOrFail wraps out-of-range Long as TypeConversionFailed`() {
        val overflow = Int.MAX_VALUE.toLong() + 1L
        val ex =
            assertFailsWith<MappingException.TypeConversionFailed> {
                convertOrFail("kotlin.Long", "kotlin.Int") {
                    IntLongConverter.convertFromNonNull(overflow)
                }
            }
        assertEquals("kotlin.Long", ex.from)
        assertEquals("kotlin.Int", ex.to)
    }
}
