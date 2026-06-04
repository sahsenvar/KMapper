# @KMapperConfig ve @UseMapTypeConverter

Built-in tabloda bulunmayan converter'ları processor'a tanıtmanın yolu `@KMapperConfig`'tir. Alan bazlı override için `@UseMapTypeConverter` kullanılır.

---

## @KMapperConfig — Global Converter Listesi

```kotlin
@KMapperConfig(converters: Array<KClass<*>> = [])
```

`@KMapperConfig` bir `object` üzerine eklenir. `converters` dizisindeki her sınıf `MapTypeConverter<S,T>`'nin alt tipi olmalıdır.

```kotlin
@KMapperConfig(converters = [
    UuidStringConverter::class,
    StatusConverter::class,
    StringInstantConverter::class,   // built-in olduğu hâlde listeye eklenebilir (zararsız)
])
object AppMapperConfig
```

Processor bu nesneyi derleme zamanında bulur, her converter'ın `MapTypeConverter<S,T>` supertype'ından `(S,T)` çiftini çözer ve bu çiftler için üretilen çağrıları otomatik olarak bağlar. **Runtime'da elle kayıt gerekmez** — processor aynı listeden runtime kaydını da üretir.

---

## Öncelik Sırası

Bir alan için converter şu öncelikte seçilir:

1. **`@UseMapTypeConverter`** (alan bazlı override) — en yüksek öncelik
2. **`@KMapperConfig` listesi** (global kayıt)
3. **Built-in converter tablosu** (her zaman arka planda erişilebilir)

Daha yüksek öncelikli kural bulununca arama durur.

---

## @UseMapTypeConverter — Alan Bazlı Override

Aynı `(S,T)` çifti için global listeden farklı bir converter kullanmanız gerektiğinde `@UseMapTypeConverter` tek alana uygulanır:

```kotlin
// Global @KMapperConfig: StringInstantConverter (ISO-8601)
@KMapperConfig(converters = [StringInstantConverter::class])
object AppMapperConfig

@MapTo(EventDomain::class)
data class EventRemote(
    val startsAt: String,          // global: ISO-8601 ile dönüştürülür

    @UseMapTypeConverter(LongStringToInstantConverter::class)  // alan bazlı override
    val legacyTime: String,        // bu alan için farklı format
)

data class EventDomain(
    val startsAt: Instant,
    val legacyTime: Instant,
)
```

Üretilen kod:

```kotlin
public fun EventRemote.toEventDomain(): EventDomain = EventDomain(
    startsAt   = convertOrFail("String", "Instant") { StringInstantConverter.convertToNonNull(startsAt) },
    legacyTime = convertOrFail("String", "Instant") { LongStringToInstantConverter.convertToNonNull(legacyTime) },
)
```

`@UseMapTypeConverter` ile belirtilen converter `@KMapperConfig` listesinde bulunmak zorunda değildir; yalnızca o alan için aktif olduğundan processor tek bir alanda kullanılacağını doğrulayabilir.

---

## Eksik Converter → Derleme Hatası

Bir alan için gereken `(S,T)` çifti ne built-in tabloda ne global listede ne de alan bazlı override'da bulunamazsa processor **derleme hatası** verir:

```
e: [kmap] No converter for UUID -> String.
   Add it to @KMapperConfig or annotate the field with @UseMapTypeConverter.
```

---

## Belirsiz Global — Derleme Hatası

`@KMapperConfig` listesinde **aynı `(S,T)` çifti** için iki farklı converter bulunursa processor yine derleme hatası verir:

```kotlin
@KMapperConfig(converters = [
    StringInstantConverter::class,   // String → Instant
    EpochStringInstantConverter::class,  // String → Instant  ← aynı çift!
])
object AppMapperConfig
// e: [kmap] Ambiguous global converters for (String, Instant):
//    StringInstantConverter, EpochStringInstantConverter.
//    Remove one from @KMapperConfig and use @UseMapTypeConverter on the field that needs the other.
```

**Çözüm:** Genel kullanılan converter'ı `@KMapperConfig`'te bırakın, istisnai olanı yalnızca ihtiyaç duyulan alana `@UseMapTypeConverter` ile uygulayın.

---

## KMapper.addConverter — Runtime Escape Hatch

Derleme zamanı güvenliği gerektirmeyen durumlarda `KMapper.addConverter(converter)` çağrısı ile converter runtime'da kaydedilebilir:

```kotlin
// Application.onCreate veya iOS app delegate:
KMapper.addConverter(MyConverter)
```

Bu yol **compile-time safe değildir** — processor bu kaydı göremez, eksik converter kontrolü yapamaz. Yalnızca dinamik ya da test ortamlarında kullanın; production kodunda `@KMapperConfig` tercih edilmeli.

---

Sonraki adım: [Immutable Koleksiyonlar (converters-compose)](immutable.md)
