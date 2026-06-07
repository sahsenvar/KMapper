# 5 Dakikada İlk Mapper

Bu sayfada bir REST yanıtını (`RemoteModel`) domain modeline dönüştüren ilk mapper'ı yazıyoruz.

## 1. Modelleri tanımla

Hedef (domain) modelin sade Kotlin sınıfıdır:

```kotlin
data class UserDomain(
    val id: String,
    val email: String,
)
```

Kaynak (remote) modele `@MapTo` ekleyerek "bunu `UserDomain`'e eşleyebilmek istiyorum" dersiniz:

```kotlin
import com.sahsenvar.kmapper.annotations.MapTo

@MapTo(UserDomain::class)
data class UserRemote(
    val id: String,
    val email: String,
)
```

> KMapper, modellerinizin belirli bir arayüzü (`RemoteModel`, `DomainModel` vb.) uygulamasını **zorunlu tutmaz**. Bu arayüzler yalnızca kendi mimarinizde bir konvansiyondur; isterseniz kullanabilirsiniz.

## 2. Derle

Projeyi derleyin. KMapper, kaynakla aynı pakette `UserRemoteMappers.kt` üretir:

```kotlin
public fun UserRemote.toUserDomain(): UserDomain = UserDomain(
    id = id,
    email = email,
)
```

> **Not:** Örneklerde sadeleştirilmiş gövde gösterilir. Üretilen gerçek kod ayrıca `KMapper.hasListeners` korumalı gözlemleme guard'ları içerir ve gövdesi `val result = …; return result` biçimindedir — bkz. [MappingListener](../gozlemleme/listener.md).

## 3. Kullan

```kotlin
val remote = UserRemote(id = "42", email = "a@b.com")
val domain: UserDomain = remote.toUserDomain()
```

Hepsi bu kadar. İsim eşleşen alanlar otomatik kopyalanır.

## İç içe modeller otomatik çalışır

Bir alan kendisi de `@MapTo`'lu bir tipse, KMapper iç eşlemeyi otomatik çağırır:

```kotlin
data class AddressDomain(val city: String)
data class CustomerDomain(val name: String, val address: AddressDomain)

@MapTo(AddressDomain::class)
data class AddressRemote(val city: String)

@MapTo(CustomerDomain::class)
data class CustomerRemote(val name: String, val address: AddressRemote)
```

Üretilen kod iç eşlemeyi zincirler:

```kotlin
public fun CustomerRemote.toCustomerDomain(): CustomerDomain = CustomerDomain(
    name = name,
    address = address.toAddressDomain(),
)
```

## Null-safety daha baştan devrede

Diyelim remote alan nullable ama domain alanı zorunlu:

```kotlin
data class UserDomain(val id: String)          // zorunlu

@MapTo(UserDomain::class)
data class UserRemote(val id: String?)          // nullable
```

KMapper, `null` durumunu **sessizce yutmaz** — gürültülü bir istisna üretir:

```kotlin
public fun UserRemote.toUserDomain(): UserDomain = UserDomain(
    id = id ?: throw MappingException.RequiredFieldMissing("id"),
)
```

Varsayılan vermek isterseniz `@MapDefaultValue` kullanabilirsiniz (bkz. [Null-Safety](../temel-kullanim/null-safety.md)).

## Sırada ne var?

- Alan adları farklıysa: **[Alan Eşleştirme (@FieldMap)](../temel-kullanim/alan-eslestirme.md)**
- Tip dönüşümü gerekiyorsa (örn. `String` → `Int`): **[Tip Dönüşümü](../tip-donusumu/builtin.md)**
- Enum eşlemesi: **[MappableEnum](../enum/mappable-enum.md)**
- Ters yön (`DomainModel → RemoteModel`): **[@MapTo ve @MapFrom](../temel-kullanim/mapto-mapfrom.md)**
