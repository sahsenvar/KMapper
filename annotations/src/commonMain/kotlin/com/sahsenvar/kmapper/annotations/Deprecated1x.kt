package com.sahsenvar.kmapper.annotations

/*
 * 1.x compatibility stubs — ERROR-level on purpose.
 *
 * None of these compile; they exist so that code (or an AI assistant) written against the
 * 1.x API gets a GUIDED compile error naming the 2.0 replacement, instead of an unresolved
 * reference. Remove after the 2.x line stabilizes. Signatures mirror v1.0.0 exactly.
 */

/** 1.x name of [IgnoreMap] — same semantics, clearer name in 2.0. */
@Deprecated(
    message = "Removed in 2.0: @Ignore became @IgnoreMap (same semantics — the field is excluded from auto-matching).",
    replaceWith = ReplaceWith("IgnoreMap", "com.sahsenvar.kmapper.annotations.IgnoreMap"),
    level = DeprecationLevel.ERROR,
)
typealias Ignore = IgnoreMap

/**
 * 1.x name of [ConvertWith]. Note the parameter rename: 1.x `converter = X::class`
 * is 2.0 `use = X::class` (plus the new per-field `onFail` policy).
 */
@Deprecated(
    message = "Removed in 2.0: @UseMapTypeConverter(converter = X::class) became @ConvertWith(use = X::class). " +
        "@ConvertWith also carries the per-field failure policy: @ConvertWith(use = X::class, onFail = OnFail.Throw).",
    replaceWith = ReplaceWith("ConvertWith", "com.sahsenvar.kmapper.annotations.ConvertWith"),
    level = DeprecationLevel.ERROR,
)
typealias UseMapTypeConverter = ConvertWith

/**
 * 1.x source-side validation — replaced by the field-anchored [Validate]: one declaration on
 * the field that OWNS the rule fires before conversion when the field is a source and after
 * conversion when it is a target, in every generated direction.
 */
@Deprecated(
    message = "Removed in 2.0: @ValidateFrom/@ValidateTo became the single field-anchored @Validate — " +
        "declare it once on the field that owns the rule; it fires on both sides of every mapping direction.",
    replaceWith = ReplaceWith("Validate", "com.sahsenvar.kmapper.annotations.Validate"),
    level = DeprecationLevel.ERROR,
)
typealias ValidateFrom = Validate

/** 1.x target-side validation — see [ValidateFrom]; both names collapse into [Validate]. */
@Deprecated(
    message = "Removed in 2.0: @ValidateFrom/@ValidateTo became the single field-anchored @Validate — " +
        "declare it once on the field that owns the rule; it fires on both sides of every mapping direction.",
    replaceWith = ReplaceWith("Validate", "com.sahsenvar.kmapper.annotations.Validate"),
    level = DeprecationLevel.ERROR,
)
typealias ValidateTo = Validate

/**
 * 1.x expression-based fallback — removed without an annotation replacement: the Kotlin
 * constructor default IS the fallback in 2.0. Declare `val plan: String = "FREE"` on the
 * target; mapping omits the argument so the default applies (and [IgnoreDefaultValue] opts
 * a default out of being a wire fallback).
 */
@Deprecated(
    message = "Removed in 2.0: there is no @MapDefaultValue — declare a constructor default on the target field " +
        "(val plan: String = \"FREE\"); mapping omits the argument so the Kotlin default applies. " +
        "Use @IgnoreDefaultValue when a default must NOT act as a wire fallback.",
    level = DeprecationLevel.ERROR,
)
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class MapDefaultValue(
    val expression: String,
)
