# @KMapperConfig — Registration and Discovery

`@KMapperConfig` is your module's one-stop registration point: list converter objects and
collection wrappers once, and every mapping in the module can use them — **no per-field
annotation needed**.

```kotlin
import com.sahsenvar.kmapper.annotations.KMapperConfig

@KMapperConfig(
    converters = [MoneyStringConverter::class],
    wrappers = [PersistentListWrapper::class, NonEmptyListWrapper::class],
)
object AppMapperConfig
```

The carrier (`AppMapperConfig`) is just an anchor for the annotation — any object works, one
per module is the pattern.

## Discovery is by type pair

You never say *which field* uses `MoneyStringConverter`. The processor sees a field pair
`Money → String` (either direction), finds a registered converter whose `(S, T)` types match,
and wires it in. Declaration order doesn't matter.

```kotlin
@MapTo(Invoice::class)
data class InvoiceResponse(
    val total: String, // String -> Money: resolved automatically via the config
)

data class Invoice(val total: Money)
```

## Resolution and shadowing

For each field pair, first match wins:

1. **[`@ConvertWith`](convert-with.md)** on the field — the explicit override
2. **`@KMapperConfig` converters** — *your* `Instant ↔ String` converter shadows the
   built-in one for the whole module (e.g. to switch the wire format to epoch strings)
3. **[core built-ins](built-in.md)**
4. **compile error** — `MissingConverter`, naming the pair

Two registered converters claiming the **same pair** is a compile error, not a coin toss: the
registry must stay unambiguous. Format *variants* of one pair (UTF-8 vs Base64
`String ↔ ByteString`) belong in per-field `@ConvertWith` instead.

## Wrappers

`wrappers = [...]` registers [`@CollectionWrapper`](custom-converter.md#collection-wrappers)
objects that teach collection mapping new container types:

```kotlin
data class UserD(
    val tags: PersistentList<Tag>,  // List<TagR> -> PersistentList<Tag>: wrapper + element mapping
    val roles: NonEmptyList<String>, // empty wire list -> MappingException.EmptyCollection
)
```

Add-ons ship ready wrappers — [immutable](immutable.md), [arrow](arrow.md) — and your own
container types register through the identical mechanism.

## Scope

One `@KMapperConfig` covers the **compilation module** it lives in. In a multi-module build,
each module that declares mappings has its own config (a tiny object usually) — see
[Multi-Module Projects](../advanced/multi-module.md).

> Next: **[@ConvertWith and OnFail →](convert-with.md)**
