package com.sahsenvar.kmapper.annotations

import kotlin.reflect.KClass

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class FieldMap(
    val fieldName: String,
    val targetClass: KClass<*> = Nothing::class,
)
