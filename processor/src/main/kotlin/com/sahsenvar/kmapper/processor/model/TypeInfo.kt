package com.sahsenvar.kmapper.processor.model

import com.google.devtools.ksp.symbol.KSType

/**
 * Information about a type involved in mapping.
 */
data class TypeInfo(
    val qualifiedName: String,
    val simpleName: String,
    val isNullable: Boolean,
    val isCollection: Boolean,
    val elementType: KSType?,
)
