# Immutable Collections — converters-immutable

kmap's `core` module only understands stdlib `List`/`Set` mappings. To use `kotlinx.collections.immutable` types such as `PersistentList`, `ImmutableList`, `ImmutableSet`, or `PersistentSet` as target types, add the **`converters-immutable`** module.

---

## Setup

Add the `converters-immutable` dependency to the relevant module:

```kotlin
// build.gradle.kts
commonMainImplementation("io.github.sahsenvar:kmapper-converters-immutable:1.0.0")
```

The KSP dependency does not change. However, you must **explicitly list the wrappers you need in `@KMapperConfig.wrappers`** — adding the dependency alone is not sufficient:

```kotlin
import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.immutable.PersistentListWrapper
import com.sahsenvar.kmapper.immutable.ImmutableListWrapper
import com.sahsenvar.kmapper.immutable.ImmutableSetWrapper
import com.sahsenvar.kmapper.immutable.PersistentSetWrapper

@KMapperConfig(
    wrappers = [
        PersistentListWrapper::class,
        ImmutableListWrapper::class,
        ImmutableSetWrapper::class,
        PersistentSetWrapper::class,
    ]
)
object AppMapperConfig
```

You only need to include the wrappers you actually use.

---

## Provided Wrapper Objects

`converters-immutable` defines four `object`s annotated with `@CollectionWrapper`, one for each immutable collection type:

```kotlin
@CollectionWrapper(forType = PersistentList::class)
object PersistentListWrapper {
    fun <T> wrap(items: List<T>): PersistentList<T> = items.toPersistentList()
}

@CollectionWrapper(forType = ImmutableList::class)
object ImmutableListWrapper {
    fun <T> wrap(items: List<T>): ImmutableList<T> = items.toImmutableList()
}

@CollectionWrapper(forType = ImmutableSet::class)
object ImmutableSetWrapper {
    fun <T> wrap(items: List<T>): ImmutableSet<T> = items.toImmutableSet()
}

@CollectionWrapper(forType = PersistentSet::class)
object PersistentSetWrapper {
    fun <T> wrap(items: List<T>): PersistentSet<T> = items.toPersistentSet()
}
```

You do not call these objects directly; the processor selects the matching wrapper from `@KMapperConfig.wrappers` and emits a `wrap(...)` call when it sees the target field type.

**Cross-kind conversion:** Even if the source field is a `List<T>`, `Set<T>`, or `PersistentList<T>`, if the target field is `PersistentSet<T>` the processor emits `PersistentSetWrapper.wrap(...)`. The source and target collection kinds do not need to match.

---

## Usage

Use an immutable collection type in the target class and add the corresponding wrapper to `@KMapperConfig.wrappers`:

```kotlin
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

@KMapperConfig(wrappers = [PersistentListWrapper::class])
object AppMapperConfig
```

The processor sees that the target type of `tags` is `PersistentList`, selects `PersistentListWrapper` from the `wrappers` list, and generates:

```kotlin
public fun ArticleRemote.toArticleDomain(): ArticleDomain = ArticleDomain(
    title = title,
    tags  = PersistentListWrapper.wrap(tags.map { it.toTagDomain() }),
)
```

The same mechanism works for `ImmutableList`, `ImmutableSet`, and `PersistentSet`.

---

## @CollectionWrapper — How It Works

The `@CollectionWrapper(forType = PersistentList::class)` annotation is placed on an `object` and compiled with `BINARY` retention. During the consumer module's KSP run, the processor reads `@KMapperConfig(wrappers = [...])` and resolves each wrapper object's `@CollectionWrapper.forType` value from the compiled dependency artifact — this is standard type+annotation resolution and works on all platforms (JVM, Android, iOS/Native).

No `getDeclarationsFromPackage` or auto-discovery is used; wrappers must be listed explicitly.

If more than one `@CollectionWrapper` for the same `forType` appears in the `wrappers` list, the processor reports a **compile error**.

---

## Writing Your Own @CollectionWrapper Object

If you need to support an immutable collection type not provided by the library, you can write your own wrapper:

```kotlin
// in your own module:
@CollectionWrapper(forType = MyImmutableList::class)
object MyImmutableListWrapper {
    fun <T> wrap(items: List<T>): MyImmutableList<T> = MyImmutableList.copyOf(items)
}
```

Then add it to the consuming module's `@KMapperConfig.wrappers`:

```kotlin
@KMapperConfig(wrappers = [MyImmutableListWrapper::class])
object AppMapperConfig
```

---

Other references: [Collections (Basic Usage)](../basic-usage/collections.md) | [@KMapperConfig](kmapperconfig.md)
