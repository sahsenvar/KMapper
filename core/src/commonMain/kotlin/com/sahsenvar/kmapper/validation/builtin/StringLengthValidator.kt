package com.sahsenvar.kmapper.validation.builtin

import com.sahsenvar.kmapper.validation.Validator

/**
 * Base for string-length validators: the value's length must be in `minLength..maxLength`.
 *
 * `@Validate` can only reference `object` validators, so subclass with concrete arguments:
 *
 * ```kotlin
 * object UsernameLengthValidator : StringLengthValidator(minLength = 3, maxLength = 20)
 * ```
 */
open class StringLengthValidator(
    private val minLength: Int,
    private val maxLength: Int = Int.MAX_VALUE,
) : Validator<String>(String::class) {
    init {
        require(minLength >= 0) { "minLength must be >= 0 (was $minLength)" }
        require(maxLength >= minLength) { "maxLength must be >= minLength ($maxLength < $minLength)" }
    }

    override fun validate(value: String): String? = if (value.length in minLength..maxLength) {
        null
    } else {
        "length must be in $minLength..$maxLength (was ${value.length})"
    }
}
