# KMapper — project guidance

## Talking with the user
When you use a technical term (e.g. "richer-first naming", "round-trip property", "override detection"),
explain it briefly in plain language with **one concrete example** — teach the term, don't assume it.
Keep it short (a sentence or two per term), never a wall of text. The user is **not** averse to jargon;
they want to *learn* it, and once a term is shared you can use it freely. Build shared vocabulary
incrementally and concisely. If you catch yourself writing a dense technical paragraph, stop and simplify.

## Core design principle: user–author parity (symmetry)

**Any capability the library uses internally MUST be available to users for their own code.**
No author-only privileged mechanisms. If a user can't write a custom converter — or anything else —
with the same power and philosophy as the built-ins, the artifact is weak.

Concretely for converters:
- Built-ins and user converters share the **same** `MapTypeConverter` base, the **same** compile-time
  discovery (per-pair, order-independent), and the **same** `@UnsupportedDirection(direction, reason)`
  annotation for declaring an intentionally-unsupported direction with a compile-time guiding message.
- Generated mappers call converter **objects** (e.g. `LongIntConverter.convertFrom(...)`), never
  inlined ad-hoc conversions (no raw `x.toLong()`), precisely so user and author converters run on
  identical rails and can be reasoned about, overridden, and extended the same way.

When adding a feature, ask: *can a user achieve the same thing with their own types?* If not, redesign.

## Converter resolution & error behavior (quick reference)

Per-field `@ConvertWith(use, onFail)` is **override-only** (two independent axes; direction-scoped
`@ConvertTo`/`@ConvertFrom` beat it in their own direction). Auto-discovery (built-in registry +
`@KMapperConfig`) finds converters by type pair without any annotation. A needed-but-unsupported
direction → compile-time `UnsupportedConversion` (with the `@UnsupportedDirection` reason if present,
else generic); a pair with no converter at all → compile-time `MissingConverter`.

Runtime behavior = the **fallback ladder**: `converted value > declared default > type's absence form
(null | not-a-member) > error`. Absence is always type-driven; `OnFail { Auto, Throw, Skip }` governs
brokenness only. Broken absorption is **reported** to the degradation sink (`MappingListener.onDegradation`),
declared-absence flows are silent. The boundary is a value: generated `toXResult(): Result<X>`
(arrow add-on: `toXAccumulated(): IorNel`). Defaults come from target constructor defaults via
**omit/copy** — `@MapDefaultValue` no longer exists (`@IgnoreDefaultValue` masks a default from mapping;
`@IgnoreMap` removes a field from auto-matching; field-anchored `@Validate` replaced ValidateFrom/To).
`@UnsupportedDirection(reason)` is **function-level** (on the `= unsupported()` stub). Artifacts:
`kmapper-core` (standalone, hand-written world) / `kmapper-annotations` (depends on core) /
`kmapper-compiler` (KSP). Full decisions: `docs/converter-redesign.md` (ledger).

## Code & testing conventions

- **Descriptive names, never cryptic.** No single-letter or over-abbreviated variables, functions, or
  classes (not `r`/`c` — use `result`/`compilation`). Applies to test code too.
- **Tests use Kotest, in `commonTest`** (Kotest's KMP support covers the targets).
  - Converter unit tests → `FunSpec` + `withData` (data-driven input/expected tables).
  - Processor tests → `BehaviorSpec` (Given / When / Then).
- **Mix property-based and example-based tests:**
  - *Property* (`checkAll`) for broad rules — round-trips (`parse(format(x)) == x`), "widening preserves
    the value", etc.
  - *Example* for specific behaviors — throws on malformed input, overflow rejected, `"TRUE"` rejected.
- **Always think through edge cases and add tests for them** — boundaries (MIN/MAX/0/-1), empty string,
  overflow, NaN / ±Infinity, malformed input, wrong case, etc.
- **Test fixtures** are named `DataModel` (wire / DTO side) and `DomainModel` (domain side).

## Design docs
- `docs/converter-redesign.md` — converter subsystem decisions + parked items.
- `docs/superpowers/specs/2026-06-08-converter-redesign-design.md` — converter redesign spec.
- `docs/superpowers/plans/2026-06-08-converter-redesign-scaffolding.md` — implementation plan.
