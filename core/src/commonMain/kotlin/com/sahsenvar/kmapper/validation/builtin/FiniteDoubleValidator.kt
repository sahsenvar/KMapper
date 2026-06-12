package com.sahsenvar.kmapper.validation.builtin

import com.sahsenvar.kmapper.validation.Validator

/**
 * The value must be a finite number — rejects `NaN`, `Infinity`, and `-Infinity`.
 *
 * Useful on wire-side [Double] fields: JSON parsers and arithmetic upstream can smuggle
 * non-finite values into otherwise plausible-looking data.
 */
object FiniteDoubleValidator : Validator<Double>(Double::class) {
    override fun validate(value: Double): String? = if (value.isFinite()) null else "must be a finite number (was $value)"
}
