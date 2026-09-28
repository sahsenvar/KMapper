# Error Handling and MappingException

KMapper's error contract in one sentence: **everything that can fail at runtime throws a
path-carrying, typed `MappingException` from the plain `toX()`; everything that can be known
earlier fails the build instead.** Want the failure delivered as a value instead of thrown?
That's an opt-in on top — see [Return Wrappers](../basic-usage/return-wrappers.md).

## The plain mapper throws

Every generated mapper is, first and always, `fun Source.toX(): X`:

```kotlin
val user: User = response.toUser() // throws MappingException on a hard failure
```

There is no `Result`, no `getOrThrow()` hop — a hard failure is an exception, exactly like any
other function in Kotlin that can fail. Catch it where it makes sense, or let it propagate:

```kotlin
val user = try {
    response.toUser()
} catch (exception: MappingException) {
    log(exception)
    User.GUEST
}
```

## Prefer failures as values? Add a wrapper

If you want the old `Result<X>`-returning shape — or a `Flow<X>`, or something else entirely —
declare it per mapping or module-wide, and the extension is generated **next to** `toX()`, never
instead of it:

```kotlin
@MapTo(User::class, wrapper = KMapperWrapper.KtResult::class)
data class UserResponse(/* … */)

// both are generated:
// fun UserResponse.toUser(): User
// fun UserResponse.toUserResult(): Result<User>

val result: Result<User> = response.toUserResult()
result.fold(
    onSuccess = { render(it) },
    onFailure = { e -> showError(); log(e) },
)
```

Full coverage of the built-in wrappers (`None`, `KtResult`, `Flow`), the module-wide Gradle/KSP
setting, and writing your own: [Return Wrappers](../basic-usage/return-wrappers.md).

A practical pattern that needs no wrapper at all: let `toX()` throw in debug builds and tests
(bad wire data crashes the nightly build, loudly), and wrap the one call site that talks to
production telemetry with `runCatching { … }` or a `KtResult`-wrapped mapping.

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
