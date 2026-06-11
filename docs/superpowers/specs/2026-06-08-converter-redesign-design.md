# Converter Redesign — Behavior Specification

**Spec date:** 2026-06-08 · **reconciled 2026-06-11**
**Status:** design **LOCKED** — single-pass reconciliation from the decision ledger
(`docs/converter-redesign.md`). That ledger is the source of truth; this spec is the behavioral
elaboration. The implementation plan derives from this spec.

## Goal

Rebuild KMapper's converter subsystem so that:

- One bilateral converter type `MapTypeConverter<S, T>` serves built-ins and users identically
  (**user–author parity** — no author-only mechanisms).
- Missing/unsupported conversions fail at **compile time** (`MissingConverter` /
  `UnsupportedConversion`); runtime behavior follows a single **fallback ladder** with
  **observable** leniency (degradation sink).
- The mapper boundary returns **values**, never surprise exceptions: core `Result`, arrow
  add-on `IorNel`.

### Principles (normative)

1. **Parity** — every internal mechanism is public: same base class, same pair discovery, same
   `@UnsupportedDirection`, same conversion seams. Generated code calls converter *objects*,
   never inlined casts.
2. **Visibility** — behavior deviations are declared where they are read: on the field or at the
   call-site. No global behavior switches; the sink globalizes observation only.
3. **Fallback ladder** — `converted value > declared default > type's absence form
   (null | not-a-member) > error`. Absence and brokenness merge in outcome; they differ only in
   the bottom-of-ladder error type.
4. **Report rule** — events that lose data or stem from breakage are reported; declared-absence
   flows are silent.
5. **Loud field / contained boundary** — field errors are typed and path-carrying; the boundary
   wraps them as values. `.getOrThrow()` is the caller's explicit choice.

## Artifacts & modules (Koin-style layering)

| Artifact | Contents | Depends on |
|---|---|---|
| **`kmapper-core`** | `MapTypeConverter` + `@UnsupportedDirection`, `MappingException`, `ConverterErrors`, scalar+collection seams, `KMapper`/`MappingListener`/`MappingDegradation`, built-ins, `TypeConverterRegistry`, `MappableEnum`, `Validator` | — (kotlinx-datetime for Instant built-ins) |
| **`kmapper-annotations`** | `@MapTo`, `@MapFrom`, `@FieldMap`, `@KMapperConfig`, `@CollectionWrapper`, `@ConvertWith/To/From`, `OnFail`, `@Validate`, `@IgnoreMap`, `@IgnoreDefaultValue` | core (typed `use` param) |
| **`kmapper-compiler`** | the KSP processor (Gradle path stays `:processor`; coordinates renamed) | annotations, core |

- **Core is standalone**: hand-written mappers use seams + converters with no codegen.
  `@UnsupportedDirection` lives in core (converter *contract*, not a mapping declaration);
  without the compiler it is inert metadata and the runtime `unsupported()` throw still
  enforces the direction (compile-time enforcement is what the compiler adds).
- Codegen users add `annotations` + `ksp(compiler)`. Add-ons (`converters-*`, `validators`)
  depend on core only (+ annotations where `@CollectionWrapper` is declared).
- Package names are unchanged everywhere (`com.sahsenvar.kmapper.annotations.*` stays), so the
  compiler's FQN constants are unaffected by the module split.

## The converter type

```kotlin
abstract class MapTypeConverter<S : Any, T : Any>(
    val sourceType: KClass<S>,
    val targetType: KClass<T>,
) {
    /** Total forward conversion (S → T). Override if the direction is supported. */
    open fun convertTo(source: S): T = unsupported(defaultUnsupportedMessage())

    /** Total reverse conversion (T → S). Override if the direction is supported. */
    open fun convertFrom(target: T): S = unsupported(defaultUnsupportedMessage())

    /**
     * Sanctioned-null variants — override to declare "this input has no legitimate
     * counterpart" (e.g. blank string → no Int). Defaults delegate to the total methods,
     * so plain converters need not override them.
     */
    open fun convertToOrNull(source: S): T? = convertTo(source)
    open fun convertFromOrNull(target: T): S? = convertFrom(target)

    /** For annotated unsupported-direction stubs: `= unsupported()` (default message). */
    protected fun unsupported(): Nothing = unsupported(defaultUnsupportedMessage())

    /** Protected so authors can reject a shape from their own override bodies. */
    protected fun unsupported(message: String): Nothing =
        throw MappingException.UnsupportedConversion(message)

    private fun defaultUnsupportedMessage(): String =
        unsupportedConversionMessage(sourceType.simpleName ?: "?", targetType.simpleName ?: "?")
}
```

