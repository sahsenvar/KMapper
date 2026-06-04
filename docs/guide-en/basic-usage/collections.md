# Collections

kmap maps each element in `List` and `Set` collections automatically. No special annotation is needed for collection fields — when the element type is a mapped model, the processor detects this on its own.

---

## List Mapping

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

The generated code uses `.map { it.toTagDomain() }`:

```kotlin
public fun ArticleRemote.toArticleDomain(): ArticleDomain = ArticleDomain(
    title = title,
    tags  = tags.map { it.toTagDomain() },
)
```

---

## Set Mapping

The same rule applies to `Set`; the processor generates `.map { }.toSet()`:

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

Generated:

```kotlin
public fun RoleRemote.toRoleDomain(): RoleDomain = RoleDomain(
    name        = name,
    permissions = permissions.map { it.toPermissionDomain() }.toSet(),
)
```

---

## Nullable Collections

When the source collection is nullable, a safe call is added:

```kotlin
@MapTo(ArticleDomain::class)
data class ArticleRemote(
    val title: String,
    val tags: List<TagRemote>?,    // optional list
)

data class ArticleDomain(
    val title: String,
    val tags: List<TagDomain>?,
)
```

Generated:

```kotlin
public fun ArticleRemote.toArticleDomain(): ArticleDomain = ArticleDomain(
    title = title,
    tags  = tags?.map { it.toTagDomain() },
)
```

If the target `tags` field were required (`List<TagDomain>`, not nullable), the null-safety rules would apply — see [Null-Safety](null-safety.md).

---

## Nested Collections

Collection elements can themselves contain collections; the processor chains every level:

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

Generated:

```kotlin
public fun CategoryRemote.toCategoryDomain(): CategoryDomain = CategoryDomain(
    name          = name,
    subCategories = subCategories.map { it.toCategoryDomain() },
)
```

---

## Immutable Collections

If you want to use `kotlinx.collections.immutable` types such as `PersistentList`, `ImmutableList`, or `ImmutableSet` as target types, add the `converters-immutable` module. That module provides wrapper functions annotated with `@CollectionWrapper`, which the processor discovers automatically.

For details, see [Immutable Collections (converters-immutable)](../type-conversion/immutable.md).
