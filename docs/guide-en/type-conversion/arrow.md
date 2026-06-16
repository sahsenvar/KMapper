# Arrow — converters-arrow

Wrappers and processor support for [Arrow](https://arrow-kt.io) types: non-empty collections
as a *mapping-time guarantee*, and `Option` as an explicit-absence type.

## Setup

```kotlin
commonMain.dependencies {
    implementation("io.github.sahsenvar:kmapper-converters-arrow:2.2.0")
}
```

```kotlin
@KMapperConfig(wrappers = [NonEmptyListWrapper::class, NonEmptySetWrapper::class])
object AppMapperConfig
```

## NonEmptyList / NonEmptySet

```kotlin
data class Role(val permissions: NonEmptySet<Permission>)

@MapTo(Role::class)
data class RoleResponse(val permissions: List<PermissionResponse>)
```

An **empty wire list fails the mapping** with `MappingException.EmptyCollection` — that is
the point: the domain type promises non-emptiness, so mapping enforces it at the boundary
instead of letting an impossible value in. The failure arrives as a `Result` like every other
mapping error.

## Option

With the add-on on the classpath, the processor maps nullable sources into `Option` targets
directly — including nested mapped pairs:

```kotlin
data class Profile(
    val nickname: Option<String>,  // String?  -> Option<String>
    val badge: Option<Badge>,      // BadgeR?  -> Option<Badge> (sub-mapper inside)
)

@MapTo(Profile::class)
data class ProfileResponse(
    val nickname: String?,
    val badge: BadgeResponse?,
)
```

`null` becomes `None`; present values become `Some(mapped)` — declared absence stays silent,
exactly like the [nullable escape](../basic-usage/null-safety.md).

> The accumulated-error boundary (`toXAccumulated(): IorNel<…>`) is designed and parked for a
> follow-up release — see [Limitations & Roadmap](../reference/limitations.md).

> Next: **[Date and Time →](datetime.md)**