- Two **total** methods per pair; a non-overridden direction throws `UnsupportedConversion` —
  a runtime safety net only, never reached through generated code (the processor refuses to
  emit a call to a non-overridden direction).
- **Sanctioned null** differs from a throwing converter in exactly two ways:
  (a) the null is **silent** (legitimate flow, not a degradation — report rule),
  (b) it **survives `onFail = Throw`** (it is not a failure).
- Generated code calls the `OrNull` variant **only where null can land** (nullable or defaulted
  target); elsewhere it calls the total method, so a sanctioned null can never be asked to fill
  a slot that cannot absorb it.

```kotlin
// Example: user converter with one sanctioned null
object IntStringConverter : MapTypeConverter<Int, String>(Int::class, String::class) {
    override fun convertTo(source: Int): String = source.toString()
    override fun convertFrom(target: String): Int = target.toInt()       // throws on malformed
    override fun convertFromOrNull(target: String): Int? =
        if (target.isBlank()) null else target.toInt()                   // "" → legitimate no-value
}
```

## Intentionally-unsupported directions: `@UnsupportedDirection` (parity)

**Function-level** — the annotated function IS the direction (no `Direction` enum, no
`direction` parameter, no `@Repeatable`):

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class UnsupportedDirection(val reason: String)
```

```kotlin
object LongIntConverter : MapTypeConverter<Long, Int>(Long::class, Int::class) {
    override fun convertFrom(target: Int): Long = target.toLong()

    @UnsupportedDirection("Long -> Int narrows and can truncate; convert explicitly if intended.")
    override fun convertTo(source: Long): Int = unsupported()
}
```

- A widening built-in implements its safe direction and declares the lossy one as an annotated
  `unsupported()` stub. An **X-pair** declares **both** totals as annotated stubs with
  pair-specific reasons. Every primitive pair is an object: it either converts or explains, at
  compile time, why it intentionally will not.
- A **user** converter uses the same annotation for its own reasons — full parity.
- **Detection rule:** a direction is *provided* iff it is declared AND not annotated.
  **The annotation wins** — KSP cannot inspect function bodies, so an annotated function is
  treated as unsupported regardless of its body (documented contract; the stub body must be
  `= unsupported()`). The annotation goes on the **total** method only; on an `OrNull` variant
  → compile error ("annotate the total method"). One annotated total covers its `OrNull`
  variant too.
- The processor turns a needed-but-unsupported direction into `UnsupportedConversion(reason)`;
  a needed-and-undeclared direction gets the generic message.

## Discovery, annotations, resolution

**Resolution principle:** the per-field annotation is **override-only** — never required to
*find* a converter. Built-ins (pair-keyed, order-independent registry) and `@KMapperConfig`
converters are auto-discovered by type pair. The processor compares the field's
`(source, target)` to the converter's `(S, T)` to pick `convertTo` vs `convertFrom`
(orientation-aware), independent of `@MapTo`/`@MapFrom`.

```kotlin
enum class OnFail { Auto, Throw, Skip }
// Auto  = follow the fallback ladder (default)
// Throw = harden BROKENNESS only (absence stays type-driven)
// Skip  = collection elements only: compact instead of null-in-place

