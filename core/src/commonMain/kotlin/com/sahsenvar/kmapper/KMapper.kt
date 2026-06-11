package com.sahsenvar.kmapper

import com.sahsenvar.kmapper.converter.MapTypeConverter
import com.sahsenvar.kmapper.converter.TypeConverterRegistry
import kotlin.concurrent.Volatile
import kotlin.reflect.KClass

/**
 * Listeners are observers: an exception thrown by a listener is suppressed and never affects
 * the mapping or other listeners.
 */
interface MappingListener {
    fun onMapStart(
        source: Any,
        target: KClass<*>,
    ) {}

    fun onMapComplete(
        source: Any,
        result: Any,
    ) {}

    fun onError(
        source: Any,
        error: MappingException,
    ) {}

    /** Absorbed-leniency tap (skips, broken→null/default absorptions, duplicate keys). Default no-op. */
    fun onDegradation(event: MappingDegradation) {}
}

object KMapper {
    /**
     * Listener registry. Uses copy-on-write semantics: addListener/removeListener each
     * reassign the reference to a new immutable list rather than mutating a shared list.
     * This avoids data races on platforms with shared-memory concurrency (JVM/Android)
     * without requiring an external dependency such as atomicfu.
     *
     * Listeners are intended to be registered once during app initialisation (e.g. in
     * Application.onCreate or the iOS app delegate), not toggled frequently at runtime.
     * dispatch() snapshots the list before iterating (kept for clarity, though copy-on-write
     * already guarantees a stable reference per read).
     */
    @Volatile
    private var listeners: List<MappingListener> = emptyList()

    /** Generated mapper code guards dispatch with this for ~zero cost when unused. */
    val hasListeners: Boolean get() = listeners.isNotEmpty()

    fun addListener(listener: MappingListener) {
        listeners = listeners + listener
    }

    fun removeListener(listener: MappingListener) {
        listeners = listeners - listener
    }

    fun dispatch(block: MappingListener.() -> Unit) {
        listeners.toList().forEach { listener ->
            try {
                listener.block()
            } catch (_: Throwable) {
                // Observation must never change mapping behavior: a throwing listener is
                // isolated and suppressed by contract (see MappingListener KDoc).
            }
        }
    }

    /** Runtime escape-hatch (NOT compile-time safe; prefer @KMapperConfig). */
    fun <S : Any, T : Any> addConverter(converter: MapTypeConverter<S, T>) = TypeConverterRegistry.register(converter)
}

class LoggingMappingListener(
    private val log: (String) -> Unit,
) : MappingListener {
    override fun onMapStart(
        source: Any,
        target: KClass<*>,
    ) = log("KMapper start: ${source::class.simpleName} -> ${target.simpleName}")

    override fun onMapComplete(
        source: Any,
        result: Any,
    ) = log("KMapper done: ${source::class.simpleName} -> ${result::class.simpleName}")

    override fun onError(
        source: Any,
        error: MappingException,
    ) = log("KMapper error: ${error.message}")
}
