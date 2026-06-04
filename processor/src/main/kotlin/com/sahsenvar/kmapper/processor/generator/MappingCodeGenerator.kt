package com.sahsenvar.kmapper.processor.generator

import com.sahsenvar.kmapper.processor.model.FieldInfo
import com.sahsenvar.kmapper.processor.model.MappingStrategy
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName

/**
 * Generates mapping code using KotlinPoet.
 */
class MappingCodeGenerator(private val logger: KSPLogger) {

    fun generateFieldMapping(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        strategy: MappingStrategy,
        isReverse: Boolean = false
    ): CodeBlock {
        // Unmappable: processor already emitted a compile error; return empty placeholder
        if (strategy is MappingStrategy.Unmappable) {
            return CodeBlock.of("/* unmappable field ${sourceField.name} — see KSP error above */")
        }

        val baseMapping = when (strategy) {
            is MappingStrategy.Direct -> CodeBlock.of("%N", sourceField.name)
            is MappingStrategy.Convert -> generateConvertMapping(
                sourceField,
                targetField,
                strategy,
                isReverse
            )

            is MappingStrategy.Nested -> if (sourceField.isNullable) {
                CodeBlock.of("%N?.%N()", sourceField.name, strategy.mapperFunctionName)
            } else {
                CodeBlock.of("%N.%N()", sourceField.name, strategy.mapperFunctionName)
            }

            is MappingStrategy.Collection -> generateCollectionMapping(
                sourceField,
                targetField,
                strategy
            )

            is MappingStrategy.WrappedCollection -> generateWrappedCollectionMapping(
                sourceField,
                strategy
            )

            is MappingStrategy.External -> CodeBlock.of("%N", targetField.name)

            is MappingStrategy.EnumFromWire -> generateEnumFromWireMapping(sourceField, strategy)

            is MappingStrategy.EnumToWire -> if (sourceField.isNullable) {
                CodeBlock.of("%N?.wireValue", sourceField.name)
            } else {
                CodeBlock.of("%N.wireValue", sourceField.name)
            }

            // Unmappable is handled above; this branch is unreachable but required for exhaustiveness
            is MappingStrategy.Unmappable -> CodeBlock.of("")
        }

        return applyNullableHandling(sourceField, targetField, baseMapping)
    }

    private fun generateEnumFromWireMapping(
        sourceField: FieldInfo,
        strategy: MappingStrategy.EnumFromWire
    ): CodeBlock {
        val enumClassName = ClassName.bestGuess(strategy.enumFqn)
        val enumSimpleName = strategy.enumFqn.substringAfterLast(".")
        val mappingExceptionClass = ClassName("com.sahsenvar.kmapper", "MappingException")

        return if (sourceField.isNullable) {
            CodeBlock.of(
                "%N?.let·{·w·->·%T.entries.firstOrNull·{·it.wireValue·==·w·}" +
                    "·?:·throw·%T.UnknownEnumValue(%S,·w.toString())·}",
                sourceField.name,
                enumClassName,
                mappingExceptionClass,
                enumSimpleName
            )
        } else {
            CodeBlock.of(
                "%T.entries.firstOrNull·{·it.wireValue·==·%N·}" +
                    "·?:·throw·%T.UnknownEnumValue(%S,·%N.toString())",
                enumClassName,
                sourceField.name,
                mappingExceptionClass,
                enumSimpleName,
                sourceField.name
            )
        }
    }

    private fun applyNullableHandling(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        baseMapping: CodeBlock
    ): CodeBlock {
        // Rule 1-4: Compatible nullability
        if (!sourceField.isNullable || targetField.isNullable) {
            return baseMapping
        }

        // Rule 5: @MapDefaultValue
        if (targetField.defaultValue != null) {
            return CodeBlock.of("%L ?: %L", baseMapping, targetField.defaultValue)
        }

        // Rule 2: Throw exception for required field
        return CodeBlock.of(
            "%L ?: throw %T(%S)",
            baseMapping,
            ClassName("com.sahsenvar.kmapper", "MappingException", "RequiredFieldMissing"),
            "${targetField.name}"
        )
    }