annotation class ConvertWith(
    val use: KClass<out MapTypeConverter<*, *>> = MapTypeConverter::class,  // sentinel = unset
    val onFail: OnFail = OnFail.Auto,
)
annotation class ConvertTo(/* same parameters */)    // applies to the @MapTo direction
annotation class ConvertFrom(/* same parameters */)  // applies to the @MapFrom / reverse direction
```

- Two independent override axes: `use` (which converter) and `onFail` (policy);
  `@ConvertWith(onFail = …)` without `use` is legitimate (keep auto-discovery, change policy).
- Direction-scoped `@ConvertTo`/`@ConvertFrom` beat `@ConvertWith` in their own direction
  ("incoming lenient, outgoing strict" asymmetries).
- `@UseMapTypeConverter` is renamed to `@ConvertWith` (migration typealias parked).

**Field-exclusion annotations (the Ignore family — each removes one thing from the mapper's view):**

- **`@IgnoreMap`** (rename of `@Ignore`): removes the **field** from auto-matching. Its value
  never flows through mapping; the corresponding target slot falls back to its constructor
  default (omitted) or becomes a required **external parameter** on the generated function.
- **`@IgnoreDefaultValue`** (target-side): removes only the field's **default** from mapping —
  the constructor default is construction convenience, NOT a wire fallback. The field behaves
  as defaultless on the ladder (absence → `RequiredFieldMissing`; broken under Auto → hard/null
  per the defaultless rows), sits in the constructor stage (not copy), and becomes an external
  parameter when unmapped. On a defaultless field → compile warning (no-op).

**Resolution flow per field:** per-field `use` → collections/map/option handling → same-type
Direct → nested data class → enum → `@KMapperConfig` (pair) → built-in (pair) →
**`MissingConverter`** (compile error). Pair found but needed direction not overridden →
**`UnsupportedConversion`** (compile error, with the `@UnsupportedDirection` reason if present).

**Compile-time preconditions (build fails):**
- `onFail = Skip` on a non-collection field → error ("Skip applies to collection elements").
- `use = X` where X's `(S,T)` matches neither orientation of the field pair → error.
- `@KMapperConfig` duplicate pair → error, **orientation-normalized** (`<A,B>` and `<B,A>` are
  the same pair — flipped duplicates are caught too).
- `@UnsupportedDirection` on an `OrNull` variant → error ("annotate the total method").
- `@CollectionWrapper` object whose `wrap`/`unwrap` signatures don't match the convention, or a
  mapping needing a direction the wrapper doesn't declare → error.

**Compile-time warnings:**
- **Dead-`?`**: target container/field nullable while the mapping can never produce null there
  (e.g. non-null source container → `List<T>?`) → warning, suggest dropping `?`.
- `@IgnoreDefaultValue` on a field without a constructor default → warning (no-op).

## Errors

### Compile-time (two kinds, message builders shared)

```kotlin
fun missingConverterMessage(from: String, to: String): String =
    "$from -> $to has no registered converter. Add one via @ConvertWith / @KMapperConfig " +
        "(docs link — coming soon), or rethink your source/target types."

fun unsupportedConversionMessage(from: String, to: String): String =
    """
    $from -> $to conversion is unsupported! This relates to our policy on lossy conversions
    (e.g. Long -> Int, Double -> Float). What you can do:
      1. Check the converter add-ons (docs link — coming soon)
      2. Create your own converter (docs link — coming soon)
      3. Rethink your source or target type using supported types.
    """.trimIndent()
```

- **`MissingConverter` is compile-time only** — a diagnostic message, **not** a runtime type
  (no converter → no codegen → nothing to throw).
- **`UnsupportedConversion`** is both the compile diagnostic and the runtime
  `MappingException` thrown by the `unsupported()` safety net.
- Diagnostics prefix the field location: `EventData.seq: Int? -> Long conversion is unsupported! …`

### Runtime taxonomy

The sealed `MappingException` family, now **path-carrying**:

```kotlin
sealed class MappingException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause) {

    abstract val path: String
    /** Same type, path prefixed — used by seams to accumulate paths upward. NOT wrapping. */
    abstract fun withPathPrefix(prefix: String): MappingException

