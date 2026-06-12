# Nested Models and Error Paths

When a field's type is itself a mapped pair, KMapper routes it through the generated
sub-mapper automatically — and stitches error paths together so failures stay addressable.

## Nesting just works

```kotlin
data class Order(val id: Long, val customer: Customer)
data class Customer(val name: String, val address: Address)
data class Address(val street: String, val zipCode: Int)

@MapTo(Order::class)
data class OrderResponse(val id: Long, val customer: CustomerResponse)

@MapTo(Customer::class)
data class CustomerResponse(val name: String, val address: AddressResponse)

@MapTo(Address::class)
data class AddressResponse(val street: String, val zipCode: String)
```

Each level needs its own `@MapTo` (every pair is an explicit declaration — no structural
guessing), and the top-level call maps the whole tree:

```kotlin
val order = orderResponse.toOrderResult().getOrThrow()
```

## Errors carry the full path

When `zipCode` is `"ABC"` three levels deep:

```
Cannot convert customer.address.zipCode: String -> Int failed for value "ABC" …
```

The path is built from **compile-time string literals** in the generated code — it survives
R8/ProGuard obfuscation untouched. Collections add an index segment: `items[3].price`.

## Bounding the blast radius

By default a hard failure anywhere fails the whole `toOrderResult()` — one `Result`, one
boundary. If a *section* of the payload is optional, declare it so, and the failure stops
there:

```kotlin
data class Order(
    val id: Long,
    val customer: Customer? , // a broken customer no longer kills the order
)
```

A broken sub-mapping then absorbs at the nullable escape (with an `AbsorbedConversionError`
report carrying the nested path), and the rest of the order maps normally. The ladder
composes: the escape *nearest to the failure* wins.

> Next: **[Collections →](collections.md)**
