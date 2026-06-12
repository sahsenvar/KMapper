package com.sahsenvar.kmapper.validators

import com.sahsenvar.kmapper.validation.Validator

/**
 * The value must be a usable TCP/UDP port number in `1..65535`
 * (port 0 means "let the OS pick" and is not a valid destination).
 */
object PortNumberValidator : Validator<Int>(Int::class) {
    override fun validate(value: Int): String? = if (value in 1..65535) null else "must be a port number in 1..65535 (was $value)"
}
