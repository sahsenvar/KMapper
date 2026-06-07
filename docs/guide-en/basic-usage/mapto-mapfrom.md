# @MapTo and @MapFrom

KMapper supports bidirectional mapping with two separate annotations: **`@MapTo`** generates a `toX()` function from the source class to the target, and **`@MapFrom`** generates one from the target class back toward the source.

---

## @MapTo — Forward Direction

The `@MapTo(Target::class)` annotation is placed on the **source** class. The processor generates a `toTarget()` extension function on the source class.

```kotlin
// Source: data layer
@MapTo(UserDomain::class)
data class UserRemote(
    val id: String,
    val email: String,
)

// Target: domain layer
data class UserDomain(
    val id: String,
    val email: String,
)
```

After compilation, the generated file is `UserRemoteMappers.kt` (same package as the source class):

```kotlin
// build/generated/…/UserRemoteMappers.kt
public fun UserRemote.toUserDomain(): UserDomain = UserDomain(
    id    = id,
    email = email,
)
```

Usage:

```kotlin
val domain: UserDomain = userRemote.toUserDomain()
```

---

## Multiple Targets (Repeatable)

`@MapTo` is `@Repeatable`, so you can map the same source class to more than one target:

```kotlin
@MapTo(UserDomain::class)
@MapTo(UserCache::class)
data class UserRemote(
    val id: String,
    val email: String,
)
```

The processor generates a separate extension for each target:

```kotlin
public fun UserRemote.toUserDomain(): UserDomain = UserDomain(id = id, email = email)
public fun UserRemote.toUserCache(): UserCache  = UserCache(id = id, email = email)
```

If field names or types differ between targets, use `@FieldMap(targetClass = ...)` to specify which mapping rule applies to which target (see [Field Mapping](field-mapping.md)).

---

## @MapFrom — Reverse Direction

The `@MapFrom(Source::class)` annotation is placed on the **target** class. The mapping direction is still source → target, but the annotation sits on the **target**. The generated `toX()` function still comes out as an extension on the **source** class — the only difference is which class carries the annotation.

```kotlin
data class UserRemote(
    val id: String,
    val email: String,
)

// Annotation is on the target class, but toUserDomain() is called on the source
@MapFrom(UserRemote::class)
data class UserDomain(
    val id: String,
    val email: String,
)
```

The generated code is identical to `@MapTo`:

```kotlin
public fun UserRemote.toUserDomain(): UserDomain = UserDomain(
    id    = id,
    email = email,
)
```

`@MapFrom` is also `@Repeatable`; you can map multiple sources to the same target:

```kotlin
@MapFrom(UserRemote::class)
@MapFrom(UserCache::class)
data class UserDomain(val id: String, val email: String)
```

---

## Which Annotation Should You Use?

| Situation | Preference |
|-----------|------------|
| You own the source class (e.g. DTO) | `@MapTo` on the source |
| You own the target class (e.g. domain model) | `@MapFrom` on the target |
| You own both — doesn't matter | Both produce the same code |

---

Next: [Field Mapping (@FieldMap, @Ignore)](field-mapping.md)
