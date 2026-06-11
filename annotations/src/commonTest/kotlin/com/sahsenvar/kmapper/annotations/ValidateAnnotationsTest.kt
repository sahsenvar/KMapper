package com.sahsenvar.kmapper.annotations

import com.sahsenvar.kmapper.validation.builtin.NotBlankValidator
import kotlin.test.Test

// Compile-time test: if these compile, the annotations exist with the right shape.
private data class AnnotationCompileCheck(
    @ValidateFrom(NotBlankValidator::class)
    @ValidateTo(NotBlankValidator::class)
    val name: String,
)

class ValidateAnnotationsTest {
    @Test fun `ValidateFrom and ValidateTo annotations compile on a class`() {
        // If this file compiled, the annotations are well-formed.
    }
}
