# Example Gallery

The repository ships a [sample module](https://github.com/sahsenvar/KMapper/tree/main/sample)
with **25 runnable, self-contained examples** — every feature of the library, ordered from
basic to advanced within each category. If you want to know how to do something, there is an
example that shows it.

Run everything:

```bash
./gradlew sample:runSample
```

…or open any file in the IDE and hit ▶ next to its `main`.

## Learning path

| # | Category | You will learn |
|---|----------|----------------|
| 1 | **Basics** | `@MapTo`, the `toXResult(): Result<X>` boundary, `@MapFrom`, one source → many targets |
| 2 | **Fields** | `@FieldMap` renaming, `@IgnoreMap`, `@IgnoreDefaultValue`, caller-supplied parameters |
| 3 | **Nullability & defaults** | the fallback ladder, production `Result` handling patterns |
| 4 | **Converters** | auto-discovery, custom converters, `@ConvertWith(use, onFail)`, sanctioned null, parameterized converters, `@UnsupportedDirection` |
| 5 | **Collections** | element ladder, Set/Map semantics, `OnFail.Throw`/`Skip` on elements, `@CollectionWrapper` |
| 6 | **Nested objects** | sub-mappers, deep error paths, bounding the blast radius |
| 7 | **Enums** | `MappableEnum`, unknown wire values |
| 8 | **Validation** | field-anchored `@Validate`, the validator library, custom validators |
| 9 | **Observability** | `MappingListener`, the degradation sink, "crash in debug, observe in prod" |
| 10 | **Hand-written mappers** | `kmapper-core` standalone — the same seams generated code uses |

Each example file documents its expected output in comments, and the gallery is compiled by
the regular project build — the examples can't silently drift from the library's API.

> Next: **[@MapTo and @MapFrom →](../basic-usage/mapto-mapfrom.md)**
