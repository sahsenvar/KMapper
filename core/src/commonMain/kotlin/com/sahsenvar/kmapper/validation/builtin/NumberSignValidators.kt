package com.sahsenvar.kmapper.validation.builtin

import com.sahsenvar.kmapper.validation.Validator

/** The value must be strictly positive (`> 0`). */
object PositiveIntValidator : Validator<Int>(Int::class) {
    override fun validate(value: Int): String? = if (value > 0) null else "must be positive (was $value)"
}

/** The value must be zero or positive (`>= 0`). */
object NonNegativeIntValidator : Validator<Int>(Int::class) {
    override fun validate(value: Int): String? = if (value >= 0) null else "must not be negative (was $value)"
}

/** The value must be strictly positive (`> 0`). */
object PositiveLongValidator : Validator<Long>(Long::class) {
    override fun validate(value: Long): String? = if (value > 0L) null else "must be positive (was $value)"
}

/** The value must be zero or positive (`>= 0`). */
object NonNegativeLongValidator : Validator<Long>(Long::class) {
    override fun validate(value: Long): String? = if (value >= 0L) null else "must not be negative (was $value)"
}

/** The value must be strictly positive (`> 0.0`); rejects NaN. */
object PositiveDoubleValidator : Validator<Double>(Double::class) {
    override fun validate(value: Double): String? = if (value > 0.0) null else "must be positive (was $value)"
}

/** The value must be zero or positive (`>= 0.0`); rejects NaN. */
object NonNegativeDoubleValidator : Validator<Double>(Double::class) {
    override fun validate(value: Double): String? = if (value >= 0.0) null else "must not be negative (was $value)"
}
