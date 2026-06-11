package com.sahsenvar.kmapper

/** Diagnostic name of a cause; R8 may obfuscate class names in release builds — acceptable for log taps. */
private fun causeName(cause: Throwable): String = cause::class.simpleName ?: "Throwable"

/**
 * A reported-but-absorbed mapping event. Report rule: every event that loses data or stems
 * from breakage is reported; declared-absence flows (null→null pass-through, absent→default,
 * sanctioned null) are silent.
 */
sealed class MappingDegradation {
    /** Field path from the mapping root, e.g. "items[3]" or "prices[\"usd\"]". */
    abstract val path: String

    /** A broken value absorbed into null/default at a nullable or defaulted landing site. */
    class AbsorbedConversionError(
        override val path: String,
        val from: String,
        val to: String,
        val cause: Throwable,
    ) : MappingDegradation() {
        override fun toString(): String = "AbsorbedConversionError(path=$path, $from -> $to, cause=${causeName(cause)})"
    }

    /** A broken element dropped from a collection (skip rung of the element ladder). */
    class DroppedBrokenElement(
        override val path: String,
        val cause: Throwable,
    ) : MappingDegradation() {
        override fun toString(): String = "DroppedBrokenElement(path=$path, cause=${causeName(cause)})"
    }

    /** A null source element dropped from a non-null-element collection (free filterNotNull). */
    class DroppedNullElement(
        override val path: String,
    ) : MappingDegradation() {
        override fun toString(): String = "DroppedNullElement(path=$path)"
    }

    /** Two source keys converged on the same target key; last write wins. */
    class DuplicateKey(
        override val path: String,
        val key: String,
    ) : MappingDegradation() {
        override fun toString(): String = "DuplicateKey(path=$path, key=$key)"
    }

    /** Two distinct source elements converged to one target element in a Set. */
    class ConvergedDuplicateElement(
        override val path: String,
    ) : MappingDegradation() {
        override fun toString(): String = "ConvergedDuplicateElement(path=$path)"
    }
}
