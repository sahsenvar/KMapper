# Multi-Module Projects

The rule of thumb: **the compiler goes where mappings are declared; the runtime goes where
generated code is called.**

## Who needs what

| Module | Needs |
|--------|-------|
| declares `@MapTo`/`@MapFrom` models | `kmapper-annotations` + `ksp(kmapper-compiler)` |
| only *calls* `toXResult()` | `kmapper-core` (usually transitively) |
| only uses seams/validators by hand | `kmapper-core` |

A typical layered app:

```
:data       -> declares wire models + @MapTo + its own @KMapperConfig; runs KSP
:domain     -> plain models; kmapper-core only (transitive)
:app        -> calls generated mappers; no KSP
```

## Cross-module model pairs work

`@MapTo(DomainUser::class)` in `:data` can point at a class from `:domain` — KSP resolves
classpath symbols, including their constructor defaults (the
[ladder](../basic-usage/null-safety.md) works across module boundaries).

## One @KMapperConfig per declaring module

[Registration](../type-conversion/kmapperconfig.md) is per compilation module. Modules don't
inherit each other's configs; give each mapping-declaring module its own (sharing converter
*objects* between them is fine — they're ordinary classes):

```kotlin
// :data/src/…/DataMapperConfig.kt
@KMapperConfig(converters = [MoneyStringConverter::class])
object DataMapperConfig
```

## KMP wiring reminder

In a multiplatform module the processor is registered **per target**
(`kspCommonMainMetadata`, `kspJvm`, `kspIosArm64`, …) — full snippet in
[Installation](../getting-started/installation.md#kotlin-multiplatform).

> Next: **[Architecture →](architecture.md)**
