package com.sahsenvar.kmapper.processor.analyzer

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.Modifier

/**
 * Which directions a converter provides, and any declared @UnsupportedDirection reasons.
 *
 * A direction is PROVIDED iff its TOTAL method is declared AND not annotated
 * `@UnsupportedDirection` — the annotation wins, because KSP cannot inspect function
 * bodies (documented contract: the stub body is `= unsupported()`). An OrNull variant
 * never provides a direction on its own: a hard landing site would call the throwing
 * total at runtime, so an OrNull-only override is a guided compile error at resolution.
 */
data class ConverterShape(
    /** FQN of the converter's source type S in MapTypeConverter<S, T>. */
    val sourceFqn: String,
    /** FQN of the converter's target type T in MapTypeConverter<S, T>. */
    val targetFqn: String,
    /** True when the total convertTo is declared. */
    val declaredToTotal: Boolean,
    /** True when the convertToOrNull variant is declared. */
    val declaredToOrNull: Boolean,
    /** True when the total convertFrom is declared. */
    val declaredFromTotal: Boolean,
    /** True when the convertFromOrNull variant is declared. */
    val declaredFromOrNull: Boolean,
    /** Reason from @UnsupportedDirection on convertTo, or null when not annotated. */
    val unsupportedToReason: String?,
    /** Reason from @UnsupportedDirection on convertFrom, or null when not annotated. */
    val unsupportedFromReason: String?,
    /**
     * Name of the OrNull variant carrying a misplaced @UnsupportedDirection (e.g.
     * "convertToOrNull"), or null when no OrNull variant is annotated — compile error
     * at resolution, naming the offending function.
     */
    val orNullAnnotatedFunction: String?,
) {
    /** True when an OrNull variant carries a misplaced @UnsupportedDirection. */
    val orNullAnnotated: Boolean get() = orNullAnnotatedFunction != null

    /** True when the S -> T direction is provided: total convertTo declared, not annotated. */
    val providesTo: Boolean get() = declaredToTotal && unsupportedToReason == null

    /** True when the T -> S direction is provided: total convertFrom declared, not annotated. */
    val providesFrom: Boolean get() = declaredFromTotal && unsupportedFromReason == null

    /** convertToOrNull declared without its total — compile error when S -> T is needed. */
    val orNullOnlyTo: Boolean get() = declaredToOrNull && !declaredToTotal

    /** convertFromOrNull declared without its total — compile error when T -> S is needed. */
    val orNullOnlyFrom: Boolean get() = declaredFromOrNull && !declaredFromTotal
}

/**
 * Reads a converter declaration's [ConverterShape] from the KSP [Resolver]:
 * its (S, T) type pair from the MapTypeConverter supertype — found by walking UP the
 * superclass chain, so parameterized-converter bases (the ledger's official recipe:
 * `object PriceFormatConverter : FormattedDoubleStringConverter(digits = 2)` where the
 * abstract base extends `MapTypeConverter<Double, String>`) resolve too — and which
 * directions it provides, via function-level detection of the declared overrides (most
 * derived wins) and their `@UnsupportedDirection` annotations.
 *
 * Generic-passthrough bases (`Base<X> : MapTypeConverter<X, String>`) are OUT OF SCOPE:
 * resolving them would require type-argument substitution through arbitrary chains; they
 * produce a guided compile error via [logger] instead.
 *
 * @param logger optional diagnostics sink; null disables the generic-passthrough error
 *   (introspection-probe tests construct the introspector without a logger).
 */
