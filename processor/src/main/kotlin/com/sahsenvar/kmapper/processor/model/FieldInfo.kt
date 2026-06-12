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

    /** Effective directive for the requested direction (direction-scoped beats bilateral). */
    fun directiveFor(isReverse: Boolean): ConverterDirective? = if (isReverse) {
        convertFromDirective ?: convertWith
    } else {
        convertToDirective ?: convertWith
    }

    /** Effective onFail policy for the requested direction; [OnFailPolicy.Auto] when no directive applies. */
    fun onFailFor(isReverse: Boolean): OnFailPolicy = directiveFor(isReverse)?.onFail ?: OnFailPolicy.Auto
}
