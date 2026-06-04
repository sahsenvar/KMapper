# Observability — MappingListener

kmap embeds a lightweight observability hook into every generated mapper. When no listener is registered, this hook has **~zero cost** and leaves no overhead in production builds.

## The `MappingListener` Interface

```kotlin
// com.sahsenvar.kmapper
interface MappingListener {
    fun onMapStart(source: Any, target: KClass<*>) {}
    fun onMapComplete(source: Any, result: Any) {}
    fun onError(source: Any, error: MappingException) {}
}
```

All methods have a default no-op implementation; override only the ones you need. The interface is forward-compatible: future versions may add new methods like `onFieldDefaulted` or `onConversion` without breaking your existing implementations.

## KMapper — Registering Listeners

The `KMapper` singleton manages the listener list:

```kotlin
object KMapper {
    val hasListeners: Boolean
    fun addListener(listener: MappingListener)
    fun removeListener(listener: MappingListener)
    fun dispatch(block: MappingListener.() -> Unit)
}
```

Listeners are held with copy-on-write semantics: `addListener`/`removeListener` calls create a new list instead of mutating the existing one. This prevents data races in shared-memory environments like JVM/Android without requiring an external dependency such as `atomicfu`.

## Generated Code — Guarded Dispatch

Every generated mapper contains a guard block protected by `KMapper.hasListeners`:

```kotlin
fun UserRemote.toUserDomain(): UserDomain {
    if (KMapper.hasListeners) KMapper.dispatch { onMapStart(this@toUserDomain, UserDomain::class) }
    val result = UserDomain(
        id = id ?: throw MappingException.RequiredFieldMissing("id"),
    )
    if (KMapper.hasListeners) KMapper.dispatch { onMapComplete(this@toUserDomain, result) }
    return result
}
```

When `hasListeners` is false (no listener registered), `dispatch` is never called. There is no additional overhead as long as you do not register a listener in production.

Events are emitted at **per-mapper** coarse granularity — the generated code stays lean.

## LoggingMappingListener

kmap ships a basic logging implementation:

```kotlin
class LoggingMappingListener(private val log: (String) -> Unit) : MappingListener {
    override fun onMapStart(source: Any, target: KClass<*>) =
        log("kmap start: ${source::class.simpleName} -> ${target.simpleName}")

    override fun onMapComplete(source: Any, result: Any) =
        log("kmap done: ${source::class.simpleName} -> ${result::class.simpleName}")

    override fun onError(source: Any, error: MappingException) =
        log("kmap error: ${error.message}")
}
```

Pass any log function to the `log` parameter — `println`, Timber, Napier, or similar:

```kotlin
// Android (Application.onCreate / DI init)
KMapper.addListener(LoggingMappingListener { message -> Log.d("kmap", message) })

// Kotlin/Native or shared code
KMapper.addListener(LoggingMappingListener(::println))
```

## Registering at App Init

Listeners should be registered once at application startup. The API is not designed for frequent `addListener`/`removeListener` calls.

**Android:**

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            KMapper.addListener(LoggingMappingListener { message ->
                Log.d("kmap", message)
            })
        }
    }
}
```

**Koin / DI init:**

```kotlin
// KoinInitializer or a similar initialization point
KMapper.addListener(LoggingMappingListener { message -> logger.debug(message) })
```

**Custom Listener:**

```kotlin
class MetricsMappingListener(private val tracker: MetricsTracker) : MappingListener {
    override fun onError(source: Any, error: MappingException) {
        tracker.recordMappingError(
            sourceType = source::class.simpleName ?: "Unknown",
            errorType  = error::class.simpleName ?: "Unknown",
        )
    }
}

KMapper.addListener(MetricsMappingListener(myTracker))
```

---

Next: [Multi-Module Projects](../advanced/multi-module.md)
