package com.sahsenvar.kmapper.validation.builtin

import com.sahsenvar.kmapper.validation.Validator

object NotEmptyStringValidator : Validator<String>(String::class) {
    override fun validate(value: String): String? = if (value.isEmpty()) "must not be empty" else null
}
