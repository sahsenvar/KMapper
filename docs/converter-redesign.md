# Converter Redesign — Decision Ledger

**Status:** design **LOCKED** (2026-06-11) — this ledger is the single source of truth for the
converter-subsystem redesign. It supersedes every earlier draft of this note.
**Spec (behavior detail):** `docs/superpowers/specs/2026-06-08-converter-redesign-design.md`
**Plan (implementation):** `docs/superpowers/plans/2026-06-08-converter-redesign-scaffolding.md`

> Reconciliation note: earlier decision IDs (D1–D5, P1, Q1–Q6) are resolved at the bottom
> (§Resolved history) so old references stay traceable. Everything above that is current.

---

## A) Principles

1. **User–author parity.** Every mechanism the library uses internally is available to users:
   same `MapTypeConverter` base, same pair-keyed discovery, same `@UnsupportedDirection`,
   same public conversion seams. Generated mappers call converter *objects* — never inlined
   ad-hoc conversions.
2. **Visibility principle.** A behavior deviation must be readable where the behavior is read:
   on the field (type, default, annotation) or at the call-site (`Result` handling). **No global
   behavior switches** (`@KMapperConfig` gets no `onFail` — permanently rejected). The
   degradation sink is not an exception: it globalizes *observation*, not behavior.
3. **Fallback ladder** (the default behavior):
   `converted value > declared default > the type's absence form (null | not-a-member) > error`.
   Absence and brokenness merge at the outcome level; they differ only in the error type at the
   bottom of the ladder (`RequiredFieldMissing` vs `TypeConversionFailed`).
4. **Report rule.** Every event that loses data or stems from breakage is reported to the sink;
   declared-absence flows are silent. Reported: broken→skip/null/default, null-element→skip,
   duplicate-key overwrite, set convergence dedup. Silent: null→null pass-through,
   absent→default, sanctioned null.
5. **Loud field / contained boundary.** Field errors are born typed and path-carrying; at the
   mapper boundary everything is a *value* (`Result` / `IorNel`). The library itself never
   throws at the caller — `.getOrThrow()` is the caller's explicit choice.

## A2) Artifacts & module boundaries

- **`kmapper-core`** — standalone, codegen-free world (Koin-style layering): `MapTypeConverter`
  + `@UnsupportedDirection`, `MappingException`, `ConverterErrors`, conversion seams,
  `KMapper`/`MappingListener`/`MappingDegradation`, built-ins, `TypeConverterRegistry`,
  `MappableEnum`, `Validator`. Hand-written mappers need ONLY this.
- **`kmapper-annotations`** — declaration annotations (`@MapTo/@MapFrom/@FieldMap/@KMapperConfig/
  @CollectionWrapper/@ConvertWith/@ConvertTo/@ConvertFrom/OnFail/@Validate/@IgnoreMap/
  @IgnoreDefaultValue`). **Depends on core** (typed `use: KClass<out MapTypeConverter<*,*>>`) —
  consistent with the layering: whoever wants annotations is already a core user.
- **`kmapper-compiler`** — the KSP processor (Gradle module path stays `:processor`; only the
  published coordinates change to `kmapper-compiler`).
- Dependency flow: `domain → annotations (→ core)`; `data → annotations + ksp(compiler)`;
  `converters-* / validators → core` (+ `annotations` only where `@CollectionWrapper` is declared).
- **Core stays standalone**: `@UnsupportedDirection` lives in core because it is part of the
  converter *contract*, not a mapping declaration; without the compiler it is inert metadata —
  the runtime `unsupported()` throw still enforces the direction. Core-only users trade
  compile-time enforcement for the runtime safety net; nothing in core references the compiler.
- **Parameterized converters are the official recipe** for format variants (e.g. Double→String
  with digits/separators): abstract base with constructor params + one-line configured `object`s,
  applied per-field via `@ConvertWith(use = …)` (or one of them globally via `@KMapperConfig`,
  which shadows the built-in for that pair). No annotation-args machinery.

## B) Converter model

6. `MapTypeConverter<S : Any, T : Any>(sourceType: KClass<S>, targetType: KClass<T>)` with
   **two total methods** — `convertTo(S): T`, `convertFrom(T): S` — both `open`, defaulting to
   `unsupported(message)`; plus a parameterless `protected unsupported()` using the default
   message (for annotated stubs). Richer-first naming (see §D built-ins).
7. **Sanctioned null** (opt-in): `convertToOrNull(S): T?` / `convertFromOrNull(T): S?` — the
   converter author's "this input has no legitimate counterpart". Distinct from a throwing
   converter in two ways: (a) the resulting null is *silent* (legitimate flow, not a
   degradation), (b) it survives `onFail = Throw` (it is not a failure). Generated code calls
   the `OrNull` variant only where null can land (nullable or defaulted target).
