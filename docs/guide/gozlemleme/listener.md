# Gözlemleme — MappingListener

KMapper, ürettiği her mapper'a hafif bir gözlemleme kancası yerleştirir. Dinleyici kayıtlı değilken bu kanca **~sıfır maliyete** sahiptir; production build'lerde herhangi bir overhead bırakmaz.

## MappingListener Interface'i

```kotlin
// com.sahsenvar.kmapper
interface MappingListener {
    fun onMapStart(source: Any, target: KClass<*>) {}
    fun onMapComplete(source: Any, result: Any) {}
    fun onError(source: Any, error: MappingException) {}
}
```

Tüm metotlar varsayılan no-op implementasyona sahiptir; yalnızca ihtiyacınız olan metotları override edin. Interface ileri-uyumludur: sonraki sürümlerde `onFieldDefaulted`, `onConversion` gibi yeni metotlar eklenebilir — mevcut implementasyonlarınız bozulmaz.

## KMapper — Dinleyici Kaydı

`KMapper` singleton nesnesi dinleyici listesini yönetir:

```kotlin
object KMapper {
    val hasListeners: Boolean
    fun addListener(listener: MappingListener)
    fun removeListener(listener: MappingListener)
    fun dispatch(block: MappingListener.() -> Unit)
}
```

Dinleyiciler copy-on-write semantiğiyle tutulur: `addListener`/`removeListener` çağrıları yeni bir liste oluşturur, mevcut listeyi mutate etmez. Bu, JVM/Android gibi shared-memory ortamlarında veri yarışını atomicfu gibi harici bir bağımlılık gerektirmeden önler.

## Üretilen Kod — Guarded Dispatch

Üretilen her mapper, `KMapper.hasListeners` ile korunan bir guard bloğu içerir:

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

`hasListeners` false olduğunda (dinleyici yok) `dispatch` hiç çağrılmaz. Production ortamında dinleyici kaydetmediğiniz sürece ek overhead yoktur.

Olaylar **mapper başına** (per-mapper) kaba granülerlikte yayılır — üretilen kod yalın kalır.

## LoggingMappingListener

KMapper, temel bir loglama implementasyonu sunar:

```kotlin
class LoggingMappingListener(private val log: (String) -> Unit) : MappingListener {
    override fun onMapStart(source: Any, target: KClass<*>) =
        log("KMapper start: ${source::class.simpleName} -> ${target.simpleName}")

    override fun onMapComplete(source: Any, result: Any) =
        log("KMapper done: ${source::class.simpleName} -> ${result::class.simpleName}")

    override fun onError(source: Any, error: MappingException) =
        log("KMapper error: ${error.message}")
}
```

`log` parametresine istediğiniz log fonksiyonunu geçin — `println`, Timber, Napier veya benzeri:

```kotlin
// Android (Application.onCreate / DI init)
KMapper.addListener(LoggingMappingListener { message -> Log.d("KMapper", message) })

// Kotlin/Native veya ortak kod
KMapper.addListener(LoggingMappingListener(::println))
```

## App Init'te Kayıt

Dinleyiciler uygulama başlangıcında bir kez kayıt edilmelidir. Sık `addListener`/`removeListener` çağrısı için tasarlanmamıştır.

**Android:**

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            KMapper.addListener(LoggingMappingListener { message ->
                Log.d("KMapper", message)
            })
        }
    }
}
```

**Koin / DI init:**

```kotlin
// KoinInitializer veya benzeri yapılanma noktası
KMapper.addListener(LoggingMappingListener { message -> logger.debug(message) })
```

**Özel Dinleyici:**

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

Sonraki adım: [Çok Modüllü Projeler](../ileri/cok-modullu.md)
