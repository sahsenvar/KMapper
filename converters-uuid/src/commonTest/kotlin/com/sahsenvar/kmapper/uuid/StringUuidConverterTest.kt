@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.sahsenvar.kmapper.uuid

import io.kotest.assertions.throwables.shouldThrow
import kotlin.test.Test
import kotlin.test.assertEquals

class StringUuidConverterTest {
    private val sample = "550e8400-e29b-41d4-a716-446655440000"

    @Test fun `round-trip valid UUID string`() {
        val uuid = StringUuidConverter.convertToNonNull(sample)
        assertEquals(sample, StringUuidConverter.convertFromNonNull(uuid))
    }

    @Test fun `convertTo on invalid string throws`() {
        shouldThrow<Exception> {
            StringUuidConverter.convertToNonNull("not-a-uuid")
        }
    }
}