8. `@UnsupportedDirection(reason: String)` — **function-level** (sits on the overridden
   `convertTo`/`convertFrom` stub: `@UnsupportedDirection("…") override fun convertTo(…) =
   unsupported()`). No `Direction` enum, no `direction` parameter, no `@Repeatable` — the
   annotated function IS the direction. Detection rule: a direction is provided iff it is
   declared AND not annotated; **annotation wins** (KSP cannot inspect bodies). Annotating an
   `OrNull` variant → compile error ("annotate the total method"). Used identically by built-ins
   and user converters.
9. **Discovery is pair-keyed** (built-in registry + `@KMapperConfig`), order-independent.
   `@ConvertWith` is **override-only** with two independent axes: `use` (which converter,
   optional) and `onFail` (policy). `@ConvertWith(onFail = …)` without `use` is legitimate.
10. **Direction-scoped annotations** `@ConvertTo(use, onFail)` / `@ConvertFrom(use, onFail)`
    override `@ConvertWith` in their own direction.

## C) Error model

11. **Compile-time:** `MissingConverter` (no converter for the pair — message-only diagnostic,
    not a runtime type) · `UnsupportedConversion` (converter exists, direction intentionally
    unsupported — reason from `@UnsupportedDirection`) · `onFail` preconditions (`Skip` only on
    collection elements) · `@KMapperConfig` duplicate pair → error, **orientation-normalized**
    (`<A,B>` vs `<B,A>` is the same pair) · `@UnsupportedDirection` on an `OrNull` variant →
    error · `@CollectionWrapper` signature check + needed-but-missing `wrap`/`unwrap` direction
    → error · `@IgnoreDefaultValue` on a defaultless field → warning (no-op) ·
    **dead-`?` warning** (target nullable while the mapping can never produce null there).
12. **Runtime taxonomy:** the existing sealed `MappingException` family is the base
    (`TypeConversionFailed`, `RequiredFieldMissing`, `UnknownEnumValue` — rides the same ladder;
    `ValidationFailed`; `EmptyCollection` belongs to validators, not core mapping). Exceptions
    gain a `path`.
13. **No-wrap rule:** a `MappingException` propagates as-is (path gets prefixed per level);
    any other `Throwable` becomes `TypeConversionFailed(cause)`.
14. **Path accumulation:** `customer.address.zipCode`, `items[3].price`, `prices["usd"]`,
    `matrix[2][7]`. All names are embedded as codegen string literals (R8-safe — never derived
    at runtime via reflection/`simpleName`).

## D) Built-in converters (primitive matrix — locked earlier, unchanged)

- **Richer-first naming:** `Instant > Double > Float > Long > Int > Short > Byte > Boolean >
  String`; class name = type-parameter order; widening converters override `convertFrom`
  (poorer → richer is the safe direction).
- **12 numeric widening** (override `convertFrom` with the real body; `convertTo` is an
  annotated `unsupported()` stub): ShortByte, IntByte, LongByte, IntShort, LongShort, LongInt,
  FloatByte, DoubleByte, FloatShort, DoubleShort, DoubleInt, DoubleFloat.
- **7 String pairs** (bilateral: format total / parse throws on malformed): ByteString,
  ShortString, IntString, LongString, FloatString, DoubleString, BooleanString
  (`toBooleanStrict()` — only `"true"`/`"false"`).
- **9 X-pairs** (both totals are annotated `unsupported()` stubs with pair-specific reasons):
  FloatInt, FloatLong, DoubleLong, ByteBoolean, ShortBoolean, IntBoolean, LongBoolean,
  FloatBoolean, DoubleBoolean.
- **2 Instant** (kotlinx-datetime): InstantString (ISO-8601), InstantLong (epoch millis).
- Total: **28 primitive pair objects + 2 Instant = 30** — every pair either converts or explains
  at compile time why it will not.

## E) Default behavior — scalar ladder

15. Truth table (no annotation; ✗T = TypeConversionFailed, ✗R = RequiredFieldMissing):

    | # | Source | Target | Default | OK | broken | source null |
    |---|---|---|---|---|---|---|
    | 1 | `S` | `T` | no | value | **✗T** (hard) | — |
    | 2 | `S` | `T` | yes | value | **default** (reported) | — |
    | 3 | `S` | `T?` | no | value | **null** (reported) | — |
    | 4 | `S` | `T?` | yes | value | **default** (reported) | — |
    | 5 | `S?` | `T` | no | value | **✗T** (hard) | **✗R** (hard) |
    | 6 | `S?` | `T` | yes | value | **default** (reported) | **default** (silent) |
    | 7 | `S?` | `T?` | no | value | **null** (reported) | **null** (silent) |
    | 8 | `S?` | `T?` | yes | value | **default** (reported) | **default** (silent) |

    Rows 1 and 5 are the only hard cells: **no declared escape → error**. Everywhere else the
    ladder absorbs — broken absorption is *reported*, absence absorption is *silent* (report
    rule). Row 8: **default beats null** (single ladder, no special case).
