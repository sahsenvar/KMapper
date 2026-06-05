package com.sahsenvar.kmapper.validation

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertNotNull

private object AlwaysValidStringValidator : Validator<String>(String::class) {
    override fun validate(value: String): String? = null
}

private object AlwaysInvalidStringValidator : Validator<String>(String::class) {
    override fun validate(value: String): String? = "always invalid"
}

class ValidatorContractTest {
    @Test fun `returning null means valid`() {
        assertNull(AlwaysValidStringValidator.validate("hello"))
    }

    @Test fun `returning non-null means invalid with reason`() {
        assertNotNull(AlwaysInvalidStringValidator.validate("hello"))
        assertNotNull(AlwaysInvalidStringValidator.validate("")) // null is never passed
    }
}
