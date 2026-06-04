package com.sahsenvar.kmapper.arrow

import com.sahsenvar.kmapper.MappingException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NonEmptyListWrapperTest {

    @Test
    fun `non-empty list wraps`() {
        assertEquals(listOf(1, 2), listOf(1, 2).asNonEmptyList().toList())
    }

    @Test
    fun `empty list throws EmptyCollection`() {
        assertFailsWith<MappingException.EmptyCollection> {
            emptyList<Int>().asNonEmptyList()
        }
    }
}
