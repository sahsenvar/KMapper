package com.sahsenvar.kmapper.validators

import com.sahsenvar.kmapper.validation.Validator

/**
 * The value must be structurally valid Base64: standard (`+/`) or URL-safe (`-_`) alphabet,
 * padded or unpadded. `=` may only appear as 1-2 trailing padding characters that complete
 * a 4-character block. The empty string is valid (it encodes zero bytes) — combine with
 * [com.sahsenvar.kmapper.validation.builtin.NotEmptyStringValidator] to also require content.
 */
object Base64Validator : Validator<String>(String::class) {
    override fun validate(value: String): String? = if (isValidBase64(value)) null else "must be valid Base64"
}

/**
 * The value must be a hex string: an even number of hex digits, upper- or lower-case.
 * The empty string is valid (it encodes zero bytes) — combine with
 * [com.sahsenvar.kmapper.validation.builtin.NotEmptyStringValidator] to also require content.
 */
object HexStringValidator : Validator<String>(String::class) {
    override fun validate(value: String): String? = if (value.length % 2 == 0 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        null
    } else {
        "must be a hex string with an even number of digits"
    }
}

private fun isValidBase64(value: String): Boolean {
    val paddingLength = value.length - value.trimEnd('=').length
    if (paddingLength > 2) return false
    val body = value.dropLast(paddingLength)
    if ('=' in body) return false // padding only at the end
    if (!body.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '-' || it == '_' }) {
        return false
    }
    return if (paddingLength > 0) {
        (body.length + paddingLength) % 4 == 0 // padded form must complete a 4-block
    } else {
        body.length % 4 != 1 // unpadded form: a remainder of 1 encodes no whole byte
    }
}
