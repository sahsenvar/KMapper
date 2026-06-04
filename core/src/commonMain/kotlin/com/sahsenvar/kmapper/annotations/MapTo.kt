package com.sahsenvar.kmapper.annotations

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class MapTo(val target: KClass<*>)
