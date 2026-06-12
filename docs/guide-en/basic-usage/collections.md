# Collections

`List`, `Set`, and `Map` fields map element-by-element, and **each element rides its own
fallback ladder**. The design goal: one malformed element out of a hundred should cost you
that element, not the payload — and never silently.

## Lists

```kotlin
data class Sensors(val readings: List<Int>)

@MapTo(Sensors::class)
data class SensorsResponse(val readings: List<String>) // "42" -> 42 per element
```

Element behavior when a value is broken or null, by **target element type**:

| Target element | Broken/null element becomes | Reported as |
|----------------|------------------------------|-------------|
| `List<T?>` | `null` in place (size preserved) | `AbsorbedConversionError` / position kept |
| `List<T>` | dropped (list compacts) | `DroppedBrokenElement` / `DroppedNullElement` |

Same philosophy as scalars: the type declares the escape, the sink hears about every use of
it.

## Per-field element policy: OnFail

[`@ConvertWith(onFail = …)`](../type-conversion/convert-with.md) tunes a single collection
field. The annotation goes on the **source field of the generating direction**:

```kotlin
@MapTo(Measurements::class)
data class MeasurementsResponse(
    @ConvertWith(onFail = OnFail.Throw)
    val invoiceLines: List<String>, // all-or-nothing: any broken line fails the mapping

    @ConvertWith(onFail = OnFail.Skip)
    val tagIds: List<String?>, // compact: drop broken/null elements even into List<T?>
)
```

- `OnFail.Throw` — hardens the field: first broken element fails the whole mapping with
  `items[i]`-style path.
- `OnFail.Skip` — compacts: broken/null elements are dropped (and reported), even where the
  target could hold nulls.
- `OnFail.Auto` (default) — the table above.

## Sets

Same ladder. One extra wrinkle: converting elements can make two distinct source elements
equal in the target (`"01"` and `"1"` both → `1`). The set keeps one and reports
`ConvergedDuplicateElement` — silent data loss isn't a thing, even when it's set semantics.

## Maps

Keys and values each ride the ladder. Two map-specific rules:

- a **broken key** drops the whole entry (a value without an address is meaningless) —
  reported as `DroppedBrokenElement`;
- two source keys converting to the same target key keep the **last** entry and report
  `DuplicateKey`.

`Map<String, String> → Map<String, String>` with matching types passes through untouched.

## Beyond stdlib collections: wrappers

`PersistentList`, `NonEmptyList`, and friends are one `@CollectionWrapper` registration away —
the same element semantics, a different container. See
[Immutable Collections](../type-conversion/immutable.md) and
[Arrow](../type-conversion/arrow.md), or write your own wrapper for your own container type
([custom converter guide](../type-conversion/custom-converter.md#collection-wrappers)).

> Next: **[Built-in Converters →](../type-conversion/built-in.md)**
