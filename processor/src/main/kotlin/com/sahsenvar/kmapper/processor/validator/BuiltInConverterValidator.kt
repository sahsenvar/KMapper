package com.sahsenvar.kmapper.processor.validator

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration

/**
 * Validates MapTypeConverter implementations for duplicate/bilateral conflicts at compile-time.
 *
 * Detects two types of problems:
 * 1. Exact duplicates: Same converter registered twice (S → T, S → T)
 * 2. Bilateral conflicts: Reverse converter exists (S → T exists, T → S added)
 *
 * Example bilateral conflict:
 *   StringIntConverter (String ↔ Int) already handles both directions
 *   A second Int→String converter is redundant and will cause ERROR
 */
class BuiltInConverterValidator(private val logger: KSPLogger) {

    companion object {
        private const val MAP_TYPE_CONVERTER_FQN =
            "com.sahsenvar.kmapper.converter.MapTypeConverter"
    }

    /**
     * Validates all MapTypeConverter subclasses in the codebase.
     *
     * @param resolver KSP resolver to find all converter classes
     * @return true if validation passed, false if errors were found
     */
    fun validate(resolver: Resolver): Boolean {
        val converterClasses = findAllConverters(resolver)
        if (converterClasses.isEmpty()) {
            logger.info("No MapTypeConverter implementations found (this is expected during initial setup)")
            return true
        }

        val converterPairs = mutableMapOf<Pair<String, String>, ConverterInfo>()
        var hasError = false

        converterClasses.forEach { converter ->
            val sourceType = extractSourceType(converter)
            val targetType = extractTargetType(converter)

            if (sourceType == null || targetType == null) {
                logger.warn("Skipping ${converter.simpleName.asString()}: could not extract type parameters")
                return@forEach
            }

            val forwardKey = sourceType to targetType
            val reverseKey = targetType to sourceType

            when {
                // Exact duplicate: Same converter registered twice
                converterPairs.containsKey(forwardKey) -> {
                    val existing = converterPairs[forwardKey]!!
                    logger.error(
                        """
                        ❌ EXACT DUPLICATE CONVERTER DETECTED

                        Type pair: $sourceType → $targetType

                        Existing converter:
                          ${existing.className} (${existing.location})

                        Duplicate converter:
                          ${converter.simpleName.asString()} (${converter.containingFile?.fileName})

                        → Remove the duplicate converter.
                        """.trimIndent(),
                        converter
                    )
                    hasError = true
                }

                // Bilateral conflict: Reverse converter already exists
                converterPairs.containsKey(reverseKey) -> {
                    val existing = converterPairs[reverseKey]!!
                    logger.error(
                        """
                        ❌ BILATERAL CONVERTER CONFLICT DETECTED

                        Existing converter: ${existing.className}
                          Location: ${existing.location}
                          Handles: $targetType ↔ $sourceType (BILATERAL)

                        New converter: ${converter.simpleName.asString()}
                          Location: ${converter.containingFile?.fileName}
                          Would handle: $sourceType ↔ $targetType (BILATERAL)

                        ⚠️ MapTypeConverter is BILATERAL - it already converts both directions!

                        Example:
                          ${existing.className}.convertTo(value)    → $sourceType → $targetType
                          ${existing.className}.convertFrom(value)  → $targetType → $sourceType

                        → Remove ${converter.simpleName.asString()}, keep ${existing.className}.
                        """.trimIndent(),
                        converter
                    )
                    hasError = true
                }

                else -> {
                    converterPairs[forwardKey] = ConverterInfo(
                        className = converter.simpleName.asString(),
                        location = converter.containingFile?.fileName ?: "unknown"
                    )
                }
            }
        }

        if (!hasError) {
            logger.info("✅ Converter validation passed: ${converterPairs.size} unique converters registered")
        }

        return !hasError
    }

    /**
     * Finds all classes that extend MapTypeConverter.
     */
    private fun findAllConverters(resolver: Resolver): List<KSClassDeclaration> {
        return resolver.getAllFiles()
            .flatMap { it.declarations }
            .filterIsInstance<KSClassDeclaration>()
            .filter { classDecl ->
                classDecl.superTypes.any { superType ->
                    val declaration = superType.resolve().declaration
                    declaration.qualifiedName?.asString() == MAP_TYPE_CONVERTER_FQN
                }
            }
            .toList()
    }

    /**
     * Extracts the source type (S) from MapTypeConverter<S, T>.
     */
    private fun extractSourceType(converter: KSClassDeclaration): String? {
        val superType = converter.superTypes
            .map { it.resolve() }
            .firstOrNull { it.declaration.qualifiedName?.asString() == MAP_TYPE_CONVERTER_FQN }
            ?: return null

        return superType.arguments.firstOrNull()
            ?.type?.resolve()
            ?.declaration?.qualifiedName?.asString()
    }

    /**
     * Extracts the target type (T) from MapTypeConverter<S, T>.
     */
    private fun extractTargetType(converter: KSClassDeclaration): String? {
        val superType = converter.superTypes
            .map { it.resolve() }
            .firstOrNull { it.declaration.qualifiedName?.asString() == MAP_TYPE_CONVERTER_FQN }
            ?: return null

        return superType.arguments.getOrNull(1)
            ?.type?.resolve()
            ?.declaration?.qualifiedName?.asString()
    }

    private data class ConverterInfo(
        val className: String,
        val location: String
    )
}
