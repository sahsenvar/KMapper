# Annotation Reference

All annotations live in `com.sahsenvar.kmapper.annotations` (`kmapper-annotations` artifact).

## Mapping declaration

| Annotation | Target | Purpose |
|------------|--------|---------|
| `@MapTo(target, wrapper)` | class (repeatable) | generate `Source.toTarget(): Target` (+ whatever `wrapper` adds, e.g. `toTargetResult()`) — declared on the source |
| `@MapFrom(source, wrapper)` | class (repeatable) | same generation, declared on the target |

→ [@MapTo and @MapFrom](../basic-usage/mapto-mapfrom.md)

## Return wrappers

| Annotation | Target | Purpose |
|------------|--------|---------|
| `KMapperWrapper` (interface, not annotation) | — | `Default` \| `None` \| `KtResult` \| `Flow`, or your own `object` implementing it |
| `@WrapperSuffix(suffix)` | class (a `KMapperWrapper` object) | names the generated function suffix, e.g. `"Result"` → `toXResult()` |

→ [Return Wrappers](../basic-usage/return-wrappers.md)

## Field directives

| Annotation | Target | Purpose |
|------------|--------|---------|
| `@FieldMap(fieldName, targetClass)` | property (repeatable) | match a differently-named target field; optionally scoped to one target |
| `@IgnoreMap` | property | exclude the field from auto-matching; the target slot defaults or becomes a caller parameter |
| `@IgnoreDefaultValue` | property | the constructor default is construction convenience only — absence becomes `RequiredFieldMissing` |

→ [Field Mapping](../basic-usage/field-mapping.md)

Placement rule: field directives are read from the **class that declares the mapping**
([details](../type-conversion/convert-with.md#the-placement-rule-worth-memorizing)).

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
Full mapping: [Migrating from 1.x](migration-1x.md).
