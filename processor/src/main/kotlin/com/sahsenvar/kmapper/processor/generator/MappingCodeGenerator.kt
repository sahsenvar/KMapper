package com.sahsenvar.kmapper.processor.generator

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSType
import com.sahsenvar.kmapper.processor.model.FieldInfo
import com.sahsenvar.kmapper.processor.model.MappingStrategy
import com.sahsenvar.kmapper.processor.model.OnFailPolicy
import com.sahsenvar.kmapper.processor.model.isStdlibSetContainer
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
 * runtime via `MappingException.withPathPrefix`; element seams append their own `[i]` /
 * `["key"]` segments); type literals are codegen string literals (fully-qualified for
 * converter pairs and collection elements, simple class names for scalar nested mappers)
 * so release builds stay readable under R8 without a mapping file.
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
                        generateCollectionMapping(sourceField, targetField, strategy, onFail),
                        chainIsNullable = sourceField.isNullable,
                        targetField = targetField,
                        landingShape = landingShape,
                    )

                is MappingStrategy.WrappedCollection ->
                    applyChainLanding(
                        generateWrappedCollectionMapping(sourceField, targetField, strategy, onFail),
                        chainIsNullable = sourceField.isNullable,
                        targetField = targetField,
                        landingShape = landingShape,
                    )

                is MappingStrategy.MapValues ->
                    applyChainLanding(
                        generateMapValuesMapping(sourceField, targetField, strategy, onFail),
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
                    generateEnumFromWireMapping(sourceField, targetField, strategy, landingShape, onFail)

                is MappingStrategy.EnumToWire ->
                    applyChainLanding(
                        generateEnumToWireMapping(sourceField),
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

    /**
     * Seam member for the (landing shape × policy) cell. `OnFail.Skip` reaches the element
     * seams via the collection table only; on a scalar landing site it is rejected upstream
     * (TypeMatcher precondition), so seeing it here is a processor bug — fail loudly.
     */
    private fun seamFor(
        landingShape: LandingShape,
        onFail: OnFailPolicy,
    ): MemberName {
        check(onFail != OnFailPolicy.Skip) { "OnFail.Skip must be rejected upstream of scalar seam selection" }
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
     * Container-level landing for chain-shaped strategies (collections, maps, wrapped
     * collections, enum→wire reads, Option unwrap): a nullable chain into a HARD landing
     * site gets the `orRequired` absence guard; the COPY stage falls back to `base.<field>`;
     * nullable targets pass the chain through unchanged. This is the CONTAINER half of the
     * scope separation — element failure handling lives inside the element seams and never
     * escalates here.
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

        val validationFailed = ClassName(SEAMS_PACKAGE, "MappingException", "ValidationFailed")
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
     * Wire → enum bridge riding the SAME ladder seams as any conversion (spec: UnknownEnumValue
     * "rides the same ladder"): the entries lookup is the convert lambda, so an unknown wire
     * value absorbs to null/default at nullable/defaulted landing sites (reported) and stays
     * hard only where the ladder is hard. The lambda throws with an EMPTY path — the seam
     * prefixes the TARGET field's name (a path names where the value LANDS; the source field
     * name can differ under @FieldMap renames).
     */
    private fun generateEnumFromWireMapping(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        strategy: MappingStrategy.EnumFromWire,
        landingShape: LandingShape,
        onFail: OnFailPolicy,
    ): CodeBlock = emitSeamCall(
        sourceField = sourceField,
        targetField = targetField,
        landingShape = landingShape,
        onFail = onFail,
        fromLiteral = sourceField.type.fqn(),
        toLiteral = strategy.enumFqn.substringAfterLast("."),
        convertLambda = enumEntriesLookupLambda(strategy.enumFqn),
    )

    /**
     * The wire→enum convert lambda — `MappableEnum.entries` lookup throwing
     * [com.sahsenvar.kmapper.MappingException.UnknownEnumValue] (empty path: the seam
     * prefixes the landing path). Shared by the scalar EnumFromWire emission and the
     * element-level collection emission so both ride identical rails.
     */
    private fun enumEntriesLookupLambda(enumFqn: String): CodeBlock {
        val enumClassName = ClassName.bestGuess(enumFqn)
        val enumSimpleName = enumFqn.substringAfterLast(".")
        val mappingExceptionClass = ClassName(SEAMS_PACKAGE, "MappingException")
        return CodeBlock.of(
            "{·wire·->·%T.entries.firstOrNull·{·it.wireValue·==·wire·}" +
                "·?:·throw·%T.UnknownEnumValue(%S,·%S,·wire.toString())·}",
            enumClassName,
            mappingExceptionClass,
            "",
            enumSimpleName,
        )
    }

    /**
     * Enum → wire value via MappableEnum.wireValue — a property read that can never break,
     * so no seam is needed; the container-level landing (orRequired / `?: base.x`) is applied
     * by [applyChainLanding] after this returns.
     */
    private fun generateEnumToWireMapping(sourceField: FieldInfo): CodeBlock = if (sourceField.isNullable) {
        CodeBlock.of("%N?.wireValue", sourceField.name)
    } else {
        CodeBlock.of("%N.wireValue", sourceField.name)
    }

    /**
     * Element-ladder collection emission: Convert/Nested elements ride the convertEach…
     * seams selected by the (target element shape × onFail) table — see [listElementSeamName];
     * Direct same-type elements keep the container passthrough (no seam). Container-level
     * null handling is applied by [applyChainLanding] after this returns (scope separation:
     * element failure never escalates to the container).
     *
     * Emission shapes:
     *   tags.convertEachOrSkip("tags", "kotlin.String", "kotlin.Long") { LongStringConverter.convertFromOrNull(it) }
     *   tags?.convertEachOrFail("tags", "TagDataModel", "TagDomainModel") { it.toTagDomainModelResult().getOrThrow() }
     */
    private fun generateCollectionMapping(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        strategy: MappingStrategy.Collection,
        onFail: OnFailPolicy,
    ): CodeBlock {
        val sourceElementType = elementTypeOf(sourceField.type)
        val targetElementType = elementTypeOf(targetField.type)
        if (sourceElementType == null || targetElementType == null) {
            return CodeBlock.of("%N", sourceField.name)
        }
        val seamName = listElementSeamName(targetElementType.isMarkedNullable, strategy.isSet, onFail)
        val convertLambda =
            elementConvertLambda(strategy.elementStrategy, seamTakesTotalConvert(seamName))
                ?: return CodeBlock.of("%N", sourceField.name)
        return elementSeamCall(
            receiver = CodeBlock.of("%N", sourceField.name),
            receiverIsNullable = sourceField.isNullable,
            seamName = seamName,
            path = targetField.name,
            fromLiteral = sourceElementType.fqn(),
            toLiteral = targetElementType.fqn(),
            convertLambda = convertLambda,
        )
    }

    /**
     * @CollectionWrapper emission, both directions. The wrapper object handles ONLY the
     * container shell; element conversion stays on the normal seam rails. The wrapper
     * invocation itself rides a `convertOrFail` guard so a wrapper-thrown [MappingException]
     * (e.g. EmptyCollection from a non-empty container) reaches the boundary carrying the
     * FIELD path — the guard wraps ONLY the wrap/unwrap call, never the element seam chain,
     * so element errors keep their own already-rooted paths (no double prefixing):
     *
     * Forward (wrap — target is the registered type):
     *   source.convertEachOrSkip(…) { … }.convertOrFail(path, from, to) { WrapperObject.wrap(it) }
     *   source?.convertEachOrSkip(…) { … }?.convertOrFail(…) { WrapperObject.wrap(it) }   (nullable source)
     *   source[?].convertOrFail(…) { WrapperObject.wrap(it) }                              (Direct elements)
     *
     * Unwrap (source is the registered type, target a plain collection):
     *   source.convertOrFail(path, from, to) { WrapperObject.unwrap(it) }.convertEachOrSkip(…) { … }
     *   source?.convertOrFail(…) { WrapperObject.unwrap(it) }?.convertEachOrSkip(…) { … }
     *
     * A nullable source uses `?.convertOrFail` — the safe call keeps null flowing to the
     * container landing (no absence guard here; that's [applyChainLanding]'s job).
     *
     * The wrap contract takes List<T>, so the forward element seams are always List-shaped;
     * the unwrap direction picks Set seams when the plain TARGET is a Set.
     */
    private fun generateWrappedCollectionMapping(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        strategy: MappingStrategy.WrappedCollection,
        onFail: OnFailPolicy,
    ): CodeBlock {
        val wrapperClass = ClassName.bestGuess(strategy.wrapperObjectFqn)
        val containerFrom = sourceField.type.fqn()
        val containerTo = targetField.type.fqn()
        return if (strategy.useUnwrap) {
            val unwrapped =
                wrapperShellGuard(
                    receiver = CodeBlock.of("%N", sourceField.name),
                    receiverIsNullable = sourceField.isNullable,
                    path = targetField.name,
                    fromLiteral = containerFrom,
                    toLiteral = containerTo,
                    wrapperCall = CodeBlock.of("{·%T.unwrap(it)·}", wrapperClass),
                )
            val sourceElementType = elementTypeOf(sourceField.type)
            val targetElementType = elementTypeOf(targetField.type)
            if (sourceElementType == null || targetElementType == null) return unwrapped
            val seamName =
                listElementSeamName(targetElementType.isMarkedNullable, targetField.type.isStdlibSetContainer(), onFail)
            val convertLambda =
                elementConvertLambda(strategy.elementStrategy, seamTakesTotalConvert(seamName))
                    ?: return unwrapped
            elementSeamCall(
                receiver = unwrapped,
                receiverIsNullable = sourceField.isNullable,
                seamName = seamName,
                path = targetField.name,
                fromLiteral = sourceElementType.fqn(),
                toLiteral = targetElementType.fqn(),
                convertLambda = convertLambda,
            )
        } else {
            val wrapCall = CodeBlock.of("{·%T.wrap(it)·}", wrapperClass)
            val sourceElementType = elementTypeOf(sourceField.type)
            val targetElementType = elementTypeOf(targetField.type)
            val seamName =
                if (sourceElementType != null && targetElementType != null) {
                    // wrap(List<T>) by contract — the inner seam is always the List-shaped one.
                    listElementSeamName(targetElementType.isMarkedNullable, isSetTarget = false, onFail = onFail)
                } else {
                    null
                }
            val convertLambda =
                seamName?.let { elementConvertLambda(strategy.elementStrategy, seamTakesTotalConvert(it)) }
            val innerChain =
                if (seamName != null && convertLambda != null && sourceElementType != null && targetElementType != null) {
                    elementSeamCall(
                        receiver = CodeBlock.of("%N", sourceField.name),
                        receiverIsNullable = sourceField.isNullable,
                        seamName = seamName,
                        path = targetField.name,
                        fromLiteral = sourceElementType.fqn(),
                        toLiteral = targetElementType.fqn(),
                        convertLambda = convertLambda,
                    )
                } else {
                    CodeBlock.of("%N", sourceField.name)
                }
            wrapperShellGuard(
                receiver = innerChain,
                receiverIsNullable = sourceField.isNullable,
                path = targetField.name,
                fromLiteral = containerFrom,
                toLiteral = containerTo,
                wrapperCall = wrapCall,
            )
        }
    }

    /**
     * Path guard for the wrapper-object invocation: `receiver[?].convertOrFail(path, from, to)
     * { Wrapper.wrap/unwrap(it) }`. The receiver is evaluated BEFORE the guard, so only the
     * wrapper call itself is caught — a thrown [MappingException] gets the field path via
     * `withPathPrefix` (same type, e.g. EmptyCollection keeps its detail), anything else
     * becomes a typed TypeConversionFailed at the field. The safe-call variant keeps a null
     * chain flowing untouched (absence stays [applyChainLanding]'s decision).
     */
    private fun wrapperShellGuard(
        receiver: CodeBlock,
        receiverIsNullable: Boolean,
        path: String,
        fromLiteral: String,
        toLiteral: String,
        wrapperCall: CodeBlock,
    ): CodeBlock {
        val safeCall = if (receiverIsNullable) "?" else ""
        return CodeBlock.of(
            "%L$safeCall.%M(%S,·%S,·%S)·%L",
            receiver,
            MemberName(SEAMS_PACKAGE, "convertOrFail"),
            path,
            fromLiteral,
            toLiteral,
            wrapperCall,
        )
    }

    /**
     * Map<K,V1> → Map<K,V2> emission through the convertEntries… seams (per-entry key/value
     * ladders, keyed paths like `prices["usd"]`). Keys stay same-type in v1 — the identity
     * `{ it }` lambda satisfies the seam's convertKey parameter; key converters are parked.
     * Direct same-type values keep the container passthrough.
     *
     * Emission shape:
     *   prices.convertEntriesOrSkip("prices", "kotlin.String", "kotlin.String",
     *       "kotlin.String", "kotlin.Long", { it }) { LongStringConverter.convertFromOrNull(it) }
     *
     * [applyChainLanding] runs after this method returns, so a nullable source into a hard
     * or defaulted target lands correctly (orRequired / ?: base.x) without extra logic here.
     */
    private fun generateMapValuesMapping(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        strategy: MappingStrategy.MapValues,
        onFail: OnFailPolicy,
    ): CodeBlock {
        val sourceKeyType = sourceField.type.arguments.getOrNull(0)?.type?.resolve()
        val targetKeyType = targetField.type.arguments.getOrNull(0)?.type?.resolve()
        val sourceValueType = sourceField.type.arguments.getOrNull(1)?.type?.resolve()
        val targetValueType = targetField.type.arguments.getOrNull(1)?.type?.resolve()
        if (sourceKeyType == null || targetKeyType == null || sourceValueType == null || targetValueType == null) {
            return CodeBlock.of("%N", sourceField.name)
        }
        val seamName = mapEntrySeamName(targetValueType.isMarkedNullable, onFail)
        val convertValueLambda =
            elementConvertLambda(strategy.valueStrategy, seamTakesTotalConvert(seamName))
                ?: return CodeBlock.of("%N", sourceField.name)
        val safeCall = if (sourceField.isNullable) "?" else ""
        return CodeBlock.of(
            "%N$safeCall.%M(%S,·%S,·%S,·%S,·%S,·{·it·})·%L",
            sourceField.name,
            MemberName(SEAMS_PACKAGE, seamName),
            targetField.name,
            sourceKeyType.fqn(),
            targetKeyType.fqn(),
            sourceValueType.fqn(),
            targetValueType.fqn(),
            convertValueLambda,
        )
    }

    /**
     * Element seam for a List/Set-shaped container, per the LOCKED table:
     *
     * | target element | Auto | Skip | Throw |
     * |---|---|---|---|
     * | `T` (List)  | convertEachOrSkip      | convertEachOrSkip | convertEachOrFail      |
     * | `T?` (List) | convertEachOrNull      | convertEachOrSkip | convertEachOrNullStrict |
     * | Set         | convertEachOrSkipToSet | same              | convertEachOrFailToSet  |
     */
    private fun listElementSeamName(
        targetElementNullable: Boolean,
        isSetTarget: Boolean,
        onFail: OnFailPolicy,
    ): String = when {
        // Every branch enumerates the FULL policy set: a future OnFailPolicy entry must
        // make each cell's decision explicit instead of inheriting an `else` default.
        isSetTarget ->
            when (onFail) {
                OnFailPolicy.Throw -> "convertEachOrFailToSet"
                OnFailPolicy.Auto, OnFailPolicy.Skip -> "convertEachOrSkipToSet"
            }

        targetElementNullable ->
            when (onFail) {
                OnFailPolicy.Throw -> "convertEachOrNullStrict"
                OnFailPolicy.Skip -> "convertEachOrSkip"
                OnFailPolicy.Auto -> "convertEachOrNull"
            }

        else ->
            when (onFail) {
                OnFailPolicy.Throw -> "convertEachOrFail"
                OnFailPolicy.Auto, OnFailPolicy.Skip -> "convertEachOrSkip"
            }
    }

    /**
     * Entry seam for a Map-shaped container, per the LOCKED table:
     *
     * | target value | Auto | Skip | Throw |
     * |---|---|---|---|
     * | `VT`  | convertEntriesOrSkip       | same                 | convertEntriesOrFail |
     * | `VT?` | convertEntriesValueOrNull  | convertEntriesOrSkip | convertEntriesOrFail |
     */
    private fun mapEntrySeamName(
        targetValueNullable: Boolean,
        onFail: OnFailPolicy,
    ): String = when (onFail) {
        // Exhaustive over the policy set — a future OnFailPolicy entry must decide its cell.
        OnFailPolicy.Throw -> "convertEntriesOrFail"
        OnFailPolicy.Auto -> if (targetValueNullable) "convertEntriesValueOrNull" else "convertEntriesOrSkip"
        OnFailPolicy.Skip -> "convertEntriesOrSkip"
    }

    /**
     * True when [seamName]'s convert parameter is total `(S) -> T` (the OrFail family) —
     * the converter's TOTAL method is called there. Every other seam accepts a
     * nullable-returning convert, so the converter's OrNull method rides it (sanctioned
     * null lands as skip/null per the seam's own rung).
     */
    private fun seamTakesTotalConvert(seamName: String): Boolean = seamName in setOf("convertEachOrFail", "convertEachOrFailToSet", "convertEntriesOrFail")

    /**
     * Convert lambda for an element-level strategy: converter call (orientation-aware,
     * total vs OrNull per the seam), nested sub-mapper through the Result boundary, or the
     * enum bridges mirroring their scalar emissions (entries lookup for wire→enum — an
     * unknown wire value throws into the seam and rides its rung; `wireValue` read for
     * enum→wire). Returns null for element strategies with no seam-side conversion (Direct
     * passthrough stays container-level; deeper recursion is out of scope for v1).
     */
    private fun elementConvertLambda(
        elementStrategy: MappingStrategy,
        useTotalConverterMethod: Boolean,
    ): CodeBlock? = when (elementStrategy) {
        is MappingStrategy.Convert -> {
            val converterClassName = ClassName.bestGuess(elementStrategy.converterFqn)
            val totalMethod = if (elementStrategy.forward) "convertTo" else "convertFrom"
            val convertMethod = if (useTotalConverterMethod) totalMethod else "${totalMethod}OrNull"
            CodeBlock.of("{·%T.%N(it)·}", converterClassName, convertMethod)
        }

        is MappingStrategy.Nested ->
            CodeBlock.of("{·it.%N().getOrThrow()·}", elementStrategy.mapperFunctionName)

        is MappingStrategy.EnumFromWire -> enumEntriesLookupLambda(elementStrategy.enumFqn)

        is MappingStrategy.EnumToWire -> CodeBlock.of("{·it.wireValue·}")

        else -> null
    }

    /**
     * Shared element-seam emission:
     * `receiver[?].seam(path, fromElementFqn, toElementFqn) { convert }`.
     * A nullable receiver chain stays nullable — [applyChainLanding] lands it.
     */
    private fun elementSeamCall(
        receiver: CodeBlock,
        receiverIsNullable: Boolean,
        seamName: String,
        path: String,
        fromLiteral: String,
        toLiteral: String,
        convertLambda: CodeBlock,
    ): CodeBlock {
        val safeCall = if (receiverIsNullable) "?" else ""
        return CodeBlock.of(
            "%L$safeCall.%M(%S,·%S,·%S)·%L",
            receiver,
            MemberName(SEAMS_PACKAGE, seamName),
            path,
            fromLiteral,
            toLiteral,
            convertLambda,
        )
    }

    /** First type argument of a collection-shaped [type], or null when unavailable. */
    private fun elementTypeOf(type: KSType): KSType? = type.arguments
        .firstOrNull()
        ?.type
        ?.resolve()

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
