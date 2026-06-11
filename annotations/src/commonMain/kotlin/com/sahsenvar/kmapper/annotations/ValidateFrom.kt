package com.sahsenvar.kmapper.annotations

import kotlin.reflect.KClass

/**
 * Validates the SOURCE field value BEFORE type conversion and null handling.
 * All listed [validators] are checked in order (fail-fast).
 * Each validator must be an `object` singleton subclassing [com.sahsenvar.kmapper.validation.Validator].
 * Null source values are never passed to validators — null handling is separate.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ValidateFrom(
    vararg val validators: KClass<*>,
)
