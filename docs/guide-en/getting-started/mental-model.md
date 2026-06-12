# The Mental Model

Everything KMapper does follows from three rules. Internalize these and every page in this
guide becomes a footnote.

## Rule 1 — Absence follows the type

When a source value is **missing** (a nullable source field is `null`), the *target field's
type* decides what happens. No annotation needed:

| Target field | Result of absence |
|--------------|-------------------|
| `val x: T? ` | `null` |
| `val x: T = default` | the constructor default (the argument is simply omitted) |
| `val x: T` (neither) | `MappingException.RequiredFieldMissing` |

Declared absence is **silent** — a nullable field being null is normal data, not an incident.

## Rule 2 — Brokenness is loud, but contained

When a source value is **present but broken** (a date that doesn't parse, an enum value the
app doesn't know), KMapper walks the **fallback ladder**:

```
converted value  >  constructor default  >  null  >  error
```

A broken value may be absorbed by a declared escape (default or nullable) — but unlike
absence, **every absorption is reported** to the
[degradation sink](../observability/listener.md). The whole mapping returns
`Result<T>`, so even a hard error never crashes your app unless you call `.getOrThrow()`.

The distinction matters: *absence is data, brokenness is a signal.* You keep serving users
from the 99 good fields while your telemetry tells you about the 1 bad one.

## Rule 3 — Leniency is explicit, local, and compiler-checked

Nothing above can be made *more* lenient globally — there is no "ignore errors" switch.
Adjustments are per-field annotations, visible in the model:

```kotlin
@ConvertWith(onFail = OnFail.Throw)  // this field is too important to absorb
@ConvertWith(onFail = OnFail.Skip)   // drop broken elements of this collection
```

And anything that *cannot* be done safely — a converter that would truncate, a direction
nobody wrote — fails **at compile time** with a message that names the fix.

## The parity principle

One more thing that isn't a rule but a promise: **whatever the library can do, you can do.**
Built-in converters, validators, and collection wrappers are ordinary public classes on the
same rails as yours. If `LocalDate ↔ String` gets a built-in converter object resolved by
type pair, your `Money ↔ String` converter is registered, resolved, overridden, and
compile-checked in exactly the same way. There is no privileged author-only mechanism.

## Where each concept lives

| Concept | Page |
|---------|------|
| the ladder on scalars | [Null-Safety](../basic-usage/null-safety.md) |
| the ladder on collection elements | [Collections](../basic-usage/collections.md) |
| converters and discovery | [Built-ins](../type-conversion/built-in.md), [@KMapperConfig](../type-conversion/kmapperconfig.md) |
| per-field policy | [@ConvertWith and OnFail](../type-conversion/convert-with.md) |
| invariants | [@Validate](../validation/validate.md) |
| the report channel | [Observability](../observability/listener.md) |

> Next: **[Example Gallery →](examples.md)**
