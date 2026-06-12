package com.sahsenvar.kmapper.validators

import com.sahsenvar.kmapper.validation.Validator

/**
 * The value must be a plausible payment-card number (PAN): 12-19 digits passing the
 * Luhn checksum. Spaces and hyphens between digit groups are tolerated
 * (`4111 1111 1111 1111`), any other character is rejected.
 *
 * This is a *structural* check — it catches typos, not whether the card exists.
 */
object CreditCardNumberValidator : Validator<String>(String::class) {
    override fun validate(value: String): String? {
        val digits = value.filterNot { it == ' ' || it == '-' }
        if (digits.length !in 12..19 || !digits.all { it in '0'..'9' }) {
            return "must be a 12-19 digit card number"
        }
        return if (passesLuhn(digits)) null else "must pass the Luhn check (typo in the card number?)"
    }
}

private fun passesLuhn(digits: String): Boolean {
    var sum = 0
    for ((indexFromRight, char) in digits.reversed().withIndex()) {
        val digit = char - '0'
        sum +=
            if (indexFromRight % 2 == 1) {
                (digit * 2).let { doubled -> if (doubled > 9) doubled - 9 else doubled }
            } else {
                digit
            }
    }
    return sum % 10 == 0
}
