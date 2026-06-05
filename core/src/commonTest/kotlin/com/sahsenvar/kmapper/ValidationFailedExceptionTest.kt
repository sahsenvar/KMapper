package com.sahsenvar.kmapper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ValidationFailedExceptionTest {
    @Test fun `ValidationFailed carries field and reason`() {
        val ex = MappingException.ValidationFailed("email", "must be a valid email")
        assertEquals("email", ex.field)
        assertEquals("must be a valid email", ex.reason)
        assertIs<MappingException>(ex)
    }

    @Test fun `ValidationFailed message format`() {
        val ex = MappingException.ValidationFailed("name", "must not be blank")
        assertEquals("Validation failed for 'name': must not be blank", ex.message)
    }
}
