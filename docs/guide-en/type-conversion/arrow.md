# NonEmptyList — converters-arrow

To use Arrow's `NonEmptyList<T>` as a mapping target, add the **`converters-arrow`** module. This module uses the `@CollectionWrapper` mechanism — you do **not** need to list it in `@KMapperConfig`; adding the dependency is sufficient.

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

The KSP dependency does not change:

```kotlin
dependencies {
    add("kspCommonMainMetadata", "io.github.sahsenvar:kmapper-processor:0.1.0")
}
```

---

## Provided Wrapper Function

`converters-arrow` defines one `@CollectionWrapper` wrapper:

```kotlin
@CollectionWrapper(forType = NonEmptyList::class)
fun <T> List<T>.asNonEmptyList(): NonEmptyList<T> =
    toNonEmptyListOrNull() ?: throw MappingException.EmptyCollection("NonEmptyList source was empty")
```

The processor discovers this wrapper automatically via the descriptor mechanism; you only add the dependency.

---

## Usage

All you need is to use `NonEmptyList<T>` in the target class:

```kotlin
import arrow.core.NonEmptyList

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

The processor sees that the target type of `tags` is `NonEmptyList` and chains the wrapper:

```kotlin
public fun PostRemote.toPostDomain(): PostDomain = PostDomain(
    title = title,
    tags  = tags.map { it.toTagDomain() }.asNonEmptyList(),
)
```

---

## Empty List — MappingException.EmptyCollection

If `source.tags` is an empty list, `asNonEmptyList()` throws `MappingException.EmptyCollection` at runtime. This is a necessary consequence of Arrow's **non-empty** semantics:

```kotlin
// If source tags = []:
// → MappingException.EmptyCollection("NonEmptyList source was empty")
```

Guard against this by wrapping the mapping call in `try/catch` or `runCatching` when the source list may be empty.

---

## @CollectionWrapper — How It Works

The `@CollectionWrapper(forType = NonEmptyList::class)` annotation is compiled with `BINARY` retention. During its own KSP run, `converters-arrow` sees this wrapper and generates a **descriptor object** into the `com.sahsenvar.kmapper.generated` package. The consuming module's processor run discovers this descriptor via `resolver.getDeclarationsFromPackage(...)`.

If more than one `@CollectionWrapper` for the same `forType` is found on the classpath, the processor reports a **compile error**.

---

## Roadmap

`Option<NonEmptyList<T>>` support (empty source → `None`, non-empty source → `Some(nel)`) is planned for a future release.

---

Next: [Date/Time Converters →](datetime.md) | See also: [@KMapperConfig](kmapperconfig.md) | [Collections](../basic-usage/collections.md)
