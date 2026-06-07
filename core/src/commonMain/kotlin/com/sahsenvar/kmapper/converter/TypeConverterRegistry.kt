package com.sahsenvar.kmapper.converter

import kotlin.reflect.KClass

expect object TypeConverterRegistry {
    fun <S : Any, T : Any> register(converter: MapTypeConverter<S, T>)

    fun <S : Any, T : Any> get(
        sourceType: KClass<S>,
        targetType: KClass<T>,
    ): MapTypeConverter<S, T>?

    fun <S : Any, T : Any> has(
        sourceType: KClass<S>,
        targetType: KClass<T>,
    ): Boolean
}
