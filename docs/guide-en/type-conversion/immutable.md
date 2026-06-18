# Immutable Collections — converters-immutable

[`@CollectionWrapper`](custom-converter.md#collection-wrappers) objects for
[kotlinx-collections-immutable](https://github.com/Kotlin/kotlinx.collections.immutable):
map wire `List`s straight into immutable domain collections.

## Setup

```kotlin
commonMain.dependencies {
    implementation("io.github.sahsenvar:kmapper-converters-immutable:2.2.2")
}
```

Register the wrappers you use in [`@KMapperConfig`](kmapperconfig.md):

```kotlin
@KMapperConfig(wrappers = [PersistentListWrapper::class, PersistentSetWrapper::class])
object AppMapperConfig
```

## Wrappers (`com.sahsenvar.kmapper.immutable`)

| Wrapper | Maps `List<T>` ↔ |
|---------|------------------|
| `PersistentListWrapper` | `PersistentList<T>` |
| `ImmutableListWrapper` | `ImmutableList<T>` |
| `PersistentSetWrapper` | `PersistentSet<T>` |
| `ImmutableSetWrapper` | `ImmutableSet<T>` |

## Usage

```kotlin
data class User(val tags: PersistentList<Tag>)

@MapTo(User::class)
data class UserResponse(val tags: List<TagResponse>) // elements map via the Tag sub-mapper
```

Element conversion (including nested `@MapTo` pairs and the
[element ladder](../basic-usage/collections.md)) is unchanged — wrappers only swap the
container. Both directions work: a `PersistentList` source unwraps back to `List` for the
reverse mapping.

> Next: **[Arrow →](arrow.md)**
