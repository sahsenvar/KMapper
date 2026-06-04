package com.sahsenvar.kmapper

import com.sahsenvar.kmapper.converter.MapTypeConverter
import com.sahsenvar.kmapper.converter.TypeConverterRegistry
import kotlin.reflect.KClass

interface MappingListener {
    fun onMapStart(source: Any, target: KClass<*>) {}
    fun onMapComplete(source: Any, result: Any) {}
    fun onError(source: Any, error: MappingException) {}
    // forward-compatible (default no-op): onFieldDefaulted / onConversion added in a later round
}

object KMapper {
    private val listeners = mutableListOf<MappingListener>()

    /** Generated mapper code guards dispatch with this for ~zero cost when unused. */
    val hasListeners: Boolean get() = listeners.isNotEmpty()

    fun addListener(listener: MappingListener) {
        listeners += listener
    }

    fun removeListener(listener: MappingListener) {
        listeners -= listener
    }

    fun dispatch(block: MappingListener.() -> Unit) {
        listeners.toList().forEach(block)
    }

    /** Runtime escape-hatch (NOT compile-time safe; prefer @KMapperConfig). */
    fun <S : Any, T : Any> addConverter(converter: MapTypeConverter<S, T>) =
        TypeConverterRegistry.register(converter)
}

class LoggingMappingListener(private val log: (String) -> Unit) : MappingListener {
    override fun onMapStart(source: Any, target: KClass<*>) =
        log("kmap start: ${source::class.simpleName} -> ${target.simpleName}")

    override fun onMapComplete(source: Any, result: Any) =
        log("kmap done: ${source::class.simpleName} -> ${result::class.simpleName}")

    override fun onError(source: Any, error: MappingException) =
        log("kmap error: ${error.message}")
}
