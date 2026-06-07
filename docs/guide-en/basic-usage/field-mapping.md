# Field Mapping — @FieldMap and @Ignore

KMapper's default behavior is to match fields **by name** between source and target. When field names or types don't align, use `@FieldMap`. When a field should be excluded from mapping entirely, use `@Ignore`.

---

## @FieldMap — Field Renaming

```kotlin
@FieldMap(fieldName: String, targetClass: KClass<*> = Nothing::class)
```

`@FieldMap` is applied to a property. `fieldName` specifies the name of the corresponding field in the target class.

```kotlin
@MapTo(UserDomain::class)
data class UserRemote(
    @FieldMap(fieldName = "userId")   // "id" in remote, "userId" in domain
    val id: String,
    val email: String,
)

data class UserDomain(
    val userId: String,
    val email: String,
)
```

Generated code:

```kotlin
public fun UserRemote.toUserDomain(): UserDomain = UserDomain(
    userId = id,
    email  = email,
)
```

---

## Specifying a Target Class for Multiple Targets

When the same source class is mapped to more than one target (repeated `@MapTo`) and a field should be renamed only for **one specific target**, the `targetClass` parameter comes into play. A `@FieldMap` without `targetClass` applies to all targets (wildcard).

```kotlin
@MapTo(UserDomain::class)
@MapTo(UserCache::class)
data class UserRemote(
    // Only "userId" for UserDomain; UserCache gets "id" (matched by name)
    @FieldMap(fieldName = "userId", targetClass = UserDomain::class)
    val id: String,
    val email: String,
)
```

Generated code:

```kotlin
public fun UserRemote.toUserDomain(): UserDomain = UserDomain(
    userId = id,      // @FieldMap(targetClass=UserDomain::class) applied
    email  = email,
)

public fun UserRemote.toUserCache(): UserCache = UserCache(
    id    = id,       // matched by name — @FieldMap not applied
    email = email,
)
```

Multiple `@FieldMap` annotations can be stacked on the same field for different targets:

```kotlin
@FieldMap(fieldName = "userId",  targetClass = UserDomain::class)
@FieldMap(fieldName = "cacheId", targetClass = UserCache::class)
val id: String,
```

---

## @Ignore — Exclude a Field

A field marked with `@Ignore` is excluded from mapping for **all targets**. Use it when the target class's constructor does not have that field, or when the value is provided externally.

```kotlin
@MapTo(UserDomain::class)
data class UserRemote(
    val id: String,
    val email: String,
    @Ignore val rawJson: String,   // this field does not exist in the domain model
)

data class UserDomain(
    val id: String,
    val email: String,
)
```

The generated code never touches `rawJson`:

```kotlin
public fun UserRemote.toUserDomain(): UserDomain = UserDomain(
    id    = id,
    email = email,
)
```

If the target constructor has a parameter that corresponds to the `@Ignore`d field and that parameter has no default value, you will get a **compile error** — give the target parameter a default value, or handle the field with `@MapDefaultValue` instead of `@Ignore`.

---

## Precedence Order

The mapping rule for a field is determined in this order:

1. `@Ignore` → exclude entirely
2. Target-specific `@FieldMap(targetClass = X::class)` → apply only for that target
3. Wildcard `@FieldMap(targetClass = Nothing::class)` → apply to all targets
4. Name-based matching → assign directly when source and target field names match

---

Next: [Null-Safety and @MapDefaultValue](null-safety.md)
