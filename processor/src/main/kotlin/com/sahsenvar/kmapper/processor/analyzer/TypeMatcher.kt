package com.sahsenvar.kmapper.processor.analyzer

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.sahsenvar.kmapper.missingConverterMessage
import com.sahsenvar.kmapper.processor.model.FieldInfo
import com.sahsenvar.kmapper.processor.model.MappingStrategy
import com.sahsenvar.kmapper.processor.model.OnFailPolicy
import com.sahsenvar.kmapper.processor.model.isStdlibListContainer
import com.sahsenvar.kmapper.processor.model.isStdlibSetContainer
import com.sahsenvar.kmapper.unsupportedConversionMessage

/**
 * Determines the appropriate mapping strategy for field transformations.
 *
 * Converter resolution is pair-keyed and orientation-aware: per-field `use=` override
 * (from @ConvertWith / @ConvertTo / @ConvertFrom) > @KMapperConfig custom registry (pair,
 * either orientation) > built-in registry (pair, either orientation). Policy-only directives
 * (onFail without use) never short-circuit discovery. A resolved pair whose needed direction
 * is not provided → UnsupportedConversion compile error; no pair at all → MissingConverter
 * compile error.
 *
 * @param customConverters Map of (sourceFqn to targetFqn) → converterFqn, populated from @KMapperConfig.
 * @param collectionWrappers Map of wrapped-collection-FQN → validated wrapper descriptor
 *   (object FQN + provided wrap/unwrap directions), from @CollectionWrapper discovery.
 * @param introspector Reads converter shapes (type pair + provided directions); null disables
 *   shape-aware resolution (every converter reference then errors as missing — tests only).
 */
