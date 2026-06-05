package com.sahsenvar.kmapper.validation.builtin

import com.sahsenvar.kmapper.validation.Validator

object NotBlankValidator : Validator<String>(String::class) {
    override fun validate(value: String): String? =
        if (value.isBlank()) "must not be blank" else null
}
