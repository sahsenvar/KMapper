package com.sahsenvar.kmapper.validators

import com.sahsenvar.kmapper.validation.Validator

private val URL_REGEX = Regex("^https?://[^\\s/\$.?#].[^\\s]*$")

object UrlValidator : Validator<String>(String::class) {
    override fun validate(value: String): String? =
        if (URL_REGEX.matches(value)) null else "must be a valid URL"
}
