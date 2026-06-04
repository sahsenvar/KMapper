# Limitations and Roadmap

## Current Limitations

### Constructor `val` Fields Only

kmap analyzes only `val` parameters in the primary constructor. `var` properties, fields assigned in `init` blocks, and properties defined outside the constructor are not mapped.

```kotlin
data class OrderRemote(
    val id: String,       // ✓ mapped
    val total: Double,    // ✓ mapped
) : RemoteModel {
    var cached: Boolean = false  // ✗ not mapped, invisible to the processor
}
```

This is an intentional design decision that deliberately limits analysis to constructor-only fields. Mutable property mapping may be evaluated in a future round.

### Enums Must Implement `MappableEnum`

If an enum type is used as a mapping source or target, that enum must implement `MappableEnum<W>`. For third-party enums, the `@UseMapTypeConverter` escape hatch is available; see [MappableEnum](../enum/mappable-enum.md).

### No `sealed class` Mapping

`sealed class` and `sealed interface` hierarchies are not yet supported. You can annotate each subclass individually with `@MapTo`, but dispatch code for the sealed hierarchy is not generated automatically.

### No `Map<K, V>` Collection

`Map<K, V>` fields are not directly supported. As a workaround, you can write the conversion yourself using a per-field `@UseMapTypeConverter`.

## Roadmap

The following features are planned for a future round; **none of them are implemented yet**.

### `sealed class` Mapping

Type-safe mapping of `sealed class` / `sealed interface` hierarchies. Automatic dispatch generation without each subclass needing its own `@MapTo`.

### `Map<K, V>` Collection Mapping

Element-by-element conversion of `Map`-typed fields.

### `converters-arrow` — Nel Support

The real contents of the `converters-arrow` artifact: `List<T>` → `NonEmptyList<T>` converters. At this point the `EmptyCollection` error type may also return.

### `verifyEnums()` — Enum Clash Detection

A utility that detects two enum constants sharing the same `wireValue`. Because KSP cannot reliably read the runtime values of constructor arguments, this check cannot be done at compile time; it is planned as an optional validation function that runs at debug/test startup:

```kotlin
// NOT YET AVAILABLE — on the roadmap
KMapper.verifyEnums()  // checks wireValue uniqueness across all registered enums
```

### `kmapper.verbose` KSP Option

A KSP option that writes to `logger.info` at build time which mappers and field matches the processor is generating:

```kotlin
// NOT YET AVAILABLE — on the roadmap
ksp {
    arg("kmapper.verbose", "true")
}
```

---

Next: [FAQ](./faq.md)
