# Annotation Reference

All annotations live in `com.sahsenvar.kmapper.annotations` (`kmapper-annotations` artifact).

## Mapping declaration

| Annotation | Target | Purpose |
|------------|--------|---------|
| `@MapTo(target)` | class (repeatable) | generate `Source.toTargetResult()` — declared on the source |
| `@MapFrom(source)` | class (repeatable) | same generation, declared on the target |

→ [@MapTo and @MapFrom](../basic-usage/mapto-mapfrom.md)

## Field directives

| Annotation | Target | Purpose |
|------------|--------|---------|
| `@FieldMap(fieldName, targetClass)` | property (repeatable) | match a differently-named target field; optionally scoped to one target |
| `@IgnoreMap` | property | exclude the field from auto-matching; the target slot defaults or becomes a caller parameter |
| `@IgnoreDefaultValue` | property | the constructor default is construction convenience only — absence becomes `RequiredFieldMissing` |

→ [Field Mapping](../basic-usage/field-mapping.md)

Placement rule: field directives are read from the **source field of the generated
direction** ([details](../type-conversion/convert-with.md#the-placement-rule-worth-memorizing)).

## Conversion control

| Annotation | Target | Purpose |
|------------|--------|---------|
| `@ConvertWith(use, onFail)` | property | per-field converter override and/or failure policy |
| `@ConvertTo(target, use, onFail)` | property (repeatable) | `@ConvertWith` scoped to one mapping direction |
| `@ConvertFrom(source, use, onFail)` | property (repeatable) | the reverse scoping |
| `OnFail` (enum) | — | `Auto` (ladder), `Throw` (never absorb), `Skip` (compact collections) |

→ [@ConvertWith and OnFail](../type-conversion/convert-with.md)

## Registration

| Annotation | Target | Purpose |
|------------|--------|---------|
| `@KMapperConfig(converters, wrappers)` | object | module-wide converter/wrapper registration; discovery by type pair |
| `@CollectionWrapper(forType)` | object | declare a `wrap`/`unwrap` pair for a custom container type |

→ [@KMapperConfig](../type-conversion/kmapperconfig.md),
[Collection wrappers](../type-conversion/custom-converter.md#collection-wrappers)

## Validation

| Annotation | Target | Purpose |
|------------|--------|---------|
| `@Validate(vararg validators)` | property | field-anchored invariants; run before (source side) / after (target side) conversion |

→ [@Validate](../validation/validate.md)

## Converter authoring (in `kmapper-core`)

| Annotation | Target | Purpose |
|------------|--------|---------|
| `@UnsupportedDirection(reason)` | function (`convertTo`/`convertFrom`) | declare a direction intentionally unsupported; the reason appears in the compile error |

→ [Refusing a direction](../type-conversion/custom-converter.md#refusing-a-direction)

## Removed in 2.0

`@Ignore` → `@IgnoreMap` · `@MapDefaultValue` → constructor defaults ·
`@UseMapTypeConverter` → `@ConvertWith` · `@ValidateFrom`/`@ValidateTo` → `@Validate`.
See the migration guide in the repository's `CHANGELOG.md`.
