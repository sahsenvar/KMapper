package com.sahsenvar.kmapper.processor.model

import com.google.devtools.ksp.symbol.KSType

/**
 * Information about a field (constructor parameter) in a class.
 */
data class FieldInfo(
    val name: String,
    val type: KSType,
    val isNullable: Boolean,
    val hasDefault: Boolean,
    val defaultValue: String?,
    val isComputed: Boolean,
    /** Map of target class FQN to list of target field names (supports multiple @FieldMap per targetClass) */
    val fieldMapTargets: Map<String, List<String>>,
    val useConverter: String?,
    /** If true, this field will be ignored in automatic mapping (requires external parameter) */
    val isIgnored: Boolean,
    /** FQNs of Validator<T> object singletons applied to the SOURCE value before mapping */
    val validateFrom: List<String> = emptyList(),
    /** FQNs of Validator<T> object singletons applied to the RESULT value after mapping */
    val validateTo: List<String> = emptyList(),
) {
    /** Legacy: Returns first FieldMap target name (for single @MapTo scenarios) */
    val fieldMapTarget: String? get() = fieldMapTargets.values.firstOrNull()?.firstOrNull()
}
