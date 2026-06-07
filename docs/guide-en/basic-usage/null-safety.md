# Null-Safety and @MapDefaultValue

KMapper detects nullable-to-non-null conversions at compile time and generates safe code. `null` is never silently swallowed.

---

## Four Nullability Cases

| Source | Target | Generated assignment |
|--------|--------|----------------------|
| `T` (required) | `T` (required) | Direct assignment |
| `T` (required) | `T?` (nullable) | Direct assignment |
| `T?` (nullable) | `T?` (nullable) | Direct assignment |
| `T?` (nullable) | `T` (required) | `?: throw MappingException.RequiredFieldMissing("field")` or `@MapDefaultValue` |

Only the last row requires special handling. All others are direct assignments.

---

## Nullable → Required: Default Behavior

When the source field is nullable and the target field is required, the processor automatically inserts a `RequiredFieldMissing` throw:

```kotlin
@MapTo(OrderDomain::class)
data class OrderRemote(
    val id: String?,          // nullable
    val amount: Double?,      // nullable
)

data class OrderDomain(
    val id: String,           // required
    val amount: Double,       // required
)
```

Generated code:

```kotlin
public fun OrderRemote.toOrderDomain(): OrderDomain = OrderDomain(
    id     = id     ?: throw MappingException.RequiredFieldMissing("id"),
    amount = amount ?: throw MappingException.RequiredFieldMissing("amount"),
)
```

When `null` arrives, `MappingException.RequiredFieldMissing` is thrown. It is a `RuntimeException`, so you can log it or convert it to a domain error before catching:

```kotlin
try {
    val domain = orderRemote.toOrderDomain()
} catch (e: MappingException.RequiredFieldMissing) {
    // e.field → which field arrived as null
}
```

---

## @MapDefaultValue — Providing a Default

If you want to use a specific value instead of throwing when `null` arrives, add `@MapDefaultValue(expression)`. The `expression` is a Kotlin expression inserted verbatim into the generated code — it can be a string literal, a reference, or a function call.

```kotlin
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@MapTo(EventDomain::class)
data class EventRemote(
    val title: String?,

    @MapDefaultValue("Clock.System.now()")
    val createdAt: Instant?,

    @MapDefaultValue("0")
    val viewCount: Int?,
)

data class EventDomain(
    val title: String,
    val createdAt: Instant,
    val viewCount: Int,
)
```

Generated code:

```kotlin
public fun EventRemote.toEventDomain(): EventDomain = EventDomain(
    title     = title     ?: throw MappingException.RequiredFieldMissing("title"),
    createdAt = createdAt ?: Clock.System.now(),
    viewCount = viewCount ?: 0,
)
```

`title` has no `@MapDefaultValue`, so it still throws. `createdAt` and `viewCount` use their default expressions.

---

## Required → Nullable Target

When the source field is required but the target is nullable, a direct assignment is generated with no extra check:

```kotlin
@MapTo(UserCache::class)
data class UserDomain(
    val email: String,    // required
)

data class UserCache(
    val email: String?,   // nullable — but the source is already required
)
```

Generated:

```kotlin
public fun UserDomain.toUserCache(): UserCache = UserCache(
    email = email,        // direct assignment
)
```

---

## Exception Hierarchy

For the full list of `MappingException.RequiredFieldMissing` and other types, see [Error Handling](../error-handling/mapping-exception.md).

---

Next: [Nested Models](nested-models.md)
