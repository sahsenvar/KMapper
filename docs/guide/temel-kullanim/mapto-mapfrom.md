# @MapTo ve @MapFrom

kmap, iki yönlü eşleştirmeyi iki ayrı anotasyonla destekler: **`@MapTo`** kaynak sınıftan hedefe, **`@MapFrom`** ise hedef sınıftan kaynağa doğru bir `toX()` fonksiyonu üretir.

---

## @MapTo — İleri Yön

`@MapTo(Target::class)` anotasyonu **kaynak** sınıfa eklenir. Processor, kaynak sınıfın extension fonksiyonu olarak `toTarget()` üretir.

```kotlin
// Kaynak: data katmanı
@MapTo(UserDomain::class)
data class UserRemote(
    val id: String,
    val email: String,
)

// Hedef: domain katmanı
data class UserDomain(
    val id: String,
    val email: String,
)
```

Derleme sonrası üretilen dosya `UserRemoteMappers.kt` (kaynak sınıfla aynı paket):

```kotlin
// build/generated/…/UserRemoteMappers.kt
public fun UserRemote.toUserDomain(): UserDomain = UserDomain(
    id    = id,
    email = email,
)
```

Kullanımı:

```kotlin
val domain: UserDomain = userRemote.toUserDomain()
```

---

## Birden Çok Hedef (Repeatable)

`@MapTo` tekrarlanabilir (`@Repeatable`) olduğu için aynı kaynak sınıfı birden fazla hedefe eşleyebilirsiniz:

```kotlin
@MapTo(UserDomain::class)
@MapTo(UserCache::class)
data class UserRemote(
    val id: String,
    val email: String,
)
```

Processor her hedef için ayrı bir extension üretir:

```kotlin
public fun UserRemote.toUserDomain(): UserDomain = UserDomain(id = id, email = email)
public fun UserRemote.toUserCache(): UserCache  = UserCache(id = id, email = email)
```

Alan adları veya tipleri hedefler arasında farklılaşıyorsa `@FieldMap(targetClass = ...)` ile hangi eşleştirmenin hangi hedefe ait olduğunu belirtebilirsiniz (bkz. [Alan Eşleştirme](alan-eslestirme.md)).

---

## @MapFrom — Ters Yön

`@MapFrom(Source::class)` anotasyonu **hedef** sınıfa eklenir; yani eşleştirme yönü kaynaktan hedefe aynı olsa da anotasyonu **hedef sınıfa** koyarsınız. Üretilen `toX()` fonksiyonu yine **kaynak** sınıfın extension'ı olarak çıkar — tek fark anotasyonun kimin üzerinde durduğudur.

```kotlin
data class UserRemote(
    val id: String,
    val email: String,
)

// Anotasyon hedef sınıfta; ama toUserDomain() kaynaktan çağrılır
@MapFrom(UserRemote::class)
data class UserDomain(
    val id: String,
    val email: String,
)
```

Üretilen kod `@MapTo` ile özdeştir:

```kotlin
public fun UserRemote.toUserDomain(): UserDomain = UserDomain(
    id    = id,
    email = email,
)
```

`@MapFrom` da `@Repeatable`'dır; birden fazla kaynaktan aynı hedefe eşleyebilirsiniz:

```kotlin
@MapFrom(UserRemote::class)
@MapFrom(UserCache::class)
data class UserDomain(val id: String, val email: String)
```

---

## Hangi Anotasyonu Seçmeli?

| Durum | Tercih |
|-------|--------|
| Kaynak sınıf sizin kontrolünüzde (ör. DTO) | `@MapTo` kaynağa |
| Hedef sınıf sizin kontrolünüzde (ör. domain model) | `@MapFrom` hedefe |
| İkisi de sizin — fark etmez | İkisi de aynı kodu üretir |

---

Sonraki adım: [Alan Eşleştirme (@FieldMap, @Ignore)](alan-eslestirme.md)
