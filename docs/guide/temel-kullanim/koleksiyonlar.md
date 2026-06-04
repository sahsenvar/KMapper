# Koleksiyonlar

kmap, `List` ve `Set` koleksiyonlarındaki her elemanı otomatik olarak eşler. Koleksiyon tipine özel bir anotasyon gerekmez — element tipi eşlenmiş bir model olduğunda processor bunu kendisi fark eder.

---

## List Eşleştirme

```kotlin
@MapTo(TagDomain::class)
data class TagRemote(val id: String, val name: String)

data class TagDomain(val id: String, val name: String)

@MapTo(ArticleDomain::class)
data class ArticleRemote(
    val title: String,
    val tags: List<TagRemote>,
)

data class ArticleDomain(
    val title: String,
    val tags: List<TagDomain>,
)
```

Üretilen kod `.map { it.toTagDomain() }` kullanır:

```kotlin
public fun ArticleRemote.toArticleDomain(): ArticleDomain = ArticleDomain(
    title = title,
    tags  = tags.map { it.toTagDomain() },
)
```

---

## Set Eşleştirme

`Set` için aynı kural geçerlidir; processor `.map { }.toSet()` üretir:

```kotlin
@MapTo(PermissionDomain::class)
data class PermissionRemote(val code: String)

data class PermissionDomain(val code: String)

@MapTo(RoleDomain::class)
data class RoleRemote(
    val name: String,
    val permissions: Set<PermissionRemote>,
)

data class RoleDomain(
    val name: String,
    val permissions: Set<PermissionDomain>,
)
```

Üretilen:

```kotlin
public fun RoleRemote.toRoleDomain(): RoleDomain = RoleDomain(
    name        = name,
    permissions = permissions.map { it.toPermissionDomain() }.toSet(),
)
```

---

## Nullable Koleksiyonlar

Kaynak koleksiyon nullable ise güvenli çağrı eklenir:

```kotlin
@MapTo(ArticleDomain::class)
data class ArticleRemote(
    val title: String,
    val tags: List<TagRemote>?,    // opsiyonel liste
)

data class ArticleDomain(
    val title: String,
    val tags: List<TagDomain>?,
)
```

Üretilen:

```kotlin
public fun ArticleRemote.toArticleDomain(): ArticleDomain = ArticleDomain(
    title = title,
    tags  = tags?.map { it.toTagDomain() },
)
```

Hedef `tags` alanı zorunlu (`List<TagDomain>`, nullable değil) olsaydı null-safety kuralları devreye girerdi — bkz. [Null-Safety](null-safety.md).

---

## İç İçe Koleksiyonlar

Koleksiyon elemanlarının kendisi de koleksiyon içerebilir; processor her seviyeyi zincirler:

```kotlin
@MapTo(CategoryDomain::class)
data class CategoryRemote(
    val name: String,
    val subCategories: List<CategoryRemote>,
)

data class CategoryDomain(
    val name: String,
    val subCategories: List<CategoryDomain>,
)
```

Üretilen:

```kotlin
public fun CategoryRemote.toCategoryDomain(): CategoryDomain = CategoryDomain(
    name          = name,
    subCategories = subCategories.map { it.toCategoryDomain() },
)
```

---

## Immutable Koleksiyonlar

`PersistentList`, `ImmutableList`, `ImmutableSet` gibi `kotlinx.collections.immutable` tiplerini hedef olarak kullanmak istiyorsanız `converters-compose` modülünü ekleyin. Bu modül `@CollectionWrapper` anotasyonunu taşıyan sarmalayıcı fonksiyonlar sağlar ve processor bunları otomatik keşfeder.

Ayrıntılar için bkz. [Immutable Koleksiyonlar (converters-compose)](../tip-donusumu/immutable.md).
