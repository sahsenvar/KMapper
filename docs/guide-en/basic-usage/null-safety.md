# Null-Safety and the Fallback Ladder

This page is Rule 1 and Rule 2 of the [mental model](../getting-started/mental-model.md) in
working code.

## The four nullability cases

For a field mapped between source and target, the compiler handles all four combinations
exhaustively:

| Source | Target | Behavior |
|--------|--------|----------|
| `T` | `T` | direct copy / conversion |
| `T` | `T?` | direct — a non-null value satisfies a nullable slot |
| `T?` | `T?` | direct — null flows through as null |
| `T?` | `T` | **the interesting one** — see below |

## Nullable → non-null: the ladder decides

```kotlin
data class User(
    val email: String,                //  no escape       -> absence is an error
    val nickname: String = "anon",    //  default escape  -> absence takes the default
    val bio: String?,                 //  nullable escape -> absence becomes null
)

@MapTo(User::class)
data class UserResponse(
    val email: String?,
    val nickname: String?,
    val bio: String?,
)
```

When a source field is null:

```
1. (no value to convert)
2. target has a constructor default?  -> omit the argument, default applies   [silent]
3. target is nullable?               -> null                                  [silent]
4. neither                           -> MappingException.RequiredFieldMissing
```

Absence taking a declared escape is **silent** — null bios are data, not incidents.

## Broken values ride the same ladder — loudly

When the value is present but its conversion **throws** (`"not-a-date"`, unknown enum value),
the same escapes apply, with two differences:

- the absorption is **reported** to the [degradation sink](../observability/listener.md)
  (`AbsorbedConversionError`, with field path and cause), and
- you can forbid absorption per field with
  [`@ConvertWith(onFail = OnFail.Throw)`](../type-conversion/convert-with.md).

```kotlin
// joined: String? -> LocalDate?   with value "garbage"
// -> target gets null, sink gets AbsorbedConversionError(path="joined", cause=TypeConversionFailed)
```

## The hard floor: RequiredFieldMissing

With no escape, mapping stops with a path-carrying exception — delivered as a `Result`
failure, not a crash:

```kotlin
val result = UserResponse(email = null, …).toUserResult()
result.exceptionOrNull()?.message
// Required field missing: email
```

## Hand-written code: the same rails

`kmapper-core`'s public seams give hand-written mappers identical semantics — this is
literally what generated code calls:

```kotlin
val email = response.email.orRequired("email") // null -> RequiredFieldMissing("email")
val joined = response.joined.convertOrNull("joined", "kotlin.String", "kotlinx.datetime.LocalDate") {
    LocalDateStringConverter.convertFrom(it) // broken -> null + sink report
}
```

See the `CoreOnlyMapping` example in the [gallery](../getting-started/examples.md) for a
complete hand-written mapper.

> Next: **[Nested Models and Error Paths →](nested-models.md)**
