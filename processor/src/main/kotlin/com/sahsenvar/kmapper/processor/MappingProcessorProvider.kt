package com.sahsenvar.kmapper.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * Provider for MappingProcessor.
 */
class MappingProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = MappingProcessor(
        codeGenerator = environment.codeGenerator,
        logger = environment.logger,
    )
}
