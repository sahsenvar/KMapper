package com.sahsenvar.kmapper.annotations

import com.sahsenvar.kmapper.validation.Validator
import kotlin.reflect.KClass

/**
 * Field-anchored validation: whenever this field participates in a mapping — as source
 * (validated BEFORE conversion) or as target (validated AFTER) — its value runs through the
 * validators. Fires at mapping time only; failure is a hard ValidationFailed.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Validate(
    vararg val validators: KClass<out Validator<*>>,
)
