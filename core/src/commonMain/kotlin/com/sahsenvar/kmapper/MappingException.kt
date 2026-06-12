package com.sahsenvar.kmapper

/**
 * Base of all runtime mapping errors.
 *
 * Every subtype except [UnsupportedConversion] carries a [path] — the field path from the
 * mapping root (e.g. `"customer.address.zipCode"`, `"items[3].price"`). Seams extend the path
 * via [withPathPrefix] as the error climbs toward the root: the exception keeps its concrete
 * TYPE and payload, only the path grows (this is NOT wrapping).
 *
 * R8/proguard note: all names in messages come from caller-provided string literals;
 * nothing is derived via reflection.
 */
sealed class MappingException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    /** Field path from the mapping root, e.g. "customer.address.zipCode" or "items[3].price". */
    abstract val path: String

    /**
     * Same exception type with [prefix] prepended to the path. Used by seams — NOT wrapping.
     * Reconstructing means the rethrown exception's stack trace starts at the prefixing seam;
     * the path string is the locator, by design.
     */
    abstract fun withPathPrefix(prefix: String): MappingException

    class RequiredFieldMissing(
        override val path: String,
    ) : MappingException("Required field missing: $path") {
        override fun withPathPrefix(prefix: String) = RequiredFieldMissing(joinPath(prefix, path))
    }

    class TypeConversionFailed(
        override val path: String,
        val from: String,
        val to: String,
        override val cause: Throwable,
    ) : MappingException(conversionMessage(path, from, to), cause) {
        override fun withPathPrefix(prefix: String) = TypeConversionFailed(joinPath(prefix, path), from, to, cause)
    }

    class UnknownEnumValue(
        override val path: String,
        val enum: String,
        val value: Any,
    ) : MappingException(unknownEnumMessage(path, enum, value)) {
        override fun withPathPrefix(prefix: String) = UnknownEnumValue(joinPath(prefix, path), enum, value)
    }

    class EmptyCollection(
        override val path: String,
        val detail: String,
    ) : MappingException(emptyCollectionMessage(path, detail)) {
        override fun withPathPrefix(prefix: String) = EmptyCollection(joinPath(prefix, path), detail)
    }

    class ValidationFailed(
        override val path: String,
        val reason: String,
    ) : MappingException("Validation failed for '$path': $reason") {
        override fun withPathPrefix(prefix: String) = ValidationFailed(joinPath(prefix, path), reason)
    }

    /**
     * Intentionally-unsupported conversion direction. Path-less by design: the failure is about
     * a TYPE PAIR, not a field, so [withPathPrefix] is a no-op returning the same instance.
     */
    class UnsupportedConversion(
        message: String,
    ) : MappingException(message) {
        override val path: String get() = ""

        override fun withPathPrefix(prefix: String) = this
    }
}

/**
 * Joins a seam prefix onto an existing path: dot for plain segments
 * (`"address"` + `"zipCode"` → `"address.zipCode"`), no dot before an index segment
 * (`"items"` + `"[3]"` → `"items[3]"`), an empty path yields just the prefix, and an
 * empty prefix is a no-op yielding just the path.
 */
private fun joinPath(
    prefix: String,
    path: String,
): String = when {
    prefix.isEmpty() -> path
    path.isEmpty() -> prefix
    path.startsWith("[") -> "$prefix$path"
    else -> "$prefix.$path"
}

private fun unknownEnumMessage(
    path: String,
    enum: String,
    value: Any,
): String = if (path.isEmpty()) {
    "Unknown wire value '$value' for enum $enum"
} else {
    "Unknown wire value '$value' for enum $enum at $path"
}

private fun conversionMessage(
    path: String,
    from: String,
    to: String,
): String = if (path.isEmpty()) "Cannot convert $from -> $to" else "Cannot convert $path: $from -> $to"

private fun emptyCollectionMessage(
    path: String,
    detail: String,
): String = if (path.isEmpty()) {
    "Collection cannot be empty: $detail"
} else {
    "Collection cannot be empty: $detail (at $path)"
}
