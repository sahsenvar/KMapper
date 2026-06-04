# Alan Eşleştirme — @FieldMap ve @Ignore

kmap'in varsayılan davranışı, kaynak ve hedef arasında **ada göre** alan eşleştirmesidir. Alan adları veya tipler uyuşmadığında `@FieldMap`, bir alanın eşleştirme dışında tutulması gerektiğinde `@Ignore` devreye girer.

---

## @FieldMap — Alan Yeniden Adlandırma

```kotlin
@FieldMap(fieldName: String, targetClass: KClass<*> = Nothing::class)
```

`@FieldMap` özelliğe eklenir. `fieldName`, hedef sınıftaki alan adını belirtir.

```kotlin
@MapTo(UserDomain::class)
data class UserRemote(
    @FieldMap(fieldName = "userId")   // remote'ta "id", domain'de "userId"
    val id: String,
    val email: String,
)

data class UserDomain(
    val userId: String,
    val email: String,
)
```

Üretilen kod:

```kotlin
public fun UserRemote.toUserDomain(): UserDomain = UserDomain(
    userId = id,
    email  = email,
)
```

---

## Çoklu Hedefte Hedef Sınıfını Belirtme

Aynı kaynak sınıf birden fazla hedefe eşleniyorsa (`@MapTo` tekrarlı) ve bir alan yalnızca **belirli bir hedef** için farklı adlandırılıyorsa `targetClass` parametresi devreye girer. `targetClass` verilmeyen `@FieldMap` tüm hedeflere uygulanır (wildcard).

```kotlin
@MapTo(UserDomain::class)
@MapTo(UserCache::class)
data class UserRemote(
    // Yalnızca UserDomain için "userId", UserCache için "id" (ada göre eşleşir)
    @FieldMap(fieldName = "userId", targetClass = UserDomain::class)
    val id: String,
    val email: String,
)
```

Üretilen kod:

```kotlin
public fun UserRemote.toUserDomain(): UserDomain = UserDomain(
    userId = id,      // @FieldMap(targetClass=UserDomain::class) uygulandı
    email  = email,
)

public fun UserRemote.toUserCache(): UserCache = UserCache(
    id    = id,       // ada göre eşleşti — @FieldMap uygulanmadı
    email = email,
)
```

Birden fazla `@FieldMap` aynı alana farklı hedefler için eklenebilir:

```kotlin
@FieldMap(fieldName = "userId",  targetClass = UserDomain::class)
@FieldMap(fieldName = "cacheId", targetClass = UserCache::class)
val id: String,
```

---

## @Ignore — Alanı Dışla

`@Ignore` ile işaretlenen alan **hiçbir hedef için** eşleştirilmez. Hedef sınıfın constructor'ında o alan yoksa ya da dışarıdan parametre olarak sağlanıyorsa kullanılır.

```kotlin
@MapTo(UserDomain::class)
data class UserRemote(
    val id: String,
    val email: String,
    @Ignore val rawJson: String,   // domain modelde bu alan yok
)

data class UserDomain(
    val id: String,
    val email: String,
)
```

Üretilen kod `rawJson`'a hiç dokunmaz:

```kotlin
public fun UserRemote.toUserDomain(): UserDomain = UserDomain(
    id    = id,
    email = email,
)
```

Hedef constructor'ında `@Ignore`'lu alana karşılık gelen parametre varsa ve varsayılan değer taşımıyorsa **derleme hatası** alırsınız — bunun için hedef parametreye varsayılan bir değer verin ya da alanı `@Ignore` yerine `@MapDefaultValue` ile ele alın.

---

## Öncelik Sırası

Bir alan için eşleştirme kuralı şu öncelik sırasıyla belirlenir:

1. `@Ignore` → tamamen dışla
2. Hedef-spesifik `@FieldMap(targetClass = X::class)` → yalnızca o hedef için uygula
3. Wildcard `@FieldMap(targetClass = Nothing::class)` → tüm hedeflere uygula
4. Ada göre eşleştirme → kaynak ve hedef alan adı aynıysa doğrudan ata

---

Sonraki adım: [Null-Safety ve @MapDefaultValue](null-safety.md)
