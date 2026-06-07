package com.sahsenvar.kmapper.processor.validator

import com.google.devtools.ksp.processing.KSPLogger

/**
 * Validates the converter configuration for ambiguity at compile-time.
 *
 * Scope (intentionally narrow):
 *   - Checks the GLOBAL @KMapperConfig(converters=[...]) list for duplicate (S→T) pairs.
 *     Two converters in the global list for the same pair is genuinely ambiguous → ERROR.
 *   - Does NOT scan all MapTypeConverter subclasses in the compilation.
 *     A converter referenced ONLY via @UseMapTypeConverter is explicit and unambiguous;
 *     it must never trigger a duplicate error even when its (S,T) pair collides with
 *     a global or built-in converter.
 *
 * Bilateral check (S→T vs T→S) is intentionally removed from this validator because
 * MapTypeConverter is bidirectional by design and the bilateral "conflict" was only
 * meaningful for the old "scan everything" approach, not for an explicit opt-in list.
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

        val seen = mutableMapOf<Pair<String, String>, String>() // (S,T) → first converterFqn
        var hasError = false

        for ((typePair, converterFqn) in globalConverterEntries) {
            val (sourceFqn, targetFqn) = typePair
            val existing = seen[typePair]
            if (existing != null) {
                logger.error(
                    """
                    ❌ DUPLICATE CONVERTER IN @KMapperConfig DETECTED

                    Type pair: $sourceFqn → $targetFqn

                    First converter:  $existing
                    Second converter: $converterFqn

                    @KMapperConfig lists two converters for the same (S,T) pair — this is ambiguous.
                    → Keep exactly one converter for this pair in @KMapperConfig(converters=[...]).
                      If you need a different converter for a specific field, use @UseMapTypeConverter
                      on that field instead of adding a second entry to @KMapperConfig.
                    """.trimIndent(),
                )
                hasError = true
            } else {
                seen[typePair] = converterFqn
            }
        }

        if (!hasError) {
            logger.info("✅ Converter validation passed: ${seen.size} unique global converter(s)")
        }

        return !hasError
    }
}
