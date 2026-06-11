package com.sahsenvar.kmapper

/**
 * Compile-time diagnostic message for a type pair with no converter at all.
 * Shared between the KSP `MissingConverter` diagnostic and any runtime reporting;
 * there is intentionally NO MissingConverter exception type (compile-time-only).
 */
fun missingConverterMessage(
    from: String,
    to: String,
): String = "$from -> $to has no registered converter. Add one via @ConvertWith / @KMapperConfig " +
    "(docs link — coming soon), or rethink your source/target types."

/**
 * Message for an intentionally-unsupported conversion direction. Used both by the KSP
 * `UnsupportedConversion` diagnostic and by [MappingException.UnsupportedConversion] at runtime.
 */
fun unsupportedConversionMessage(
    from: String,
    to: String,
): String =
    """
    $from -> $to conversion is unsupported! This relates to our policy on lossy conversions
    (e.g. Long -> Int, Double -> Float). What you can do:
      1. Check the converter add-ons (docs link — coming soon)
      2. Create your own converter (docs link — coming soon)
      3. Rethink your source or target type using supported types.
    """.trimIndent()
