package com.sahsenvar.kmapper.processor.analyzer

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.Variance
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.ksp.toClassName

/**
 * One `wrap` overload of a return wrapper, parsed from its signature
 * `fun <S, T> wrap(source: R<S>, map: (S) -> T): W<T>`.
 *
 * [receiverShape] / [returnShape] are the unsubstituted `R<S>` / `W<T>`; [typeParameterOrder] is
 * the declared type-parameter order, so the call site can pass explicit type arguments (which
 * also disambiguates overloads such as `wrap(S)` vs `wrap(Flow<S>)`).
 */
data class WrapFunction(
    val receiverShape: KSType,
    val returnShape: KSType,
    val sourceTypeParameter: String,
    val targetTypeParameter: String,
    val typeParameterOrder: List<String>,
)

/** A resolved return wrapper: the object to call, its suffix and its `wrap` overloads. */
data class ReturnWrapper(
    val wrapperClassName: ClassName,
    val suffix: String,
    val wrapFunctions: List<WrapFunction>,
)

/**
 * Resolves the `wrapper = …` argument of `@MapTo` / `@MapFrom` into a [ReturnWrapper]
 * (null = no wrapper, only the plain `toX()` core).
 *
 * `KMapperWrapper.Default` defers to the module-wide KSP option [WRAPPER_OPTION] (set by the
 * Gradle `KMapper { wrapper = … }` extension), which itself defaults to `KMapperWrapper.None`.
 * Built-in and user wrappers are resolved identically — from their `@WrapperSuffix` and `wrap`
 * signatures — so a user wrapper has exactly the power of the built-ins.
 */
class ReturnWrapperResolver(
    private val resolver: Resolver,
    private val logger: KSPLogger,
    private val moduleWideOption: String?,
) {
    companion object {
        const val WRAPPER_OPTION = "kmapper.wrapper"
        private const val WRAPPER_INTERFACE = "com.sahsenvar.kmapper.KMapperWrapper"
        private const val DEFAULT_WRAPPER = "$WRAPPER_INTERFACE.Default"
        private const val NONE_WRAPPER = "$WRAPPER_INTERFACE.None"
        private const val WRAP_FUNCTION_NAME = "wrap"
        private val BUILT_IN_SHORT_NAMES = setOf("Default", "None", "KtResult", "Flow")
    }

    private val cache = mutableMapOf<String, ReturnWrapper?>()

    /** Resolves the wrapper named by [annotation]'s `wrapper` argument, reporting errors on [anchor]. */
    fun resolve(
        annotation: KSAnnotation,
        anchor: KSNode,
    ): ReturnWrapper? {
        val declaredType = annotation.arguments.firstOrNull { it.name?.asString() == "wrapper" }?.value as? KSType
        val declaredFqn = declaredType?.declaration?.qualifiedName?.asString() ?: DEFAULT_WRAPPER
        val effectiveFqn = if (declaredFqn == DEFAULT_WRAPPER) moduleWideWrapperFqn() else declaredFqn
        return resolveFqn(effectiveFqn, anchor)
    }

    /**
     * The module-wide wrapper: a built-in short name (`None`, `KtResult`, `Flow`) or the fully
     * qualified name of a user wrapper object. Unset (or `Default`) → `None`.
     */
    private fun moduleWideWrapperFqn(): String {
        val option = moduleWideOption?.trim().orEmpty()
        return when {
            option.isEmpty() || option == "Default" || option == DEFAULT_WRAPPER -> NONE_WRAPPER
            option in BUILT_IN_SHORT_NAMES -> "$WRAPPER_INTERFACE.$option"
            else -> option
        }
    }

    private fun resolveFqn(
        wrapperFqn: String,
        anchor: KSNode,
    ): ReturnWrapper? {
        if (wrapperFqn == NONE_WRAPPER) return null
        if (wrapperFqn in cache) return cache[wrapperFqn]

        val wrapperDeclaration = resolver.getClassDeclarationByName(resolver.getKSNameFromString(wrapperFqn))
        val resolved =
            if (wrapperDeclaration == null) {
                logger.error(
                    "KMapper wrapper '$wrapperFqn' (from the '$WRAPPER_OPTION' option) cannot be resolved. " +
                        "Use None, KtResult, Flow, or the fully qualified name of an object implementing KMapperWrapper.",
                    anchor,
                )
                null
            } else {
                parseWrapper(wrapperDeclaration)
            }
        cache[wrapperFqn] = resolved
        return resolved
    }

    private fun parseWrapper(wrapperDeclaration: KSClassDeclaration): ReturnWrapper? {
        val wrapperFqn = wrapperDeclaration.qualifiedName?.asString() ?: return null
        if (wrapperDeclaration.classKind != ClassKind.OBJECT) {
            logger.error("KMapper wrapper $wrapperFqn must be an object (its wrap functions are called statically).", wrapperDeclaration)
            return null
        }

        val suffix =
            wrapperDeclaration.annotations
                .firstOrNull { it.shortName.asString() == "WrapperSuffix" }
                ?.arguments
                ?.firstOrNull()
                ?.value as? String
        if (suffix.isNullOrBlank()) {
            logger.error(
                "KMapper wrapper $wrapperFqn needs a non-blank @WrapperSuffix (e.g. @WrapperSuffix(\"Result\") → toXResult()).",
                wrapperDeclaration,
            )
            return null
        }

        val candidates =
            wrapperDeclaration
                .getDeclaredFunctions()
                .filter { it.simpleName.asString() == WRAP_FUNCTION_NAME && it.isPublic() }
                .toList()
        if (candidates.isEmpty()) {
            logger.error(
                "KMapper wrapper $wrapperFqn declares no public `wrap` function. " +
                    "Expected: fun <S, T> wrap(source: R<S>, map: (S) -> T): W<T>",
                wrapperDeclaration,
            )
            return null
        }

        val wrapFunctions = candidates.map { parseWrapFunction(wrapperFqn, it) ?: return null }
        return ReturnWrapper(wrapperDeclaration.toClassName(), suffix, wrapFunctions)
    }

    private fun parseWrapFunction(
        wrapperFqn: String,
        function: KSFunctionDeclaration,
    ): WrapFunction? {
        fun reject(): WrapFunction? {
            logger.error(
                "KMapper wrapper $wrapperFqn has a `wrap` function with an unsupported shape. " +
                    "Expected exactly: fun <S, T> wrap(source: R<S>, map: (S) -> T): W<T>",
                function,
            )
            return null
        }

        if (function.extensionReceiver != null || function.parameters.size != 2 || function.typeParameters.size != 2) {
            return reject()
        }
        val lambdaType = function.parameters[1].type.resolve()
        val lambdaDeclaration = lambdaType.declaration.qualifiedName?.asString()
        if (lambdaDeclaration != "kotlin.Function1" || lambdaType.arguments.size != 2) return reject()

        val sourceTypeParameter = lambdaType.arguments[0].type?.resolve()?.declaration as? KSTypeParameter
        val targetTypeParameter = lambdaType.arguments[1].type?.resolve()?.declaration as? KSTypeParameter
        if (sourceTypeParameter == null || targetTypeParameter == null || sourceTypeParameter == targetTypeParameter) {
            return reject()
        }

        val returnShape = function.returnType?.resolve() ?: return reject()
        val receiverShape = function.parameters[0].type.resolve()
        // The receiver must carry the source (S, Flow<S>, List<S>, …) — otherwise the generated
        // extension would have no way to hand the mapper its input.
        if (!receiverShape.mentionsTypeParameter(sourceTypeParameter.name.asString())) return reject()
        return WrapFunction(
            receiverShape = receiverShape,
            returnShape = returnShape,
            sourceTypeParameter = sourceTypeParameter.name.asString(),
            targetTypeParameter = targetTypeParameter.name.asString(),
            typeParameterOrder = function.typeParameters.map { it.name.asString() },
        )
    }
}

