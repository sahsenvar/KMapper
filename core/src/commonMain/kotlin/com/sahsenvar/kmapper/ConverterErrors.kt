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
    "(see $DOCS_BASE/kmapperconfig.md), or rethink your source/target types."

/** Stable docs root for compile-error guidance links (GitHub render of the in-repo guide). */
private const val DOCS_BASE: String = "https://github.com/sahsenvar/KMapper/blob/main/docs/guide-en/type-conversion"

/**
 * Shared "what you can do" guidance tail (the three options) appended to every
 * unsupported-conversion message. Extracted so the direction-neutral stub message in
 * `MapTypeConverter.unsupported()` reuses the exact same options without duplication.
 * Internal on purpose: it is an implementation detail of message assembly, not API.
 */
internal const val UNSUPPORTED_CONVERSION_GUIDANCE: String =
    " What you can do:\n" +
        "  1. Check the converter add-ons ($DOCS_BASE/built-in.md)\n" +
        "  2. Create your own converter ($DOCS_BASE/custom-converter.md)\n" +
        "  3. Rethink your source or target type using supported types."

/**
 * Message for an intentionally-unsupported conversion direction. Used both by the KSP
 * `UnsupportedConversion` diagnostic and by [MappingException.UnsupportedConversion] at runtime.
 */
fun unsupportedConversionMessage(
    from: String,
    to: String,
): String = "$from -> $to conversion is unsupported! This relates to our policy on lossy conversions\n" +
    "(e.g. Long -> Int, Double -> Float).$UNSUPPORTED_CONVERSION_GUIDANCE"
