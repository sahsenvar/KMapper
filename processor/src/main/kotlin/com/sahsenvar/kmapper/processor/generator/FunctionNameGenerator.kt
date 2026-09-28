package com.sahsenvar.kmapper.processor.generator

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration

/**
 * Generates function names for mapping operations.
 */
class FunctionNameGenerator(
    private val logger: KSPLogger,
) {
    /**
     * Generate the plain core mapper name: "to{TargetName}" (`fun Source.toX(): X`). Nested-mapper
     * call sites (TypeMatcher's strategy construction) follow the same naming atomically; return
     * wrappers append their `@WrapperSuffix` to it (see [generateWrapperFunctionName]).
     */
    fun generateMapperFunctionName(targetClass: KSClassDeclaration): String = "to${targetClass.simpleName.asString()}"

    /** Generate a wrapper function name: "to{TargetName}{suffix}", e.g. `toXResult`. */
    fun generateWrapperFunctionName(
        targetClass: KSClassDeclaration,
        suffix: String,
    ): String = generateMapperFunctionName(targetClass) + suffix

    /**
     * Generate file name: "{SourceClass}Mappers"
     */
    fun generateFileName(sourceClass: KSClassDeclaration): String = "${sourceClass.simpleName.asString()}Mappers"
}
