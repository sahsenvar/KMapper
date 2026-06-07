# KMapper Add-on Converters — Round 1 Design

- **Date:** 2026-06-04
- **Status:** Design — awaiting user review
- **Scope (R1):** Four converter add-on modules that require **no core processor mechanism change** (only a tiny `MappingException.EmptyCollection` re-add). Map<K,V>, stdlib cross-kind nuances, Arrow `Option`, and scalar-converter auto-discovery are **deferred to R2/R3**.

## Goal

Ship four optional converter add-on artifacts under `io.github.sahsenvar`, each independently consumable:

| Module (dir) | Artifact | Platforms | External deps |
|---|---|---|---|
| `converters-immutable` (rename of `converters-compose`) | `kmapper-converters-immutable` | android, jvm, iosArm64, iosSimulatorArm64 | `kotlinx-collections-immutable` (commonMain) |
| `converters-arrow` | `kmapper-converters-arrow` | same (KMP) | `arrow-core` (commonMain) |
| `converters-datetime` | `kmapper-converters-datetime` | common + jvm/android | `kotlinx-datetime` (commonMain); `java.time` (jvm/androidMain, no dep) |
| `converters-bignumber` | `kmapper-converters-bignumber` | common + jvm/android | `com.ionspin.kotlin:bignum` (commonMain); `java.math` (jvm/androidMain, no dep) |

## Two add-on shapes

1. **Collection-wrapper add-ons** (`immutable`, `arrow` Nel): ship `@CollectionWrapper`-annotated functions. The processor's existing cross-module descriptor discovery (`getDeclarationsFromPackage`) picks them up automatically — **the consumer only adds the dependency; no `@KMapperConfig` listing needed**. Source-kind-agnostic: `source.map { it.toX() }` yields a `List`, the wrapper's `wrapFn()` produces the target kind — so `List→PersistentSet`, `PersistentList→PersistentSet`, etc. all work for free.
2. **Scalar-converter add-ons** (`datetime`, `bignumber`): ship `MapTypeConverter` objects. The consumer lists the specific ones they use in `@KMapperConfig(converters = [...])` (KClass refs resolve from the dependency artifact). **Auto-discovery of scalar add-on converters is an R2 enhancement** — R1 is explicit listing.

## Tiny core change (R1)

Re-add one exception subtype dropped during extraction, for the Arrow Nel empty-source case:

```kotlin
// core/.../MappingException.kt
class EmptyCollection(val detail: String)
    : MappingException("Collection cannot be empty: $detail")
```
This is a data-only addition (no mechanism change).

## Module 1 — `converters-immutable` (rename)

**Rename:** dir `converters-compose` → `converters-immutable`; package `com.sahsenvar.kmapper.compose` → `com.sahsenvar.kmapper.immutable`; artifact `kmapper-converters-compose` → `kmapper-converters-immutable`. The already-published `kmapper-converters-compose:0.1.0` stays orphaned on Maven Central (acceptable pre-1.0). Update `settings.gradle.kts`, `:sample`, docs, README.

**`@CollectionWrapper` functions (commonMain):**
- `fun <T> List<T>.asPersistentList(): PersistentList<T>` — `forType = PersistentList::class` *(exists)*
- `fun <T> List<T>.asImmutableList(): ImmutableList<T>` — `forType = ImmutableList::class` *(exists)*
- `fun <T> List<T>.asPersistentSet(): PersistentSet<T>` — `forType = PersistentSet::class` *(NEW)*
- `fun <T> List<T>.asImmutableSet(): ImmutableSet<T>` — `forType = ImmutableSet::class` *(exists)*

Cross-kind (e.g. `List→PersistentSet`, `Set→ImmutableList`, `PersistentList→PersistentSet`) works automatically via the wrapper target type. **`PersistentMap`/`ImmutableMap` → R2** (needs core `Map<K,V>` mapping).

## Module 2 — `converters-arrow` (Nel)

**`@CollectionWrapper` function (commonMain):**
- `fun <T> List<T>.asNonEmptyList(): NonEmptyList<T> = toNonEmptyListOrNull() ?: throw MappingException.EmptyCollection("NonEmptyList source was empty")` — `forType = NonEmptyList::class`

So `List<A> → NonEmptyList<B>` generates `source.map { it.toB() }.asNonEmptyList()`; an empty source throws `MappingException.EmptyCollection` (loud, not silent). **`Option` (`T?↔Option<T>`) → R3** (needs a generic single-value container mechanism). `NonEmptySet` is a trivial future add (same pattern).

## Module 3 — `converters-datetime` (platform-split scalar converters)

`MapTypeConverter` objects. **Does NOT duplicate core built-ins** (`StringInstantConverter`, `LongInstantConverter` for `kotlinx.datetime.Instant` already exist in core).

