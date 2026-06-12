package com.sahsenvar.kmapper.processor.generator

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSType
import com.sahsenvar.kmapper.processor.model.FieldInfo
import com.sahsenvar.kmapper.processor.model.MappingStrategy
import com.sahsenvar.kmapper.processor.model.OnFailPolicy
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName

/**
 * Generates per-field mapping expressions using KotlinPoet, dispatching on the scalar
 * fallback ladder's landing shape:
 *
 * - [LandingShape.HARD] — non-null target without a usable default (ladder rows 1/5):
 *   the `convertOrFail` seam; absence and brokenness both surface as hard errors.
 * - [LandingShape.NULLABLE] — nullable target (rows 3/7): `convertOrNull` (Auto) or
 *   `convertOrNullStrict` (`OnFail.Throw`); broken absorbs to null (reported) under Auto.
 * - [LandingShape.COPY] — defaulted target built in the `.copy()` stage (rows 2/4/6/8):
 *   `convertOrElse` (Auto) or `convertOrElseStrict` with `base.<field>` as the fallback.
 *
 * Path literals are the TARGET field name (single segment — nesting prefixes accumulate at
 * runtime via `MappingException.withPathPrefix`); type literals are codegen string literals
 * (fully-qualified for converter pairs, simple class names for nested mappers) so release
 * builds stay readable under R8 without a mapping file.
 */
