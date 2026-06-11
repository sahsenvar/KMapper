package com.sahsenvar.kmapper.processor.generator

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSType
import com.sahsenvar.kmapper.processor.model.FieldInfo
import com.sahsenvar.kmapper.processor.model.MappingStrategy
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName

/**
 * Generates mapping code using KotlinPoet.
 */
class MappingCodeGenerator(
    private val logger: KSPLogger,
) {
    fun generateFieldMapping(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        strategy: MappingStrategy,
        isReverse: Boolean = false,
    ): CodeBlock {
        // Unmappable: processor already emitted a compile error; return empty placeholder
        if (strategy is MappingStrategy.Unmappable) {
            return CodeBlock.of("/* unmappable field ${sourceField.name} — see KSP error above */")
        }

        val baseMapping =
            when (strategy) {
                is MappingStrategy.Direct -> CodeBlock.of("%N", sourceField.name)
                is MappingStrategy.Convert ->
                    generateConvertMapping(
                        sourceField,
                        targetField,
                        strategy,
                        isReverse,
                    )

                is MappingStrategy.Nested ->
                    if (sourceField.isNullable) {
                        CodeBlock.of("%N?.%N()", sourceField.name, strategy.mapperFunctionName)
                    } else {
                        CodeBlock.of("%N.%N()", sourceField.name, strategy.mapperFunctionName)
                    }

                is MappingStrategy.Collection ->
                    generateCollectionMapping(
                        sourceField,
                        targetField,
                        strategy,
                    )

                is MappingStrategy.WrappedCollection ->
                    generateWrappedCollectionMapping(
                        sourceField,
                        strategy,
                    )

                is MappingStrategy.MapValues -> generateMapValuesMapping(sourceField, strategy)

                is MappingStrategy.OptionWrap -> generateOptionWrapMapping(sourceField, strategy)

                is MappingStrategy.OptionUnwrap -> generateOptionUnwrapMapping(sourceField, strategy)

                is MappingStrategy.External -> CodeBlock.of("%N", targetField.name)

                is MappingStrategy.EnumFromWire -> generateEnumFromWireMapping(sourceField, strategy)

                is MappingStrategy.EnumToWire ->
                    if (sourceField.isNullable) {
                        CodeBlock.of("%N?.wireValue", sourceField.name)
                    } else {
                        CodeBlock.of("%N.wireValue", sourceField.name)
                    }

                // Unmappable is handled above; this branch is unreachable but required for exhaustiveness
                is MappingStrategy.Unmappable -> CodeBlock.of("")
            }

        // OptionWrap: Option.fromNullable() NEVER returns null — it always yields Option.Some or
        // Option.None. The target field is Option<T> which is non-null by definition. Skip
        // applyNullableHandling entirely so no spurious ?: throw RequiredFieldMissing is emitted.
        if (strategy is MappingStrategy.OptionWrap) {
            return wrapWithValidation(sourceField, targetField, baseMapping)
        }

        // OptionUnwrap: getOrNull() always returns a nullable result, even when the source Option
        // field itself is non-null. Force sourceField.isNullable=true so applyNullableHandling
        // correctly emits the ?: throw RequiredFieldMissing guard when the target is non-null.
        val effectiveSourceField =
            if (strategy is MappingStrategy.OptionUnwrap) {
                sourceField.copy(isNullable = true)
            } else {
                sourceField
            }
        val nullableHandled = applyNullableHandling(effectiveSourceField, targetField, baseMapping)
        return wrapWithValidation(effectiveSourceField, targetField, nullableHandled)
    }

    /**
     * Wraps [expr] in a `run { }` block that fires ValidateFrom checks on the source value
     * and ValidateTo checks on the result value. Returns [expr] unchanged when both lists are empty.
     *
     * Emission per spec §2.7:
     * - ValidateFrom checks fire FIRST on the SOURCE field value (before the expr is evaluated).
     * - `val __result = <expr>` captures the already null-handled expression.
     * - ValidateTo checks fire on `__result`.
     * - The block yields `__result`.
     */
    private fun wrapWithValidation(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        expr: CodeBlock,
    ): CodeBlock {
        val fromValidators = sourceField.validateFrom
        val toValidators = sourceField.validateTo
        if (fromValidators.isEmpty() && toValidators.isEmpty()) return expr

        val validationFailed = ClassName("com.sahsenvar.kmapper", "MappingException", "ValidationFailed")
        val srcName = sourceField.name
        val tgtName = targetField.name

        val builder = CodeBlock.builder()
        builder.beginControlFlow("run")

        // ValidateFrom checks — fire on the source value BEFORE the mapping expr
        for (fqn in fromValidators) {
            val validator = ClassName.bestGuess(fqn)
            if (sourceField.isNullable) {
                // nullable source: skip validation when null
                builder.beginControlFlow("%N?.let { __s ->", srcName)
                builder.addStatement(
                    "%T.validate(__s)?.let { m -> throw %T(%S, m) }",
                    validator,
                    validationFailed,
                    tgtName,
                )
                builder.endControlFlow()
            } else {
                // non-null source: direct validate call
                builder.addStatement(
                    "%T.validate(%N)?.let { throw %T(%S, it) }",
                    validator,
                    srcName,
                    validationFailed,
                    tgtName,
                )
            }
        }

        // Capture the (already null-handled) mapping expression
        builder.addStatement("val __result = %L", expr)

        // ValidateTo checks — fire on __result AFTER the mapping expr
        for (fqn in toValidators) {
            val validator = ClassName.bestGuess(fqn)
            if (targetField.isNullable) {
                // nullable result: skip validation when null
                builder.beginControlFlow("__result?.let { __r ->")
                builder.addStatement(
                    "%T.validate(__r)?.let { m -> throw %T(%S, m) }",
                    validator,
                    validationFailed,
                    tgtName,
                )
                builder.endControlFlow()
            } else {
                // non-null result: direct validate call
                builder.addStatement(
                    "%T.validate(__result)?.let { throw %T(%S, it) }",
                    validator,
                    validationFailed,
                    tgtName,
                )
            }
        }

        builder.addStatement("__result")
        builder.endControlFlow()
        return builder.build()
    }

    private fun generateEnumFromWireMapping(
        sourceField: FieldInfo,
        strategy: MappingStrategy.EnumFromWire,
    ): CodeBlock {
        val enumClassName = ClassName.bestGuess(strategy.enumFqn)
        val enumSimpleName = strategy.enumFqn.substringAfterLast(".")
        val mappingExceptionClass = ClassName("com.sahsenvar.kmapper", "MappingException")

        // Interim path: the source field's name (real path-aware codegen lands in a later task).
        return if (sourceField.isNullable) {
            CodeBlock.of(
                "%N?.let·{·w·->·%T.entries.firstOrNull·{·it.wireValue·==·w·}" +
                    "·?:·throw·%T.UnknownEnumValue(%S,·%S,·w.toString())·}",
                sourceField.name,
                enumClassName,
                mappingExceptionClass,
                sourceField.name,
                enumSimpleName,
            )
        } else {
            CodeBlock.of(
                "%T.entries.firstOrNull·{·it.wireValue·==·%N·}" +
                    "·?:·throw·%T.UnknownEnumValue(%S,·%S,·%N.toString())",
                enumClassName,
                sourceField.name,
                mappingExceptionClass,
                sourceField.name,
                enumSimpleName,
                sourceField.name,
            )
        }
    }

    private fun applyNullableHandling(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        baseMapping: CodeBlock,
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
            "${targetField.name}",
        )
    }

    private fun generateConvertMapping(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        strategy: MappingStrategy.Convert,
        isReverse: Boolean = false,
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
                sourceField.name,
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
            sourceField.name,
        )
    }

    private fun generateCollectionMapping(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        strategy: MappingStrategy.Collection,
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
                        mapperFn,
                    )
                } else {
                    builder.add(
                        "%N.map·{·it.%N()·}",
                        sourceField.name,
                        mapperFn,
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
        strategy: MappingStrategy.WrappedCollection,
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
                        wrapperClass,
                    )
                } else {
                    // WrapperObject.wrap(source.map { it.toX() })
                    builder.add(
                        "%T.wrap(%N.map·{·it.%N()·})",
                        wrapperClass,
                        sourceField.name,
                        mapperFn,
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

    /**
     * Generates code for a Map<K,V1> → Map<K,V2> field using mapValues.
     *
     * Non-null source + Nested values:
     *   source.mapValues { (_, v) -> v.toV2() }
     *
     * Nullable source + Nested values:
     *   source?.mapValues { (_, v) -> v.toV2() }
     *
     * Direct values (same type): passthrough → source
     *
     * applyNullableHandling runs after this method returns, so nullable source→required target
     * wraps correctly with ?: throw RequiredFieldMissing without extra logic here.
     */
    private fun generateMapValuesMapping(
        sourceField: FieldInfo,
        strategy: MappingStrategy.MapValues,
    ): CodeBlock = when (strategy.valueStrategy) {
        is MappingStrategy.Nested -> {
            val mapperFn = (strategy.valueStrategy as MappingStrategy.Nested).mapperFunctionName
            if (sourceField.isNullable) {
                CodeBlock.of(
                    "%N?.mapValues·{·(_,·v)·->·v.%N()·}",
                    sourceField.name,
                    mapperFn,
                )
            } else {
                CodeBlock.of(
                    "%N.mapValues·{·(_,·v)·->·v.%N()·}",
                    sourceField.name,
                    mapperFn,
                )
            }
        }
        else -> // Direct (same value type) — passthrough
            CodeBlock.of("%N", sourceField.name)
    }

    /**
     * Generates: arrow.core.Option.fromNullable(<innerExpr>)
     *
     * innerExpr variants:
     *   no nested mapper, any nullability: source                   (fromNullable accepts null)
     *   nested mapper, non-null source:    source.toInner()
     *   nested mapper, nullable source:    source?.toInner()
     *
     * fromNullable(null) == Option.None, fromNullable(x) == Option.Some(x).
     * The FQN is emitted as a literal string — no arrow-core Gradle dep needed in :processor.
     */
    private fun generateOptionWrapMapping(
        sourceField: FieldInfo,
        strategy: MappingStrategy.OptionWrap,
    ): CodeBlock {
        val innerExpr =
            when {
                strategy.innerMapperFn == null -> CodeBlock.of("%N", sourceField.name)
                sourceField.isNullable -> CodeBlock.of("%N?.%N()", sourceField.name, strategy.innerMapperFn)
                else -> CodeBlock.of("%N.%N()", sourceField.name, strategy.innerMapperFn)
            }
        // Emit FQN via ClassName — KotlinPoet renders it as "arrow.core.Option.fromNullable(…)".
        // ClassName construction requires only String args — no arrow classpath needed.
        val optionClass = ClassName("arrow.core", "Option")
        return CodeBlock.of("%T.fromNullable(%L)", optionClass, innerExpr)
    }

    /**
     * Generates: source.getOrNull() [?.toInner()]
     *
     * The result is nullable (Inner?). The standard nullable→non-null null-guard
     * (RequiredFieldMissing) is applied by applyNullableHandling after this returns,
     * so no special handling is needed here for non-null targets.
     */
    private fun generateOptionUnwrapMapping(
        sourceField: FieldInfo,
        strategy: MappingStrategy.OptionUnwrap,
    ): CodeBlock {
        val getOrNull = CodeBlock.of("%N.getOrNull()", sourceField.name)
        return if (strategy.innerMapperFn != null) {
            CodeBlock.of("%L?.%N()", getOrNull, strategy.innerMapperFn)
        } else {
            getOrNull
        }
    }
}

/** Returns the fully-qualified name of this KSType for use in error messages. */
private fun KSType.fqn(): String = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
