# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.0.1] - 2026-06-12

### Added — AI-friendliness package

- **Guided 1.x deprecation stubs** in `kmapper-annotations`: `@Ignore`, `@UseMapTypeConverter`,
  `@ValidateFrom`, `@ValidateTo` are ERROR-level typealiases of their 2.0 replacements
  (with `ReplaceWith`), and `@MapDefaultValue` is an ERROR-level stub pointing to constructor
  defaults — 1.x-era code now fails compilation with the exact migration instruction instead
  of an unresolved reference.
- **`llms.txt` / `llms-full.txt`** at the repo root (llmstxt.org format): curated index and
  single-file full content of the English guide, generated from `docs/guide-en` in SUMMARY
  order by `scripts/generate-llms-txt.py`. (GitBook additionally auto-serves its own
  `llms.txt` for the published Türkçe space.)
- **`docs/AGENTS.md`** — consumer cheat sheet for AI coding agents: setup, the 3-rule mental
  model, annotation quick table, sharp edges, compile-error → fix table.
- README section routing AI assistants to the above.

## [2.0.0] - 2026-06-12

Migration guide: [docs/guide-en/reference/migration-1x.md](docs/guide-en/reference/migration-1x.md)
(Türkçe: [docs/guide/referans/gecis-1x.md](docs/guide/referans/gecis-1x.md)).

### Changed — converter subsystem redesign (BREAKING)

