package com.sahsenvar.kmapper.processor.analyzer

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

/**
 * Which directions a converter provides, and any declared @UnsupportedDirection reasons.
 *
 * A direction is PROVIDED iff it is declared (total or OrNull override) AND its total
 * method is not annotated `@UnsupportedDirection` — the annotation wins, because KSP
 * cannot inspect function bodies (documented contract: the stub body is `= unsupported()`).
 */
data class ConverterShape(
    /** FQN of the converter's source type S in MapTypeConverter<S, T>. */
    val sourceFqn: String,
    /** FQN of the converter's target type T in MapTypeConverter<S, T>. */
    val targetFqn: String,
    /** True when the S -> T direction (convertTo) is declared and not annotated unsupported. */
    val providesTo: Boolean,
    /** True when the T -> S direction (convertFrom) is declared and not annotated unsupported. */
    val providesFrom: Boolean,
    /** Reason from @UnsupportedDirection on convertTo, or null when not annotated. */
    val unsupportedToReason: String?,
    /** Reason from @UnsupportedDirection on convertFrom, or null when not annotated. */
    val unsupportedFromReason: String?,
    /** @UnsupportedDirection found on an OrNull variant — compile error at resolution. */
    val orNullAnnotated: Boolean,
)

/**
 * Reads a converter declaration's [ConverterShape] from the KSP [Resolver]:
 * its (S, T) type pair from the MapTypeConverter supertype and which directions it
 * provides, via function-level detection of the declared overrides and their
 * `@UnsupportedDirection` annotations.
 */
class ConverterIntrospector(
    private val resolver: Resolver,
) {
    private val converterBaseFqn = "com.sahsenvar.kmapper.converter.MapTypeConverter"
    private val unsupportedDirectionFqn = "com.sahsenvar.kmapper.converter.UnsupportedDirection"

    /**
     * Returns the [ConverterShape] for [converterFqn], or null when the declaration cannot
     * be resolved or does not extend MapTypeConverter<S, T> directly.
     */
    fun shapeOf(converterFqn: String): ConverterShape? {
        val declaration = resolver.getClassDeclarationByName(
            resolver.getKSNameFromString(converterFqn),
        ) ?: return null
        val (sourceFqn, targetFqn) = typeArgumentsOf(declaration) ?: return null

        // Function-level detection: a direction is PROVIDED iff declared AND its total method
        // is not annotated @UnsupportedDirection (the annotation wins — bodies are opaque to KSP).
        var declaredTo = false
        var declaredFrom = false
        var reasonTo: String? = null
        var reasonFrom: String? = null
        var orNullAnnotated = false
        declaration.getDeclaredFunctions().forEach { function ->
            val reason = unsupportedReasonOf(function)
            when (function.simpleName.asString()) {
                "convertTo" -> {
                    declaredTo = true
                    if (reason != null) reasonTo = reason
                }
                "convertFrom" -> {
                    declaredFrom = true
                    if (reason != null) reasonFrom = reason
                }
                "convertToOrNull" -> {
                    declaredTo = true
                    if (reason != null) orNullAnnotated = true
                }
                "convertFromOrNull" -> {
                    declaredFrom = true
                    if (reason != null) orNullAnnotated = true
                }
            }
        }
        return ConverterShape(
            sourceFqn = sourceFqn,
            targetFqn = targetFqn,
            providesTo = declaredTo && reasonTo == null,
            providesFrom = declaredFrom && reasonFrom == null,
            unsupportedToReason = reasonTo,
            unsupportedFromReason = reasonFrom,
            orNullAnnotated = orNullAnnotated,
        )
    }

    /** Reads the `reason` argument of @UnsupportedDirection on [function], or null when absent. */
    private fun unsupportedReasonOf(function: KSFunctionDeclaration): String? = function.annotations
        .firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == unsupportedDirectionFqn
        }?.arguments
        ?.firstOrNull { it.name?.asString() == "reason" }
        ?.value as? String

    /** Extracts (S-FQN, T-FQN) from the declaration's direct MapTypeConverter<S, T> supertype. */
    private fun typeArgumentsOf(declaration: KSClassDeclaration): Pair<String, String>? {
        for (supertype in declaration.superTypes) {
            val resolved = supertype.resolve()
            if (resolved.declaration.qualifiedName?.asString() != converterBaseFqn) continue
            val source = resolved.arguments
                .getOrNull(0)
                ?.type
                ?.resolve()
                ?.declaration
                ?.qualifiedName
                ?.asString()
            val target = resolved.arguments
                .getOrNull(1)
                ?.type
                ?.resolve()
                ?.declaration
                ?.qualifiedName
                ?.asString()
            if (source != null && target != null) return source to target
        }
        return null
    }
}