class TypeMatcher(
    private val logger: KSPLogger,
    private val customConverters: Map<Pair<String, String>, String> = emptyMap(),
    private val collectionWrappers: Map<String, CollectionWrapperDescriptor> = emptyMap(),
    private val introspector: ConverterIntrospector? = null,
) {
    /**
     * Entry point per (sourceField, targetField) pair: runs field-level compile-time
     * preconditions once, then resolves the strategy. Element-level recursion (collections,
     * maps) bypasses the preconditions so they fire once per field, not per recursion step.
     */
    fun determineMappingStrategy(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        isReverse: Boolean = false,
    ): MappingStrategy {
        // Precondition: OnFail.Skip only makes sense for collection-like targets (compaction).
        // A registered @CollectionWrapper target (e.g. Box<T>) is collection-like too — its
        // element conversion (incl. onFail) runs on the normal seam rails inside wrap().
        val effectiveOnFail = sourceField.onFailFor(isReverse)
        val targetIsCollectionLike = isCollectionType(targetField.type) ||
            isMapType(targetField.type) ||
            targetField.type.declaration.qualifiedName
                ?.asString() in collectionWrappers
        if (effectiveOnFail == OnFailPolicy.Skip && !targetIsCollectionLike) {
            logger.error(
                "${sourceField.name}: OnFail.Skip applies to collection elements only; " +
                    "use OnFail.Throw or a nullable/defaulted target instead.",
            )
            return MappingStrategy.Unmappable
        }

        val strategy = resolveStrategy(sourceField, targetField, isReverse)

        // Post-resolution precondition: a collection-LIKE target can still resolve to a
        // whole-value conversion (field-level converter / nested mapper for the entire
        // container) — OnFail.Skip has no element scope there either. Rejecting here keeps
        // the scalar seam table Skip-free by construction (the generator asserts it).
        if (effectiveOnFail == OnFailPolicy.Skip &&
            (strategy is MappingStrategy.Convert || strategy is MappingStrategy.Nested)
        ) {
            logger.error(
                "${sourceField.name}: OnFail.Skip targets collection elements, but this field " +
                    "resolves to a whole-value conversion; use OnFail.Throw or a " +
                    "nullable/defaulted target instead.",
            )
            return MappingStrategy.Unmappable
        }

        // Strategy-aware dead-'?' warning, AFTER resolution and only at this entry point
        // (element-level recursion goes through resolveStrategy, so it fires once per field):
        // a nullable target fed from a non-null source is dead ONLY when the resolved
        // strategy provably never yields null.
        if (targetField.isNullable && !sourceField.isNullable && neverYieldsNull(strategy)) {
            logger.warn(
                "${sourceField.name}: target is nullable but mapping from a non-null source never produces null " +
                    "(dead '?'); consider dropping the '?' on the target.",
            )
        }

        return strategy
    }

    /**
     * True when [strategy] can never produce null from a non-null source: plain value flows
     * (Direct, enum→wire reads, nested mapper, collection/map shapes) and converter calls whose
     * resolved direction declares no OrNull variant. NOT true for OptionUnwrap (None becomes
     * null), a converter direction WITH a declared OrNull variant (sanctioned null), or
     * EnumFromWire (an unknown wire value absorbs to null at a nullable landing).
     */
    private fun neverYieldsNull(strategy: MappingStrategy): Boolean = when (strategy) {
        is MappingStrategy.Direct,
        is MappingStrategy.EnumToWire,
        is MappingStrategy.Nested,
        is MappingStrategy.Collection,
        is MappingStrategy.MapValues,
        is MappingStrategy.WrappedCollection,
        -> true

        is MappingStrategy.Convert -> !resolvedDirectionDeclaresOrNull(strategy)

        // EnumFromWire CAN yield null at a nullable landing: unknown wire values are an
        // EXPECTED input class (pinned absorption — the entries-lookup throw absorbs to
        // null on the convertOrNull seam), so the target's '?' is never dead.
        else -> false
    }

    /** True when the [strategy]'s resolved direction declares its OrNull variant (sanctioned null). */
    private fun resolvedDirectionDeclaresOrNull(strategy: MappingStrategy.Convert): Boolean {
        val shape = introspector?.shapeOf(strategy.converterFqn) ?: return false
        return if (strategy.forward) shape.declaredToOrNull else shape.declaredFromOrNull
    }

    private fun resolveStrategy(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        isReverse: Boolean,
    ): MappingStrategy {
        // 1. Per-field directive override (use=…); policy-only directives do NOT short-circuit discovery
        val directive = sourceField.directiveFor(isReverse)
        if (directive?.converterFqn != null) {
            return resolveConverter(directive.converterFqn, sourceField, targetField)
        }

        // 2. Check collection types — must come before same-type check because isSameType only
        //    compares the outer type FQN (ignoring generic arguments). Two List<X>/List<Y> types
        //    with different element types would be incorrectly treated as Direct if same-type ran first.
        //
        //    Also handles @CollectionWrapper, BOTH directions: a registered target FQN
        //    (e.g. PersistentList) fed from a plain collection takes the wrap path; a
        //    registered SOURCE FQN landing on a plain collection takes the unwrap path.
        //    A mapping needing a direction the wrapper does not declare is a guided
        //    compile error (the wrapper counterpart of UnsupportedConversion).
        val targetCollFqn =
            targetField.type.declaration.qualifiedName
                ?.asString()
        val sourceCollFqn =
            sourceField.type.declaration.qualifiedName
                ?.asString()
        val wrapDescriptor = if (targetCollFqn != null) collectionWrappers[targetCollFqn] else null

        // 2-pre. A registered wrapper type mapped to ITSELF with the same element type is a
        //        plain Direct passthrough — no pointless rewrap through the wrapper object.
        //        (Different element types fall through to the wrap gate below.)
        if (wrapDescriptor != null && sourceCollFqn == targetCollFqn) {
            val sourceElementType = extractCollectionElementType(sourceField.type)
            val targetElementType = extractCollectionElementType(targetField.type)
            if (sourceElementType != null &&
                targetElementType != null &&
                isSameType(sourceElementType, targetElementType)
            ) {
                return MappingStrategy.Direct
            }
        }

        if (wrapDescriptor != null && isCollectionType(sourceField.type)) {
            // Target is a wrapped collection (e.g. PersistentList); the wrap contract is
            // fun <T> wrap(source: List<T>) — only a stdlib List-shaped source is legal.
            // A Set or another wrapped type feeding wrap() is a GUIDED compile error (no
            // silent .toList() adapter: the conversion must be readable at the field).
            if (!sourceField.type.isStdlibListContainer()) {
                logger.error(
                    "${sourceField.name}: ${wrapDescriptor.wrapperSimpleName}.wrap takes List<T>, " +
                        "but the source is ${sourceField.type.fqn()}; use a List source or " +
                        "convert the field explicitly (e.g. .toList()) via @ConvertWith.",
                )
                return MappingStrategy.Unmappable
            }
            if (!wrapDescriptor.providesWrap) {
                logger.error(
                    "${sourceField.name}: ${wrapDescriptor.wrapperSimpleName} declares no wrap for " +
                        "${wrapDescriptor.forTypeSimpleName}; add " +
                        "fun <T> wrap(source: List<T>): ${wrapDescriptor.forTypeSimpleName}<T>",
                )
                return MappingStrategy.Unmappable
            }
            // Elements run the same full resolution as the other container shapes — no silent
            // Direct fallback for element pairs that actually need a converter.
            val sourceElementType = extractCollectionElementType(sourceField.type)
            val targetElementType = extractCollectionElementType(targetField.type)
            val elementStrategy =
                if (sourceElementType != null && targetElementType != null) {
                    resolveElementStrategy(sourceField, targetField, sourceElementType, targetElementType, isReverse)
                } else {
                    MappingStrategy.Direct
                }
            return MappingStrategy.WrappedCollection(elementStrategy, wrapDescriptor.wrapperObjectFqn)
        }

        // 2a. Unwrap direction: the SOURCE is a registered wrapped type and the target is a
        //     plain stdlib collection — Wrapper.unwrap(source) feeds the element seams.
        val unwrapDescriptor = if (sourceCollFqn != null) collectionWrappers[sourceCollFqn] else null

        if (unwrapDescriptor != null && isStdlibCollectionType(targetField.type)) {
            if (!unwrapDescriptor.providesUnwrap) {
                logger.error(
                    "${sourceField.name}: ${unwrapDescriptor.wrapperSimpleName} declares no unwrap for " +
                        "${unwrapDescriptor.forTypeSimpleName}; add " +
                        "fun <T> unwrap(source: ${unwrapDescriptor.forTypeSimpleName}<T>): List<T>",
                )
                return MappingStrategy.Unmappable
            }
            val sourceElementType = extractCollectionElementType(sourceField.type)
            val targetElementType = extractCollectionElementType(targetField.type)
            val elementStrategy =
                if (sourceElementType != null && targetElementType != null) {
                    resolveElementStrategy(sourceField, targetField, sourceElementType, targetElementType, isReverse)
                } else {
                    MappingStrategy.Direct
                }
            return MappingStrategy.WrappedCollection(elementStrategy, unwrapDescriptor.wrapperObjectFqn, useUnwrap = true)
        }

        // 2b. Map<K,V> detection — must come before data-class nested check and before
        //     the plain isCollectionType check (Map is not in the collection FQN list).
        //     IMPORTANT: when BOTH sides are maps we must handle ALL cases here so we never
        //     fall through to the isSameType check (which compares only outer FQNs and would
        //     incorrectly return Direct for Map<Int,X> → Map<String,Y>).
        if (isMapType(sourceField.type) && isMapType(targetField.type)) {
            val srcKey = extractMapKeyType(sourceField.type)
            val tgtKey = extractMapKeyType(targetField.type)
            val srcVal = extractMapValueType(sourceField.type)
            val tgtVal = extractMapValueType(targetField.type)
            if (srcKey != null &&
                tgtKey != null &&
                isSameType(srcKey, tgtKey) &&
                srcVal != null &&
                tgtVal != null
            ) {
                val valueStrategy = resolveElementStrategy(sourceField, targetField, srcVal, tgtVal, isReverse)
                return MappingStrategy.MapValues(valueStrategy)
            }
            // Key type mismatch (or missing type args) → Unmappable; do NOT fall through
            // to isSameType which only compares outer FQNs and would give a wrong Direct.
            // Key converters are parked — keys must be the same type on both sides.
            logger.error(
                "no converter for ${sourceField.type.fqn()} -> ${targetField.type.fqn()}; " +
                    "Map key types must match; add @ConvertWith to convert the field manually",
            )
            return MappingStrategy.Unmappable
        }

        if (isCollectionType(sourceField.type) && isCollectionType(targetField.type)) {
            val sourceElementType = extractCollectionElementType(sourceField.type)
            val targetElementType = extractCollectionElementType(targetField.type)

            if (sourceElementType != null && targetElementType != null) {
                val elementStrategy =
                    resolveElementStrategy(sourceField, targetField, sourceElementType, targetElementType, isReverse)
                val isSet = targetField.type.isStdlibSetContainer()
                return MappingStrategy.Collection(elementStrategy, isSet)
            }
        }

        // 3c. Check Option<T> wrap/unwrap — placed after collection/Map checks and before same-type.
        //     Detection uses FQN string comparison only; no arrow-core Gradle dep in :processor.
        //     Guard: OptionWrap only when target is Option AND source is NOT Option.
        //            OptionUnwrap only when source is Option AND target is NOT Option.
        //            (Option→Option is out of scope — must not produce Option<Option<T>>.)
        val targetOptionFqn =
            targetField.type.declaration.qualifiedName
                ?.asString()
        val sourceOptionFqn =
            sourceField.type.declaration.qualifiedName
                ?.asString()

        if (targetOptionFqn == "arrow.core.Option" && sourceOptionFqn != "arrow.core.Option") {
            val innerType =
                targetField.type.arguments
                    .firstOrNull()
                    ?.type
                    ?.resolve()
            val innerMapperFn =
                if (innerType != null && isDataClass(innerType)) {
                    nestedMapperFunctionName(innerType)
                } else {
                    null
                }
            return MappingStrategy.OptionWrap(innerMapperFn)
        }
        if (sourceOptionFqn == "arrow.core.Option" && targetOptionFqn != "arrow.core.Option") {
            val innerType =
                sourceField.type.arguments
                    .firstOrNull()
                    ?.type
                    ?.resolve()
            val innerMapperFn =
                if (innerType != null && isDataClass(innerType)) {
                    nestedMapperFunctionName(innerType)
                } else {
                    null
                }
            return MappingStrategy.OptionUnwrap(innerMapperFn)
        }

        // 3b. Check same type (non-collection)
        if (isSameType(sourceField.type, targetField.type)) {
            return MappingStrategy.Direct
        }

        // 4. Check nested object mapping
        if (isDataClass(sourceField.type) && isDataClass(targetField.type)) {
            return MappingStrategy.Nested(nestedMapperFunctionName(targetField.type))
        }

        // 5. Check enum mapping via MappableEnum
        val sourceDecl = sourceField.type.declaration as? KSClassDeclaration
        val targetDecl = targetField.type.declaration as? KSClassDeclaration
        if (sourceDecl?.classKind == ClassKind.ENUM_CLASS || targetDecl?.classKind == ClassKind.ENUM_CLASS) {
            return determineEnumStrategy(sourceField, targetField, sourceDecl, targetDecl)
        }

        // 6. @KMapperConfig (pair, either orientation)
        val customConverterFqn =
            customConverters[sourceField.type.fqn() to targetField.type.fqn()]
                ?: customConverters[targetField.type.fqn() to sourceField.type.fqn()]
        if (customConverterFqn != null) {
            return resolveConverter(customConverterFqn, sourceField, targetField)
        }

        // 7. Built-in registry (pair, either orientation)
        val builtInFqn = findBuiltInConverter(sourceField.type, targetField.type)
        if (builtInFqn != null) {
            return resolveConverter(builtInFqn, sourceField, targetField)
        }

        // 8. MissingConverter (compile error)
        logger.error(
            "${sourceField.name}: " + missingConverterMessage(sourceField.type.fqn(), targetField.type.fqn()),
        )
        return MappingStrategy.Unmappable
    }

    /**
     * Shared element-level resolution for the three container shapes (plain Collection,
     * Map values, @CollectionWrapper targets): same type → Direct, data-class pair → Nested,
     * anything else recurses into the full pair-keyed converter resolution with synthetic
     * element-typed FieldInfos (directive > custom > built-in, compile errors included).
     */
    private fun resolveElementStrategy(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        sourceElementType: KSType,
        targetElementType: KSType,
        isReverse: Boolean,
    ): MappingStrategy = when {
        isSameType(sourceElementType, targetElementType) -> MappingStrategy.Direct

        isDataClass(sourceElementType) && isDataClass(targetElementType) ->
            MappingStrategy.Nested(nestedMapperFunctionName(targetElementType))

        else ->
            resolveStrategy(
                sourceField.copy(type = sourceElementType, isNullable = sourceElementType.isMarkedNullable),
                targetField.copy(type = targetElementType, isNullable = targetElementType.isMarkedNullable),
                isReverse,
            )
    }

    /**
     * Resolves a referenced converter against the field pair, orientation-aware:
     * matches the field's (source, target) to the converter's (S, T) to pick the call
     * direction, then checks the needed direction is provided. Errors (compile-time):
     * unresolvable/non-converter reference, @UnsupportedDirection on an OrNull variant,
     * neither orientation matching the field pair, the needed direction declared only
     * via its OrNull variant (guided: override the total too — a hard landing site would
     * call the throwing total at runtime), or the needed direction not provided (with
     * the declared @UnsupportedDirection reason when present).
     */
    private fun resolveConverter(
        converterFqn: String,
        sourceField: FieldInfo,
        targetField: FieldInfo,
    ): MappingStrategy {
        val sourceFqn = sourceField.type.fqn()
        val targetFqn = targetField.type.fqn()
        val shape = introspector?.shapeOf(converterFqn)
        if (shape == null) {
            // Distinguish "reference does not resolve at all" from "resolves but is not a
            // converter": the latter gets a precise message instead of the generic missing one.
            if (introspector != null && introspector.declarationExists(converterFqn)) {
                logger.error(
                    "${sourceField.name}: $converterFqn is not a MapTypeConverter — " +
                        "converters must extend MapTypeConverter<S, T> (directly or through " +
                        "a superclass chain that binds S/T to concrete types).",
                )
            } else {
                logger.error("${sourceField.name}: " + missingConverterMessage(sourceFqn, targetFqn))
            }
            return MappingStrategy.Unmappable
        }
        if (shape.orNullAnnotatedFunction != null) {
            logger.error(
                "${sourceField.name}: @UnsupportedDirection must annotate the total method " +
                    "(convertTo/convertFrom), not the OrNull variant " +
                    "${shape.orNullAnnotatedFunction} — converter $converterFqn",
            )
            return MappingStrategy.Unmappable
        }
        val forward = sourceFqn == shape.sourceFqn && targetFqn == shape.targetFqn
        val reverse = sourceFqn == shape.targetFqn && targetFqn == shape.sourceFqn
        if (!forward && !reverse) {
            logger.error(
                "${sourceField.name}: converter $converterFqn handles " +
                    "${shape.sourceFqn} <-> ${shape.targetFqn}, not $sourceFqn -> $targetFqn",
            )
            return MappingStrategy.Unmappable
        }
        val orNullOnly = if (forward) shape.orNullOnlyTo else shape.orNullOnlyFrom
        if (orNullOnly) {
            val orNullName = if (forward) "convertToOrNull" else "convertFromOrNull"
            val totalName = if (forward) "convertTo" else "convertFrom"
            logger.error(
                "${sourceField.name}: converter $converterFqn declares $orNullName without " +
                    "the total $totalName — override the total method too (OrNull is in " +
                    "addition to the total, never instead of it).",
            )
            return MappingStrategy.Unmappable
        }
        val provided = if (forward) shape.providesTo else shape.providesFrom
        if (!provided) {
            val declaredReason = if (forward) shape.unsupportedToReason else shape.unsupportedFromReason
            val message = declaredReason ?: unsupportedConversionMessage(sourceFqn, targetFqn)
            logger.error("${sourceField.name}: $message")
            return MappingStrategy.Unmappable
        }
        return MappingStrategy.Convert(converterFqn, forward)
    }

    private fun determineEnumStrategy(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        sourceDecl: KSClassDeclaration?,
        targetDecl: KSClassDeclaration?,
    ): MappingStrategy {
        val mappableEnumFqn = "com.sahsenvar.kmapper.MappableEnum"

        // Target is the enum (wire → enum)
        if (targetDecl?.classKind == ClassKind.ENUM_CLASS) {
            val wireFqn = resolveEnumWireType(targetDecl, mappableEnumFqn)
            if (wireFqn == null) {
                logger.error(
                    "enum '${targetDecl.simpleName.asString()}' must implement MappableEnum<...> " +
                        "or use @ConvertWith",
                )
                return MappingStrategy.Unmappable
            }
            val sourceFqn = sourceField.type.fqn()
            if (sourceFqn != wireFqn) {
                logger.error(
                    "enum wire type mismatch: expected $wireFqn but source type is $sourceFqn",
                )
                return MappingStrategy.Unmappable
            }
            return MappingStrategy.EnumFromWire(targetDecl.qualifiedName!!.asString())
        }

        // Source is the enum (enum → wire)
        if (sourceDecl?.classKind == ClassKind.ENUM_CLASS) {
            val wireFqn = resolveEnumWireType(sourceDecl, mappableEnumFqn)
            if (wireFqn == null) {
                logger.error(
                    "enum '${sourceDecl.simpleName.asString()}' must implement MappableEnum<...> " +
                        "or use @ConvertWith",
                )
                return MappingStrategy.Unmappable
            }
            val targetFqn = targetField.type.fqn()
            if (targetFqn != wireFqn) {
                logger.error(
                    "enum wire type mismatch: expected $wireFqn but target type is $targetFqn",
                )
                return MappingStrategy.Unmappable
            }
            return MappingStrategy.EnumToWire
        }

        // Should not reach here, but guard anyway
        logger.error("Unexpected enum resolution state for ${sourceField.name}")
        return MappingStrategy.Unmappable
    }

    /**
     * Resolves the wire type FQN from an enum's MappableEnum<W> supertype.
     * Returns null if the enum does not implement MappableEnum.
     */
    private fun resolveEnumWireType(
        enumDecl: KSClassDeclaration,
        mappableEnumFqn: String,
    ): String? {
        for (supertype in enumDecl.superTypes) {
            val resolved = supertype.resolve()
            val declFqn = resolved.declaration.qualifiedName?.asString() ?: continue
            if (declFqn == mappableEnumFqn) {
                // MappableEnum<W> — extract the W type argument
                val wireTypeArg =
                    resolved.arguments
                        .firstOrNull()
                        ?.type
                        ?.resolve()
                return wireTypeArg?.declaration?.qualifiedName?.asString()
            }
        }
        return null
    }

    /**
     * Generated-mapper call name for a nested target type — mirrors
     * FunctionNameGenerator.generateMapperFunctionName atomically (`to{Simple}Result`).
     */
    private fun nestedMapperFunctionName(targetType: KSType): String = "to${targetType.declaration.simpleName.asString()}Result"

    private fun isSameType(
        source: KSType,
        target: KSType,
    ): Boolean = source.declaration.qualifiedName?.asString() ==
        target.declaration.qualifiedName?.asString()

    fun isCollectionType(type: KSType): Boolean {
        val fqn = type.declaration.qualifiedName?.asString() ?: return false
        return fqn.startsWith("kotlin.collections.List") ||
            fqn.startsWith("kotlin.collections.Set") ||
            fqn.startsWith("kotlinx.collections.immutable")
    }

    /**
     * Returns true for the plain stdlib collection containers (List/MutableList/Set/MutableSet)
     * only — excludes wrapped types like kotlinx.collections.immutable. Used as the unwrap
     * direction's TARGET shape check: unwrap() lands on a plain collection by contract.
     */
    fun isStdlibCollectionType(type: KSType): Boolean {
        val fqn = type.declaration.qualifiedName?.asString() ?: return false
        return fqn.startsWith("kotlin.collections.List") ||
            fqn.startsWith("kotlin.collections.MutableList") ||
            fqn.startsWith("kotlin.collections.Set") ||
            fqn.startsWith("kotlin.collections.MutableSet")
    }

    fun extractCollectionElementType(type: KSType): KSType? = type.arguments
        .firstOrNull()
        ?.type
        ?.resolve()

    fun isMapType(type: KSType): Boolean {
        val fqn = type.declaration.qualifiedName?.asString() ?: return false
        return fqn == "kotlin.collections.Map" || fqn == "kotlin.collections.MutableMap"
    }

    fun extractMapKeyType(type: KSType): KSType? = type.arguments
        .getOrNull(0)
        ?.type
        ?.resolve()

    fun extractMapValueType(type: KSType): KSType? = type.arguments
        .getOrNull(1)
        ?.type
        ?.resolve()

    private fun isDataClass(type: KSType): Boolean {
        val decl = type.declaration as? KSClassDeclaration ?: return false
        return decl.modifiers.contains(Modifier.DATA)
    }

    /**
     * Pair-keyed registry of the 35 built-in converters (richer-first naming: the converter's
     * S is the richer/wider type, T the narrower). Lookup is orientation-INDEPENDENT — the same
     * entry matches both field orientations; [resolveConverter] decides convertTo vs convertFrom.
     */
    private val builtInPairs: List<Triple<String, String, String>> = run {
        val prefix = "com.sahsenvar.kmapper.converter.builtin."
        listOf(
            // numeric widening (12)
            Triple("kotlin.Short", "kotlin.Byte", prefix + "ShortByteConverter"),
            Triple("kotlin.Int", "kotlin.Byte", prefix + "IntByteConverter"),
            Triple("kotlin.Long", "kotlin.Byte", prefix + "LongByteConverter"),
            Triple("kotlin.Int", "kotlin.Short", prefix + "IntShortConverter"),
            Triple("kotlin.Long", "kotlin.Short", prefix + "LongShortConverter"),
            Triple("kotlin.Long", "kotlin.Int", prefix + "LongIntConverter"),
            Triple("kotlin.Float", "kotlin.Byte", prefix + "FloatByteConverter"),
            Triple("kotlin.Double", "kotlin.Byte", prefix + "DoubleByteConverter"),
            Triple("kotlin.Float", "kotlin.Short", prefix + "FloatShortConverter"),
            Triple("kotlin.Double", "kotlin.Short", prefix + "DoubleShortConverter"),
            Triple("kotlin.Double", "kotlin.Int", prefix + "DoubleIntConverter"),
            Triple("kotlin.Double", "kotlin.Float", prefix + "DoubleFloatConverter"),
            // String pairs (7)
            Triple("kotlin.Byte", "kotlin.String", prefix + "ByteStringConverter"),
            Triple("kotlin.Short", "kotlin.String", prefix + "ShortStringConverter"),
            Triple("kotlin.Int", "kotlin.String", prefix + "IntStringConverter"),
            Triple("kotlin.Long", "kotlin.String", prefix + "LongStringConverter"),
            Triple("kotlin.Float", "kotlin.String", prefix + "FloatStringConverter"),
            Triple("kotlin.Double", "kotlin.String", prefix + "DoubleStringConverter"),
            Triple("kotlin.Boolean", "kotlin.String", prefix + "BooleanStringConverter"),
            // X-pairs (9)
            Triple("kotlin.Float", "kotlin.Int", prefix + "FloatIntConverter"),
            Triple("kotlin.Float", "kotlin.Long", prefix + "FloatLongConverter"),
            Triple("kotlin.Double", "kotlin.Long", prefix + "DoubleLongConverter"),
            Triple("kotlin.Byte", "kotlin.Boolean", prefix + "ByteBooleanConverter"),
            Triple("kotlin.Short", "kotlin.Boolean", prefix + "ShortBooleanConverter"),
            Triple("kotlin.Int", "kotlin.Boolean", prefix + "IntBooleanConverter"),
            Triple("kotlin.Long", "kotlin.Boolean", prefix + "LongBooleanConverter"),
            Triple("kotlin.Float", "kotlin.Boolean", prefix + "FloatBooleanConverter"),
            Triple("kotlin.Double", "kotlin.Boolean", prefix + "DoubleBooleanConverter"),
            // kotlinx-datetime (5)
            Triple("kotlinx.datetime.Instant", "kotlin.String", prefix + "InstantStringConverter"),
            Triple("kotlinx.datetime.Instant", "kotlin.Long", prefix + "InstantLongConverter"),
            Triple("kotlinx.datetime.LocalDate", "kotlin.String", prefix + "LocalDateStringConverter"),
            Triple("kotlinx.datetime.LocalDateTime", "kotlin.String", prefix + "LocalDateTimeStringConverter"),
            Triple("kotlinx.datetime.LocalTime", "kotlin.String", prefix + "LocalTimeStringConverter"),
            // kotlin.time (2)
            Triple("kotlin.time.Duration", "kotlin.String", prefix + "DurationStringConverter"),
            Triple("kotlin.time.Duration", "kotlin.Long", prefix + "DurationLongConverter"),
        )
    }

    /**
     * Finds a built-in converter whose (S, T) pair matches the given types in EITHER
     * orientation; the call direction is decided later by [resolveConverter].
     */
    private fun findBuiltInConverter(
        source: KSType,
        target: KSType,
    ): String? {
        val sourceFqn = source.fqn()
        val targetFqn = target.fqn()
        return builtInPairs
            .firstOrNull { (firstFqn, secondFqn, _) ->
                (sourceFqn == firstFqn && targetFqn == secondFqn) ||
                    (sourceFqn == secondFqn && targetFqn == firstFqn)
            }?.third
    }
}

/** Returns the fully-qualified name of this KSType for use in error messages and converter lookup. */
internal fun KSType.fqn(): String = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
