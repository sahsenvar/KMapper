package com.sahsenvar.kmapper

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
    ) : MappingDegradation()

    /** A broken element dropped from a collection (skip rung of the element ladder). */
    class DroppedBrokenElement(
        override val path: String,
        val cause: Throwable,
    ) : MappingDegradation()

    /** A null source element dropped from a non-null-element collection (free filterNotNull). */
    class DroppedNullElement(
        override val path: String,
    ) : MappingDegradation()

    /** Two source keys converged on the same target key; last write wins. */
    class DuplicateKey(
        override val path: String,
        val key: String,
    ) : MappingDegradation()

    /** Two distinct source elements converged to one target element in a Set. */
    class ConvergedDuplicateElement(
        override val path: String,
    ) : MappingDegradation()
}
