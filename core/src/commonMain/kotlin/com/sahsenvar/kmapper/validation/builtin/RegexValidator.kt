package com.sahsenvar.kmapper.validation.builtin

import com.sahsenvar.kmapper.validation.Validator

/**
 * Base for pattern validators: the value must match [pattern] entirely, otherwise [reason] is reported.
 *
 * `@Validate` can only reference `object` validators, so subclass with concrete arguments:
 *
 * ```kotlin
 * object SkuValidator : RegexValidator(Regex("[A-Z]{3}-\\d{4}"), "must be a SKU like ABC-1234")
 * ```
 */
open class RegexValidator(
    private val pattern: Regex,
    private val reason: String,
) : Validator<String>(String::class) {
    override fun validate(value: String): String? = if (pattern.matches(value)) null else reason
}