- **Result boundary:** generated mappers are now `toXResult(): Result<X>` — failures arrive as
  values, never surprise exceptions (`.getOrThrow()` is the caller's explicit choice).
- **Fallback ladder** as the default behavior: `converted value > declared constructor default >
  null/not-a-member > error`; brokenness absorbed by a declared escape is REPORTED to the new
  degradation sink (`MappingListener.onDegradation`), declared-absence flows stay silent.
- **Path-carrying errors:** all `MappingException`s carry a field path from the mapping root
  (`customer.address.zipCode`, `items[3].price`) — R8-safe codegen literals.
- **Module split:** new `kmapper-annotations` artifact (declaration annotations; depends on
  `kmapper-core`); the processor publishes as `kmapper-compiler` (was `kmapper-processor`);
  `kmapper-core` is usable standalone for hand-written mappers via the new public conversion
  seams (`convertOrFail/OrNull/OrElse`, `convertEachOr*`, `convertEntriesOr*`).
- **Converter model:** 4-method `MapTypeConverter` (`convertTo`/`convertFrom` totals +
  sanctioned-null `convertToOrNull`/`convertFromOrNull`); function-level
  `@UnsupportedDirection(reason)` with compile-time enforcement; richer-first built-in naming
  (e.g. `IntStringConverter`); 35 built-in pair objects (28 primitive + kotlinx-datetime
  `Instant`/`LocalDate`/`LocalDateTime`/`LocalTime` + `kotlin.time.Duration`) — every pair
  either converts or explains why not at compile time.
- **Annotations:** `@ConvertWith(use, onFail)` (+ direction-scoped `@ConvertTo`/`@ConvertFrom`),
  `OnFail { Auto, Throw, Skip }`, field-anchored `@Validate`, `@IgnoreMap` (was `@Ignore`),
  new `@IgnoreDefaultValue`. REMOVED: `@UseMapTypeConverter`, `@MapDefaultValue` (constructor
  defaults via omit/copy replace it), `@ValidateFrom`/`@ValidateTo`.
- **Collections:** element-ladder semantics (broken/null elements skip or null-in-place with
  reporting; `OnFail.Throw` hardens, `OnFail.Skip` compacts), Map key/value ladders with
  duplicate-key reporting, Set convergence reporting, bidirectional `@CollectionWrapper`
  (`wrap`/`unwrap`, compile-checked signatures).
- **Add-on converter audit (BREAKING for `kmapper-converters-bignumber` and
  `kmapper-converters-datetime`):**
  - kotlinx-datetime `LocalDate`/`LocalDateTime`/`LocalTime` ↔ `String` moved from
    `kmapper-converters-datetime` into core built-ins (auto-resolved, no registration);
    the datetime add-on now contains only the `java.time` converters and bridges.
  - bignumber lossy directions (`BigDecimal → Double`, `BigInteger → Long`/`Int`,
    `BigDecimal → BigInteger`, and their `java.math` twins) previously truncated or lost
    precision SILENTLY — they are now `@UnsupportedDirection` and refuse at compile time;
    write a custom converter if your domain guarantees the range.
  - New in `kmapper-converters-okio`: `Base64ByteStringConverter`, `Base64UrlByteStringConverter`,
    `HexByteStringConverter` (same-pair alternates to the UTF-8 converter — select per-field
    or per-config).
  - New in `kmapper-converters-datetime`: `StringJavaDurationConverter` and the
    `KotlinJavaDurationConverter` bridge.
- **Validator library:**
  - Core `validation.builtin` grows from 3 to 16 entries: `Positive`/`NonNegative` ×
    `Int`/`Long`/`Double`, `FiniteDoubleValidator`, and parameterized open bases
    (`RegexValidator`, `StringLengthValidator`, `IntRangeValidator`/`LongRangeValidator`/
    `DoubleRangeValidator`, `CollectionSizeValidator`) that you subclass as `object`s with
    your own bounds — the same recipe as parameterized converters.
  - `kmapper-validators` add-on grows from 2 to 16: `PhoneE164Validator`, `Ipv4Validator`,
    `Ipv6Validator`, `HostnameValidator`, `UuidStringValidator`, `SlugValidator`,
    `Base64Validator`, `HexStringValidator`, `LatitudeValidator`, `LongitudeValidator`,
    `PortNumberValidator`, `CreditCardNumberValidator` (Luhn), alongside the existing
    `EmailValidator`/`UrlValidator`.

## [1.0.0] - 2026-06-05

### Added

#### Core compile-time mapping (`kmapper-core` + `kmapper-processor`)

- `@MapTo(TargetClass::class)` annotation placed on source classes; KSP generates a
  `fun Source.toTarget(): Target` extension function at compile time — no runtime reflection.
- `@MapFrom(SourceClass::class)` annotation placed on target classes; generates the same
  `toTarget()` extension on the source, providing a reverse-direction alternative when the
  target class is the natural place to declare the mapping.
- Both annotations are `@Repeatable`: a single source can be mapped to multiple targets, and a
  single target can accept multiple sources.
- **Field matching** by name by default; no configuration required for same-name, same-type fields.
- `@FieldMap(fieldName, targetClass?)` to rename a source field to a different name in the target.
  The optional `targetClass` parameter scopes the rename to a specific target when multiple
  `@MapTo` annotations are present.
- `@Ignore` to exclude a source field from all generated mappings. A compile error is emitted if
  the corresponding target constructor parameter has no default value.
- `@MapDefaultValue(expression)` to supply a Kotlin expression as the fallback value when a
  nullable source field maps to a non-null target field, preventing a `RequiredFieldMissing` throw.
- **Null-safety enforcement**: four nullability cases handled exhaustively at code-generation time —
  nullable→nullable (direct), non-null→non-null (direct), non-null→nullable (direct), and
  nullable→non-null (auto-inserted `?: throw MappingException.RequiredFieldMissing(field)` — never
  silent).
- **Nested model chaining**: when a source field's type is itself a `@MapTo`/`@MapFrom`-annotated
  class, the generated code calls the nested `toX()` extension automatically.
- **Collection mapping**: `List<T>` and `Set<T>` fields are mapped element-by-element using the
  same rules as scalar fields; nullable collections are handled without special annotations.
- **`Map<K, V>` value mapping**: `Map<K, V1>` → `Map<K, V2>` is supported out of the box via
  `mapValues`; keys must share the same type; unsupported map types (e.g. `PersistentMap`) emit a
  compile error.
- **Compile-time unconditional-cycle detection**: a guaranteed-infinite mapping cycle (A→B→A with
  no escape) is detected by the `Validator` stage of the KSP pipeline and reported as a compile
  error, not a runtime failure.
- **`MappableEnum<W>`** interface for safe enum mapping. Enums implement `MappableEnum<String>` or
  `MappableEnum<Int>` and declare a `wireValue` per constant; the processor generates
  `fromWireValue` / `toWireValue` helpers. An unknown wire value at runtime throws
  `MappingException.UnknownEnumValue`. An enum field without `MappableEnum` and without a
  `@UseMapTypeConverter` override is a compile error. Third-party enums that cannot be modified use
  `@UseMapTypeConverter` as an escape hatch.
- **`KMapper` / `MappingListener` observability**: register one or more `MappingListener`
  implementations via `KMapper.addListener(…)` / `KMapper.removeListener(…)`. The generated code
  wraps every mapping invocation with `KMapper.hasListeners`-guarded `onMapStart` / `onMapComplete`
  / `onError` dispatch — zero overhead when no listener is registered. A ready-to-use
  `LoggingMappingListener` is included.
- **`MapTypeConverter<S, T>`** abstract class for custom bidirectional scalar conversions.
  Implement `convertToNonNull(S): T` and `convertFromNonNull(T): S`; null routing is handled by
  the base class. Converters are registered globally in `@KMapperConfig(converters = […])` or
  applied per-field with `@UseMapTypeConverter(Converter::class)`.
- **`@KMapperConfig`** object-level annotation to declare the global converter list and
  collection-wrapper list consumed by the processor.
- **Built-in primitive / datetime converters** (auto-recognized, no `@KMapperConfig` entry needed):
  `String`↔`Int`, `String`↔`Long`, `String`↔`Double`, `Int`↔`Long`, `Int`↔`Double`,
  `Long`↔`Double`; `String`/`Long` → `kotlinx.datetime.Instant` (ISO-8601 / epoch-millis).

#### Validation seam (`kmapper-core`)

- `@ValidateFrom(Validator::class)` applied to a source field: runs the validator on the raw
  source value **before** type conversion or null coercion.
- `@ValidateTo(Validator::class)` applied to a source field: runs the validator on the **converted
  result** value after type conversion, before it is passed to the target constructor.
- Both annotations can be stacked on the same field (source validated first, then result).
- `Validator<T>` abstract base class; must be an `object` singleton (the processor emits fully
  qualified direct calls — no reflection, fully KMP-safe). `validate(value: T): String?` returns
  `null` for valid or a human-readable reason string for invalid.
- Type-safety: annotating a `String` field with a `Validator<Int>` is a compile error.
- Validators require no `@KMapperConfig` registration; they are referenced directly by class
  reference on the field annotation.
- **Fail-fast**: any failed validation throws `MappingException.ValidationFailed(field, reason)`
  immediately; subsequent fields are not evaluated.
- Built-in validators shipped in `kmapper-core`:
  - `NotBlankValidator` (`String`) — fails when `isBlank()` is true.
  - `NotEmptyStringValidator` (`String`) — fails when `isEmpty()` is true.
  - `NotEmptyCollectionValidator` (`Collection<*>`) — fails when `isEmpty()` is true.

#### Add-on modules

- **`kmapper-converters-immutable`** (KMP): collection-wrapper converters for
  `kotlinx.collections.immutable` — `List` → `PersistentList`, `ImmutableList`, `ImmutableSet`,
  `PersistentSet`. Declared in `@KMapperConfig(wrappers = […])`.
- **`kmapper-converters-arrow`** (KMP): Arrow collection-wrapper converters — `List` →
  `NonEmptyList`, `NonEmptySet`, `Option<T>` mapping. Empty source throws
  `MappingException.EmptyCollection`. Declared in `@KMapperConfig(wrappers = […])`.
- **`kmapper-converters-datetime`** (KMP — `kotlinx.datetime`; JVM + Android — `java.time` with
  bridges): scalar converters for `String`/`Long` ↔ `LocalDate`, `LocalDateTime`, `LocalTime`,
  `ZonedDateTime`, `OffsetDateTime`, `Instant`.
- **`kmapper-converters-bignumber`** (KMP — `ionspin BigDecimal/BigInteger`; JVM + Android —
  `java.math`): scalar converters for `String`/`Double`/`Long`/`Int` ↔ `BigDecimal`, `BigInteger`.
- **`kmapper-converters-uuid`** (KMP commonMain + JVM/Android): `String` ↔
  `kotlin.uuid.Uuid`; `String`/`kotlin.uuid.Uuid` ↔ `java.util.UUID` (JVM/Android only).
- **`kmapper-converters-okio`** (KMP): `String`/`ByteArray` ↔ `okio.ByteString`;
  `String` ↔ `okio.Path`.
- **`kmapper-converters-uri`** (platform-split JVM / Android / iOS): `String` ↔ `java.net.URI`
  (JVM), `android.net.Uri` (Android), `platform.Foundation.NSURL` (iOS).
- **`kmapper-validators`** (KMP): `EmailValidator` and `UrlValidator` for use with
  `@ValidateFrom` / `@ValidateTo`.

#### Platform & distribution

- All 10 artifacts published to Maven Central under group `io.github.sahsenvar`:
  `kmapper-core`, `kmapper-processor`, `kmapper-converters-immutable`,
  `kmapper-converters-arrow`, `kmapper-converters-datetime`, `kmapper-converters-bignumber`,
  `kmapper-converters-uuid`, `kmapper-converters-okio`, `kmapper-converters-uri`,
  `kmapper-validators`.
- Targets: JVM, Android, iOS (iosArm64, iosSimulatorArm64, iosX64) — all mapping code generated
  at compile time; no runtime reflection used on any platform.

---

[Unreleased]: https://github.com/sahsenvar/KMapper/compare/v2.0.1...HEAD
[2.0.1]: https://github.com/sahsenvar/KMapper/compare/v2.0.0...v2.0.1
[2.0.0]: https://github.com/sahsenvar/KMapper/compare/v1.0.0...v2.0.0
[1.0.0]: https://github.com/sahsenvar/KMapper/releases/tag/v1.0.0
