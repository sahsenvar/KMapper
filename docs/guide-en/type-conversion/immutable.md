# Immutable Collections — converters-compose

kmap's `core` module only understands stdlib `List`/`Set` mappings. To use `kotlinx.collections.immutable` types such as `PersistentList`, `ImmutableList`, or `ImmutableSet` as target types, add the **`converters-compose`** module.

---

## Setup

Add the `converters-compose` dependency to the relevant module:

```kotlin
// build.gradle.kts
commonMainImplementation("com.sahsenvar.kmapper:converters-compose:<version>")
```

The KSP dependency does not change; the processor discovers wrappers from `converters-compose` automatically via the descriptor mechanism.

---

## Provided Wrapper Functions

`converters-compose` defines three functions annotated with `@CollectionWrapper`, one for each immutable collection type:

```kotlin
@CollectionWrapper(forType = PersistentList::class)
fun <T> List<T>.asPersistentList(): PersistentList<T> = toPersistentList()

@CollectionWrapper(forType = ImmutableList::class)
fun <T> List<T>.asImmutableList(): ImmutableList<T> = toImmutableList()

@CollectionWrapper(forType = ImmutableSet::class)
fun <T> List<T>.asImmutableSet(): ImmutableSet<T> = toImmutableSet()
```

You do not call these functions directly; when the processor sees the target field type, it selects the appropriate wrapper automatically.

---

## Usage

All you need to do is use an immutable collection type in the target class:

```kotlin
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList

@MapTo(ArticleDomain::class)
data class ArticleRemote(
    val title: String,
    val tags: List<TagRemote>,
)

data class TagDomain(val id: String, val name: String)

data class ArticleDomain(
    val title: String,
    val tags: PersistentList<TagDomain>,    // target: PersistentList
)
```

The processor sees that the target type of `tags` is `PersistentList`, finds the `asPersistentList` wrapper on the classpath, and chains it:

```kotlin
public fun ArticleRemote.toArticleDomain(): ArticleDomain = ArticleDomain(
    title = title,
    tags  = tags.map { it.toTagDomain() }.asPersistentList(),
)
```

The same mechanism works for `ImmutableList` and `ImmutableSet`.

---

## @CollectionWrapper — How It Works

The `@CollectionWrapper(forType = PersistentList::class)` annotation is placed on a function and compiled with `BINARY` retention. During its own KSP run, `converters-compose` sees these functions and generates **descriptor objects** into the `com.sahsenvar.kmapper.generated` package. The consuming module's processor run then discovers these descriptors via `resolver.getDeclarationsFromPackage(...)`.

If more than one `@CollectionWrapper` for the same `forType` is found on the classpath, the processor reports a **compile error** — which wrapper is active should never be silent.

---

## Writing Your Own @CollectionWrapper Function

If you need to support an immutable collection type not provided by the library, you can write your own wrapper:

```kotlin
// in your own module:
@CollectionWrapper(forType = MyImmutableList::class)
fun <T> List<T>.asMyImmutableList(): MyImmutableList<T> = MyImmutableList.copyOf(this)
```

Once this function is compiled with `BINARY` retention, the processor discovers it automatically. You do not need to add it to a `@KMapperConfig` list.

---

Other references: [Collections (Basic Usage)](../basic-usage/collections.md) | [@KMapperConfig](kmapperconfig.md)