**commonMain (kotlinx-datetime):**
- `LocalDateStringConverter` — `kotlinx.datetime.LocalDate ↔ String` (ISO-8601)
- `LocalDateTimeStringConverter` — `kotlinx.datetime.LocalDateTime ↔ String`
- `LocalTimeStringConverter` — `kotlinx.datetime.LocalTime ↔ String`

**jvmMain + androidMain (java.time):** (prefixed `Java` to disambiguate in `@KMapperConfig`)
- `JavaInstantStringConverter` — `java.time.Instant ↔ String`
- `JavaInstantEpochMilliConverter` — `java.time.Instant ↔ Long`
- `JavaLocalDateStringConverter`, `JavaLocalDateTimeStringConverter`, `JavaLocalTimeStringConverter`
- `JavaZonedDateTimeStringConverter`, `JavaOffsetDateTimeStringConverter`
- **Bridges** (using kotlinx-datetime's JVM `toJavaX`/`toKotlinX`): `JavaInstantToKotlinInstantConverter`, `JavaLocalDateToKotlinLocalDateConverter`, `JavaLocalDateTimeToKotlinLocalDateTimeConverter`

iOS gets only the commonMain (kotlinx) converters — correct, since `java.time` does not exist there.

## Module 4 — `converters-bignumber` (java.math + ionspin scalar converters)

**commonMain (ionspin `com.ionspin.kotlin:bignum` — `BigDecimal`/`BigInteger`):**
- `StringBigDecimalConverter` — `String ↔ BigDecimal`
- `StringBigIntegerConverter` — `String ↔ BigInteger`
- `DoubleBigDecimalConverter` — `Double ↔ BigDecimal`
- `LongBigIntegerConverter` — `Long ↔ BigInteger`
- `IntBigIntegerConverter` — `Int ↔ BigInteger`
- `BigIntegerBigDecimalConverter` — `BigInteger ↔ BigDecimal`

**jvmMain + androidMain (`java.math` — prefixed `Java`):**
- `StringJavaBigDecimalConverter` — `String ↔ java.math.BigDecimal`
- `StringJavaBigIntegerConverter` — `String ↔ java.math.BigInteger`
- `DoubleJavaBigDecimalConverter` — `Double ↔ java.math.BigDecimal`
- `LongJavaBigIntegerConverter` — `Long ↔ java.math.BigInteger`
- `JavaBigIntegerBigDecimalConverter` — `java.math.BigInteger ↔ java.math.BigDecimal`

On jvm/android both sets exist (different target types); the consumer lists whichever matches their model. iOS gets only ionspin.

## Consumption examples

```kotlin
// Wrapper add-ons: just add the dependency — wrappers auto-discovered.
commonMainImplementation("io.github.sahsenvar:kmapper-converters-immutable:<v>")
data class UserDomain(val tags: PersistentSet<TagDomain>)   // List<TagRemote> → PersistentSet<TagDomain> works

// Scalar add-ons: list the converters you use.
commonMainImplementation("io.github.sahsenvar:kmapper-converters-bignumber:<v>")
@KMapperConfig(converters = [StringJavaBigDecimalConverter::class, LocalDateStringConverter::class])
object AppMapperConfig
```

## Testing

- Wrapper add-ons (`immutable`, `arrow`): processor **compile-tests** (kctfork) verifying the generated mapper emits `…asPersistentSet()` / `…asNonEmptyList()` for the matching target type, plus a cross-module check via the `:sample` module (or per-add-on sample). Arrow: a test that an empty source path generates the `asNonEmptyList()` call (runtime empty→`EmptyCollection` is covered by a small runtime test).
- Scalar add-ons (`datetime`, `bignumber`): the converters are plain `MapTypeConverter`s — **runtime unit tests** (jvmTest, and commonTest where multiplatform) asserting `convertToNonNull`/`convertFromNonNull` round-trips. A compile-test asserting a `@KMapperConfig`-listed add-on converter is applied in generated code.

## Deferred (R2/R3)

- **R2 (core):** `Map<K,V>` mapping (key+value element mapping) → then `PersistentMap`/`ImmutableMap` wrappers; scalar add-on converter **auto-discovery** (so datetime/bignumber don't need explicit listing).
- **R3:** Arrow `Option` (`T?↔Option<T>`) via a generic single-value container mechanism; `NonEmptySet`.

## Build/release notes

- Each module: vanniktech publishing config (same pattern as existing modules) with its `kmapper-converters-*` coordinates, version inherits root (`0.2.0-SNAPSHOT`).
- Released together as **0.2.0** when ready (the rename means `kmapper-converters-compose` stops receiving updates at 0.1.0).
- External dep versions (resolve latest stable at implementation): `arrow-core`, `com.ionspin.kotlin:bignum`; `kotlinx-datetime` (0.6.0, in catalog), `kotlinx-collections-immutable` (0.3.7, in catalog).
