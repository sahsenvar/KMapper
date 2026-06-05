package com.sahsenvar.kmapper.annotations

import kotlin.reflect.KClass

/**
 * Validates the FINAL produced field value AFTER type conversion and null/default resolution,
 * immediately before assignment to the target constructor.
 * All listed [validators] are checked in order (fail-fast).
 * Each validator must be an `object` singleton subclassing [com.sahsenvar.kmapper.validation.Validator].
 * Null result values are never passed to validators.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ValidateTo(vararg val validators: KClass<*>)