private fun KSType.mentionsTypeParameter(typeParameterName: String): Boolean {
    val declaration = declaration
    if (declaration is KSTypeParameter) return declaration.name.asString() == typeParameterName
    return arguments.any { it.type?.resolve()?.mentionsTypeParameter(typeParameterName) == true }
}

/**
 * Converts [this] wrap-signature type into a KotlinPoet [TypeName], replacing type parameters
 * by [bindings] (`S` → the mapper's source, `T` → its target). Nullability and use-site
 * variance are preserved.
 */
fun KSType.substituteTypeParameters(bindings: Map<String, TypeName>): TypeName {
    val declaration = declaration
    if (declaration is KSTypeParameter) {
        val bound =
            bindings[declaration.name.asString()]
                ?: error("Unbound type parameter ${declaration.name.asString()} in a wrap signature")
        return if (isMarkedNullable) bound.copy(nullable = true) else bound
    }
    val packageName = declaration.packageName.asString()
    val qualifiedName = declaration.qualifiedName?.asString() ?: error("Unnamed type in a wrap signature")
    val simpleNames = qualifiedName.removePrefix("$packageName.").split('.')
    val rawType = ClassName(packageName, simpleNames)
    val typeArguments =
        arguments.map { argument ->
            when (argument.variance) {
                Variance.STAR -> STAR
                Variance.COVARIANT -> WildcardTypeName.producerOf(argument.type!!.resolve().substituteTypeParameters(bindings))
                Variance.CONTRAVARIANT -> WildcardTypeName.consumerOf(argument.type!!.resolve().substituteTypeParameters(bindings))
                Variance.INVARIANT -> argument.type!!.resolve().substituteTypeParameters(bindings)
            }
        }
    val typeName = if (typeArguments.isEmpty()) rawType else rawType.parameterizedBy(typeArguments)
    return if (isMarkedNullable) typeName.copy(nullable = true) else typeName
}
