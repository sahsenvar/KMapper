package com.sahsenvar.kmapper.annotations

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class MapDefaultValue(
    val expression: String,
)
