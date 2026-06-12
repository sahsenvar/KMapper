# Migrating from 1.x to 2.0

2.0 is the converter-subsystem redesign: failures became values, the fallback ladder became
the default behavior, and the converter/validation/observability surfaces were rebuilt for
[user–author parity](../getting-started/mental-model.md#the-parity-principle). The release is
**intentionally breaking**; this page is the complete map from old to new.

## 1. Coordinates

| 1.x | 2.0 |
|-----|-----|
| `kmapper-core` | `kmapper-core` (now also usable standalone) |
| — | **`kmapper-annotations`** (new artifact — annotations moved out of core) |
| `kmapper-processor` | **`kmapper-compiler`** |
| `ksp("…:kmapper-processor:1.0.0")` | `ksp("…:kmapper-compiler:2.0.0")` |

Annotation **imports are unchanged** (`com.sahsenvar.kmapper.annotations.*`) — you only add
the new dependency:

```kotlin
implementation("io.github.sahsenvar:kmapper-core:2.0.0")
implementation("io.github.sahsenvar:kmapper-annotations:2.0.0")  // new
ksp("io.github.sahsenvar:kmapper-compiler:2.0.0")                // renamed
```

## 2. The generated API: `toX()` → `toXResult()`

| 1.x | 2.0 |
|-----|-----|
| `fun Source.toUser(): User` (throws) | `fun Source.toUserResult(): Result<User>` |

Mechanical migration — old throwing behavior is one call away:

```kotlin
// 1.x
val user = response.toUser()

// 2.0, same semantics:
val user = response.toUserResult().getOrThrow()
```

…but the [Result boundary](../error-handling/mapping-exception.md) is the feature: prefer
`getOrElse`/`fold` at real call sites.

## 3. Annotations

| 1.x | 2.0 | Notes |
|-----|-----|-------|
| `@Ignore` | `@IgnoreMap` | same idea, clearer name |
| `@MapDefaultValue(expression)` | **removed** — use a constructor default | the default now lives in one place; see [field mapping](../basic-usage/field-mapping.md) |
| `@UseMapTypeConverter(X::class)` | `@ConvertWith(use = X::class)` | gains `onFail` policy too |
| `@ValidateFrom` / `@ValidateTo` | `@Validate` | now [field-anchored](../validation/validate.md): one declaration, both directions |
| — | `@IgnoreDefaultValue`, `@ConvertTo`/`@ConvertFrom`, `@CollectionWrapper` | new capabilities |

## 4. Custom converters: the 4-method shape

| 1.x | 2.0 |
|-----|-----|
| `convertToNonNull(value: S): T` | `convertTo(source: S): T` |
| `convertFromNonNull(value: T): S` | `convertFrom(target: T): S` |
| `convertTo(value: S?): T?` (final, null-passing) | `convertToOrNull(source: S): T?` (open — [sanctioned null](../type-conversion/custom-converter.md#the-two-optional-methods-sanctioned-null)) |
| `convertFrom(value: T?): S?` (final) | `convertFromOrNull(target: T): S?` |

Null handling moved out of converters entirely — the generated
[ladder](../basic-usage/null-safety.md) owns it. Your migration: rename the two `NonNull`
methods and delete any null-shuffling. New capability:
[`@UnsupportedDirection(reason)`](../type-conversion/custom-converter.md#refusing-a-direction)
for directions you refuse to implement.

## 5. Built-in converter names: richer type first

| 1.x | 2.0 |
|-----|-----|
| `StringIntConverter` | `IntStringConverter` |
| `StringLongConverter` | `LongStringConverter` |
| `StringDoubleConverter` | `DoubleStringConverter` |
| `StringFloatConverter` | `FloatStringConverter` |
| `StringBooleanConverter` | `BooleanStringConverter` |
| `IntLongConverter` | `LongIntConverter` |
| `StringInstantConverter` | `InstantStringConverter` |
| `LongInstantConverter` | `InstantLongConverter` |

You rarely referenced these by name (discovery is automatic); fix imports where you did.
Add-on converter names are unchanged in this release.

## 6. Converters that moved or changed behavior

- **kotlinx-datetime `String` converters moved to core.** Delete
  `StringLocalDateConverter`-style entries from your `@KMapperConfig` — `LocalDate`,
  `LocalDateTime`, `LocalTime`, `Instant`, and `Duration` pairs are now
  [built-ins](../type-conversion/built-in.md). `kmapper-converters-datetime` keeps only
  `java.time` converters and bridges.
- **bignumber lossy directions now refuse at compile time** (`BigDecimal → Double`,
  `BigInteger → Long`/`Int`, `BigDecimal → BigInteger`). 1.x truncated these silently. If
  you relied on one, [write the explicit converter](../type-conversion/bignumber.md).

## 7. Behavior changes to review (not just renames)

- **The fallback ladder is the default.** In 1.x a broken value generally failed the
  mapping; in 2.0 a *nullable or defaulted* target field absorbs it (with a
  [degradation report](../observability/listener.md)). Audit fields where you *want*
  hardness and mark them `@ConvertWith(onFail = OnFail.Throw)`.
- **Collections salvage by default.** One broken element no longer fails the list — it's
  dropped/nulled and reported. `OnFail.Throw` restores all-or-nothing per field.
- **New sink channel.** `MappingListener` gained `onDegradation(event)`; register a
  listener so absorbed errors reach your telemetry.

## 8. Migration checklist

1. Update coordinates (§1) and build; fix `toX()` call sites mechanically (§2).
2. Search-and-replace annotations (§3); move `@MapDefaultValue` expressions into
   constructor defaults.
3. Rename custom-converter methods (§4); drop registrations covered by new built-ins (§6).
4. Add a degradation listener (§7) — even just a logger.
5. Audit must-be-hard fields and add `OnFail.Throw` where partial data is unacceptable.
6. Run the build: 2.0 turns the remaining gaps into **named compile errors** — they are the
   to-do list.
