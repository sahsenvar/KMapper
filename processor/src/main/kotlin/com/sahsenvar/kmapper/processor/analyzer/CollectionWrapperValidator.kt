package com.sahsenvar.kmapper.processor.analyzer

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

/**
 * A validated @CollectionWrapper registration: which container type it serves and which
 * directions it provides. TypeMatcher consults the direction flags so a mapping that needs
 * a missing direction gets a guided compile error (the wrapper counterpart of
 * UnsupportedConversion).
 */
data class CollectionWrapperDescriptor(
    /** FQN of the wrapper object (e.g. com.example.PersistentListWrapper). */
    val wrapperObjectFqn: String,
    /** FQN of the wrapped container type from @CollectionWrapper.forType. */
    val forTypeFqn: String,
    /** True when a valid `fun <T> wrap(source: List<T>): ForType<T>` is declared. */
    val providesWrap: Boolean,
    /** True when a valid `fun <T> unwrap(source: ForType<T>): List<T>` is declared. */
    val providesUnwrap: Boolean,
) {
    /** Simple name of the wrapper object for error messages. */
    val wrapperSimpleName: String get() = wrapperObjectFqn.substringAfterLast(".")

    /** Simple name of the wrapped container type for error messages. */
    val forTypeSimpleName: String get() = forTypeFqn.substringAfterLast(".")
}

/**
 * Validates a @CollectionWrapper object's duck-typed contract against its `forType`.
 * Kotlin has no higher-kinded types, so the shapes are conventions — compile-checked here:
 *
 * ```
 * fun <T> wrap(source: List<T>): ForType<T>
 * fun <T> unwrap(source: ForType<T>): List<T>
 * ```
 *
 * Both directions are optional, at least one is required. A function named wrap/unwrap with
 * the wrong shape (parameter/return container, parameter count, or type-parameter count) is
 * a compile error naming the expected signature — never silently skipped.
 */
class CollectionWrapperValidator(
    private val logger: KSPLogger,
) {
    private companion object {
        const val LIST_FQN = "kotlin.collections.List"
    }

    /**
     * Returns the validated descriptor, or null when the wrapper violates the contract
     * (errors already logged — compilation fails).
     */
    fun validate(
        wrapperDeclaration: KSClassDeclaration,
        forTypeFqn: String,
    ): CollectionWrapperDescriptor? {
        val wrapperObjectFqn = wrapperDeclaration.qualifiedName?.asString() ?: return null
        val forTypeSimpleName = forTypeFqn.substringAfterLast(".")
        val declaredFunctions = wrapperDeclaration.getDeclaredFunctions().toList()
        val wrapFunctions = declaredFunctions.filter { it.simpleName.asString() == "wrap" }
        val unwrapFunctions = declaredFunctions.filter { it.simpleName.asString() == "unwrap" }

        if (wrapFunctions.isEmpty() && unwrapFunctions.isEmpty()) {
            logger.error(
                "@CollectionWrapper object $wrapperObjectFqn declares neither wrap nor unwrap; " +
                    "at least one direction is required: " +
                    "fun <T> wrap(source: List<T>): $forTypeSimpleName<T> and/or " +
                    "fun <T> unwrap(source: $forTypeSimpleName<T>): List<T>",
                wrapperDeclaration,
            )
            return null
        }

        var shapesValid = true
        val providesWrap =
            wrapFunctions.any { function ->
                val matches = matchesShape(function, parameterFqn = LIST_FQN, returnFqn = forTypeFqn)
                if (!matches) {
                    logger.error(
                        "$wrapperObjectFqn.wrap has the wrong shape — expected " +
                            "fun <T> wrap(source: List<T>): $forTypeSimpleName<T>",
                        function,
                    )
                    shapesValid = false
                }
                matches
            }
        val providesUnwrap =
            unwrapFunctions.any { function ->
                val matches = matchesShape(function, parameterFqn = forTypeFqn, returnFqn = LIST_FQN)
                if (!matches) {
                    logger.error(
                        "$wrapperObjectFqn.unwrap has the wrong shape — expected " +
                            "fun <T> unwrap(source: $forTypeSimpleName<T>): List<T>",
                        function,
                    )
                    shapesValid = false
                }
                matches
            }
        if (!shapesValid) return null

        return CollectionWrapperDescriptor(
            wrapperObjectFqn = wrapperObjectFqn,
            forTypeFqn = forTypeFqn,
            providesWrap = providesWrap,
            providesUnwrap = providesUnwrap,
        )
    }

    /**
     * One type parameter, one value parameter whose container declaration is [parameterFqn],
     * and a return container declaration of [returnFqn]. The element type argument is the
     * function's own type parameter by construction (single type parameter + generic
     * container shapes); declaration-level FQN checks keep the validation KSP-cheap.
     */
    private fun matchesShape(
        function: KSFunctionDeclaration,
        parameterFqn: String,
        returnFqn: String,
    ): Boolean {
        if (function.typeParameters.size != 1) return false
        val parameter = function.parameters.singleOrNull() ?: return false
        val parameterDeclarationFqn =
            parameter.type
                .resolve()
                .declaration.qualifiedName
                ?.asString()
        if (parameterDeclarationFqn != parameterFqn) return false
        val returnDeclarationFqn =
            function.returnType
                ?.resolve()
                ?.declaration
                ?.qualifiedName
                ?.asString()
        return returnDeclarationFqn == returnFqn
    }
}
