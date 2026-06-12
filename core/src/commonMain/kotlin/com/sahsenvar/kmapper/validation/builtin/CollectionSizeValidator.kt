package com.sahsenvar.kmapper.validation.builtin

import com.sahsenvar.kmapper.validation.Validator

/**
 * Base for collection-size validators: the collection's size must be in `minSize..maxSize`.
 *
 * `@Validate` can only reference `object` validators, so subclass with concrete arguments:
 *
 * ```kotlin
 * object LineItemCountValidator : CollectionSizeValidator(minSize = 1, maxSize = 100)
 * ```
 */
open class CollectionSizeValidator(
    private val minSize: Int,
    private val maxSize: Int = Int.MAX_VALUE,
) : Validator<Collection<*>>(Collection::class) {
    init {
        require(minSize >= 0) { "minSize must be >= 0 (was $minSize)" }
        require(maxSize >= minSize) { "maxSize must be >= minSize ($maxSize < $minSize)" }
    }

    override fun validate(value: Collection<*>): String? = if (value.size in minSize..maxSize) {
        null
    } else {
        "size must be in $minSize..$maxSize (was ${value.size})"
    }
}
