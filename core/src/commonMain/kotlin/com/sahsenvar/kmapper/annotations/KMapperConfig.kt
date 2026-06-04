package com.sahsenvar.kmapper.annotations

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class KMapperConfig(
    val converters: Array<KClass<*>> = [],
    val wrappers: Array<KClass<*>> = []
)
