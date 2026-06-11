package com.sahsenvar.kmapper.annotations

import com.sahsenvar.kmapper.converter.MapTypeConverter
import kotlin.reflect.KClass

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class UseMapTypeConverter(
    val converter: KClass<out MapTypeConverter<*, *>>,
)
