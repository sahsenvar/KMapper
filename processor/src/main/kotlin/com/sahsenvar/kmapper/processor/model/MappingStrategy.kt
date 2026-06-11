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
     * Nested object mapping (recursive mapper call).
     */
    data class Nested(
        val mapperFunctionName: String,
    ) : MappingStrategy()

    /**
     * Collection mapping (map each element).
     * @param elementStrategy how to map each element
     * @param isSet true when the TARGET field is a kotlin.collections.Set / MutableSet —
     *   the generator will append `.toSet()` after `.map { }` to produce the correct type.
     *   List targets keep isSet = false and emit plain `.map { }`.
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
     * Collection mapping that terminates with a @CollectionWrapper object's wrap() call.
     * Emits: WrapperObject.wrap(source.map { elementMapping }) (non-null source)
     *      or source?.map { ... }?.let { WrapperObject.wrap(it) } (nullable source)
     * @param elementStrategy how to map each element
     * @param wrapperObjectFqn fully-qualified name of the wrapper object (e.g. com.example.PersistentListWrapper)
     */
    data class WrappedCollection(
        val elementStrategy: MappingStrategy,
        val wrapperObjectFqn: String,
    ) : MappingStrategy()

    /**
     * Map<K,V1> → Map<K,V2> mapping by transforming values.
     * Keys are directly assigned (same type K on both sides).
     * Emits: source.mapValues { (_, v) -> v.toV2() }    (non-null source, nested values)
     *        source?.mapValues { (_, v) -> v.toV2() }   (nullable source, nested values)
     *        source                                      (direct value — same type K, same type V)
     * Keys must be the same type on both sides. Different key types → Unmappable.
     * Plain kotlin.collections.Map only; PersistentMap/ImmutableMap wrappers are deferred.
     * @param valueStrategy how to map each value: Direct (same type) or Nested (toV2() call)
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
