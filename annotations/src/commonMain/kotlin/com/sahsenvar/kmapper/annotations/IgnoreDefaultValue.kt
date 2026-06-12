package com.sahsenvar.kmapper.annotations

/**
 * Mapping treats this field's constructor default as nonexistent: the default is Kotlin
 * construction convenience, NOT a wire fallback. Absence becomes RequiredFieldMissing; the
 * field is built in the constructor stage (not omit/copy). No-op warning without a default.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class IgnoreDefaultValue
