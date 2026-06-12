package com.sahsenvar.kmapper.processor.model

import com.google.devtools.ksp.symbol.KSType

/** Stdlib Set container FQNs — the single source for Set-shape checks (FQN equality, no prefix matching). */
private val STDLIB_SET_FQNS = setOf("kotlin.collections.Set", "kotlin.collections.MutableSet")

/** Stdlib List container FQNs — the single source for List-shape checks (FQN equality, no prefix matching). */
private val STDLIB_LIST_FQNS = setOf("kotlin.collections.List", "kotlin.collections.MutableList")

/** True for stdlib Set/MutableSet containers — selects the Set-producing element seams. */
internal fun KSType.isStdlibSetContainer(): Boolean = declaration.qualifiedName?.asString() in STDLIB_SET_FQNS

/** True for stdlib List/MutableList containers — the only legal wrap() source shape. */
internal fun KSType.isStdlibListContainer(): Boolean = declaration.qualifiedName?.asString() in STDLIB_LIST_FQNS
