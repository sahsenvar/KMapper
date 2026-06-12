package com.sahsenvar.kmapper.validators

import com.sahsenvar.kmapper.validation.Validator

/**
 * The value must be an RFC 1123 hostname: dot-separated labels of 1-63 letters, digits,
 * and hyphens (no leading/trailing hyphen per label), at most 253 characters total.
 * A single label (`localhost`) is valid; a trailing dot is not.
 */
object HostnameValidator : Validator<String>(String::class) {
    override fun validate(value: String): String? = if (isValidHostname(value)) null else "must be a valid hostname"
}

private fun isValidHostname(value: String): Boolean {
    if (value.isEmpty() || value.length > 253) return false
    return value.split('.').all { label ->
        label.length in 1..63 &&
            label.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' } &&
            label.first() != '-' &&
            label.last() != '-'
    }
}
