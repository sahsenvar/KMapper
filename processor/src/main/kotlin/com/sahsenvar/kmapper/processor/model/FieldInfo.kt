package com.sahsenvar.kmapper.processor.model

import com.google.devtools.ksp.symbol.KSType

/**
 * Processor-side mirror of `com.sahsenvar.kmapper.annotations.OnFail` — typed so policy
 * comparisons are exhaustive instead of stringly.
 */
enum class OnFailPolicy {
    Auto,
    Throw,
    Skip,
    ;

    companion object {
        /** Parses an annotation argument's simple name; unknown or absent → [Auto]. */
        fun parse(rawName: String?): OnFailPolicy = entries.firstOrNull { it.name == rawName } ?: Auto
    }
}

/**
 * Per-field converter/policy override read from @ConvertWith / @ConvertTo / @ConvertFrom.
 */
data class ConverterDirective(
    /** null = keep auto-discovery (the `use` parameter was left at its sentinel). */
    val converterFqn: String?,
    /** Failure policy for the directive's direction(s). */
    val onFail: OnFailPolicy,
)

/**
 * Information about a field (constructor parameter or computed property) in a class.
 */
data class FieldInfo(
    val name: String,
    val type: KSType,
    val isNullable: Boolean,
    val hasDefault: Boolean,
    val isComputed: Boolean,
    /** Map of target class FQN to list of target field names (supports multiple @FieldMap per targetClass) */
    val fieldMapTargets: Map<String, List<String>>,
    /** If true, this field will be ignored in automatic mapping (requires external parameter) */
    val isIgnored: Boolean,
    /** Bilateral per-field override from @ConvertWith (both directions). */
    val convertWith: ConverterDirective? = null,
    /** Direction-scoped override from @ConvertTo (forward / @MapTo direction); beats [convertWith] there. */
    val convertToDirective: ConverterDirective? = null,
    /** Direction-scoped override from @ConvertFrom (reverse / @MapFrom direction); beats [convertWith] there. */
    val convertFromDirective: ConverterDirective? = null,
    /** FQNs of Validator<T> objects from @Validate — fire whenever this field enters a mapping. */
    val validators: List<String> = emptyList(),
    /** @IgnoreDefaultValue: the constructor default is invisible to mapping. */
    val ignoreDefaultValue: Boolean = false,
) {
    /** The only default flag mapping decisions may consult (omit/copy, external params). */
    val usesDefaultInMapping: Boolean get() = hasDefault && !ignoreDefaultValue

    /**
     * This field's own directive for the requested direction (direction-scoped beats bilateral).
     * Mapping decisions use [effectiveDirective], which also decides WHICH side's field is asked.
     */
    fun directiveFor(isReverse: Boolean): ConverterDirective? = if (isReverse) {
        convertFromDirective ?: convertWith
    } else {
        convertToDirective ?: convertWith
    }
}

/**
 * The directive governing one (source, target) field pairing — owner-anchored: the class that
 * declares the mapping is asked first. That is the source in the forward (@MapTo) direction and
 * the TARGET in the reverse (@MapFrom) direction, whose source is typically a foreign class the
 * user cannot annotate (issue #82). In the reverse direction the source field stays a fallback,
 * so directives placed there keep working. A directive is taken whole (use + onFail together),
 * never merged across the two sides.
 */
fun effectiveDirective(
    sourceField: FieldInfo,
    targetField: FieldInfo,
    isReverse: Boolean,
): ConverterDirective? = if (isReverse) {
    targetField.directiveFor(isReverse = true) ?: sourceField.directiveFor(isReverse = true)
} else {
    sourceField.directiveFor(isReverse = false)
}

/** Effective onFail policy for the pairing; [OnFailPolicy.Auto] when no directive applies. */
fun effectiveOnFail(
    sourceField: FieldInfo,
    targetField: FieldInfo,
    isReverse: Boolean,
): OnFailPolicy = effectiveDirective(sourceField, targetField, isReverse)?.onFail ?: OnFailPolicy.Auto
