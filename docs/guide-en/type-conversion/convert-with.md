# @ConvertWith — Per-Field Overrides and OnFail

Auto-discovery handles the rule; `@ConvertWith` handles the exception. It does two
independent jobs, usable together or alone:

```kotlin
annotation class ConvertWith(
    val use: KClass<out MapTypeConverter<*, *>> = /* keep auto-discovery */,
    val onFail: OnFail = OnFail.Auto,
)
```

## Job 1 — `use`: pick a different converter for THIS field

```kotlin
@MapTo(Document::class)
data class DocumentResponse(
    val title: String,            // String -> ByteString: module-wide UTF-8 converter
    @ConvertWith(use = Base64ByteStringConverter::class)
    val payload: String,          // …but THIS field's wire format is Base64
)
```

`@ConvertWith` is **override-only**: you never need it for the normal case — registration in
[`@KMapperConfig`](kmapperconfig.md) plus discovery covers that. Reach for it when one field
deviates from the module-wide rule (format variants, [parameterized
converters](custom-converter.md#parameterized-converters)).

## Job 2 — `onFail`: tune this field's failure policy

| Policy | Scalar field | Collection field |
|--------|--------------|------------------|
| `Auto` (default) | the [ladder](../basic-usage/null-safety.md) | the [element ladder](../basic-usage/collections.md) |
| `Throw` | never absorb — broken value fails the mapping even into a nullable/defaulted slot | first broken element fails the mapping (all-or-nothing) |
| `Skip` | — compile error (skipping a scalar would *fabricate absence*) | drop broken/null elements, compact, report |

```kotlin
@MapTo(Measurements::class)
data class MeasurementsResponse(
    @ConvertWith(onFail = OnFail.Throw)
    val invoiceLines: List<String>, // money: refuse partial success

    @ConvertWith(onFail = OnFail.Skip)
    val tagIds: List<String?>,      // tags: best-effort, compacted
)
```

## Direction-scoped variants

`@ConvertTo(target, use, onFail)` and `@ConvertFrom(source, use, onFail)` are `@ConvertWith`
scoped to one mapping direction/target — for when the same field needs different treatment in
different generated mappings. `@ConvertWith` applies to all directions the field participates
in.

## The placement rule (worth memorizing)

Directives are read from the **class that declares the mapping** — the class carrying
`@MapTo` or `@MapFrom`. With `@MapTo` on the wire model, annotate the *wire* field (the
source); with `@MapFrom` on a UI model, annotate the *UI model's* field (the target), so the
source — often a domain class from another module — never needs annotating:

```kotlin
@MapFrom(OptionStatsModel::class)            // OptionStatsModel lives in core:domain
data class OptionStatsUiModel(
    @ConvertFrom(use = PriceTextConverter::class) val mark: String,
    @ConvertFrom(use = PercentTextConverter::class) val iv: String,
)
```

In the `@MapFrom` direction a directive on the source field still works as a fallback; if
both sides carry one, the target's wins and the build warns. In the `@MapTo` direction an
annotation on the target class is invisible. (Anchored-to-the-field `@Validate` is the
deliberate exception: it fires whichever side of a mapping its field is on.)

> Next: **[Immutable Collections →](immutable.md)**
