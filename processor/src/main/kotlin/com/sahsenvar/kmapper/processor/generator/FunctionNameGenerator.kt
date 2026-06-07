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
     * Generate mapper function name: "toTargetName"
     */
    fun generateMapperFunctionName(targetClass: KSClassDeclaration): String = "to${targetClass.simpleName.asString()}"

    /**
     * Generate file name: "{SourceClass}Mappers"
     */
    fun generateFileName(sourceClass: KSClassDeclaration): String = "${sourceClass.simpleName.asString()}Mappers"
}
