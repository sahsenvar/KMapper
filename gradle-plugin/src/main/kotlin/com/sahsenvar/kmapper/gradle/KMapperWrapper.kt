package com.sahsenvar.kmapper.gradle

import java.io.Serializable

/**
 * Build-script mirror of `com.sahsenvar.kmapper.KMapperWrapper`: the module-wide return wrapper
 * that every `@MapTo`/`@MapFrom` left at `wrapper = KMapperWrapper.Default::class` resolves to.
 *
 * ```
 * import com.sahsenvar.kmapper.gradle.KMapperWrapper
 *
 * KMapper {
 *     wrapper = KMapperWrapper.KtResult
 * }
 * ```
 *
 * (A mirror rather than the runtime type itself: build scripts are compiled against the Gradle
 * classpath, where the multiplatform `kmapper-core` does not belong.)
 */
sealed class KMapperWrapper(
    /** The value passed to the KSP option `kmapper.wrapper`. */
    val optionValue: String,
) : Serializable {
    /** Only the plain `toX(): X` (the default). */
    object None : KMapperWrapper("None") {
        private fun readResolve(): Any = None
    }

    /** Also `toXResult(): Result<X>`. */
    object KtResult : KMapperWrapper("KtResult") {
        private fun readResolve(): Any = KtResult
    }

    /** Also `toXFlow(): Flow<X>` for a single source and for a `Flow` of sources. */
    object Flow : KMapperWrapper("Flow") {
        private fun readResolve(): Any = Flow
    }

    /**
     * A user-written wrapper object, by fully qualified name — e.g.
     * `KMapperWrapper.Custom("com.example.mapping.OutcomeWrapper")`.
     */
    class Custom(
        fullyQualifiedName: String,
    ) : KMapperWrapper(fullyQualifiedName) {
        init {
            require(fullyQualifiedName.isNotBlank()) { "KMapperWrapper.Custom needs the wrapper object's fully qualified name" }
        }

        override fun equals(other: Any?): Boolean = other is Custom && other.optionValue == optionValue

        override fun hashCode(): Int = optionValue.hashCode()

        override fun toString(): String = "KMapperWrapper.Custom($optionValue)"
    }

    override fun toString(): String = "KMapperWrapper.$optionValue"
}
