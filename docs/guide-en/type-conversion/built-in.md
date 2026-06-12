# Built-in Converters

When a matched field pair has different types, KMapper resolves a converter **at compile
time**. 35 type pairs ship in core and are auto-resolved with **no registration** — and, just
as importantly, the directions that would corrupt data are *refused* at compile time.

## The catalog

All built-ins live in `com.sahsenvar.kmapper.converter.builtin` as public objects, named
**richer type first** (the type that can hold more comes first: `LongIntConverter`,
`InstantStringConverter`).

**Numeric widening (12 pairs)** — the lossless direction converts; the narrowing direction is
refused (see below):

| Richer | Poorer |
|--------|--------|
| `Short`, `Int`, `Long`, `Float`, `Double` | `Byte` |
| `Int`, `Long`, `Float`, `Double` | `Short` |
| `Long`, `Double` | `Int` |
| `Double` | `Float` |

**String pairs (7)** — `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `Boolean` ↔
`String`. Formatting is total; parsing throws on malformed input (and rides the
[ladder](../basic-usage/null-safety.md)). `Boolean` parsing is strict: `"true"`/`"false"`
only — `"TRUE"`, `"1"`, `"yes"` are refused as ambiguous wire formats.

**Cross pairs (9)** — `Float ↔ Int`, `Float ↔ Long`, `Double ↔ Long` (lossless direction
converts), and `Boolean` ↔ every numeric type. The `Boolean`/numeric pairs are special: **both
directions are refused** — `Byte → Boolean` has no canonical semantics (is `2` true?) and
`Boolean → Byte` has no canonical encoding (`0/1`? `-1`?). They exist in the registry so that
instead of a generic "no converter" error you get the *reasoned* refusal and write a one-line
converter encoding **your** wire's convention.

**kotlinx-datetime (5)** — `Instant ↔ String` (ISO-8601), `Instant ↔ Long` (epoch millis),
`LocalDate ↔ String`, `LocalDateTime ↔ String`, `LocalTime ↔ String`.

**kotlin.time (2)** — `Duration ↔ String` (ISO-8601, e.g. `PT1H30M`), `Duration ↔ Long`
(whole milliseconds; sub-millisecond precision truncates — documented trade-off matching
`Instant ↔ Long`).

## Refused directions are a feature

`LongIntConverter` converts `Int → Long` happily. Ask for `Long → Int` and the **build
fails**:

```
Long -> Int conversion is unsupported! This relates to our policy on lossy conversions
(e.g. Long -> Int, Double -> Float). What you can do:
  1. Check the converter add-ons
  2. Create your own converter
  3. Rethink your source or target type using supported types.
```

That's `@UnsupportedDirection` — a declared, reasoned refusal instead of a silent
truncation. If your domain *does* guarantee the range, you write the three-line custom
converter and own that decision explicitly. The same mechanism is yours to use in
[your own converters](custom-converter.md#refusing-a-direction).

## Resolution order

For a field needing `A → B`:

1. field-level [`@ConvertWith`](convert-with.md) — explicit override wins
2. your [`@KMapperConfig` converters](kmapperconfig.md) — a custom pair **shadows** a
   built-in for the same pair
3. core built-ins (this page)
4. nothing? → compile error `MissingConverter`, naming the pair and where to register one

> Next: **[Writing a Custom Converter →](custom-converter.md)**