    class TypeConversionFailed(
        override val path: String, val from: String, val to: String, cause: Throwable,
    ) : MappingException("Cannot convert $path: $from -> $to", cause)

    class RequiredFieldMissing(override val path: String) :
        MappingException("Required field missing: $path")

    class UnknownEnumValue(...)      // rides the same ladder as any conversion failure
    class UnsupportedConversion(...) // see above
    class ValidationFailed(...)      // validators module
    // EmptyCollection belongs to validators (used by NonEmptyList converters), not core mapping.
}
```

- **No-wrap rule:** a `MappingException` propagates as-is — seams only prefix its path
  (`withPathPrefix`); any other `Throwable` becomes `TypeConversionFailed(cause)`.
- **Path accumulation:** each level prepends its segment —
  `customer.address.zipCode`, `items[3].price`, `prices["usd"]`, `matrix[2][7]`.
- **R8 safety:** every name in messages (`property`, type names) is a **codegen string
  literal** — readable in release builds without mapping.txt. Never derived at runtime
  (`::class.simpleName` is forbidden in this path).

## Default behavior — the fallback ladder (scalar)

Truth table with **no annotation** (✗T = `TypeConversionFailed`, ✗R = `RequiredFieldMissing`):

| # | Source | Target | Default | converter OK | converter **broken** | source **null** |
|---|--------|--------|---------|--------------|----------------------|-----------------|
| 1 | `S`  | `T`  | no  | value | **✗T (hard)**        | — |
| 2 | `S`  | `T`  | yes | value | **default** (reported) | — |
| 3 | `S`  | `T?` | no  | value | **null** (reported)    | — |
| 4 | `S`  | `T?` | yes | value | **default** (reported) | — |
| 5 | `S?` | `T`  | no  | value | **✗T (hard)**        | **✗R (hard)** |
| 6 | `S?` | `T`  | yes | value | **default** (reported) | **default** (silent) |
| 7 | `S?` | `T?` | no  | value | **null** (reported)    | **null** (silent) |
| 8 | `S?` | `T?` | yes | value | **default** (reported) | **default** (silent) |

Invariants:
- Rows 1 and 5 are the only hard cells: **no declared escape → error**. Strictness is not a
  policy; it is the *absence of declared fallbacks*.
- Broken absorption is **reported**, absence absorption is **silent** (report rule).
- Row 8: **default beats null** — one ladder, no special case.
- `OnFail.Throw` turns the "broken" column of rows 2–4 and 6–8 into hard ✗T; the "source null"
  column **never changes** (absence is type-driven). The "optional but validated" field:
  `@ConvertWith(onFail = OnFail.Throw) val age: Int?` → absent → null, `"abc"` → error.
- A **sanctioned null** behaves as declared absence wherever it lands: target `T?` → null
  (silent), defaulted target → default (silent); it survives `OnFail.Throw`.
- **`@IgnoreDefaultValue`** moves a field to the defaultless rows (2→1, 4→3, 6→5, 8→7): the
  declared default is invisible to the ladder.

## Validation — field-anchored `@Validate`

```kotlin
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Validate(vararg val validators: KClass<out Validator<*>>)
```

Replaces `@ValidateFrom`/`@ValidateTo`. The validator is anchored to the **field**, not to a
mapping side: whenever the annotated field participates in a mapping —

- as **source** → its value is validated **before** conversion,
- as **target** → the produced value is validated **after** conversion,

— in both generation directions. Type-correct by construction (validator type = field type),
and the To/From vocabulary clash with `@ConvertTo`/`@ConvertFrom` disappears. Typical home is
the domain model: one declaration covers data→domain and domain→presentation. Wire-format
checks before parsing belong on the data-model field — annotate whichever side's *value* you
mean. Validation fires at **mapping time only** (hand construction is not intercepted);
failure is always a hard `MappingException.ValidationFailed` (absorbable validation parked).

## Boundary API

Two generated functions per mapping direction:

| | core (stdlib only) | arrow add-on |
|---|---|---|
| function | `toXResult(): Result<X>` | `toXAccumulated(): IorNel<MappingError, X>` |
| hard error | `failure(firstError)` — fail-fast | `Ior.Left(allErrors)` |
| soft degradations only | `success(value)` (+ sink) | `Ior.Both(degradations, value)` |
| clean | `success(value)` | `Ior.Right(value)` |

- **Value semantics are identical across both paths** (hard → no value, soft → value); they
  differ only in error completeness. `Ior.Both` = partial data + the full list of what broke —
  the contract-drift detector.
- The library never throws at the caller. Recommended prod pattern:
  `result.onFailure { log/metric }.getOrElse { fallback }`; debug strictness:
  `if (DEBUG) result.getOrThrow()`. Build-variant behavior is the caller's one-liner, not a
  library mode.
- The accumulated variant is generated when the arrow add-on is enabled (exact trigger — KSP
  argument vs classpath detection — settled in the plan). `MappingError` is the value-side
  mirror of the exception taxonomy (same path/from/to/cause data).

## Degradation sink

```kotlin
sealed interface MappingDegradation {
    val path: String
    class AbsorbedConversionError(path, val from: String, val to: String, val cause: Throwable)
    class DroppedBrokenElement(path, val cause: Throwable)
    class DroppedNullElement(path)
    class DuplicateKey(path, val key: String)
    class ConvergedDuplicateElement(path)   // Set dedup after conversion
}

