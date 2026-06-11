package com.sahsenvar.kmapper.converter

/**
 * Marks an overridden convertTo/convertFrom STUB as intentionally unsupported, with a
 * compile-time guiding reason. The stub body must be `= unsupported()`. Annotate the TOTAL
 * method only (annotating an OrNull variant is a compile error). The annotation wins over the
 * body: an annotated direction is treated as unsupported by the compiler regardless of body.
 *
 * Retention is BINARY (not SOURCE) on purpose: converters usually live in a DIFFERENT module
 * than the mapping they serve (built-ins always do), so the consumer's KSP round resolves them
 * from the classpath — a SOURCE-retained annotation would be invisible there and the direction
 * would silently look provided. BINARY keeps the declaration readable cross-module while
 * staying invisible to runtime reflection.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class UnsupportedDirection(
    val reason: String,
)
