# NonEmptyList — converters-arrow

To use Arrow's `NonEmptyList<T>` as a mapping target, add the **`converters-arrow`** module. This module uses the `@CollectionWrapper` mechanism — **unlike scalar converters, you must list it explicitly in `@KMapperConfig.wrappers`**; adding the dependency alone is not sufficient.

---

## Setup

```kotlin
// build.gradle.kts (consuming module)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.sahsenvar:kmapper-core:1.0.0")
            implementation("io.github.sahsenvar:kmapper-converters-arrow:1.0.0")
        }
    }
}
```

The KSP dependency does not change, but you must add the wrapper(s) you need to `@KMapperConfig.wrappers`:

```kotlin
import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.arrow.NonEmptyListWrapper
import com.sahsenvar.kmapper.arrow.NonEmptySetWrapper

// To use both:
@KMapperConfig(wrappers = [NonEmptyListWrapper::class, NonEmptySetWrapper::class])
object AppMapperConfig
```

KSP configuration:

```kotlin
dependencies {
    add("kspCommonMainMetadata", "io.github.sahsenvar:kmapper-processor:1.0.0")
}
```

---

## Provided Wrapper Objects

`converters-arrow` defines two `@CollectionWrapper` wrappers:

```kotlin
@CollectionWrapper(forType = NonEmptyList::class)
object NonEmptyListWrapper {
    fun <T> wrap(items: List<T>): NonEmptyList<T> =
        items.toNonEmptyListOrNull()
            ?: throw MappingException.EmptyCollection("NonEmptyList source was empty")
}

@CollectionWrapper(forType = NonEmptySet::class)
object NonEmptySetWrapper {
    fun <T> wrap(items: List<T>): NonEmptySet<T> =
        items.toNonEmptySetOrNull()
            ?: throw MappingException.EmptyCollection("NonEmptySet source was empty")
}
```

You do not call these objects directly; the processor emits the appropriate `wrap(...)` call based on the target field type.

---

## Usage

### NonEmptyList

Use `NonEmptyList<T>` in the target class and add `NonEmptyListWrapper::class` to `@KMapperConfig.wrappers`:

```kotlin
import arrow.core.NonEmptyList
import com.sahsenvar.kmapper.arrow.NonEmptyListWrapper

@KMapperConfig(wrappers = [NonEmptyListWrapper::class])
object AppMapperConfig

@MapTo(PostDomain::class)
data class PostRemote(
    val title: String,
    val tags: List<TagRemote>,
)

data class TagDomain(val id: String, val name: String)

data class PostDomain(
    val title: String,
    val tags: NonEmptyList<TagDomain>,   // target: NonEmptyList
)
```

The processor sees that the target type of `tags` is `NonEmptyList` and generates:

```kotlin
public fun PostRemote.toPostDomain(): PostDomain = PostDomain(
    title = title,
    tags  = NonEmptyListWrapper.wrap(tags.map { it.toTagDomain() }),
)
```

### NonEmptySet

`NonEmptySet<T>` is supported by the same mechanism. Add `NonEmptySetWrapper::class` to `@KMapperConfig.wrappers`:

```kotlin
import arrow.core.NonEmptySet
import com.sahsenvar.kmapper.arrow.NonEmptySetWrapper

@KMapperConfig(wrappers = [NonEmptySetWrapper::class])
object AppMapperConfig

@MapTo(RoleDomain::class)
data class RoleRemote(
    val permissions: List<PermissionRemote>,
)

data class PermissionDomain(val name: String)

data class RoleDomain(
    val permissions: NonEmptySet<PermissionDomain>,   // target: NonEmptySet
)
```

Generated:

```kotlin
public fun RoleRemote.toRoleDomain(): RoleDomain = RoleDomain(
    permissions = NonEmptySetWrapper.wrap(permissions.map { it.toPermissionDomain() }),
)
```

> **Note:** `NonEmptySet` has set semantics — duplicate elements from the source list are deduplicated. If the source list is empty, `MappingException.EmptyCollection("NonEmptySet source was empty")` is thrown.

---

## Empty List — MappingException.EmptyCollection

If `source.tags` is an empty list, `NonEmptyListWrapper.wrap(...)` throws `MappingException.EmptyCollection` at runtime. This is a necessary consequence of Arrow's **non-empty** semantics:

```kotlin
// If source tags = []:
// → MappingException.EmptyCollection("NonEmptyList source was empty")
```

Guard against this by wrapping the mapping call in `try/catch` or `runCatching` when the source list may be empty.

---

## @CollectionWrapper — How It Works

The `@CollectionWrapper(forType = NonEmptyList::class)` annotation is placed on an `object` and compiled with `BINARY` retention. During the consumer module's KSP run, the processor reads `@KMapperConfig(wrappers = [NonEmptyListWrapper::class])` and resolves `NonEmptyListWrapper`'s `@CollectionWrapper.forType = NonEmptyList::class` from the compiled dependency artifact — this is standard type+annotation resolution and works on all platforms including KMP/iOS.

No `getDeclarationsFromPackage` or auto-discovery is used.

---

## Option\<T\> Mapping

In addition to `NonEmptyList`, `converters-arrow` on the classpath also enables `Option<T>` mapping.
This is a **processor rule** — no converter object or `@KMapperConfig` entry is needed.

### T? / T → Option\<T\>

When the target field type is `Option<T>`, the processor emits `Option.fromNullable(...)`:

```kotlin
@MapTo(UserDomain::class)
data class UserRemote(
    val name: String?,      // nullable source
    val role: String,       // non-null source
)

data class UserDomain(
    val name: Option<String>,
    val role: Option<String>,
)
```

Generated:

```kotlin
public fun UserRemote.toUserDomain(): UserDomain = UserDomain(
    name = Option.fromNullable(name),
    role = Option.fromNullable(role),
)
```

### Option\<T\> → T?

When the source field type is `Option<T>`, the processor emits `getOrNull()`:

```kotlin
@MapTo(UserRemote::class)
data class UserDomain(
    val name: Option<String>,
)

data class UserRemote(
    val name: String?,
)
```

Generated:

```kotlin
public fun UserDomain.toUserRemote(): UserRemote = UserRemote(
    name = name.getOrNull(),
)
```

If the target field is non-null (e.g. `val name: String`), a null-guard fires after `getOrNull()`,
following the standard null-safety rules: `name.getOrNull() ?: throw MappingException.RequiredFieldMissing("name")`.

### Nested models inside Option

If the inner type `T` is itself a mappable type (with a generated `toT()` extension), the processor
emits the mapper call inside the wrap/unwrap:

```kotlin
// source field: role: RoleRemote?  →  target: Option<RoleDomain>
// Generated: Option.fromNullable(role?.toRoleDomain())
```

---

## Roadmap

`Option<NonEmptyList<T>>` support (empty source → `None`, non-empty source → `Some(nel)`) is planned for a future release.

---

Next: [Date/Time Converters →](datetime.md) | See also: [@KMapperConfig](kmapperconfig.md) | [Collections](../basic-usage/collections.md)