class ConverterIntrospector(
    private val resolver: Resolver,
    private val logger: KSPLogger? = null,
) {
    private val converterBaseFqn = "com.sahsenvar.kmapper.converter.MapTypeConverter"
    private val unsupportedDirectionFqn = "com.sahsenvar.kmapper.converter.UnsupportedDirection"
    private val directionFunctionNames = setOf("convertTo", "convertFrom", "convertToOrNull", "convertFromOrNull")

    // Per-instance memo: the introspector is created fresh each KSP round, so cached
    // shapes can never go stale. Null results (non-converters) recompute — cheap and rare.
    private val shapeCache = mutableMapOf<String, ConverterShape?>()

    // MapTypeConverter's own direction methods by simple name — the overridee side of the
    // resolver.overrides check for BINARY declarations (see [isRealDirectionOverride]).
    private val baseDirectionFunctions: Map<String, KSFunctionDeclaration> by lazy {
        resolver
            .getClassDeclarationByName(resolver.getKSNameFromString(converterBaseFqn))
            ?.getDeclaredFunctions()
            ?.filter { it.simpleName.asString() in directionFunctionNames }
            ?.associateBy { it.simpleName.asString() }
            ?: emptyMap()
    }

    /**
     * Returns the [ConverterShape] for [converterFqn], or null when the declaration cannot
     * be resolved or does not extend MapTypeConverter<S, T> directly. Memoized per instance.
     */
    fun shapeOf(converterFqn: String): ConverterShape? = shapeCache.getOrPut(converterFqn) { introspectShape(converterFqn) }

    /**
     * True when [converterFqn] resolves to ANY class declaration — converter or not.
     * Lets callers distinguish "reference does not resolve" from "resolves but is not
     * a direct MapTypeConverter subtype" when [shapeOf] returns null.
     */
    fun declarationExists(converterFqn: String): Boolean = resolver.getClassDeclarationByName(
        resolver.getKSNameFromString(converterFqn),
    ) != null

    private fun introspectShape(converterFqn: String): ConverterShape? {
        val declaration = resolver.getClassDeclarationByName(
            resolver.getKSNameFromString(converterFqn),
        ) ?: return null
        val chainToBase = superclassChainToConverterBase(declaration) ?: return null
        val (sourceFqn, targetFqn) = concreteTypeArguments(chainToBase.baseReference, declaration) ?: return null

        // Function-level detection, totals and OrNull variants tracked separately: only a
        // declared, un-annotated TOTAL provides its direction (the annotation wins — bodies
        // are opaque to KSP); an OrNull-only override is flagged for a guided compile error.
        // The whole superclass chain contributes (an abstract base may hold the overrides);
        // the MOST-DERIVED override of each direction method wins, including its annotation.
        var declaredToTotal = false
        var declaredToOrNull = false
        var declaredFromTotal = false
        var declaredFromOrNull = false
        var reasonTo: String? = null
        var reasonFrom: String? = null
        var orNullAnnotatedFunction: String? = null
        val claimedFunctionNames = mutableSetOf<String>()
        for (chainClass in chainToBase.chain) {
            chainClass.getDeclaredFunctions().forEach { function ->
                // Only a REAL override of the single-parameter base method counts: same-named
                // helper overloads (extra parameters) or non-override declarations never
                // declare a direction.
                if (!isRealDirectionOverride(function)) return@forEach
                val functionName = function.simpleName.asString()
                // A more-derived class in the chain already claimed this direction method.
                if (!claimedFunctionNames.add(functionName)) return@forEach
                val reason = unsupportedReasonOf(function)
                when (functionName) {
                    "convertTo" -> {
                        declaredToTotal = true
                        if (reason != null) reasonTo = reason
                    }
                    "convertFrom" -> {
                        declaredFromTotal = true
                        if (reason != null) reasonFrom = reason
                    }
                    "convertToOrNull" -> {
                        declaredToOrNull = true
                        if (reason != null) orNullAnnotatedFunction = functionName
                    }
                    "convertFromOrNull" -> {
                        declaredFromOrNull = true
                        if (reason != null) orNullAnnotatedFunction = functionName
                    }
                }
            }
        }
        return ConverterShape(
            sourceFqn = sourceFqn,
            targetFqn = targetFqn,
            declaredToTotal = declaredToTotal,
            declaredToOrNull = declaredToOrNull,
            declaredFromTotal = declaredFromTotal,
            declaredFromOrNull = declaredFromOrNull,
            unsupportedToReason = reasonTo,
            unsupportedFromReason = reasonFrom,
            orNullAnnotatedFunction = orNullAnnotatedFunction,
        )
    }

    /**
     * True when [function] is a genuine override of one of MapTypeConverter's four
     * single-parameter direction methods. Two gates:
     * - exactly one parameter — same-named helper overloads never count;
     * - Modifier.OVERRIDE (source declarations, fast path) OR resolver.overrides against
     *   the base method — BINARY declarations surface no OVERRIDE modifier in KSP, so the
     *   resolver answers for classpath converters (e.g. the built-ins from :core).
     */
    private fun isRealDirectionOverride(function: KSFunctionDeclaration): Boolean {
        if (function.parameters.size != 1) return false
        if (Modifier.OVERRIDE in function.modifiers) return true
        val baseFunction = baseDirectionFunctions[function.simpleName.asString()] ?: return false
        return resolver.overrides(function, baseFunction)
    }

    /** Reads the `reason` argument of @UnsupportedDirection on [function], or null when absent. */
    private fun unsupportedReasonOf(function: KSFunctionDeclaration): String? = function.annotations
        .firstOrNull {
            // shortName fast-path first (FieldAnalyzer's idiom): the costly resolve() only
            // runs for annotations whose short name does not already match.
            it.shortName.asString() == "UnsupportedDirection" ||
                it.annotationType.resolve().declaration.qualifiedName?.asString() == unsupportedDirectionFqn
        }?.arguments
        ?.firstOrNull { it.name?.asString() == "reason" }
        ?.value as? String

    /**
     * Returns the (S-FQN, T-FQN) pair of [declaration]'s `MapTypeConverter<S, T>` ancestry,
     * walking up the superclass chain, or null when the declaration never reaches the base
     * (or binds it through an out-of-scope generic passthrough — diagnostic via [logger]).
     * Public so @KMapperConfig discovery shares the exact same pair resolution.
     */
    fun typePairOf(declaration: KSClassDeclaration): Pair<String, String>? {
        val chainToBase = superclassChainToConverterBase(declaration) ?: return null
        return concreteTypeArguments(chainToBase.baseReference, declaration)
    }

    /**
     * The superclass chain from the converter declaration (inclusive, most-derived first) up
     * to — but excluding — MapTypeConverter, plus the resolved `MapTypeConverter<S, T>`
     * supertype reference found at the chain's end.
     */
    private data class ChainToConverterBase(
        val chain: List<KSClassDeclaration>,
        val baseReference: KSType,
    )

    /**
     * Walks the superclass chain (classes only — Kotlin has single class inheritance, so the
     * walk is linear; interfaces can never reach the abstract base) until MapTypeConverter is
     * found. A visited set guards against malformed cyclic hierarchies in broken sources.
     * Returns null when the chain ends (kotlin.Any) without meeting MapTypeConverter.
     */
    private fun superclassChainToConverterBase(declaration: KSClassDeclaration): ChainToConverterBase? {
        val chain = mutableListOf<KSClassDeclaration>()
        val visitedFqns = mutableSetOf<String>()
        var current = declaration
        while (true) {
            val currentFqn = current.qualifiedName?.asString() ?: return null
            if (!visitedFqns.add(currentFqn)) return null
            chain.add(current)
            var nextSuperclass: KSClassDeclaration? = null
            for (supertype in current.superTypes) {
                val resolved = supertype.resolve()
                val superDeclaration = resolved.declaration as? KSClassDeclaration ?: continue
                if (superDeclaration.classKind != ClassKind.CLASS) continue
                if (superDeclaration.qualifiedName?.asString() == converterBaseFqn) {
                    return ChainToConverterBase(chain, resolved)
                }
                nextSuperclass = superDeclaration
            }
            current = nextSuperclass ?: return null
        }
    }

    /**
     * Extracts the concrete (S-FQN, T-FQN) from a resolved `MapTypeConverter<S, T>` supertype
     * reference. When the intermediate base binds S/T to its OWN type parameters (generic
     * passthrough, e.g. `Base<X> : MapTypeConverter<X, String>`) the pair cannot be resolved
     * without substitution machinery — out of scope, reported as a guided compile error.
     */
    private fun concreteTypeArguments(
        baseReference: KSType,
        declaration: KSClassDeclaration,
    ): Pair<String, String>? {
        val sourceType = baseReference.arguments.getOrNull(0)?.type?.resolve()
        val targetType = baseReference.arguments.getOrNull(1)?.type?.resolve()
        if (sourceType?.declaration is KSTypeParameter || targetType?.declaration is KSTypeParameter) {
            logger?.error(
                "${declaration.qualifiedName?.asString()}: reaches MapTypeConverter through a " +
                    "generic-passthrough base (its S/T are bound to the base's own type parameters). " +
                    "Bind the pair with concrete types in the base instead — e.g. " +
                    "`abstract class FormattedDoubleStringConverter(digits: Int) : " +
                    "MapTypeConverter<Double, String>(Double::class, String::class)` — " +
                    "generic-passthrough bases are not supported.",
            )
            return null
        }
        val sourceFqn = sourceType?.declaration?.qualifiedName?.asString() ?: return null
        val targetFqn = targetType?.declaration?.qualifiedName?.asString() ?: return null
        return sourceFqn to targetFqn
    }
}
