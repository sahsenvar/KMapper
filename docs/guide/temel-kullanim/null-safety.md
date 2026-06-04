# Null-Safety ve @MapDefaultValue

kmap, nullable-to-non-null dönüşümlerini derleme zamanında fark eder ve güvenli kod üretir. `null` hiçbir zaman sessizce yutulmaz.

---

## Dört Nullability Durumu

| Kaynak | Hedef | Üretilen atama |
|--------|-------|----------------|
| `T` (zorunlu) | `T` (zorunlu) | Doğrudan atama |
| `T` (zorunlu) | `T?` (nullable) | Doğrudan atama |
| `T?` (nullable) | `T?` (nullable) | Doğrudan atama |
| `T?` (nullable) | `T` (zorunlu) | `?: throw MappingException.RequiredFieldMissing("alan")` veya `@MapDefaultValue` |

Yalnızca son satır özel davranış gerektirir. Diğerleri doğrudan atamadır.

---

## Nullable → Zorunlu: Varsayılan Hata

Kaynak alan nullable, hedef alan zorunluysa processor otomatik olarak `RequiredFieldMissing` fırlatır:

```kotlin
@MapTo(OrderDomain::class)
data class OrderRemote(
    val id: String?,          // nullable
    val amount: Double?,      // nullable
)

data class OrderDomain(
    val id: String,           // zorunlu
    val amount: Double,       // zorunlu
)
```

Üretilen kod:

```kotlin
public fun OrderRemote.toOrderDomain(): OrderDomain = OrderDomain(
    id     = id     ?: throw MappingException.RequiredFieldMissing("id"),
    amount = amount ?: throw MappingException.RequiredFieldMissing("amount"),
)
```

`null` geldiğinde `MappingException.RequiredFieldMissing` fırlatılır; bu bir `RuntimeException` olduğu için catch etmeden önce loglayabilir ya da domain hatasına dönüştürebilirsiniz:

```kotlin
try {
    val domain = orderRemote.toOrderDomain()
} catch (e: MappingException.RequiredFieldMissing) {
    // e.field → hangi alan null geldi
}
```

---

## @MapDefaultValue — Varsayılan Değer

Null geldiğinde istisna yerine belirli bir değer kullanmak istiyorsanız `@MapDefaultValue(expression)` ekleyin. `expression`, üretilen kodun içine doğrudan yerleştirilen bir Kotlin ifadesidir — string literal, referans, fonksiyon çağrısı olabilir.

```kotlin
import kotlinx.datetime.Clock

@MapTo(EventDomain::class)
data class EventRemote(
    val title: String?,

    @MapDefaultValue("Clock.System.now()")
    val createdAt: kotlinx.datetime.Instant?,

    @MapDefaultValue("0")
    val viewCount: Int?,
)

data class EventDomain(
    val title: String,
    val createdAt: kotlinx.datetime.Instant,
    val viewCount: Int,
)
```

Üretilen kod:

```kotlin
public fun EventRemote.toEventDomain(): EventDomain = EventDomain(
    title     = title     ?: throw MappingException.RequiredFieldMissing("title"),
    createdAt = createdAt ?: Clock.System.now(),
    viewCount = viewCount ?: 0,
)
```

`title` için `@MapDefaultValue` verilmedi, bu yüzden exception fırlatılır. `createdAt` ve `viewCount` için varsayılan ifadeler kullanılır.

---

## Zorunlu → Nullable Hedef

Kaynak alan zorunlu ama hedef nullable ise doğrudan atama yapılır, hiçbir ek kontrol eklenmez:

```kotlin
@MapTo(UserCache::class)
data class UserDomain(
    val email: String,    // zorunlu
)

data class UserCache(
    val email: String?,   // nullable — ama kaynak zaten zorunlu
)
```

Üretilen:

```kotlin
public fun UserDomain.toUserCache(): UserCache = UserCache(
    email = email,        // doğrudan atama
)
```

---

## Hata Hiyerarşisi

`MappingException.RequiredFieldMissing` ve diğer tiplerin tam listesi için bkz. [Hata Yönetimi](../hata-yonetimi/mapping-exception.md).

---

Sonraki adım: [İç İçe Modeller](nested.md)
