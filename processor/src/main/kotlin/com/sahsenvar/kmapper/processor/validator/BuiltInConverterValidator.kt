package com.sahsenvar.kmapper.processor.validator

import com.google.devtools.ksp.processing.KSPLogger

/**
 * Validates the converter configuration for ambiguity at compile-time.
 *
 * Scope (intentionally narrow):
 *   - Checks the GLOBAL @KMapperConfig(converters=[...]) list for duplicate pairs,
 *     ORIENTATION-NORMALIZED: <A,B> and <B,A> are the same pair (converters are
 *     pair-keyed and orientation-independent), so flipped duplicates are caught too.
 *     Two converters in the global list for the same pair is genuinely ambiguous → ERROR.
 *   - Does NOT scan all MapTypeConverter subclasses in the compilation.
 *     A converter referenced ONLY via a per-field @ConvertWith/@ConvertTo/@ConvertFrom
 *     `use=` override is explicit and unambiguous; it must never trigger a duplicate
 *     error even when its (S,T) pair collides with a global or built-in converter.
 */
class BuiltInConverterValidator(
    private val logger: KSPLogger,
) {
    /**
     * Validates the global converter list from @KMapperConfig(converters=[...]).
     *
     * Receives pre-extracted (sourceFqn to targetFqn) → converterFqn entries
     * (built by MappingProcessor.discoverCustomConverters before calling this).
     * Reports an error if the same (S,T) pair appears more than once.
     *
     * @param globalConverterEntries list of ((sourceFqn, targetFqn), converterFqn) entries
     *        discovered from all @KMapperConfig annotations in the compilation.
     * @return true if validation passed, false if errors were found
     */
    fun validate(globalConverterEntries: List<Pair<Pair<String, String>, String>>): Boolean {
        if (globalConverterEntries.isEmpty()) return true

        // normalized (S,T) → (first declared pair, first converterFqn). Normalization makes
        // orientation-flipped duplicates (<A,B> vs <B,A>) collide — converters are pair-keyed
        // and orientation-independent, so two converters for the same pair are ambiguous
        // regardless of the declared orientation.
        val seen = mutableMapOf<Pair<String, String>, Pair<Pair<String, String>, String>>()
        var hasError = false

        for ((typePair, converterFqn) in globalConverterEntries) {
            val normalizedPair = normalized(typePair)
            val existing = seen[normalizedPair]
            if (existing != null) {
                val (firstDeclaredPair, firstConverterFqn) = existing
                logger.error(
                    """
                    ❌ DUPLICATE CONVERTER IN @KMapperConfig DETECTED

                    Type pair (orientation-independent): ${normalizedPair.first} <-> ${normalizedPair.second}

                    First converter:  $firstConverterFqn (declared as ${firstDeclaredPair.first} → ${firstDeclaredPair.second})
                    Second converter: $converterFqn (declared as ${typePair.first} → ${typePair.second})

                    @KMapperConfig lists two converters for the same pair — converters are
                    orientation-independent, so <A,B> and <B,A> are the SAME pair. This is ambiguous.
                    → Keep exactly one converter for this pair in @KMapperConfig(converters=[...]).
                      If you need a different converter for a specific field, use @ConvertWith
                      on that field instead of adding a second entry to @KMapperConfig.
                    """.trimIndent(),
                )
                hasError = true
            } else {
                seen[normalizedPair] = typePair to converterFqn
            }
        }

        if (!hasError) {
            logger.info("✅ Converter validation passed: ${seen.size} unique global converter(s)")
        }

        return !hasError
    }

    /** Sorts a (S, T) pair so <A,B> and <B,A> normalize to the same key. */
    private fun normalized(pair: Pair<String, String>): Pair<String, String> = if (pair.first <= pair.second) pair else pair.second to pair.first
}
