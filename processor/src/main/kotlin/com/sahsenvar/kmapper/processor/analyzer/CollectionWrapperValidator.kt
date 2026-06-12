package com.sahsenvar.kmapper.processor.analyzer

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter

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

        // Partition-then-policy: every same-name overload is individually validated — a
        // matching overload provides the direction, every non-matching one is reported
        // (never silently skipped), independent of declaration order.
        val providesWrap =
            validateDirectionCandidates(
                candidates = wrapFunctions,
                parameterFqn = LIST_FQN,
                returnFqn = forTypeFqn,
                expectedSignature = "fun <T> wrap(source: List<T>): $forTypeSimpleName<T>",
                wrapperObjectFqn = wrapperObjectFqn,
                directionName = "wrap",
            )
        val providesUnwrap =
            validateDirectionCandidates(
                candidates = unwrapFunctions,
                parameterFqn = forTypeFqn,
                returnFqn = LIST_FQN,
                expectedSignature = "fun <T> unwrap(source: $forTypeSimpleName<T>): List<T>",
                wrapperObjectFqn = wrapperObjectFqn,
                directionName = "unwrap",
            )

        // No valid direction at all → no usable descriptor (errors above already failed the
        // build). A valid direction next to reported bad overloads KEEPS the descriptor so
        // downstream resolution stays coherent (no cascading missing-direction errors).
        if (!providesWrap && !providesUnwrap) return null

        return CollectionWrapperDescriptor(
            wrapperObjectFqn = wrapperObjectFqn,
            forTypeFqn = forTypeFqn,
            providesWrap = providesWrap,
            providesUnwrap = providesUnwrap,
        )
    }

    /**
     * Validates all same-name [candidates] for one direction: each candidate either matches
     * the exact contract shape or is reported as a guided error quoting [expectedSignature].
     * Returns true when at least one candidate matches (the direction is provided).
     */
    private fun validateDirectionCandidates(
        candidates: List<KSFunctionDeclaration>,
        parameterFqn: String,
        returnFqn: String,
        expectedSignature: String,
        wrapperObjectFqn: String,
        directionName: String,
    ): Boolean {
        val (matchingCandidates, mismatchedCandidates) =
            candidates.partition { matchesShape(it, parameterFqn = parameterFqn, returnFqn = returnFqn) }
        for (mismatched in mismatchedCandidates) {
            logger.error(
                "$wrapperObjectFqn.$directionName has the wrong shape — expected $expectedSignature",
                mismatched,
            )
        }
        return matchingCandidates.isNotEmpty()
    }

    /**
     * One type parameter, one value parameter whose container declaration is [parameterFqn],
     * and a return container declaration of [returnFqn]. Type-argument LINKAGE is enforced:
     * the parameter container's element argument AND the return container's element argument
     * must BOTH be the function's own type parameter — `fun <T> wrap(source: List<String>):
     * ForType<Int>` matches the container declarations but is NOT the contract shape.
     * Declaration-level comparisons keep the validation KSP-cheap (no full type equality).
     */
    private fun matchesShape(
        function: KSFunctionDeclaration,
        parameterFqn: String,
        returnFqn: String,
    ): Boolean {
        val ownTypeParameter = function.typeParameters.singleOrNull() ?: return false
        val parameter = function.parameters.singleOrNull() ?: return false
        val parameterType = parameter.type.resolve()
        if (parameterType.declaration.qualifiedName?.asString() != parameterFqn) return false
        val returnType = function.returnType?.resolve() ?: return false
        if (returnType.declaration.qualifiedName?.asString() != returnFqn) return false
        return elementArgumentIsTypeParameter(parameterType, ownTypeParameter, function) &&
            elementArgumentIsTypeParameter(returnType, ownTypeParameter, function)
    }

    /**
     * True when [containerType]'s first type argument resolves to [ownTypeParameter] declared
     * on [function] — compared by declaration (name + owning function), not by FQN string,
     * because type parameters have no qualified name.
     */
    private fun elementArgumentIsTypeParameter(
        containerType: KSType,
        ownTypeParameter: KSTypeParameter,
        function: KSFunctionDeclaration,
    ): Boolean {
        val elementDeclaration =
            containerType.arguments
                .firstOrNull()
                ?.type
                ?.resolve()
                ?.declaration
        val elementTypeParameter = elementDeclaration as? KSTypeParameter ?: return false
        return elementTypeParameter.name.asString() == ownTypeParameter.name.asString() &&
            elementTypeParameter.parentDeclaration == function
    }
}