16. `OnFail { Auto, Throw, Skip }` — `Auto` (default) = follow the ladder; `Throw` hardens
    **brokenness only** (absence stays type-driven — "optional but validated":
    `@ConvertWith(onFail = Throw) val age: Int?` → absent → null, `"abc"` → error); `Skip` is
    collection-element-only. `Null`/`UseDefault` policies were dropped — the ladder already
    does that.
17. No `fallback` parameter on seams — defaults live in constructors (omit/copy).
17b. **`@IgnoreMap`** (rename of `@Ignore`): the mapper pretends the annotated field does not
    exist for auto-matching — its value never flows through mapping; the corresponding target
    slot falls back to its constructor default (omitted) or becomes a required **external
    parameter** on the generated function.
17c. **`@IgnoreDefaultValue`** (target-side, NEW): the constructor default is NOT a mapping
    fallback — the field behaves as defaultless on the ladder (rows 2→1, 4→3, 6→5, 8→7;
    absence → `RequiredFieldMissing`), moves from the copy stage to the constructor stage, and
    an unmapped field becomes an external parameter. Decouples "Kotlin construction
    convenience" from "wire fallback". Ignore-family logic: `@IgnoreMap` removes the *field*
    from the mapper's view; `@IgnoreDefaultValue` removes only the field's *default*.

## E2) Validation

17d. **Single field-anchored `@Validate(vararg validators: KClass<out Validator<*>>)`** replaces
    `@ValidateFrom`/`@ValidateTo`. Semantics: whenever the annotated field participates in a
    mapping — as **source** (validated before conversion) or as **target** (validated after) —
    its value runs through the validators. Type-correct by construction (validator type = field
    type); kills the To/From vocabulary clash with `@ConvertTo/@ConvertFrom`. Typical home: the
    domain model (single source of truth for both data→domain and domain→presentation).
    Validation fires at **mapping time only** (not on hand construction); failure is always a
    hard `ValidationFailed` (absorbable validation parked).

## F) Boundary API + sink

18. Generated per direction: core **`toXResult(): Result<X>`** (fail-fast on the first hard
    error) and arrow add-on **`toXAccumulated(): IorNel<MappingError, X>`**
    (`Right` = clean, `Both` = partial value + all degradations, `Left` = hard failure with all
    collected errors). **Value semantics are identical across both** (hard → no value,
    soft → value); they differ only in error completeness.
19. **Degradation sink:** typed events (`AbsorbedConversionError`, `DroppedBrokenElement`,
    `DroppedNullElement`, `DuplicateKey`, `ConvergedDuplicateElement`); process-wide listener,
    default no-op; "crash in debug, observe in prod" is a one-line call-site pattern, not a
    library mode.

## G) Nested mapping

20. A sub-mapper **is** a converter: the outer field applies its own ladder to the sub-mapper's
    hard failure; soft degradations inside flow to the sink/Nel with deep paths; blast radius is
    read off the type declarations (GraphQL-style: an error climbs until a declared escape
    absorbs it, else surfaces at the boundary).

## H) Collections

21. **Scope separation:** element failure never escalates to the container (collapse rejected).
22. Container = plain scalar ladder; **empty ≠ absent** (incoming `[]` is always `[]`).
23. **Element ladder:** `converted > null-in-place (element T?) > skip (not-a-member)` — never
    hard on its own. Null source element: `T` target → skip+report (free `filterNotNull`),
    `T?` target → silent pass-through. Broken element: skip / null-in-place + report.
24. On collection fields `onFail` targets **elements**: `Throw` → broken element fails the
    mapping; `Skip` → compact even a `T?` list (skip instead of null-in-place).
25. **Map:** key and value convert with their own ladders; an unproducible side drops the entry
    (+report). Post-conversion **key collision → last-wins + `DuplicateKey` report**.
26. **Set:** unproducible element always skips (null-in-place is degenerate in a set);
    post-conversion convergence dedup is reported.
27. Nested collections recurse (`matrix[2][7]`); paths accumulate.
28. **Collection seams** (public, parity): `convertEachOrSkip / convertEachOrNull /
    convertEachOrFail` (+ Map/Set variants, + arrow accumulating counterparts). Core-only users
    hand-write container shells in plain Kotlin (`.toPersistentList()`) — `@CollectionWrapper`
    is only the codegen bridge.
