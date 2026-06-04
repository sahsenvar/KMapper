# Nested Models

kmap recognizes nested models automatically. When a field references another mapped model, the processor chains a `toX()` call in the generated code — no extra annotation is needed.

---

## Basic Nested Mapping

In the example below, `OrderRemote` contains an `AddressRemote` field. Both remote classes are annotated with `@MapTo` pointing to their respective domain counterparts:

```kotlin
@MapTo(AddressDomain::class)
data class AddressRemote(
    val street: String,
    val city: String,
)

data class AddressDomain(
    val street: String,
    val city: String,
)

@MapTo(OrderDomain::class)
data class OrderRemote(
    val id: String,
    val address: AddressRemote,
)

data class OrderDomain(
    val id: String,
    val address: AddressDomain,
)
```

When generating `OrderRemote.toOrderDomain()`, the processor checks the type of the `address` field, finds the `AddressRemote → AddressDomain` mapping, and chains the call:

```kotlin
public fun OrderRemote.toOrderDomain(): OrderDomain = OrderDomain(
    id      = id,
    address = address.toAddressDomain(),
)
```

`toAddressDomain()` is also generated separately in `AddressRemoteMappers.kt`; everything is wired at compile time.

---

## Nullable Nested Models

If the nested model is nullable, a safe call (`?.`) is added automatically:

```kotlin
@MapTo(ProfileDomain::class)
data class ProfileRemote(
    val userId: String,
    val address: AddressRemote?,    // optional
)

data class ProfileDomain(
    val userId: String,
    val address: AddressDomain?,
)
```

Generated:

```kotlin
public fun ProfileRemote.toProfileDomain(): ProfileDomain = ProfileDomain(
    userId  = userId,
    address = address?.toAddressDomain(),
)
```

If the target `address` field were required (`AddressDomain`, not nullable), the null-safety rules would apply — see [Null-Safety](null-safety.md).

---

## Circular Dependencies

kmap catches **unconditional cycles** at compile time and reports an error:

```kotlin
// COMPILE ERROR — unconditional cycle:
@MapTo(BDomain::class) data class A(val b: B)   // non-null
@MapTo(ADomain::class) data class B(val a: A)   // non-null
// e: Mapping cycle detected: A -> B -> A. This would cause infinite construction at runtime.
//    Break the cycle with a nullable field, a collection, or @Ignore.
```

However, if the cycle is **conditional** (through a nullable field or a collection), it is allowed:

```kotlin
// OK — nullable parent reference
@MapTo(CategoryDomain::class)
data class CategoryRemote(
    val id: String,
    val parent: CategoryRemote?,     // nullable → conditional cycle, allowed
)

// OK — through a collection
@MapTo(NodeDomain::class)
data class NodeRemote(
    val value: Int,
    val children: List<NodeRemote>,  // list → conditional cycle, allowed
)
```

---

Next: [Collections](collections.md)
