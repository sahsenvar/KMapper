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

        // Rule 5: @MapDefaultValue — annotation lives on the SOURCE field (the @MapTo-annotated class)
        // so we read sourceField.defaultValue, not targetField.defaultValue.
        val defaultValue = sourceField.defaultValue ?: targetField.defaultValue
        if (defaultValue != null) {
            return CodeBlock.of("%L ?: %L", baseMapping, defaultValue)
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

        // When the target collection type is a Set (kotlin.collections.Set / MutableSet),
        // `.map { }` returns a List — we must append `.toSet()` (or `?.toSet()` for a nullable
        // source chain) so the produced value matches the Set<T> target type.
        // List targets need no suffix: `.map { }` already returns List<T>.
        if (strategy.isSet && strategy.elementStrategy is MappingStrategy.Nested) {
            if (sourceField.isNullable) {
                builder.add("?.toSet()")
            } else {
                builder.add(".toSet()")
            }
        }

        // Non-stdlib collection targets (e.g. kotlinx.collections.immutable.*) go exclusively
        // through the @CollectionWrapper / MappingStrategy.WrappedCollection path.
        return builder.build()
    }

    /**
     * Generates code for a @CollectionWrapper field using the wrapper object's wrap() method.
     *
     * Non-null source:
     *   WrapperObject.wrap(source.map { it.toX() })     (Nested element)
     *   WrapperObject.wrap(source)                       (Direct element)
     *
     * Nullable source:
     *   source?.map { it.toX() }?.let { WrapperObject.wrap(it) }   (Nested element)
     *   source?.let { WrapperObject.wrap(it) }                       (Direct element)
     */
    private fun generateWrappedCollectionMapping(
        sourceField: FieldInfo,
        strategy: MappingStrategy.WrappedCollection
    ): CodeBlock {
        val builder = CodeBlock.builder()
        val wrapperClass = ClassName.bestGuess(strategy.wrapperObjectFqn)

        when (strategy.elementStrategy) {
            is MappingStrategy.Nested -> {
                val mapperFn = (strategy.elementStrategy as MappingStrategy.Nested).mapperFunctionName
                if (sourceField.isNullable) {
                    // source?.map { it.toX() }?.let { WrapperObject.wrap(it) }
                    builder.add(
                        "%N?.map·{·it.%N()·}?.let·{·%T.wrap(it)·}",
                        sourceField.name,
                        mapperFn,
                        wrapperClass
                    )
                } else {
                    // WrapperObject.wrap(source.map { it.toX() })
                    builder.add(
                        "%T.wrap(%N.map·{·it.%N()·})",
                        wrapperClass,
                        sourceField.name,
                        mapperFn
                    )
                }
            }

            is MappingStrategy.Direct -> {
                if (sourceField.isNullable) {
                    // source?.let { WrapperObject.wrap(it) }
                    builder.add("%N?.let·{·%T.wrap(it)·}", sourceField.name, wrapperClass)
                } else {
                    // WrapperObject.wrap(source)
                    builder.add("%T.wrap(%N)", wrapperClass, sourceField.name)
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
