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

            is MappingStrategy.External -> CodeBlock.of("%N", targetField.name)
        }

        return applyNullableHandling(sourceField, targetField, baseMapping)
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

        // Base mapping
        when (strategy.elementStrategy) {
            is MappingStrategy.Nested -> {
                builder.add(
                    "%N?.map·{ it.%N() }",
                    sourceField.name,
                    (strategy.elementStrategy as MappingStrategy.Nested).mapperFunctionName
                )
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
}

/** Returns the fully-qualified name of this KSType for use in error messages. */
private fun KSType.fqn(): String =
    declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
