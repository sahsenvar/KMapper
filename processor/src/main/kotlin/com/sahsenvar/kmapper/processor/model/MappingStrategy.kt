package com.sahsenvar.kmapper.processor.model

/**
 * Strategy for mapping a field from source to target.
 */
sealed class MappingStrategy {
    /**
     * Direct assignment (same type, nullable compatible).
     */
    data object Direct : MappingStrategy()

    /**
     * Converter call resolved orientation-aware.
     */
    data class Convert(
        val converterFqn: String,
        /** true → field source/target == converter S/T → convertTo; false → reverse → convertFrom. */
        val forward: Boolean,
    ) : MappingStrategy()

    /**
     * Nested object mapping (recursive mapper call). [mapperFunctionName] is a top-level
     * extension function generated in [sourcePackageName] (the SOURCE type's package) — the
     * generator must reference it via a package-qualified `MemberName` (not a bare identifier)
     * so KotlinPoet emits the import when the parent mapper lives in a different package
     * (issue #44).
     */
    data class Nested(
        val mapperFunctionName: String,
        val sourcePackageName: String,
    ) : MappingStrategy()

    /**
     * Collection mapping: elements ride the convertEach… element seams, selected by the
     * (target element shape × onFail) table — e.g.
     * `tags.convertEachOrSkip("tags", from, to) { … }`. Direct same-type elements keep the
     * container passthrough (no seam); container-level null handling stays separate
     * (scope separation: element failure never escalates to the container).
     * @param elementStrategy how to map each element (feeds the seam's convert lambda)
     * @param isSet true when the TARGET field is a kotlin.collections.Set / MutableSet —
     *   selects the Set-producing seams (convertEachOrSkipToSet / convertEachOrFailToSet).
     */
    data class Collection(
        val elementStrategy: MappingStrategy,
        val isSet: Boolean = false,
    ) : MappingStrategy()

    /**
     * External field (comes from function parameter).
     */
    data class External(
        val parameterName: String,
    ) : MappingStrategy()

    /**
     * No mapping strategy could be determined (type mismatch without a converter).
     * The generator skips this field entirely; the processor emits a compile error.
     */
    data object Unmappable : MappingStrategy()

    /**
     * Wire-backed enum mapping: source wire value → target enum via MappableEnum.entries.
     */
    data class EnumFromWire(
        val enumFqn: String,
    ) : MappingStrategy()

    /**
     * Enum → wire value mapping via MappableEnum.wireValue.
     */
    data object EnumToWire : MappingStrategy()

    /**
     * Wire String → enum via kotlinx.serialization `@Serializable`/`@SerialName` (the
     * MappableEnum-free path). [entries] is the resolved `entrySimpleName → wireValue` list
     * (wireValue = `@SerialName` argument, else the entry name); the generator emits a
     * compile-time `when` over these — no runtime serializer. Wire type is always String.
     */
    data class SerializableEnumFromWire(
        val enumFqn: String,
        val entries: List<Pair<String, String>>,
    ) : MappingStrategy()

    /**
     * Enum → wire String via `@Serializable`/`@SerialName` — the mirror of
     * [SerializableEnumFromWire]; generated as a `when` mapping each entry to its wire literal.
     */
    data class SerializableEnumToWire(
        val enumFqn: String,
        val entries: List<Pair<String, String>>,
    ) : MappingStrategy()

    /**
     * Enum → Enum mapping by matching constant names (parity with the String↔Enum bridges
     * above) — no wire type involved. [entryNames] is every SOURCE enum entry's simple name,
     * in declaration order; each is guaranteed (by construction — TypeMatcher validates this
     * at resolution time) to have a same-named entry on the target enum, so the generator
     * emits an exhaustive `when` with no `else` branch and this conversion can never fail.
     */
    data class EnumToEnum(
        val sourceEnumFqn: String,
        val targetEnumFqn: String,
        val entryNames: List<String>,
    ) : MappingStrategy()

    /**
     * Collection mapping whose container shell is handled by a @CollectionWrapper object,
     * in either direction. Element conversion stays on the normal seam rails:
     *   forward (wrap):  `WrapperObject.wrap(<element seam chain>)`
     *   reverse (unwrap): `WrapperObject.unwrap(source).<element seam chain>`
     * @param elementStrategy how to map each element
     * @param wrapperObjectFqn fully-qualified name of the wrapper object (e.g. com.example.PersistentListWrapper)
     * @param useUnwrap true when the SOURCE field is the registered wrapped type and the
     *   target is a plain collection — the generator calls unwrap() and feeds the seams.
     */
    data class WrappedCollection(
        val elementStrategy: MappingStrategy,
        val wrapperObjectFqn: String,
        val useUnwrap: Boolean = false,
    ) : MappingStrategy()

    /**
     * Map<K,V1> → Map<K,V2> mapping: entries ride the convertEntries… seams (per-entry
     * key/value ladders at keyed paths like `prices["usd"]`), selected by the
     * (target value shape × onFail) table — e.g.
     * `prices.convertEntriesOrSkip("prices", keyFrom, keyTo, valueFrom, valueTo, { it }) { … }`.
     * Keys stay same-type in v1 (the identity lambda fills the seam's convertKey slot);
     * different key types → Unmappable. Direct same-type values keep the container
     * passthrough (no seam).
     * Plain kotlin.collections.Map only; PersistentMap/ImmutableMap wrappers are deferred.
     * @param valueStrategy how to map each value (feeds the seam's convertValue lambda)
     */
    data class MapValues(
        val valueStrategy: MappingStrategy,
    ) : MappingStrategy()

    /**
     * Target field type is `arrow.core.Option<Inner>`.
     * Source field is `Inner` (non-null) or `Inner?` (nullable).
     * Detection: matched by target-type FQN string "arrow.core.Option" — no arrow Gradle dep needed.
     *
     * @param innerMapperFn non-null when the inner type requires a nested mapper call (data class).
     */
    data class OptionWrap(
        val innerMapperFn: String? = null,
    ) : MappingStrategy()

    /**
     * Source field type is `arrow.core.Option<Inner>`.
     * Target field is `Inner?` or `Inner` (non-null guarded by existing RequiredFieldMissing path).
     * Detection: matched by source-type FQN string "arrow.core.Option".
     *
     * @param innerMapperFn non-null when the inner type requires a nested mapper call (data class).
     */
    data class OptionUnwrap(
        val innerMapperFn: String? = null,
    ) : MappingStrategy()
}
