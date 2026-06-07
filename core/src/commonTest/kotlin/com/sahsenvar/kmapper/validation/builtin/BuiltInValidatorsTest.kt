package com.sahsenvar.kmapper.validation.builtin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BuiltInValidatorsTest {
    // NotBlankValidator
    @Test fun `NotBlankValidator - valid non-blank string`() {
        assertNull(NotBlankValidator.validate("hello"))
    }

    @Test fun `NotBlankValidator - blank string is invalid`() {
        assertNotNull(NotBlankValidator.validate("   "))
    }

    @Test fun `NotBlankValidator - empty string is invalid`() {
        assertNotNull(NotBlankValidator.validate(""))
    }

    @Test fun `NotBlankValidator - reason message`() {
        assertEquals("must not be blank", NotBlankValidator.validate("  "))
    }

    // NotEmptyStringValidator
    @Test fun `NotEmptyStringValidator - valid non-empty`() {
        assertNull(NotEmptyStringValidator.validate("x"))
    }

    @Test fun `NotEmptyStringValidator - empty string invalid`() {
        assertNotNull(NotEmptyStringValidator.validate(""))
    }

    @Test fun `NotEmptyStringValidator - blank is valid only checks emptiness`() {
        assertNull(NotEmptyStringValidator.validate("  "))
    }

    @Test fun `NotEmptyStringValidator - reason message`() {
        assertEquals("must not be empty", NotEmptyStringValidator.validate(""))
    }

    // NotEmptyCollectionValidator
    @Test fun `NotEmptyCollectionValidator - valid list`() {
        assertNull(NotEmptyCollectionValidator.validate(listOf(1)))
    }

    @Test fun `NotEmptyCollectionValidator - empty list invalid`() {
        assertNotNull(NotEmptyCollectionValidator.validate(emptyList<Int>()))
    }

    @Test fun `NotEmptyCollectionValidator - empty set invalid`() {
        assertNotNull(NotEmptyCollectionValidator.validate(emptySet<String>()))
    }

    @Test fun `NotEmptyCollectionValidator - reason message`() {
        assertEquals("must not be empty", NotEmptyCollectionValidator.validate(emptyList<Any>()))
    }
}
