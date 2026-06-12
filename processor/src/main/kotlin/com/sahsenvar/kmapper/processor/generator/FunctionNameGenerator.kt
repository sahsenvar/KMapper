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
     * Generate mapper function name: "to{TargetName}Result" — the `Result` suffix is part of
     * the boundary contract (`fun Source.toXResult(): Result<X>`); nested-mapper call sites
     * (TypeMatcher's strategy construction) follow the same naming atomically.
     */
    fun generateMapperFunctionName(targetClass: KSClassDeclaration): String = "to${targetClass.simpleName.asString()}Result"

    /**
     * Generate file name: "{SourceClass}Mappers"
     */
    fun generateFileName(sourceClass: KSClassDeclaration): String = "${sourceClass.simpleName.asString()}Mappers"
}
