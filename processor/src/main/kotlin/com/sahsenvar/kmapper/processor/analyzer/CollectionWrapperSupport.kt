package com.sahsenvar.kmapper.processor.analyzer

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType

private const val COLLECTION_WRAPPER_ANNOTATION = "com.sahsenvar.kmapper.annotations.CollectionWrapper"
private const val KMAPPER_CONFIG_ANNOTATION = "com.sahsenvar.kmapper.annotations.KMapperConfig"

/**
 * Reads all @KMapperConfig.wrappers listings (in-module, works on KMP/iOS), resolves each
 * wrapper object's @CollectionWrapper.forType, and validates the duck-typed wrap/unwrap
 * contract via [CollectionWrapperValidator] to build Map<forTypeFqn, descriptor>.
 *
 * Design:
 *   - No cross-module symbol enumeration (getDeclarationsFromPackage / getSymbolsWithAnnotation
 *     for dependency packages) — only the consumer's own @KMapperConfig is read (in-module).
 *   - For each KClass listed in @KMapperConfig.wrappers, resolve its @CollectionWrapper.forType
 *     by reading the annotation on the resolved dependency class. This is standard
 *     dependency annotation resolution, which works cross-module in KSP2.
 *   - Signature validation: wrap/unwrap shapes are checked against forType; a bad shape or a
 *     wrapper with neither direction → logger.error (causes COMPILATION_ERROR).
 *   - Duplicate forType (same collection type listed via two wrapper objects) → logger.error
 *     (causes COMPILATION_ERROR).
 *
 * This replaces the old descriptor-generation + getDeclarationsFromPackage machinery which
 * failed in KSP2's per-module kspCommonMainMetadata invocations.
 */
fun discoverWrappersFromConfig(
    resolver: Resolver,
    logger: KSPLogger,
): Map<String, CollectionWrapperDescriptor> {
    val validator = CollectionWrapperValidator(logger)

    // Accumulate all validated descriptors first, then check duplicates
    val entries = mutableListOf<CollectionWrapperDescriptor>()

    resolver
        .getSymbolsWithAnnotation(KMAPPER_CONFIG_ANNOTATION)
        .filterIsInstance<KSClassDeclaration>()
        .forEach { cfg ->
            val annotation =
                cfg.annotations.firstOrNull {
                    it.shortName.asString() == "KMapperConfig"
                } ?: return@forEach

            val wrappersArg =
                annotation.arguments.firstOrNull {
                    it.name?.asString() == "wrappers"
                } ?: return@forEach

            @Suppress("UNCHECKED_CAST")
            val wrapperTypes = wrappersArg.value as? List<KSType> ?: return@forEach

            for (wrapperType in wrapperTypes) {
                val wrapperDecl = wrapperType.declaration as? KSClassDeclaration ?: continue
                val wrapperObjectFqn = wrapperDecl.qualifiedName?.asString() ?: continue

                // Read @CollectionWrapper.forType from the wrapper class declaration.
                // This is a dependency annotation-resolution read — works cross-module in KSP2.
                val collectionWrapperAnnotation =
                    wrapperDecl.annotations.firstOrNull {
                        it.shortName.asString() == "CollectionWrapper"
                    }

                if (collectionWrapperAnnotation == null) {
                    logger.error(
                        "Class $wrapperObjectFqn listed in @KMapperConfig.wrappers " +
                            "must be annotated with @CollectionWrapper(forType = ...)",
                        cfg,
                    )
                    continue
                }

                val forTypeArg =
                    collectionWrapperAnnotation.arguments
                        .firstOrNull {
                            it.name?.asString() == "forType"
                        }?.value

                val forTypeFqn =
                    (forTypeArg as? KSType)
                        ?.declaration
                        ?.qualifiedName
                        ?.asString()

                if (forTypeFqn == null) {
                    logger.error(
                        "Could not resolve @CollectionWrapper.forType on $wrapperObjectFqn",
                        wrapperDecl,
                    )
                    continue
                }

                val descriptor = validator.validate(wrapperDecl, forTypeFqn) ?: continue
                entries.add(descriptor)
            }
        }

    // Detect duplicate forType registrations — same collection type mapped by two wrappers
    val result = mutableMapOf<String, CollectionWrapperDescriptor>()
    for (descriptor in entries) {
        val existing = result[descriptor.forTypeFqn]
        if (existing != null) {
            logger.error(
                "Duplicate @CollectionWrapper for forType=${descriptor.forTypeFqn}: " +
                    "both ${existing.wrapperObjectFqn} and ${descriptor.wrapperObjectFqn} are registered. " +
                    "Remove one from @KMapperConfig.wrappers.",
            )
        } else {
            result[descriptor.forTypeFqn] = descriptor
        }
    }

    return result
}
