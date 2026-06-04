# @KMapperConfig and @UseMapTypeConverter

`@KMapperConfig` is how you register converters that are not in the built-in table with the processor. For per-field overrides, use `@UseMapTypeConverter`.

---

## @KMapperConfig — Global Converter List

```kotlin
@KMapperConfig(converters: Array<KClass<*>> = [])
```

`@KMapperConfig` is applied to an `object`. Every class in the `converters` array must be a subtype of `MapTypeConverter<S,T>`.

```kotlin
@KMapperConfig(converters = [
    UuidStringConverter::class,
    StatusConverter::class,
    StringInstantConverter::class,   // built-in, but harmless to include in the list
])
object AppMapperConfig
```

The processor finds this object at compile time, resolves the `(S,T)` pair from each converter's `MapTypeConverter<S,T>` supertype, and automatically wires the generated calls for those pairs. **No manual runtime registration is needed** — the processor generates the runtime registration from the same list.

---

## Precedence Order

The converter for a given field is chosen in this order:

1. **`@UseMapTypeConverter`** (per-field override) — highest priority
2. **`@KMapperConfig` list** (global registration)
3. **Built-in converter table** (always available in the background)

The search stops as soon as a higher-priority rule is found.

---

## @UseMapTypeConverter — Per-Field Override

When you need a different converter than the one in the global list for the same `(S,T)` pair, apply `@UseMapTypeConverter` to a single field:

```kotlin
// Global @KMapperConfig: StringInstantConverter (ISO-8601)
@KMapperConfig(converters = [StringInstantConverter::class])
object AppMapperConfig

@MapTo(EventDomain::class)
data class EventRemote(
    val startsAt: String,          // global: converted with ISO-8601

    @UseMapTypeConverter(LongStringToInstantConverter::class)  // per-field override
    val legacyTime: String,        // different format for this field
)

data class EventDomain(
    val startsAt: Instant,
    val legacyTime: Instant,
)
```

Generated code:

```kotlin
public fun EventRemote.toEventDomain(): EventDomain = EventDomain(
    startsAt   = convertOrFail("String", "Instant") { StringInstantConverter.convertToNonNull(startsAt) },
    legacyTime = convertOrFail("String", "Instant") { LongStringToInstantConverter.convertToNonNull(legacyTime) },
)
```

A converter specified with `@UseMapTypeConverter` does not need to be in the `@KMapperConfig` list; it is valid only for the annotated field and overrides the global list for that field.

---

## Missing Converter → Compile Error

If the required `(S,T)` pair for a field is found in neither the built-in table, the global list, nor a per-field override, the processor reports a **compile error**:

```
no converter for UUID -> String; add it to @KMapperConfig(converters=[...]) or annotate the field with @UseMapTypeConverter
```

---

## Ambiguous Global — Compile Error

If the `@KMapperConfig` list contains two different converters for the **same `(S,T)` pair**, the processor also reports a compile error:

```kotlin
@KMapperConfig(converters = [
    StringInstantConverter::class,   // String → Instant
    EpochStringInstantConverter::class,  // String → Instant  ← same pair!
])
object AppMapperConfig
```

The processor reports something like:

```
❌ DUPLICATE CONVERTER IN @KMapperConfig DETECTED

Type pair: kotlin.String → kotlinx.datetime.Instant

First converter:  ...StringInstantConverter
Second converter: ...EpochStringInstantConverter

@KMapperConfig lists two converters for the same (S,T) pair — this is ambiguous.
→ Keep exactly one converter for this pair in @KMapperConfig(converters=[...]).
  If you need a different converter for a specific field, use @UseMapTypeConverter
  on that field instead of adding a second entry to @KMapperConfig.
```

**Resolution:** Keep the generally-used converter in `@KMapperConfig`, and apply the exceptional one only to the specific field that needs it via `@UseMapTypeConverter`.

---

## KMapper.addConverter — Runtime Escape Hatch

When compile-time safety is not a requirement, you can register a converter at runtime with `KMapper.addConverter(converter)`:

```kotlin
// Application.onCreate or iOS app delegate:
KMapper.addConverter(MyConverter)
```

This path is **not compile-time safe** — the processor cannot see this registration and cannot check for missing converters. Use it only in dynamic or test environments; prefer `@KMapperConfig` in production code.

---

Next: [Immutable Collections (converters-immutable)](immutable.md)
