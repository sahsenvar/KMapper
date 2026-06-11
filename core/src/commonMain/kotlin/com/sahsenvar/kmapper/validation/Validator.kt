package com.sahsenvar.kmapper.validation

import kotlin.reflect.KClass

/**
 * Base class for field value validators used with the field-anchored [@Validate] annotation.
 *
 * Implementations MUST be `object` singletons — the processor emits direct FQN calls
 * (e.g. `NotBlankValidator.validate(x)`) with no reflection.
 *
 * The [validate] method receives a NON-NULL value. Null handling is owned by the existing
 * nullability machinery in MappingCodeGenerator.applyNullableHandling; validators only fire
 * when a non-null value is present.
 *
 * @param T the type of value this validator accepts
 * @param targetType the KClass of T, used for documentation/introspection
 */
abstract class Validator<T : Any>(
    val targetType: KClass<T>,
) {
    /**
     * Returns `null` if [value] is valid, or a human-readable reason string if invalid.
     */
    abstract fun validate(value: T): String?
}
