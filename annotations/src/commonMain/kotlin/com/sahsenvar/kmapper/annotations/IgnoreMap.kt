package com.sahsenvar.kmapper.annotations

/**
 * The mapper pretends this field does not exist for auto-matching: its value never flows
 * through mapping; the target slot falls back to its constructor default or becomes a
 * required external parameter on the generated function.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class IgnoreMap
