# NonEmptyList — converters-arrow

To use Arrow's `NonEmptyList<T>` as a mapping target, add the **`converters-arrow`** module. This module uses the `@CollectionWrapper` mechanism — **unlike scalar converters, you must list it explicitly in `@KMapperConfig.wrappers`**; adding the dependency alone is not sufficient.

> **Note:** `converters-arrow` is new in version **0.2.0** and is not yet published to Maven Central.
> Until it is released, use `publishToMavenLocal` + `mavenLocal()`.
> `core` and `processor` are still available from Maven Central at `0.1.0`.

---

## Setup

```kotlin
// settings.gradle.kts — add mavenLocal for the pre-release add-on
dependencyResolutionManagement {
    repositories {
        mavenLocal()        // for 0.2.0 add-ons
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts (consuming module)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.sahsenvar:kmapper-core:0.1.0")
            implementation("io.github.sahsenvar:kmapper-converters-arrow:0.2.0")
        }
    }
}
```

The KSP dependency does not change, but you must add the wrapper to `@KMapperConfig.wrappers`:

```kotlin
import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.arrow.NonEmptyListWrapper

@KMapperConfig(wrappers = [NonEmptyListWrapper::class])
object AppMapperConfig
```

KSP configuration:

```kotlin
dependencies {
    add("kspCommonMainMetadata", "io.github.sahsenvar:kmapper-processor:0.1.0")
}
```

---

## Provided Wrapper Object

`converters-arrow` defines one `@CollectionWrapper` wrapper:

```kotlin
@CollectionWrapper(forType = NonEmptyList::class)
object NonEmptyListWrapper {
    fun <T> wrap(items: List<T>): NonEmptyList<T> =
        items.toNonEmptyListOrNull()
            ?: throw MappingException.EmptyCollection("NonEmptyList source was empty")
}
```

You do not call this object directly; the processor emits a `NonEmptyListWrapper.wrap(...)` call whenever it sees a `NonEmptyList` target field.

---

## Usage

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

## Roadmap

`Option<NonEmptyList<T>>` support (empty source → `None`, non-empty source → `Some(nel)`) is planned for a future release.

---

Next: [Date/Time Converters →](datetime.md) | See also: [@KMapperConfig](kmapperconfig.md) | [Collections](../basic-usage/collections.md)
