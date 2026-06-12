package com.sahsenvar.kmapper.validators

import com.sahsenvar.kmapper.validation.Validator

private val E164_REGEX = Regex("^\\+[1-9]\\d{1,14}$")

/**
 * The value must be an E.164 international phone number: a `+`, a non-zero country-code
 * digit, then up to 14 further digits (`+905551112233`). No spaces, dashes, or parentheses —
 * E.164 is the canonical wire format, not a display format.
 */
object PhoneE164Validator : Validator<String>(String::class) {
    override fun validate(value: String): String? = if (E164_REGEX.matches(value)) null else "must be an E.164 phone number like +905551112233"
}
