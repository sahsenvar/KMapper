package com.sahsenvar.kmapper.validators

import com.sahsenvar.kmapper.validation.Validator

/** The value must be a latitude in `-90.0..90.0` degrees; rejects NaN and infinities. */
object LatitudeValidator : Validator<Double>(Double::class) {
    override fun validate(value: Double): String? = if (value in -90.0..90.0) null else "must be a latitude in -90.0..90.0 (was $value)"
}

/** The value must be a longitude in `-180.0..180.0` degrees; rejects NaN and infinities. */
object LongitudeValidator : Validator<Double>(Double::class) {
    override fun validate(value: Double): String? = if (value in -180.0..180.0) null else "must be a longitude in -180.0..180.0 (was $value)"
}
