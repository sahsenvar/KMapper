package com.sahsenvar.kmapper.converter

/**
 * Marks an overridden convertTo/convertFrom STUB as intentionally unsupported, with a
 * compile-time guiding reason. The stub body must be `= unsupported()`. Annotate the TOTAL
 * method only (annotating an OrNull variant is a compile error). The annotation wins over the
 * body: an annotated direction is treated as unsupported by the compiler regardless of body.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class UnsupportedDirection(
    val reason: String,
)
