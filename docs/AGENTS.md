# KMapper — cheat sheet for AI coding agents

Compact rules for writing **consumer code** that uses KMapper 2.x. Full docs:
[docs/guide-en](guide-en/README.md) · single-file context: [llms-full.txt](../llms-full.txt)
· migrating 1.x code: [migration guide](guide-en/reference/migration-1x.md).

## Setup (copy-paste)

```kotlin
plugins { id("com.google.devtools.ksp") }

dependencies {
    implementation("io.github.sahsenvar:kmapper-core:2.2.2")
    implementation("io.github.sahsenvar:kmapper-annotations:2.2.2")
    ksp("io.github.sahsenvar:kmapper-compiler:2.2.2")
}
```

KMP: add the compiler per target (`kspCommonMainMetadata`, `kspJvm`, `kspIosArm64`, …) — see
[installation](guide-en/getting-started/installation.md#kotlin-multiplatform).

## The model (3 rules — everything follows from these)

1. **Absence follows the type.** Null source value → nullable target gets `null`; defaulted
   target gets its constructor default; neither → `RequiredFieldMissing`. No annotation.
2. **Brokenness is loud but contained.** Unparseable/unknown values absorb into a declared
   escape (default/nullable) WITH a report to `MappingListener.onDegradation`; no escape →
   typed failure. The mapper returns `Result<T>` — nothing throws unless you `.getOrThrow()`.
3. **Leniency is explicit and local.** Per-field `@ConvertWith(onFail = Throw|Skip)`; no
   global switches. Unsafe conversions (`Long → Int`) are compile errors, not runtime data loss.

## Canonical declaration

```kotlin
data class User(val id: Long, val joined: LocalDate, val bio: String?)

@MapTo(User::class)                       // on the WIRE model; @MapFrom(Source::class) on the
data class UserResponse(                  // target when the source class isn't yours
    val id: Long,
    val joined: String,                   // String -> LocalDate: built-in, no registration
    val bio: String?,
)

val user: Result<User> = response.toUserResult()   // generated; note the ...Result suffix
```

## Annotation quick table

| Need | Write |
|------|-------|
| different field names | `@FieldMap("targetName")` on the source field (`targetClass = X::class` to scope when several `@MapTo`s) |
| exclude a field | `@IgnoreMap` (target slot defaults, or becomes a required parameter of the generated function) |
| default must not mask missing wire data | `@IgnoreDefaultValue` on the target field |
| custom conversion for one field | `@ConvertWith(use = MyConverter::class)` |
| field too important to absorb | `@ConvertWith(onFail = OnFail.Throw)` |
| compact broken list elements | `@ConvertWith(onFail = OnFail.Skip)` (collections only) |
| invariant on a value | `@Validate(NotBlankValidator::class, …)` on the owning field |
| register converters/wrappers module-wide | `@KMapperConfig(converters = [...], wrappers = [...])` on any object, once per module |

## Custom converter / validator shapes (must be `object`)

```kotlin
object MoneyStringConverter : MapTypeConverter<Money, String>(Money::class, String::class) {
    override fun convertTo(source: Money): String = source.format()
    override fun convertFrom(target: String): Money = Money.parse(target) // throw on bad input
}

object QuantityValidator : IntRangeValidator(1..999) // parameterized base, object subclass
```

Register the converter in `@KMapperConfig(converters = [MoneyStringConverter::class])` —
discovery is by type pair; no per-field annotation needed for the normal case.

## Sharp edges (agents get these wrong)

- **Directive placement:** `@FieldMap`/`@ConvertWith` are read from the **source field of the
  generated direction**. With `@MapTo` on the wire model, annotate the WIRE field.
  (`@Validate` is the exception: it fires from either side.)
- **One converter per type pair per module** in `@KMapperConfig`; format variants of the same
  pair go per-field via `@ConvertWith(use = …)`.
- **Generated names:** function is `toXResult()` (not `toX()`); file lands in
  `build/generated/ksp/<target>/kotlin/…` named after the RECEIVER class (`@MapFrom`
  generates onto the source/wire class).
- **Enums:** implement `MappableEnum<W>` with explicit `wireValue`, **or** annotate a
  kotlinx.serialization `@Serializable` enum (wire value = `@SerialName` else entry name,
  String only; `MappableEnum` wins if both). Name/ordinal mapping does not exist. Unknown wire
  value: nullable enum target → `null` + report; non-null → error.
- **Don't catch around mappers** — branch on the `Result`. `CancellationException` always
  propagates.
- `kotlinx-datetime` types and `kotlin.time.Duration` need **no add-on** (core built-ins);
  `java.time` lives in `kmapper-converters-datetime` (JVM/Android only).

## Compile errors → fixes

| Error says | Fix |
|------------|-----|
| `X -> Y has no registered converter` | register a converter for the pair in `@KMapperConfig`, or `@ConvertWith` on the field |
| `X -> Y conversion is unsupported!` | direction refused by policy (lossy). Write the 3-line custom converter if your domain guarantees safety |
| `must specify targetClass` | several `@MapTo`/`@MapFrom` on the class — scope each `@FieldMap` with `targetClass =` |
| `OnFail.Skip applies to collection elements only` | use `Throw` or a default/nullable escape for scalars |
| `@Ignore`/`@UseMapTypeConverter`/`@ValidateFrom`/`@MapDefaultValue` deprecation ERROR | you wrote 1.x API — the message names the 2.0 replacement; see the [migration guide](guide-en/reference/migration-1x.md) |

## Verifying behavior

Read the generated function first (`build/generated/ksp/...`) — it is plain Kotlin and answers
most "why did it map like that" questions. Runnable examples for every feature:
[sample gallery](../sample/README.md) (`./gradlew sample:runSample`).