fun interface MappingDegradationListener { fun onDegradation(event: MappingDegradation) }

object KMapper {
    /** Process-wide observation tap. Default no-op. Set once at app start. */
    var degradationListener: MappingDegradationListener = MappingDegradationListener { }
}
```

- Observation only — never changes mapping behavior (visibility principle intact).
- "Crash in debug, observe in prod" is a listener the app installs, not a library mode.
- Events fire per occurrence; throttling/summarizing is the listener's concern
  (per-mapping summary event parked).

## Defaults — omit/copy (no `@MapDefaultValue`)

`@MapDefaultValue` is **removed**. A field's default is its **target constructor default**;
the mapper applies it by **omit/copy**: build the target with defaulted fields omitted (the
constructor default applies), then `.copy(field = seam result)` where the seam falls back to
`base.field`.

- Works for any type (objects, collections — no annotation-encodable-value limitation).
- Requires the target to be a **data class** (already KMapper's model).
- ⚠️ **Gate:** depends on KSP reading `KSValueParameter.hasDefault` **across module
  boundaries** (flag lives in Kotlin metadata; the value itself is never needed). This is
  **Task 1 of the plan** — empirically verified before anything is built on it.

## Conversion seams (public, parity)

Hand-written mappers use the **same** seams generated code uses.

### Scalar seams — behavior matrix

| Seam (codegen context) | absent (source null) | broken (converter threw) | sanctioned null |
|---|---|---|---|
| `convertOrFail` — target `T`, no default | ✗R(path) | ✗T(path, cause) | n/a (total method called) |
| `convertOrNull` — target `T?` (Auto) | null, silent | **null + report** | null, silent |
| `convertOrElse(fallback)` — defaulted target (Auto) | fallback, silent | **fallback + report** | fallback, silent |
| strict variants — `OnFail.Throw` on `T?`/defaulted | null/fallback, silent | **rethrow (hard)** | null/fallback, silent |

Proposed signatures (final names settle in the plan; behavior above is locked):

```kotlin
inline fun <S : Any, T : Any> S?.convertOrFail(
    path: String, from: String, to: String, convert: (S) -> T,
): T
inline fun <S : Any, T : Any> S?.convertOrNull(
    path: String, from: String, to: String, convert: (S) -> T?,
): T?
inline fun <S : Any, T : Any> S?.convertOrElse(
    fallback: T, path: String, from: String, to: String, convert: (S) -> T?,
): T
// + Strict variants (rethrow broken) for OnFail.Throw on nullable/defaulted targets.
```

All seams: catch `MappingException` → `throw e.withPathPrefix(path)` (no wrapping); catch other
`Throwable` → `TypeConversionFailed(path, from, to, cause)` or absorb-and-report per the matrix.

**Codegen method-selection rule:** call `convertToOrNull`/`convertFromOrNull` wherever the
landing site can absorb null (nullable or defaulted target — the `OrNull`/`OrElse`/strict
seams); call the total method in `convertOrFail` contexts.

### Collection seams

```kotlin
inline fun <S : Any, T : Any> Iterable<S?>.convertEachOrSkip(
    path: String, convert: (S) -> T,
): List<T>      // null & broken elements dropped; every drop reported with indexed path
inline fun <S : Any, T : Any> Iterable<S?>.convertEachOrNull(
    path: String, convert: (S) -> T?,
): List<T?>     // null-in-place; broken→null reported; null pass-through silent
inline fun <S : Any, T : Any> Iterable<S?>.convertEachOrFail(
    path: String, convert: (S) -> T,
): List<T>      // first unproducible element → hard error with items[i] path
// + Map (per-entry key/value) and Set variants; arrow module adds accumulating counterparts.
```

## Nested mapping

A sub-mapper **is** a converter: `address: AddressData → AddressDomain` resolves to the
sub-mapper call; the outer field applies its **own ladder** to the sub-mapper's hard failure
(default object → default; nullable → null; neither → propagate).

- Inner soft degradations are absorbed inside and reported with **deep paths**; the outer
  mapping sees success.
- Inner hard `MappingException` propagates **unwrapped**, path-prefixed per level
  (`withPathPrefix`).
- **Blast radius** is declared by types, GraphQL-style: an error climbs until a declared escape
  (nullable / defaulted field) absorbs it, else it surfaces at the mapper boundary as
  `Result.failure`. Single deep error + `onFail`-free non-null field chain → whole mapping
  fails; an absorbing field bounds the damage to its own subtree (document the trade-off).

## Collections

**Scope separation (normative):** the **container** and its **elements** live on separate
ladders; element failure **never** escalates to the container.

### Container scope — plain scalar ladder

| Source | Target | Default | source list null | source list `[]` |
|---|---|---|---|---|
| `List<S>`  | `List<T>`  | – | — | `[]` |
| `List<S>?` | `List<T>`  | no | ✗R (hard) | `[]` |
| `List<S>?` | `List<T>`  | `= emptyList()` | **default**, silent | `[]` |
| `List<S>?` | `List<T>?` | no | **null**, silent | `[]` |
| `List<S>?` | `List<T>?` | yes | **default** (row 8), silent | `[]` |

**Empty ≠ absent:** incoming `[]` always produces `[]` — never the default, never an error.
("Must not be empty" is validation, not mapping.)

### Element scope — element ladder

`converted > null-in-place (element T?) > skip (not-a-member)` — never hard on its own.

| Target element | source element null (`List<S?>`) | element broken |
|---|---|---|
| `T` | **skip + report** (free `filterNotNull`) | **skip + report** |
| `T?` | **null-in-place, silent** (pass-through) | **null-in-place + report** |

`onFail` on a collection field targets **elements**:

| Policy | on `List<T>` | on `List<T?>` |
|---|---|---|
| `Throw` | broken → **hard** (null element still skips — absence is type-driven) | broken → **hard** ("optional but validated elements") |
| `Skip` | (already the default) | drop instead of null-in-place → compact list |

Notes:
- Skip preserves **relative order** but breaks **length/index alignment** — for
  position-coupled data use `List<T?>` (alignment-preserving) or `OnFail.Throw`.
- Extreme case, accepted consciously: 100/100 broken elements under defaults → empty list +
  100 sink events (mapping "succeeds"); the accumulated path / sink is what makes it visible.
- A fully-broken list yields `[]` while an *absent* list (no default) is hard ✗R — different
  scopes, intended asymmetry; declare `= emptyList()` if absent should also mean empty.

### Map / Set / nested collections

- **`Map<K, V>`:** each entry's key and value convert on their own ladders. Unproducible value:
  `V?` → null-in-place, else **drop entry + report**; unproducible key → **drop entry + report**
  (no null keys). Path: `prices["usd"]`. Post-conversion **key collision → last-wins +
  `DuplicateKey` report** (strict-collision parked).
- **`Set<T>`:** unproducible element **always skips** (null-in-place is degenerate in a set);
  post-conversion convergence dedup (distinct sources → same target) is **reported**.
- **Nested collections** recurse — the inner collection is the outer's "element", running its
  own ladders; paths accumulate: `matrix[2][7]`, `ordersByDay["mon"][3].price`.

### Source × target combination matrix (16)

Element behavior is chosen by the **target element type**; container behavior by the **target
container** (`?`/default) and only engages when the **source container** is nullable; a
nullable **source element** adds the null-element event. Cells:

| src \ tgt | `List<T>` | `List<T?>` | `List<T>?` | `List<T?>?` |
|---|---|---|---|---|
| `List<S>`   | skip broken | null-in-place broken | **dead-`?`** (= `List<T>`) | **dead-`?`** (= `List<T?>`) |
| `List<S?>`  | + skip nulls (filterNotNull) | + silent null pass-through | **dead-`?`** | **dead-`?`** |
| `List<S>?`  | + container ladder (default/✗R) | + container ladder | + container null/default — nullable-collection smell | idem |
| `List<S?>?` | "dirtiest wire → cleanest domain" preset | aligned-lenient | never-hard compact | maximally soft |

- **Dead-`?` warning:** non-null source container → nullable target container can never
  receive null from mapping → compile-time warning.
- **Idiom note (docs):** prefer non-null collection + `= emptyList()` over `List<T>?`; use a
  nullable collection only when *absent ≠ empty* genuinely matters.

### Custom containers: `@CollectionWrapper` (bidirectional)

```kotlin
@CollectionWrapper(forType = PersistentList::class)
object PersistentListWrapper {
    fun <T> wrap(source: List<T>): PersistentList<T> = source.toPersistentList()
    fun <T> unwrap(source: PersistentList<T>): List<T> = source.toList()
}
```

- Registered via `@KMapperConfig(wrappers = [...])`. **Element conversion stays on the normal
  rails** (ladder, seams, `onFail`); the wrapper only handles the container shell:
  `Wrapper.wrap(items.convertEachOrSkip(...))` on the way in, `Wrapper.unwrap(...)` feeding the
  element seams on the way out.
- `wrap` and `unwrap` are both optional, **at least one required**; a mapping that needs the
  missing direction → compile error (the wrapper counterpart of `UnsupportedConversion`).
- A typed contract is impossible (Kotlin has no higher-kinded types), so the convention is
  duck-typed but **compile-checked**: the processor validates both signatures against
  `forType` and errors with guidance otherwise.
- Scope: single-type-parameter containers. Map-shaped custom containers (two type params)
  are parked. Core-only (hand-written) users need none of this — seams + plain Kotlin
  (`.toPersistentList()`) suffice.

### Selection guide (user docs)

| Need | Write |
|---|---|
| 1 bad must not kill 99 (salvage) | nothing — `List<T>` default |
| filter source nulls | nothing — `List<S?> → List<T>` |
| preserve positions/length | target `List<T?>` |
| any bad element fails the mapping | `@ConvertWith(onFail = OnFail.Throw)` |
| compact even a nullable-element list | `@ConvertWith(onFail = OnFail.Skip)` |
| absent list = empty | `= emptyList()` |
| full error list | `toXAccumulated()` → `Ior.Both` |
| continuous prod observation | sink listener |

## Built-in converters (locked matrix)

Richer-first naming (`Instant > Double > Float > Long > Int > Short > Byte > Boolean > String`;
class name = type-parameter order; widening converters override `convertFrom`):

- **12 numeric widening** (real `convertFrom`; lossy `convertTo` = annotated `unsupported()`
  stub): `ShortByteConverter`, `IntByteConverter`, `LongByteConverter`, `IntShortConverter`,
  `LongShortConverter`, `LongIntConverter`, `FloatByteConverter`, `DoubleByteConverter`,
  `FloatShortConverter`, `DoubleShortConverter`, `DoubleIntConverter`, `DoubleFloatConverter`.
- **7 String pairs** (bilateral; format total, parse throws on malformed):
  `ByteStringConverter` … `DoubleStringConverter`, `BooleanStringConverter`
  (`toBooleanStrict()` — only `"true"`/`"false"`, anything else throws).
- **9 X-pairs** (both totals = annotated `unsupported()` stubs with pair-specific reasons):
  `FloatIntConverter`, `FloatLongConverter`, `DoubleLongConverter`,
  `ByteBooleanConverter`, `ShortBooleanConverter`, `IntBooleanConverter`,
  `LongBooleanConverter`, `FloatBooleanConverter`, `DoubleBooleanConverter`.
- **2 Instant** (kotlinx-datetime): `InstantStringConverter` (ISO-8601),
  `InstantLongConverter` (epoch millis).

**28 primitive pairs + 2 Instant = 30 objects** — every pair either converts or explains why not.

## Generated code (golden examples)

Scalar + defaults (omit/copy):

```kotlin
fun UserData.toUserDomainResult(): Result<UserDomain> = runCatching {
    val base = UserDomain(
        id = id.convertOrFail("id", "String", "Long", LongStringConverter::convertFrom),
        age = age.convertOrNull("age", "String", "Int", IntStringConverter::convertFromOrNull),
        // plan omitted → constructor default applies
    )
    base.copy(
        plan = plan.convertOrElse(base.plan, "plan", "String", "Plan", PlanConverter::convertToOrNull),
    )
}
```

Collections + nested:

```kotlin
fun FeedData.toFeedDomainResult(): Result<FeedDomain> = runCatching {
    val base = FeedDomain(
        tagIds = tagIds.convertEachOrSkip("tagIds") { LongStringConverter.convertFrom(it) },
        // items omitted → default emptyList()
    )
    base.copy(
        items = items?.convertEachOrSkip("items") { it.toItemDomainResult().getOrThrow() }
            ?: base.items,
    )
}
```

## Breaking changes / migration

- `convertToNonNull`/`convertFromNonNull` → `convertTo`/`convertFrom` (all converter
  implementations, including `converters-*` add-on modules — mechanical rename).
- Generated `toX()` → `toXResult(): Result<X>` (boundary change; callers add `.getOrThrow()`
  for old behavior).
- `@UseMapTypeConverter` → `@ConvertWith` (deprecated typealias parked).
- `@Ignore` → `@IgnoreMap`; `@ValidateFrom`/`@ValidateTo` → field-anchored `@Validate`.
- `applyNullableHandling` (generator-side `?:`/throw wrapper) is **replaced** by the ladder
  seams; the hardcoded direction-keyed built-in table and `IntLongConverter`'s runtime
  `require` are removed.
- `@MapDefaultValue` is removed (omit/copy).
- Annotations move to the new `kmapper-annotations` artifact (same packages); the processor's
  published coordinates become `kmapper-compiler`.

## Testing strategy

- **Kotest in `commonTest`** (KMP). Converter unit tests → `FunSpec` + `withData` (data-driven
  edge tables); processor tests → `BehaviorSpec` (Given/When/Then) over kctfork compile-tests.
- **Property + example mix:** `checkAll` round-trips (`parse(format(x)) == x`, widening
  preserves value); examples for malformed/overflow/strict-case/NaN/±Inf/MIN/MAX/empty.
- Fixtures named `DataModel` / `DomainModel`.
- Behavior-table tests: every row of the scalar ladder, element ladder, container ladder, and
  the seam matrix has at least one test; sink events asserted via a recording listener.

## Deferred / parked

See the ledger (`docs/converter-redesign.md` §J/§K) — `OnAbsent`, strict-collision, summary
sink event, container-pair precedence, `@FieldMap` path qualification, migration typealias,
GitBook docs links, stdlib Uuid/Duration add-ons, datetime module-boundary cleanup, arrow
`MappingError` model shape.
