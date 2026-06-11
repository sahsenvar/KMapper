package com.sahsenvar.kmapper.annotations

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class MapFrom(
    val source: KClass<*>,
)
