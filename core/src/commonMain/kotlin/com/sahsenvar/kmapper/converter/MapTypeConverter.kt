package com.sahsenvar.kmapper.converter

import kotlin.reflect.KClass

abstract class MapTypeConverter<S : Any, T : Any>(
    val sourceType: KClass<S>,
    val targetType: KClass<T>,
) {
    abstract fun convertToNonNull(value: S): T

    abstract fun convertFromNonNull(value: T): S

    fun convertTo(value: S?): T? = value?.let { convertToNonNull(it) }

    fun convertFrom(value: T?): S? = value?.let { convertFromNonNull(it) }
}
