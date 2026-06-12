# Limitations & Roadmap

Honest edges of the current release, with the reasoning — most are deliberate scope
decisions, recorded in the project's design ledger.

## Current limitations

- **One converter per type pair per module.** [Discovery](../type-conversion/kmapperconfig.md)
  must stay unambiguous; format variants are per-field
  [`@ConvertWith`](../type-conversion/convert-with.md) decisions.
- **Validators and converters are objects.** Generated code calls them by FQN — no instance
  state, no DI. Parameterization happens through
  [open-base subclassing](../type-conversion/custom-converter.md#parameterized-converters).
- **`@FieldMap` matches by simple name.** Qualified path renames
  (`Data.wireScore → Domain.score` syntax) aren't supported; a single name has sufficed.
- **Map-shaped custom containers** (two type parameters, `MultiMap<K, V>`-style) are outside
  the `@CollectionWrapper` convention — wrappers cover single-element-type containers.
- **Validation is always hard.** A `@Validate` failure never rides the
  [ladder](../basic-usage/null-safety.md); "absorbable validation" is parked until a real
  use case demands it.
- **`kotlin.uuid.Uuid` is not a core built-in** while the API is experimental —
  [the add-on covers it](../type-conversion/uuid.md#why-isnt-uuid--string-a-core-built-in).

## Parked (designed, not yet shipped)

- **Arrow accumulated boundary** — `toXAccumulated(): IorNel<MappingError, X>`: collect *all*
  failures instead of failing fast. Designed end-to-end; ships in a follow-up release.
- **Per-mapping summary sink event** ("3/100 items dropped") and listener throttling
  guidance.
- **`OnAbsent` element policy** (treat null source elements as errors) — if demanded.
- **Strict-on-collision option for Map keys** (currently: last wins + `DuplicateKey` report).
- **`converters-format` add-on** (locale-aware number formatting) — the
  [parameterized-converter recipe](../type-conversion/custom-converter.md#parameterized-converters)
  covers it user-side meanwhile.

Found a limitation that blocks you?
[Open an issue](https://github.com/sahsenvar/KMapper/issues) — parked items get unparked by
real use cases.

> Next: **[FAQ →](faq.md)**
