package com.sahsenvar.kmapper.validators

import com.sahsenvar.kmapper.validation.Validator

private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

/**
 * The value must be a canonical 8-4-4-4-12 hex UUID string
 * (`123e4567-e89b-12d3-a456-426614174000`); both upper- and lower-case digits are accepted.
 * Braced (`{...}`) and compact (no-dash) forms are rejected.
 */
object UuidStringValidator : Validator<String>(String::class) {
    override fun validate(value: String): String? = if (UUID_REGEX.matches(value)) null else "must be a UUID like 123e4567-e89b-12d3-a456-426614174000"
}
