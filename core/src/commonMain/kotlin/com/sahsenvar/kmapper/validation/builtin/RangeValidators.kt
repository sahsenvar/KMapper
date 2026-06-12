package com.sahsenvar.kmapper.validation.builtin

import com.sahsenvar.kmapper.validation.Validator

/**
 * Base for [Int] range validators: the value must be in [range].
 *
 * `@Validate` can only reference `object` validators, so subclass with concrete arguments:
 *
 * ```kotlin
 * object QuantityValidator : IntRangeValidator(1..999)
 * ```
 */
open class IntRangeValidator(
    private val range: IntRange,
) : Validator<Int>(Int::class) {
    init {
        require(!range.isEmpty()) { "range must not be empty (was $range)" }
    }

    override fun validate(value: Int): String? = if (value in range) null else "must be in $range (was $value)"
}

/**
 * Base for [Long] range validators: the value must be in [range].
 *
 * ```kotlin
 * object EpochMillisValidator : LongRangeValidator(0..4_102_444_800_000) // until year 2100
 * ```
 */
open class LongRangeValidator(
    private val range: LongRange,
) : Validator<Long>(Long::class) {
    init {
        require(!range.isEmpty()) { "range must not be empty (was $range)" }
    }

    override fun validate(value: Long): String? = if (value in range) null else "must be in $range (was $value)"
}

/**
 * Base for [Double] range validators: the value must be finite and in `min..max` (inclusive).
 *
 * NaN is never in range, so it is always rejected.
 *
 * ```kotlin
 * object PercentageValidator : DoubleRangeValidator(0.0, 100.0)
 * ```
 */
open class DoubleRangeValidator(
    private val min: Double,
    private val max: Double,
) : Validator<Double>(Double::class) {
    init {
        require(!min.isNaN() && !max.isNaN()) { "range bounds must not be NaN" }
        require(min <= max) { "min must be <= max ($min > $max)" }
    }

    override fun validate(value: Double): String? = if (value in min..max) null else "must be in $min..$max (was $value)"
}
