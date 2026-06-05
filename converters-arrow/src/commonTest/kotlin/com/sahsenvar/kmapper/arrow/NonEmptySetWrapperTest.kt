package com.sahsenvar.kmapper.arrow

import com.sahsenvar.kmapper.MappingException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NonEmptySetWrapperTest {

    @Test
    fun `non-empty list wraps to NonEmptySet`() {
        val result = NonEmptySetWrapper.wrap(listOf(1, 2, 3))
        assertEquals(3, result.size)
        assertTrue(result.contains(1))
        assertTrue(result.contains(2))
        assertTrue(result.contains(3))
    }

    @Test
    fun `duplicate elements are deduplicated in NonEmptySet`() {
        val result = NonEmptySetWrapper.wrap(listOf(1, 1, 2))
        assertEquals(2, result.size)
        assertTrue(result.contains(1))
        assertTrue(result.contains(2))
    }

    @Test
    fun `empty list throws EmptyCollection`() {
        assertFailsWith<MappingException.EmptyCollection> {
            NonEmptySetWrapper.wrap(emptyList<Int>())
        }
    }
}