class MappingCodeGenerator(
    private val logger: KSPLogger,
) {
    private companion object {
        const val SEAMS_PACKAGE = "com.sahsenvar.kmapper"
    }

    /** Where the converted value lands — decides the seam family (see class KDoc). */
    private enum class LandingShape { HARD, NULLABLE, COPY }

    fun generateFieldMapping(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        strategy: MappingStrategy,
        isReverse: Boolean = false,
        inCopyStage: Boolean = false,
    ): CodeBlock {
        // Unmappable: processor already emitted a compile error; return empty placeholder
        if (strategy is MappingStrategy.Unmappable) {
            return CodeBlock.of("/* unmappable field ${sourceField.name} — see KSP error above */")
        }

        val landingShape =
            when {
                inCopyStage -> LandingShape.COPY
                targetField.isNullable -> LandingShape.NULLABLE
                else -> LandingShape.HARD
            }
        val onFail = sourceField.onFailFor(isReverse)

        val baseMapping =
            when (strategy) {
                is MappingStrategy.Direct -> generateDirectMapping(sourceField, targetField, landingShape)

                is MappingStrategy.Convert ->
                    generateConvertMapping(sourceField, targetField, strategy, landingShape, onFail)

                is MappingStrategy.Nested ->
                    generateNestedMapping(sourceField, targetField, strategy, landingShape, onFail)

                is MappingStrategy.Collection ->
                    applyChainLanding(
                        generateCollectionMapping(sourceField, strategy),
                        chainIsNullable = sourceField.isNullable,
                        targetField = targetField,
                        landingShape = landingShape,
                    )

                is MappingStrategy.WrappedCollection ->
                    applyChainLanding(
                        generateWrappedCollectionMapping(sourceField, strategy),
                        chainIsNullable = sourceField.isNullable,
                        targetField = targetField,
                        landingShape = landingShape,
                    )

                is MappingStrategy.MapValues ->
                    applyChainLanding(
                        generateMapValuesMapping(sourceField, strategy),
                        chainIsNullable = sourceField.isNullable,
                        targetField = targetField,
                        landingShape = landingShape,
                    )

                is MappingStrategy.OptionWrap ->
                    // Option.fromNullable NEVER yields null — no landing handling needed.
                    generateOptionWrapMapping(sourceField, strategy)

                is MappingStrategy.OptionUnwrap ->
                    // getOrNull() makes the chain nullable regardless of the field's own type.
                    applyChainLanding(
                        generateOptionUnwrapMapping(sourceField, strategy),
                        chainIsNullable = true,
                        targetField = targetField,
                        landingShape = landingShape,
                    )

                is MappingStrategy.EnumFromWire ->
                    applyChainLanding(
                        generateEnumFromWireMapping(sourceField, targetField, strategy),
                        chainIsNullable = sourceField.isNullable,
                        targetField = targetField,
                        landingShape = landingShape,
                    )

                is MappingStrategy.EnumToWire ->
                    applyChainLanding(
                        if (sourceField.isNullable) {
                            CodeBlock.of("%N?.wireValue", sourceField.name)
                        } else {
                            CodeBlock.of("%N.wireValue", sourceField.name)
                        },
                        chainIsNullable = sourceField.isNullable,
                        targetField = targetField,
                        landingShape = landingShape,
                    )

                is MappingStrategy.External -> CodeBlock.of("%N", targetField.name)

                // Unmappable is handled above; this branch is unreachable but required for exhaustiveness
                is MappingStrategy.Unmappable -> CodeBlock.of("")
            }

        return wrapWithValidation(sourceField, targetField, baseMapping)
    }

    /** Seam member for the (landing shape × policy) cell. `OnFail.Skip` on a scalar is a compile error upstream. */
    private fun seamFor(
        landingShape: LandingShape,
        onFail: OnFailPolicy,
    ): MemberName {
        val seamName =
            when (landingShape) {
                LandingShape.HARD -> "convertOrFail"
                LandingShape.NULLABLE -> if (onFail == OnFailPolicy.Throw) "convertOrNullStrict" else "convertOrNull"
                LandingShape.COPY -> if (onFail == OnFailPolicy.Throw) "convertOrElseStrict" else "convertOrElse"
            }
        return MemberName(SEAMS_PACKAGE, seamName)
    }

    /**
     * Direct assignment. Nullable source into a HARD landing site needs the absence guard
     * (`orRequired`); the COPY stage falls back to the base default (`?: base.x` — row 8:
     * default beats null). Everything else passes through unchanged.
     */
    private fun generateDirectMapping(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        landingShape: LandingShape,
    ): CodeBlock = when {
        !sourceField.isNullable -> CodeBlock.of("%N", sourceField.name)

        landingShape == LandingShape.COPY ->
            CodeBlock.of("%N·?:·base.%N", sourceField.name, targetField.name)

        landingShape == LandingShape.HARD ->
            CodeBlock.of(
                "%N.%M(%S)",
                sourceField.name,
                MemberName(SEAMS_PACKAGE, "orRequired"),
                targetField.name,
            )

        else -> CodeBlock.of("%N", sourceField.name)
    }

    /**
     * Converter call through the ladder seams. Orientation was resolved by TypeMatcher:
     * forward → `convertTo`, reverse → `convertFrom`. OrNull-capable landing sites (NULLABLE,
     * COPY) call the converter's `OrNull` variant so a sanctioned null can land; the HARD
     * site calls the total method (spec: codegen method-selection rule).
     */
    private fun generateConvertMapping(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        strategy: MappingStrategy.Convert,
        landingShape: LandingShape,
        onFail: OnFailPolicy,
    ): CodeBlock {
        val converterClassName = ClassName.bestGuess(strategy.converterFqn)
        val totalMethod = if (strategy.forward) "convertTo" else "convertFrom"
        val convertMethod = if (landingShape == LandingShape.HARD) totalMethod else "${totalMethod}OrNull"
        return emitSeamCall(
            sourceField = sourceField,
            targetField = targetField,
            landingShape = landingShape,
            onFail = onFail,
            fromLiteral = sourceField.type.fqn(),
            toLiteral = targetField.type.fqn(),
            convertLambda = CodeBlock.of("{·%T.%N(it)·}", converterClassName, convertMethod),
        )
    }

    /**
     * Nested mapping through the same seams: the sub-mapper IS the converter
     * (`{ it.toXResult().getOrThrow() }`). Inner hard MappingExceptions propagate unwrapped
     * and the seam prefixes this field's path segment (`withPathPrefix`) — deep paths like
     * `address.zipCode` accumulate level by level. From/to literals are the class SIMPLE
     * names (codegen literals, R8-safe).
     */
    private fun generateNestedMapping(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        strategy: MappingStrategy.Nested,
        landingShape: LandingShape,
        onFail: OnFailPolicy,
    ): CodeBlock = emitSeamCall(
        sourceField = sourceField,
        targetField = targetField,
        landingShape = landingShape,
        onFail = onFail,
        fromLiteral = sourceField.type.declaration.simpleName.asString(),
        toLiteral = targetField.type.declaration.simpleName.asString(),
        convertLambda = CodeBlock.of("{·it.%N().getOrThrow()·}", strategy.mapperFunctionName),
    )

    /**
     * Shared seam-call emission: `receiver.seam(path, from, to[, base.field]) { convert }`.
     * The seam receiver overloads cover both source nullabilities, so the emission is
     * identical for `S` and `S?` sources.
     */
    private fun emitSeamCall(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        landingShape: LandingShape,
        onFail: OnFailPolicy,
        fromLiteral: String,
        toLiteral: String,
        convertLambda: CodeBlock,
    ): CodeBlock {
        val seam = seamFor(landingShape, onFail)
        val path = targetField.name
        return if (landingShape == LandingShape.COPY) {
            CodeBlock.of(
                "%N.%M(%S,·%S,·%S,·base.%N)·%L",
                sourceField.name,
                seam,
                path,
                fromLiteral,
                toLiteral,
                targetField.name,
                convertLambda,
            )
        } else {
            CodeBlock.of(
                "%N.%M(%S,·%S,·%S)·%L",
                sourceField.name,
                seam,
                path,
                fromLiteral,
                toLiteral,
                convertLambda,
            )
        }
    }

    /**
     * INTERIM container-level landing for chain-shaped strategies (collections, maps,
     * wrapped collections, enum bridges, Option unwrap): a nullable chain into a HARD
     * landing site gets the `orRequired` absence guard; the COPY stage falls back to
     * `base.<field>`; nullable targets pass the chain through unchanged.
     *
     * The full element-ladder codegen (the convertEach… / convertEntries… seams) replaces
     * the `.map { }` chains in the collections chunk — only the container-level null
     * handling has moved to the seams here.
     */
    private fun applyChainLanding(
        chain: CodeBlock,
        chainIsNullable: Boolean,
        targetField: FieldInfo,
        landingShape: LandingShape,
    ): CodeBlock = when {
        // Non-null chain: the value is always present — every landing site takes it as-is
        // (a non-null chain in the copy stage simply always overrides the base default).
        !chainIsNullable -> chain

        landingShape == LandingShape.COPY ->
            CodeBlock.of("%L·?:·base.%N", chain, targetField.name)

        landingShape == LandingShape.HARD ->
            CodeBlock.of("%L.%M(%S)", chain, MemberName(SEAMS_PACKAGE, "orRequired"), targetField.name)

        else -> chain
    }

    /**
     * Wraps [expr] in a `run { }` block that fires the SOURCE field's @Validate checks on the
     * source value and the TARGET field's @Validate checks on the result value (field-anchored
     * validation: a field's validators fire whenever it enters a mapping, as source BEFORE the
     * conversion and as target AFTER). Returns [expr] unchanged when both lists are empty.
     *
     * Runs inside the mapper's `runCatching`, so a thrown ValidationFailed becomes
     * `Result.failure` at the boundary.
     *
     * Emission:
     * - Source-field validators fire FIRST on the SOURCE field value (before the expr is evaluated).
     * - `val __result = <expr>` captures the seam-handled expression.
     * - Target-field validators fire on `__result`.
     * - The block yields `__result`.
     */
    private fun wrapWithValidation(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        expr: CodeBlock,
    ): CodeBlock {
        val fromValidators = sourceField.validators
        val toValidators = targetField.validators
        if (fromValidators.isEmpty() && toValidators.isEmpty()) return expr

        val validationFailed = ClassName("com.sahsenvar.kmapper", "MappingException", "ValidationFailed")
        val srcName = sourceField.name
        val tgtName = targetField.name

        val builder = CodeBlock.builder()
        builder.beginControlFlow("run")

        // Source-field validators — fire on the source value BEFORE the mapping expr
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

        // Capture the (already seam-handled) mapping expression
        builder.addStatement("val __result = %L", expr)

        // Target-field validators — fire on __result AFTER the mapping expr
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

    /**
     * Wire → enum bridge via MappableEnum.entries. The path literal is the TARGET field's
     * name (a path names where the value LANDS — consistent with every seam emission; the
     * source field name can differ under @FieldMap renames).
     */
    private fun generateEnumFromWireMapping(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        strategy: MappingStrategy.EnumFromWire,
    ): CodeBlock {
        val enumClassName = ClassName.bestGuess(strategy.enumFqn)
        val enumSimpleName = strategy.enumFqn.substringAfterLast(".")
        val mappingExceptionClass = ClassName("com.sahsenvar.kmapper", "MappingException")

        return if (sourceField.isNullable) {
            CodeBlock.of(
                "%N?.let·{·w·->·%T.entries.firstOrNull·{·it.wireValue·==·w·}" +
                    "·?:·throw·%T.UnknownEnumValue(%S,·%S,·w.toString())·}",
                sourceField.name,
                enumClassName,
                mappingExceptionClass,
                targetField.name,
                enumSimpleName,
            )
        } else {
            CodeBlock.of(
                "%T.entries.firstOrNull·{·it.wireValue·==·%N·}" +
                    "·?:·throw·%T.UnknownEnumValue(%S,·%S,·%N.toString())",
                enumClassName,
                sourceField.name,
                mappingExceptionClass,
                targetField.name,
                enumSimpleName,
                sourceField.name,
            )
        }
    }

    /**
     * INTERIM collection emission (`.map { }` chains): element conversion shape unchanged
     * until the element-ladder chunk lands; nested element mappers already ride the Result
     * boundary (`it.toXResult().getOrThrow()`). Container-level null handling is applied by
     * [applyChainLanding] after this returns.
     */
    private fun generateCollectionMapping(
        sourceField: FieldInfo,
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
                        "%N?.map·{·it.%N().getOrThrow()·}",
                        sourceField.name,
                        mapperFn,
                    )
                } else {
                    builder.add(
                        "%N.map·{·it.%N().getOrThrow()·}",
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
     * Generates code for a @CollectionWrapper field using the wrapper object's wrap() method
     * (INTERIM shape — see [generateCollectionMapping]).
     *
     * Non-null source:
     *   WrapperObject.wrap(source.map { it.toXResult().getOrThrow() })   (Nested element)
     *   WrapperObject.wrap(source)                                        (Direct element)
     *
     * Nullable source:
     *   source?.map { ... }?.let { WrapperObject.wrap(it) }               (Nested element)
     *   source?.let { WrapperObject.wrap(it) }                            (Direct element)
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
                    // source?.map { it.toXResult().getOrThrow() }?.let { WrapperObject.wrap(it) }
                    builder.add(
                        "%N?.map·{·it.%N().getOrThrow()·}?.let·{·%T.wrap(it)·}",
                        sourceField.name,
                        mapperFn,
                        wrapperClass,
                    )
                } else {
                    // WrapperObject.wrap(source.map { it.toXResult().getOrThrow() })
                    builder.add(
                        "%T.wrap(%N.map·{·it.%N().getOrThrow()·})",
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
     * Generates code for a Map<K,V1> → Map<K,V2> field using mapValues (INTERIM shape —
     * see [generateCollectionMapping]).
     *
     * Non-null source + Nested values:
     *   source.mapValues { (_, v) -> v.toV2Result().getOrThrow() }
     *
     * Nullable source + Nested values:
     *   source?.mapValues { (_, v) -> v.toV2Result().getOrThrow() }
     *
     * Direct values (same type): passthrough → source
     *
     * [applyChainLanding] runs after this method returns, so a nullable source into a hard
     * or defaulted target lands correctly (orRequired / ?: base.x) without extra logic here.
     */
    private fun generateMapValuesMapping(
        sourceField: FieldInfo,
        strategy: MappingStrategy.MapValues,
    ): CodeBlock = when (strategy.valueStrategy) {
        is MappingStrategy.Nested -> {
            val mapperFn = (strategy.valueStrategy as MappingStrategy.Nested).mapperFunctionName
            if (sourceField.isNullable) {
                CodeBlock.of(
                    "%N?.mapValues·{·(_,·v)·->·v.%N().getOrThrow()·}",
                    sourceField.name,
                    mapperFn,
                )
            } else {
                CodeBlock.of(
                    "%N.mapValues·{·(_,·v)·->·v.%N().getOrThrow()·}",
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
     *   no nested mapper, any nullability: source                                  (fromNullable accepts null)
     *   nested mapper, non-null source:    source.toInnerResult().getOrThrow()
     *   nested mapper, nullable source:    source?.toInnerResult()?.getOrThrow()
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
                sourceField.isNullable ->
                    CodeBlock.of("%N?.%N()?.getOrThrow()", sourceField.name, strategy.innerMapperFn)
                else -> CodeBlock.of("%N.%N().getOrThrow()", sourceField.name, strategy.innerMapperFn)
            }
        // Emit FQN via ClassName — KotlinPoet renders it as "arrow.core.Option.fromNullable(…)".
        // ClassName construction requires only String args — no arrow classpath needed.
        val optionClass = ClassName("arrow.core", "Option")
        return CodeBlock.of("%T.fromNullable(%L)", optionClass, innerExpr)
    }

    /**
     * Generates: source.getOrNull() [?.toInnerResult()?.getOrThrow()]
     *
     * The result is nullable (Inner?). The landing-site handling (orRequired for hard
     * targets, ?: base.x in the copy stage) is applied by [applyChainLanding] after this
     * returns — the chain is ALWAYS treated as nullable.
     */
    private fun generateOptionUnwrapMapping(
        sourceField: FieldInfo,
        strategy: MappingStrategy.OptionUnwrap,
    ): CodeBlock {
        val getOrNull = CodeBlock.of("%N.getOrNull()", sourceField.name)
        return if (strategy.innerMapperFn != null) {
            CodeBlock.of("%L?.%N()?.getOrThrow()", getOrNull, strategy.innerMapperFn)
        } else {
            getOrNull
        }
    }
}

/** Returns the fully-qualified name of this KSType for use in error messages. */
private fun KSType.fqn(): String = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