29. The 16-combination source×target matrix + selection guide + the nullable-collection idiom
    note live in the spec.
29b. **`@CollectionWrapper` is bidirectional**: the wrapper object declares
    `fun <T> wrap(source: List<T>): W<T>` and/or `fun <T> unwrap(source: W<T>): List<T>` —
    at least one required; a mapping needing the missing direction → compile error (the
    wrapper counterpart of `UnsupportedConversion`). Signatures are **compile-checked by the
    processor** (a typed interface is impossible — Kotlin has no higher-kinded types — so the
    convention is duck-typed but validated). Scope: single-type-parameter containers;
    Map-shaped custom containers (two type params) parked.

## I) Defaults

30. **`@MapDefaultValue` is removed.** Defaults are target constructor defaults, applied via
    **omit/copy**: build the target with defaulted fields omitted, then `.copy(field = seam
    result)` — works for any type, requires data class.
    ✅ **GATE PASSED (2026-06-11).** The cross-module `hasDefault` empirical test
    (`CrossModuleHasDefaultGateTest`, commits e6addaa/077f971) proved all flag shapes — literal,
    function-call expression, null-on-nullable, const reference, computed expression,
    middle-position, both polarities — read identically cross-module (classpath/metadata) and
    in-module. omit/copy and ladder rows 2/4/6/8 are safe to build on.

## J) Parked (explicitly deferred, not lost)

- `OnAbsent` element policy (treat null source elements as errors) — if demanded.
- Strict-on-collision option for Map keys.
- Per-mapping summary sink event ("items: 3/100 dropped") + listener throttling guidance.
- Whole-container converter precedence vs element-wise decomposition (exact-pair converter
  wins; detail when first needed).
- `@FieldMap`-qualified paths (`Data.wireScore → Domain.score`) — single name suffices for now.
- Migration typealias `@Deprecated UseMapTypeConverter = ConvertWith`.
- GitBook docs task: add-ons page + public custom-converter page → replace the two
  "coming soon" placeholders in the error messages.
- Stdlib add-ons: `String ↔ kotlin.uuid.Uuid` (needs `@ExperimentalUuidApi`),
  `String ↔ kotlin.time.Duration`; kotlinx-datetime boundary cleanup (promote
  LocalDate/LocalDateTime/LocalTime or demote Instant — make it consistent).
- **Map-shaped custom containers** (`MultiMap<K,V>`-style, two type params) — outside the
  v1 `@CollectionWrapper` convention.
- **Absorbable validation** (a `@Validate` failure riding the ladder instead of always-hard).
- **`converters-format` add-on** (locale-aware number formatting) — the parameterized-converter
  recipe covers it user-side meanwhile.

## K) Shaped during implementation (behavior locked, form open)

- Exact seam names/signatures for the `onFail = Throw` × nullable/defaulted-target variants
  (behavior matrix is in the spec; names settle in the plan tasks).
- `MappingError` value model for the arrow path (mirror of the exception taxonomy).
- Degradation event type naming.
- (Resolved during reconciliation: sink mechanism = `onDegradation` default method on the
  existing `KMapper`/`MappingListener` registry.)

---

## Resolved history (old IDs → outcome)

- **D1** (single bilateral type) — **kept**.
- **D2** (`@ConvertWith` rename; drop direction annotations) — **superseded**: rename stands;
  direction-scoped `@ConvertTo`/`@ConvertFrom` are *reinstated* carrying `use` + `onFail` (B10).
- **D3** (one-way safety via compile-time diagnostic) — **evolved** into
  `MissingConverter` / `UnsupportedConversion` + `@UnsupportedDirection` parity (C11, B8).
- **D4** (null behavior owned by converter body, 4 open methods) — **superseded**: base is two
  total methods; null semantics live in the ladder + opt-in sanctioned null (B6, B7, E15).
- **D5** (lossless numeric matrix only) — **kept**, expressed richer-first with X-pair objects (§D).
- **P1** (`@MapDefaultValue` redesign) — **resolved by removal**: omit/copy (I30).
- **Q1** (`S?→T` ownership) → ladder row 5/6. **Q2** (diagnostic texts) → spec §errors.
  **Q3** (user one-way) → `@UnsupportedDirection` parity. **Q4** (null API shape) → B6/B7.
  **Q5** (matrix scope) → §D. **Q6** (migration) → parked typealias (J).
- `StringBooleanConverter` strict parse — **adopted** (`toBooleanStrict`, §D).
