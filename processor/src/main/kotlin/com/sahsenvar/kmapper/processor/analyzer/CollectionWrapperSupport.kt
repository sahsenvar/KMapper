@file:OptIn(com.google.devtools.ksp.KspExperimental::class)

package com.sahsenvar.kmapper.processor.analyzer

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType

private const val COLLECTION_WRAPPER_ANNOTATION = "com.sahsenvar.kmapper.annotations.CollectionWrapper"
private const val COLLECTION_WRAPPER_DESCRIPTOR_ANNOTATION = "com.sahsenvar.kmapper.annotations.CollectionWrapperDescriptor"
private const val GENERATED_PACKAGE = "com.sahsenvar.kmapper.generated"

/**
 * Handles @CollectionWrapper descriptor generation (round N) and discovery (round N+1).
 *
 * Round-invariant: once a descriptor object is emitted we track its name to avoid re-emitting
 * on subsequent KSP rounds (which would cause duplicate-class errors).
 */
class CollectionWrapperSupport(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) {
    /** Names of descriptor objects already generated in this processor run. */
    private val emittedDescriptors = mutableSetOf<String>()

    /**
     * Step 1 — Generate descriptor objects for all @CollectionWrapper functions in [resolver].
     * Called every round; guarded by [emittedDescriptors] to prevent duplicates.
     *
     * Returns the number of new descriptors generated this round (0 in later rounds).
     */
    fun generateDescriptors(resolver: Resolver): Int {
        val functions = resolver
            .getSymbolsWithAnnotation(COLLECTION_WRAPPER_ANNOTATION)
            .filterIsInstance<KSFunctionDeclaration>()
            .toList()

        if (functions.isEmpty()) return 0

        var generated = 0
        for (fn in functions) {
            val forTypeArg = fn.annotations
                .first { it.shortName.asString() == "CollectionWrapper" }
                .arguments
                .firstOrNull { it.name?.asString() == "forType" }
                ?.value

            val forTypeFqn = (forTypeArg as? KSType)
                ?.declaration?.qualifiedName?.asString()
                ?: continue

            val fnPackage = fn.packageName.asString()
            val fnSimple = fn.simpleName.asString()
            val wrapFqn = if (fnPackage.isBlank()) fnSimple else "$fnPackage.$fnSimple"

            // Sanitize to make a valid Kotlin identifier for the object name
            val sanitized = wrapFqn.replace('.', '_').replace('<', '_').replace('>', '_')
            val objectName = "KmapWrapper_$sanitized"

            if (objectName in emittedDescriptors) continue

            try {
                val file = codeGenerator.createNewFile(
                    dependencies = Dependencies.ALL_FILES,
                    packageName = GENERATED_PACKAGE,
                    fileName = objectName
                )
                file.bufferedWriter().use { w ->
                    w.write(
                        """
                        |package $GENERATED_PACKAGE
                        |
                        |import com.sahsenvar.kmapper.annotations.CollectionWrapperDescriptor
                        |
                        |@CollectionWrapperDescriptor(
                        |    forType = "$forTypeFqn",
                        |    wrapFunction = "$wrapFqn"
                        |)
                        |public object $objectName
                        """.trimMargin()
                    )
                }
                emittedDescriptors.add(objectName)
                generated++
                logger.info("Generated CollectionWrapper descriptor: $objectName (forType=$forTypeFqn, wrap=$wrapFqn)")
            } catch (e: Exception) {
                // File may already exist in a prior round of the same processor run.
                // The emittedDescriptors set is the primary guard; this is a safety catch.
                emittedDescriptors.add(objectName)
                logger.info("CollectionWrapper descriptor $objectName already exists (round guard): ${e.message}")
            }
        }
        return generated
    }

    /**
     * Step 2 — Discover all @CollectionWrapperDescriptor objects (from generated package + dependencies).
     * Called every round after [generateDescriptors].
     *
     * Returns a Map of forTypeFqn → wrapFunctionFqn.
     * Logs an error (causing COMPILATION_ERROR) if the same forType is registered by multiple wrappers.
     */
    fun discoverWrappers(resolver: Resolver): Map<String, String> {
        val declarations = resolver.getDeclarationsFromPackage(GENERATED_PACKAGE)

        val entries = mutableListOf<Pair<String, String>>() // (forType, wrapFqn)

        for (decl in declarations) {
            val descriptorAnnotation = decl.annotations.firstOrNull {
                it.shortName.asString() == "CollectionWrapperDescriptor"
            } ?: continue

            val forType = descriptorAnnotation.arguments
                .firstOrNull { it.name?.asString() == "forType" }
                ?.value as? String ?: continue
            val wrapFunction = descriptorAnnotation.arguments
                .firstOrNull { it.name?.asString() == "wrapFunction" }
                ?.value as? String ?: continue

            entries.add(forType to wrapFunction)
        }

        // Check for duplicates
        val result = mutableMapOf<String, String>()
        val seen = mutableMapOf<String, String>() // forType -> first wrapFqn

        for ((forType, wrapFqn) in entries) {
            if (forType in seen) {
                logger.error(
                    "multiple @CollectionWrapper for the same forType '$forType': " +
                        "'${seen[forType]}' and '$wrapFqn'. Remove one."
                )
                // Don't add to result — the error will abort compilation.
            } else {
                seen[forType] = wrapFqn
                result[forType] = wrapFqn
            }
        }

        return result
    }
}
