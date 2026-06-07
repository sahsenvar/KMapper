package com.sahsenvar.kmapper.processor.analyzer

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType

private const val COLLECTION_WRAPPER_ANNOTATION = "com.sahsenvar.kmapper.annotations.CollectionWrapper"
private const val KMAPPER_CONFIG_ANNOTATION = "com.sahsenvar.kmapper.annotations.KMapperConfig"

/**
 * Reads all @KMapperConfig.wrappers listings (in-module, works on KMP/iOS) and resolves
 * each wrapper object's @CollectionWrapper.forType to build Map<forTypeFqn, wrapperObjectFqn>.
 *
 * Design:
 *   - No cross-module symbol enumeration (getDeclarationsFromPackage / getSymbolsWithAnnotation
 *     for dependency packages) — only the consumer's own @KMapperConfig is read (in-module).
 *   - For each KClass listed in @KMapperConfig.wrappers, resolve its @CollectionWrapper.forType
 *     by reading the annotation on the resolved dependency class. This is standard
 *     dependency annotation resolution, which works cross-module in KSP2.
 *   - Duplicate forType (same collection type listed via two wrapper objects) → logger.error
 *     (causes COMPILATION_ERROR).
 *
 * This replaces the old descriptor-generation + getDeclarationsFromPackage machinery which
 * failed in KSP2's per-module kspCommonMainMetadata invocations.
 */
fun discoverWrappersFromConfig(
    resolver: Resolver,
    logger: KSPLogger,
): Map<String, String> {
    // forTypeFqn → wrapperObjectFqn; accumulate all first, then check duplicates
    val entries = mutableListOf<Pair<String, String>>()

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

                entries.add(forTypeFqn to wrapperObjectFqn)
            }
        }

    // Detect duplicate forType registrations — same collection type mapped by two wrappers
    val seenForTypes = mutableMapOf<String, String>() // forTypeFqn → first wrapperObjectFqn
    val result = mutableMapOf<String, String>()
    for ((forTypeFqn, wrapperObjectFqn) in entries) {
        val existing = seenForTypes[forTypeFqn]
        if (existing != null) {
            logger.error(
                "Duplicate @CollectionWrapper for forType=$forTypeFqn: " +
                    "both $existing and $wrapperObjectFqn are registered. " +
                    "Remove one from @KMapperConfig.wrappers.",
            )
        } else {
            seenForTypes[forTypeFqn] = wrapperObjectFqn
            result[forTypeFqn] = wrapperObjectFqn
        }
    }

    return result
}
