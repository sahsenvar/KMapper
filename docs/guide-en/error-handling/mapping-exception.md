# The Result Boundary and MappingException

KMapper's error contract in one sentence: **everything that can fail at runtime arrives as a
`Result` failure holding a path-carrying `MappingException`; everything that can be known
earlier fails the build instead.**

## The Result boundary

Every generated mapper returns `Result<T>`:

```kotlin
val result: Result<User> = response.toUserResult()
```

You choose the failure policy *at the call site*, with stdlib tools:

```kotlin
// crash-on-bad-data (tests, debug builds, truly-required data):
val user = result.getOrThrow()

// fallback:
val user = result.getOrElse { User.GUEST }

// branch:
result.fold(
    onSuccess = { render(it) },
    onFailure = { e -> showError(); log(e) },
)
```

A practical pattern: `getOrThrow()` in debug, `getOrElse` + telemetry in release — bad wire
data crashes the nightly build, not the user.

## The exception taxonomy

All failures are subtypes of the sealed `MappingException`; each carries the **field path**
from the mapping root (`customer.address.zipCode`, `items[3].price`):

| Type | Meaning |
|------|---------|
| `RequiredFieldMissing` | absent value, target had no escape ([ladder](../basic-usage/null-safety.md) floor) |
| `TypeConversionFailed` | converter threw — carries the original cause |
| `UnknownEnumValue` | wire value matches no [`MappableEnum`](../enum/mappable-enum.md) constant |
| `EmptyCollection` | a non-empty container ([NonEmptyList](../type-conversion/arrow.md)) got an empty wire list |
| `ValidationFailed` | a [`@Validate`](../validation/validate.md) rule rejected the value |
| `UnsupportedConversion` | a refused [`@UnsupportedDirection`](../type-conversion/custom-converter.md#refusing-a-direction) was hit at runtime (hand-written code paths; generated code refuses at compile time) |

Because the type is sealed, an exhaustive `when` over failure kinds compiles — and grows a
warning when a future version adds a kind.

Paths are generated as compile-time string literals: they **survive R8/ProGuard** unchanged.

## What never reaches runtime

These are *build errors*, by design:

- **`MissingConverter`** — a field pair has no converter anywhere
  (`Money -> String has no registered converter. Add one via @ConvertWith / @KMapperConfig…`)
- **`UnsupportedConversion`** — the needed direction is declared-refused
  (`Long -> Int conversion is unsupported! …` with the author's reason)
- structural problems: unmappable field, wrapper signature violations, `OnFail.Skip` on a
  scalar, an `OrNull`-only converter override, …

The compile messages name the field, the pair, and the fix — they are part of the API
surface, not an afterthought.

## Relationship to the sink

`MappingException` is the **hard-failure** channel. Errors *absorbed* by a declared escape
never throw — they go to the [degradation sink](../observability/listener.md) instead. Same
taxonomy (`AbsorbedConversionError` carries the would-have-been exception as its cause),
different severity.

> Next: **[Observability →](../observability/listener.md)**
