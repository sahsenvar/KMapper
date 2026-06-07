package com.sahsenvar.kmapper.converter

import kotlin.reflect.KClass

actual object TypeConverterRegistry {
    private val map = mutableMapOf<Pair<String, String>, MapTypeConverter<*, *>>()

    private fun key(
        s: KClass<*>,
        t: KClass<*>,
    ) = (s.qualifiedName ?: s.toString()) to (t.qualifiedName ?: t.toString())

    actual fun <S : Any, T : Any> register(converter: MapTypeConverter<S, T>) {
        map.getOrPut(key(converter.sourceType, converter.targetType)) { converter }
    }

    @Suppress("UNCHECKED_CAST")
    actual fun <S : Any, T : Any> get(
        sourceType: KClass<S>,
        targetType: KClass<T>,
    ): MapTypeConverter<S, T>? = map[key(sourceType, targetType)] as? MapTypeConverter<S, T>

    actual fun <S : Any, T : Any> has(
        sourceType: KClass<S>,
        targetType: KClass<T>,
    ): Boolean = map.containsKey(key(sourceType, targetType))
}
