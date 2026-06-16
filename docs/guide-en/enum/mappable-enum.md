# MappableEnum

Enums opt into mapping by declaring their **wire value** explicitly. KMapper never maps an
enum by `name` or `ordinal` — renaming a constant or reordering the enum can therefore never
silently corrupt data.

## Declaring

```kotlin
import com.sahsenvar.kmapper.MappableEnum

enum class OrderStatus(override val wireValue: String) : MappableEnum<String> {
    PENDING("pending"),
    SHIPPED("shipped"),
    DELIVERED("delivered"),
}
```

`MappableEnum<W>` is generic over the wire type — string wires are typical, but `Int` codes
work the same way.

## Mapping

A `String ↔ OrderStatus` field pair now converts in both directions with no further setup —
formatting writes `wireValue`, parsing matches against it:

```kotlin
@MapTo(TrackedOrder::class)
data class OrderEvent(val id: Long, val status: String)

data class TrackedOrder(val id: Long, val status: OrderStatus)
```

## Unknown wire values: you choose the policy with the type

An unknown value (`"teleported"`) is brokenness, and rides the
[ladder](../basic-usage/null-safety.md) like any other brokenness:

```kotlin
data class TrackedOrder(val status: OrderStatus)   // strict: unknown -> UnknownEnumValue failure
data class OrderPreview(val status: OrderStatus?)  // tolerant: unknown -> null + sink report
```

```
strict fails    -> Unknown wire value 'teleported' for enum OrderStatus at status
preview absorbs -> OrderPreview(id=2, status=null)   (+ AbsorbedConversionError to the sink)
```

The nullable variant is the standard **forward-compatibility pattern**: the server may add
new statuses before your app ships, your UI shows "unknown", your telemetry counts how often.

Enum elements inside collections get the same treatment per element — see
[Collections](../basic-usage/collections.md).

## Alternative: kotlinx.serialization `@Serializable` enums

If your enum is already a kotlinx.serialization `@Serializable` enum, you don't have to
implement `MappableEnum` and repeat the wire values — KMapper reads them straight from the
annotations:

```kotlin
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class OrderStatus {
    @SerialName("pending") PENDING,
    SHIPPED, // no @SerialName → wire value is the entry name, "SHIPPED"
}
```

A field pair `String ↔ OrderStatus` maps in both directions, exactly as with `MappableEnum`.
The wire value of each entry is its **`@SerialName` argument, else the entry's declared name**
— identical to how the enum (de)serializes in JSON, so the mapping and your serialization
agree by construction.

Details:

- **`MappableEnum` wins.** If an enum has both, the `MappableEnum.wireValue` path is used
  (the `@SerialName` annotations are ignored). Use whichever one fits; you never need both.
- **String wire only.** `@Serializable` enums serialize as strings, so the other side must be
  `String` (a non-`String` side is the usual `enum wire type mismatch` compile error). For an
  `Int`-coded enum, use `MappableEnum<Int>`.
- **Unknown values** behave exactly as above — they ride the ladder (hard at a non-null
  target, absorbed to `null` + reported at a nullable one).
- **No runtime dependency.** KMapper reads the annotations at compile time and generates a
  plain `when` — `kmapper-core`/`kmapper-compiler` do **not** depend on kotlinx-serialization.
- **Distinct values required.** Two entries resolving to the same wire value is a compile
  error (the decode would be ambiguous).

> Next: **[The Result Boundary and MappingException →](../error-handling/mapping-exception.md)**
