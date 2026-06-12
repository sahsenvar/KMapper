package com.sahsenvar.kmapper.validators

import com.sahsenvar.kmapper.validation.Validator

private val SLUG_REGEX = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")

/**
 * The value must be a URL slug: lower-case letters and digits in hyphen-separated runs
 * (`converter-redesign-2`). No upper-case, no leading/trailing/double hyphens, no spaces.
 */
object SlugValidator : Validator<String>(String::class) {
    override fun validate(value: String): String? = if (SLUG_REGEX.matches(value)) null else "must be a URL slug like converter-redesign-2"
}
