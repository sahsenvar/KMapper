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

> Next: **[The Result Boundary and MappingException →](../error-handling/mapping-exception.md)**
