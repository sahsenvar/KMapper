# İç İçe Modeller

kmap, iç içe geçmiş modelleri otomatik olarak tanır. Bir alan başka bir eşlenmiş modele referans veriyorsa processor, üretilen kodda doğrudan `toX()` çağrısı zincirler — fazladan anotasyon gerekmez.

---

## Temel İç İçe Eşleştirme

Aşağıdaki örnekte `OrderRemote`, bir `AddressRemote` alanı içeriyor. Her iki remote sınıfı da kendi domain karşılığına `@MapTo` ile işaretlenmiş:

```kotlin
@MapTo(AddressDomain::class)
data class AddressRemote(
    val street: String,
    val city: String,
)

data class AddressDomain(
    val street: String,
    val city: String,
)

@MapTo(OrderDomain::class)
data class OrderRemote(
    val id: String,
    val address: AddressRemote,
)

data class OrderDomain(
    val id: String,
    val address: AddressDomain,
)
```

Processor `OrderRemote.toOrderDomain()` üretirken `address` alanının tipini kontrol eder, `AddressRemote → AddressDomain` eşleştirmesinin var olduğunu görür ve çağrısı zincirler:

```kotlin
public fun OrderRemote.toOrderDomain(): OrderDomain = OrderDomain(
    id      = id,
    address = address.toAddressDomain(),
)
```

`toAddressDomain()` ayrıca `AddressRemoteMappers.kt` dosyasında da üretilir; her şey derleme zamanında bağlanır.

---

## Nullable İç Modeller

İç model nullable ise güvenli çağrı (`?.`) otomatik eklenir:

```kotlin
@MapTo(ProfileDomain::class)
data class ProfileRemote(
    val userId: String,
    val address: AddressRemote?,    // opsiyonel
)

data class ProfileDomain(
    val userId: String,
    val address: AddressDomain?,
)
```

Üretilen:

```kotlin
public fun ProfileRemote.toProfileDomain(): ProfileDomain = ProfileDomain(
    userId  = userId,
    address = address?.toAddressDomain(),
)
```

Hedef `address` alanı zorunlu (`AddressDomain`, nullable değil) olsaydı null-safety kuralları devreye girerdi — bkz. [Null-Safety](null-safety.md).

---

## Döngüsel Bağımlılıklar

kmap, **koşulsuz döngüleri** derleme zamanında yakalar ve hata verir:

```kotlin
// DERLEME HATASI — koşulsuz döngü:
@MapTo(BDomain::class) data class A(val b: B)   // non-null
@MapTo(ADomain::class) data class B(val a: A)   // non-null
// e: Mapping cycle detected: A -> B -> A. This would cause infinite construction at runtime.
//    Break the cycle with a nullable field, a collection, or @Ignore.
```

Ancak döngü **koşullu** ise (nullable alan veya koleksiyon üzerinden) geçerlidir:

```kotlin
// OK — nullable parent referansı
@MapTo(CategoryDomain::class)
data class CategoryRemote(
    val id: String,
    val parent: CategoryRemote?,     // nullable → koşullu döngü, izin verilir
)

// OK — koleksiyon üzerinden
@MapTo(NodeDomain::class)
data class NodeRemote(
    val value: Int,
    val children: List<NodeRemote>,  // liste → koşullu döngü, izin verilir
)
```

---

Sonraki adım: [Koleksiyonlar](koleksiyonlar.md)
