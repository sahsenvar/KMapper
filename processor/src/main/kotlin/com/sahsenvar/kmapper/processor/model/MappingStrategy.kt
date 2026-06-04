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
     * Use a TypeConverter.
     */
    data class Convert(val converterFqn: String) : MappingStrategy()

    /**
     * Nested object mapping (recursive mapper call).
     */
    data class Nested(val mapperFunctionName: String) : MappingStrategy()

    /**
     * Collection mapping (map each element).
     * @param elementStrategy how to map each element
     * @param isSet true when the TARGET field is a kotlin.collections.Set / MutableSet —
     *   the generator will append `.toSet()` after `.map { }` to produce the correct type.
     *   List targets keep isSet = false and emit plain `.map { }`.
     */
    data class Collection(
        val elementStrategy: MappingStrategy,
        val isSet: Boolean = false
    ) : MappingStrategy()

    /**
     * External field (comes from function parameter).
     */
    data class External(val parameterName: String) : MappingStrategy()

    /**
     * No mapping strategy could be determined (type mismatch without a converter).
     * The generator skips this field entirely; the processor emits a compile error.
     */
    data object Unmappable : MappingStrategy()

    /**
     * Wire-backed enum mapping: source wire value → target enum via MappableEnum.entries.
     */
    data class EnumFromWire(val enumFqn: String) : MappingStrategy()

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
        val wrapperObjectFqn: String
    ) : MappingStrategy()
}
