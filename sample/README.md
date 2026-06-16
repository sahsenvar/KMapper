# KMapper Sample Gallery

Every feature of KMapper, demonstrated with small, runnable, real-world examples.
Each file is self-contained (its own models + a `main()` you can run from the IDE) and ordered
from the most basic usage to the most advanced within its category.

## Learning path

| # | Category | Files (basic → advanced) | You will learn |
|---|----------|--------------------------|----------------|
| 1 | **Basics** (`sample.basics`) | `BasicMapping.kt` → `ReverseMapping.kt` → `MultipleTargets.kt` | `@MapTo`, the `toXResult(): Result<X>` boundary, `@MapFrom`, mapping one source to many targets |
| 2 | **Fields** (`sample.fields`) | `FieldRenaming.kt` → `IgnoreFamily.kt` → `ExternalParameters.kt` | `@FieldMap`, `@IgnoreMap`, `@IgnoreDefaultValue`, values supplied by the caller |
| 3 | **Nullability & defaults** (`sample.nullability`) | `FallbackLadder.kt` → `ResultBoundary.kt` | the fallback ladder (default > null > error), production `Result` handling patterns |
| 4 | **Converters** (`sample.converters`) | `BuiltInConverters.kt` → `CustomConverter.kt` → `PerFieldOverride.kt` → `OnFailPolicies.kt` → `SanctionedNull.kt` → `ParameterizedConverters.kt` → `OneWayConverters.kt` | auto-discovery, writing converters, `@ConvertWith(use, onFail)`, `convertToOrNull`, format variants, `@UnsupportedDirection` |
| 5 | **Collections** (`sample.collections`) | `ListMapping.kt` → `SetAndMapMapping.kt` → `ElementPolicies.kt` → `WrappedCollections.kt` | element ladder (salvage by default), Set/Map semantics, `OnFail.Throw`/`Skip` on elements, `@CollectionWrapper` (PersistentList, NonEmptyList) |
| 6 | **Nested objects** (`sample.nested`) | `NestedObjects.kt` → `DeepErrorPaths.kt` | sub-mappers, error paths like `customer.address.zipCode`, bounding the blast radius |
| 7 | **Enums** (`sample.enums`) | `EnumMapping.kt` → `SerializableEnumMapping.kt` | `MappableEnum`, kotlinx.serialization `@SerialName` enums, unknown wire values, absorption at nullable targets |
| 8 | **Validation** (`sample.validation`) | `FieldValidation.kt` | field-anchored `@Validate`, custom validators, the validators add-on |
| 9 | **Observability** (`sample.observability`) | `ListenersAndSink.kt` | `MappingListener`, the degradation sink, "crash in debug, observe in prod" |
| 10 | **Hand-written mappers** (`sample.handwritten`) | `CoreOnlyMapping.kt` | using `kmapper-core` alone — the same seams generated code uses, no annotations/KSP |

Global converter/wrapper registration for the whole module lives in
`sample.config.MappingConfig` — the same pattern a real app would use.

Run the whole gallery from the command line (or hit ▶ next to any file's `main` in the IDE):

```bash
./gradlew sample:runSample
```

## Mental model in three rules

1. **Absence follows the type.** Source value missing? Nullable target → `null`; defaulted
   target → the constructor default; neither → `RequiredFieldMissing`. No annotation needed.
2. **Brokenness is loud by default, but contained.** A failed conversion is absorbed only where
   the type declares an escape (default / nullable) — and every absorption is REPORTED to the
   degradation sink. The mapper itself returns `Result`: your app never crashes unless you call
   `.getOrThrow()`.
3. **Leniency is explicit, local, compiler-checked.** `@ConvertWith(onFail = …)` is visible on
   the field; `OnFail.Skip` works only on collection elements; impossible conversions fail at
   compile time with a guiding message.
