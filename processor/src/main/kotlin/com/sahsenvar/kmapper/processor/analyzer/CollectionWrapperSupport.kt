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
 *
 * Cross-module strategy — JVM path (kspJvm):
 *   [generateDescriptors] reads @CollectionWrapper functions via getSymbolsWithAnnotation
 *   (BINARY-retention) and caches in-memory. [discoverWrappers] merges in-memory cache with
 *   getDeclarationsFromPackage results (from compiled .class files in dependency JARs) and
 *   getSymbolsWithAnnotation(@CollectionWrapperDescriptor) (from binary descriptor objects).
 *   This gives correct results in a single-invocation JVM KSP run.
 *
 * Cross-module strategy — KMP metadata path (kspCommonMainMetadata):
 *   KSP2 runs the processor in per-module invocations. The consumer module's invocation
 *   (invocation 3 for integration-test) has a resolver scope limited to its own sources;
 *   getDeclarationsFromPackage and getSymbolsWithAnnotation both return 0 for dependency-module
 *   packages. This is a KSP2 KMP limitation: cross-module symbol visibility in metadata mode.
 *   As a workaround, wrapper descriptor objects are committed as static source files in each
 *   converter module (not just KSP-generated), but even this does not fix the isolation;
 *   getDeclarationsFromPackage still returns 0 in the consumer invocation.
 *   Known consequence: kspCommonMainMetadata cannot generate correct KMP mappers for fields
 *   using collection wrappers from dependency modules. Use kspJvm for JVM-target consumers.
 */
class CollectionWrapperSupport(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) {
    /** Names of descriptor objects already generated in this processor run. */
    private val emittedDescriptors = mutableSetOf<String>()

    /**
     * In-memory cache of forTypeFqn → wrapFqn, built from @CollectionWrapper functions seen
     * via getSymbolsWithAnnotation (both local and binary-dependency functions).
     * Populated by generateDescriptors; read by discoverWrappers.
     */
    private val inMemoryWrappers = mutableMapOf<String, String>()

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

            // Always cache in-memory so discoverWrappers can return it immediately,
            // even before the generated descriptor file is compiled to a .class.
            inMemoryWrappers[forTypeFqn] = wrapFqn

            if (objectName in emittedDescriptors) continue

            // Skip generation if the descriptor already exists as a source declaration.
            // This handles the case where the descriptor was committed as a static source file
            // in the converter module (the canonical pattern for KSP2 KMP cross-module discovery).
            val alreadyDeclared = resolver
                .getDeclarationsFromPackage(GENERATED_PACKAGE)
                .any { it.simpleName.asString() == objectName }
            if (alreadyDeclared) {
                emittedDescriptors.add(objectName)
                logger.info("CollectionWrapper descriptor $objectName already declared as source; skipping generation.")
                continue
            }

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
     * Step 2 — Discover all @CollectionWrapper mappings.
     * Called every round after [generateDescriptors].
     *
     * Sources (merged, deduplicated):
     * 1. [inMemoryWrappers]: populated from @CollectionWrapper functions via getSymbolsWithAnnotation.
     *    Works immediately even before generated descriptor files are compiled (covers same-round
     *    generation from both local sources and binary-dependency functions).
     * 2. getDeclarationsFromPackage: covers @CollectionWrapperDescriptor binary objects from
     *    external artifacts that were NOT seen via getSymbolsWithAnnotation (e.g. wrappers whose
     *    @CollectionWrapper annotation was stripped but descriptor class was published separately).
     *
     * Returns a Map of forTypeFqn → wrapFunctionFqn.
     * Logs an error (causing COMPILATION_ERROR) if the same forType is registered by multiple wrappers.
     */
    fun discoverWrappers(resolver: Resolver): Map<String, String> {
        val entries = mutableListOf<Pair<String, String>>() // (forType, wrapFqn)

        // Source 1: in-memory cache from generateDescriptors (immediate, no compile step needed).
        entries.addAll(inMemoryWrappers.entries.map { it.key to it.value })

        // Source 2: getDeclarationsFromPackage (binary descriptor objects from dependency artifacts).
        // Source 2a: getDeclarationsFromPackage — works on JVM (descriptor .class files visible in
        // dependency JARs). Returns 0 in kspCommonMainMetadata's consumer invocation (KSP2 KMP
        // limitation: dependency module packages not enumerable from consumer invocation scope).
        val declarations = resolver.getDeclarationsFromPackage(GENERATED_PACKAGE)
        val declList = declarations.toList()
        for (decl in declList) {
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

        // Source 2b: getSymbolsWithAnnotation(@CollectionWrapperDescriptor) — also works on JVM.
        // Same KSP2 KMP limitation applies: returns 0 in consumer module invocation's metadata mode.
        // Both this and 2a are retained as they provide redundancy for future KSP2 fixes and for
        // published-artifact consumers where getDeclarationsFromPackage does work.
        val descriptorSymbols = resolver
            .getSymbolsWithAnnotation(COLLECTION_WRAPPER_DESCRIPTOR_ANNOTATION)
            .toList()
        for (decl in descriptorSymbols) {
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

        // Deduplicate and check for conflicts.
        val result = mutableMapOf<String, String>()
        val seen = mutableMapOf<String, String>() // forType -> first wrapFqn

        for ((forType, wrapFqn) in entries) {
            if (forType in seen && seen[forType] != wrapFqn) {
                logger.error(
                    "multiple @CollectionWrapper for the same forType '$forType': " +
                        "'${seen[forType]}' and '$wrapFqn'. Remove one."
                )
                // Don't add to result — the error will abort compilation.
            } else if (forType !in seen) {
                seen[forType] = wrapFqn
                result[forType] = wrapFqn
            }
        }

        return result
    }
}
