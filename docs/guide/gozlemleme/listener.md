# MappingListener ve Degradation Sink

Mapping'lerin production'da yaptığı her şeyi iki kanal söyler:

- **`Result` hataları** — sert hatalar, çağrı noktasında.
  ([Hata yönetimi](../hata-yonetimi/mapping-exception.md).)
- **Degradation sink** — *emilen* her esneklik: null'a dönen bozuk tarih, atılan liste
  elemanı, çakışan map anahtarı. Bu sayfa o kanal.

## Listener kaydetmek

```kotlin
import com.sahsenvar.kmapper.KMapper
import com.sahsenvar.kmapper.MappingListener
import com.sahsenvar.kmapper.MappingDegradation

// bir kez, uygulama açılışında (Application.onCreate / iOS app delegate):
KMapper.addListener(object : MappingListener {
    override fun onDegradation(event: MappingDegradation) {
        telemetry.count("mapping.degradation", event::class.simpleName)
    }

    override fun onError(source: Any, error: MappingException) {
        log.warn("mapping failed: ${error.message}")
    }
})
```

`MappingListener` izleme için `onMapStart`/`onMapComplete` da sunar. Bütün metotların
varsayılan no-op gövdeleri var — yalnızca gerekeni override edin. Hazır bir
`LoggingMappingListener(log)` dahildir.

Listener'lar sözleşme gereği saf gözlemcidir: içlerinde fırlayan exception bastırılır;
mapping'i ya da diğer listener'ları asla etkileyemez. Üretilen kod önce
`KMapper.hasListeners`'a bakar — kanal kullanılmadığında maliyeti ~sıfırdır.

## Degradation olayları

`MappingDegradation` sealed'dır; her olay alan yolu taşır:

| Olay | Ne zaman |
|------|----------|
| `AbsorbedConversionError` | bozuk skaler bir default/null kaçışına emildi ([ladder](../temel-kullanim/null-safety.md) 2./3. basamak) — nedeni taşır |
| `DroppedBrokenElement` | bozuk koleksiyon elemanı atıldı |
| `DroppedNullElement` | null eleman non-null eleman tipine sığmadı, atıldı |
| `DuplicateKey` | iki map anahtarı dönüşüm sonrası çakıştı; son giren kaldı |
| `ConvergedDuplicateElement` | iki set elemanı dönüşüm sonrası çakıştı |

Akılda tutulacak ayrım ([zihinsel modelin](../baslarken/zihinsel-model.md) 2. kuralı):
**beyan edilmiş eksiklik sessizdir** — nullable alana null gelmesi olay *değildir*;
**emilen bozukluk her zaman raporlanır** — veri vardı ve kayboldu, telemetri bilmeli.

## Debug/release kalıbı

```kotlin
if (BuildConfig.DEBUG) {
    KMapper.addListener(object : MappingListener {
        override fun onDegradation(event: MappingDegradation) =
            error("degradation in debug build: $event") // geliştirirken gürültüyle çök…
    })
} else {
    KMapper.addListener(MetricsListener) // …production'da sessizce gözle
}
```

Bir uyarı: `onDegradation` *içinde* mapping çalıştırmayın — tap içindeki bir degrading seam,
dispatch'i özyinelemeye sokar.

> Sıradaki: **[Çok Modüllü Projeler →](../ileri/cok-modullu.md)**
