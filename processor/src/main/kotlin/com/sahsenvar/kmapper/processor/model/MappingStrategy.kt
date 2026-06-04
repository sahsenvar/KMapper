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
     */
    data class Collection(val elementStrategy: MappingStrategy) : MappingStrategy()

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
     * Collection mapping that terminates with a @CollectionWrapper call.
     * Emits: source.map { <elementMapping> }.<wrapSimpleName>()
     * @param elementStrategy how to map each element
     * @param wrapFunctionFqn fully-qualified name of the wrapper extension function
     */
    data class WrappedCollection(
        val elementStrategy: MappingStrategy,
        val wrapFunctionFqn: String
    ) : MappingStrategy()
}
