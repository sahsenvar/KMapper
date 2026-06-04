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
}
