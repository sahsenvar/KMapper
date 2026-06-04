package com.sahsenvar.kmapper.annotations

import kotlin.reflect.KClass

// BINARY retention so KSP can read this annotation from compiled dependency artifacts.
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class CollectionWrapper(val forType: KClass<*>)
