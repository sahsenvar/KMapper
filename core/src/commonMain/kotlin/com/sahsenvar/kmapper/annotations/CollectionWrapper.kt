package com.sahsenvar.kmapper.annotations

import kotlin.reflect.KClass

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class CollectionWrapper(val forType: KClass<*>)