    private fun generateConvertMapping(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        strategy: MappingStrategy.Convert,
        isReverse: Boolean = false
    ): CodeBlock {
        val converterClassName = ClassName.bestGuess(strategy.converterFqn)
        val convertOrFail = MemberName("com.sahsenvar.kmapper", "convertOrFail")

        // Choose conversion method based on direction
        val convertMethod = if (isReverse) "convertFrom" else "convertTo"
        val convertNonNullMethod = if (isReverse) "convertFromNonNull" else "convertToNonNull"

        val fromFqn = sourceField.type.fqn()
        val toFqn = targetField.type.fqn()

        // If source is non-nullable and target is non-nullable, use convertToNonNull/convertFromNonNull
        if (!sourceField.isNullable && !targetField.isNullable) {
            return CodeBlock.of(
                "%M(%S,·%S)·{·%T.%N(%N)·}",
                convertOrFail,
                fromFqn,
                toFqn,
                converterClassName,
                convertNonNullMethod,
                sourceField.name
            )
        }

        // Otherwise use convertTo/convertFrom (handles nullable) — still wrap for consistency
        return CodeBlock.of(
            "%M(%S,·%S)·{·%T.%N(%N)·}",
            convertOrFail,
            fromFqn,
            toFqn,
            converterClassName,
            convertMethod,
            sourceField.name
        )
    }

    private fun generateCollectionMapping(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        strategy: MappingStrategy.Collection
    ): CodeBlock {
        val builder = CodeBlock.builder()

        // Base mapping — use safe-call map (?. ) only when the source field is nullable.
        // A non-null List source must use plain .map { } so the result type stays non-null.
        when (strategy.elementStrategy) {
            is MappingStrategy.Nested -> {
                val mapperFn = (strategy.elementStrategy as MappingStrategy.Nested).mapperFunctionName
                if (sourceField.isNullable) {
                    builder.add(
                        "%N?.map·{·it.%N()·}",
                        sourceField.name,
                        mapperFn
                    )
                } else {
                    builder.add(
                        "%N.map·{·it.%N()·}",
                        sourceField.name,
                        mapperFn
                    )
                }
            }

            else -> {
                builder.add("%N", sourceField.name)
            }
        }

        // filterNotNull for nullable elements
        val targetTypeFqn = targetField.type.declaration.qualifiedName?.asString()

        // ImmutableList conversion for UiModel
        if (targetTypeFqn?.startsWith("kotlinx.collections.immutable") == true) {
            if (targetTypeFqn.contains("ImmutableList")) {
                builder.add("?.toImmutableList()")
            } else if (targetTypeFqn.contains("ImmutableSet")) {
                builder.add("?.toImmutableSet()")
            }
        }

        return builder.build()
    }

    /**
     * Generates code for a @CollectionWrapper field: source.map { elementMapping }.wrapFn().
     * The wrapper function is an extension on List<T> returning the wrapped collection type.
     *
     * Examples:
     *   tags.map { it.toTagDomain() }.asPersistentList()    (non-null source)
     *   tags?.map { it.toTagDomain() }?.asPersistentList()  (nullable source)
     */
    private fun generateWrappedCollectionMapping(
        sourceField: FieldInfo,
        strategy: MappingStrategy.WrappedCollection
    ): CodeBlock {
        val builder = CodeBlock.builder()

        val wrapPkg = strategy.wrapFunctionFqn.substringBeforeLast(".", missingDelimiterValue = "")
        val wrapSimple = strategy.wrapFunctionFqn.substringAfterLast(".")
        val wrapMember = if (wrapPkg.isNotBlank()) {
            MemberName(wrapPkg, wrapSimple)
        } else {
            MemberName("", wrapSimple)
        }

        when (strategy.elementStrategy) {
            is MappingStrategy.Nested -> {
                val mapperFn = (strategy.elementStrategy as MappingStrategy.Nested).mapperFunctionName
                if (sourceField.isNullable) {
                    // tags?.map { it.toTagDomain() }?.asPersistentList()
                    builder.add(
                        "%N?.map·{·it.%N()·}?.%M()",
                        sourceField.name,
                        mapperFn,
                        wrapMember
                    )
                } else {
                    // tags.map { it.toTagDomain() }.asPersistentList()
                    builder.add(
                        "%N.map·{·it.%N()·}.%M()",
                        sourceField.name,
                        mapperFn,
                        wrapMember
                    )
                }
            }

            is MappingStrategy.Direct -> {
                if (sourceField.isNullable) {
                    builder.add("%N?.%M()", sourceField.name, wrapMember)
                } else {
                    builder.add("%N.%M()", sourceField.name, wrapMember)
                }
            }

            else -> {
                // Fallback: just emit the source (unlikely in practice)
                builder.add("%N", sourceField.name)
            }
        }

        return builder.build()
    }
}

/** Returns the fully-qualified name of this KSType for use in error messages. */
private fun KSType.fqn(): String =
    declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
