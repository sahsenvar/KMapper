package com.sahsenvar.kmapper.validators

import com.sahsenvar.kmapper.validation.Validator

private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

object EmailValidator : Validator<String>(String::class) {
    override fun validate(value: String): String? = if (EMAIL_REGEX.matches(value)) null else "must be a valid email"
}
