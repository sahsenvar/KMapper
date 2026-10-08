# Return Wrappers

Every `@MapTo`/`@MapFrom` mapping always generates the plain core: `fun Source.toX(): X`,
which throws a [`MappingException`](../error-handling/mapping-exception.md) on a hard failure.
A **return wrapper** adds one or more extra extension functions *on top of* that plain core —
it never replaces it, and nested mappings always call the plain core, never a wrapper, so
wrappers never change how mappers compose.

```kotlin
@MapTo(User::class, wrapper = KMapperWrapper.KtResult::class)
data class UserResponse(val id: Long, val name: String)

// both generated:
// fun UserResponse.toUser(): User
// fun UserResponse.toUserResult(): Result<User>
```

## The built-ins

KMapper ships four wrappers, passed as `wrapper = KMapperWrapper.<W>::class` to
`@MapTo`/`@MapFrom` (you can add your own, see below):

| Wrapper | Adds | Notes |
|---------|------|-------|
| `Default` (the default) | nothing by itself | resolves to the **module-wide setting**, or [`None`](#none) if nothing is configured |
| `None` | nothing | only the plain `toX(): X` |
| `KtResult` | `fun Source.toXResult(): Result<X>` | hard failures become `Result.failure` |
| `Flow` | `fun Source.toXFlow(): Flow<X>` and `fun Flow<Source>.toXFlow(): Flow<X>` | a cold single-value flow (the mapping runs, and may throw, on collection) and a flow that maps every emitted source |

```kotlin
@MapTo(User::class, wrapper = KMapperWrapper.Flow::class)
data class UserResponse(val id: Long, val name: String)

val single: Flow<User> = response.toUserFlow()
val many: Flow<User> = responses.toUserFlow() // responses: Flow<UserResponse>
```

`KMapperWrapper.Flow` is why `kmapper-core` depends on `kotlinx-coroutines-core` (`api`,
`1.10.2`) — it's a plain dependency, not a special case; see [Writing your own wrapper](#writing-your-own-wrapper).

## Per-mapping vs. module-wide

Leaving `wrapper` unset is the same as `wrapper = KMapperWrapper.Default::class` — it defers to
whatever the *module* has configured, or `None` if nothing has. Two ways to set that module-wide
default, in order of preference:

**1. The Gradle plugin.** Apply it alongside the KSP plugin, in the same module:

```kotlin
// build.gradle.kts
plugins {
    id("com.google.devtools.ksp") version "2.3.10-2.0.5"
    id("io.github.sahsenvar.kmapper") version "3.0.1"
}

import com.sahsenvar.kmapper.gradle.KMapperWrapper

KMapper {
    wrapper = KMapperWrapper.KtResult // None (default) | KtResult | Flow | Custom("fqn")
}
```

The plugin (`io.github.sahsenvar:kmapper-gradle-plugin`, on Maven Central) forwards this to the
processor as the `kmapper.wrapper` KSP option. It warns at configuration time if applied without
the KSP plugin — there would be no processor to reach.

**2. The KSP option directly**, without the plugin:

```kotlin
ksp {
    arg("kmapper.wrapper", "KtResult") // or the fully qualified name of a user wrapper
}
```

Either way, every `@MapTo`/`@MapFrom` left at `wrapper = KMapperWrapper.Default::class` (i.e.
every one that doesn't say otherwise) picks this up. A mapping that explicitly names a wrapper —
including explicitly `KMapperWrapper.None::class` — always overrides the module setting.

## Writing your own wrapper

The built-ins use **no privileged mechanism** — this is the same user–author parity principle
that applies to converters and validators throughout KMapper: anything the library does
internally, you can do the same way in your own code. A wrapper is an `object` implementing
`KMapperWrapper`, annotated with `@WrapperSuffix("...")`,
exposing one or more public `wrap` functions of the shape:

```kotlin
fun <S, T> wrap(source: <R in terms of S>, map: (S) -> T): <W in terms of T>
```

For each `wrap` overload the compiler generates one receiver extension:
`fun R<Source>.to{Target}{suffix}(…externals): W<Target> = YourWrapper.wrap(this) { it.to{Target}(…externals) }`.

```kotlin
@WrapperSuffix("Either")
object EitherWrapper : KMapperWrapper {
    fun <S, T> wrap(source: S, map: (S) -> T): Either<Throwable, T> = Either.catch { map(source) }
}

@MapTo(UserDomain::class, wrapper = EitherWrapper::class)   // → toUserDomainEither()
data class UserDto(/* … */)
```

`KtResult` and `Flow` are written exactly this way — open
`core/src/commonMain/kotlin/com/sahsenvar/kmapper/KMapperWrapper.kt` in the repo and you're
reading the same shape a user wrapper follows. A wrapper with several `wrap` overloads (e.g. one
for `S` and one for `Flow<S>`, like the built-in `Flow` wrapper) generates one receiver extension
*per overload* — that's how a single wrapper adds both `Source.toXFlow()` and
`Flow<Source>.toXFlow()`.

`@WrapperSuffix` has **binary retention**: wrappers usually live in a different module than the
mapping that uses them (the built-ins always do), so the consuming module's KSP round reads the
suffix from the compiled classpath, not from source.

To use a custom wrapper as the module-wide default instead of per-mapping, pass its fully
qualified name: `KMapper { wrapper = KMapperWrapper.Custom("com.example.mapping.EitherWrapper") }`
(Gradle plugin) or `ksp { arg("kmapper.wrapper", "com.example.mapping.EitherWrapper") }` (KSP
option directly).

## Migrating from 2.x

In 2.x, every mapping generated `toXResult(): Result<X>` and nothing else. In 3.0.0, the
default generated function is the plain `toX()`, which throws. Pick one:

- **Adopt the new default.** `dto.toUserResult().getOrThrow()` → `dto.toUser()`, and
  `dto.toUserResult()` → `runCatching { dto.toUser() }` wherever you still want a `Result`
  locally.
- **Keep the old API, module-wide, with no per-call-site changes.** Apply the Gradle plugin and
  set `KMapper { wrapper = KMapperWrapper.KtResult }`, or without the plugin,
  `ksp { arg("kmapper.wrapper", "KtResult") }`. `toXResult()` is generated again, next to the
  now-also-generated `toX()`.

Either migration is mechanical and safe to do incrementally, module by module, since the setting
is per-module.

> Next: **[Built-in Converters →](../type-conversion/built-in.md)**
