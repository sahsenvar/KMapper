# Field Mapping and the Ignore Family

Name matching covers most fields for free. These are the tools for the rest.

## @FieldMap — different names

```kotlin
@MapTo(User::class)
data class UserResponse(
    @FieldMap("displayName")
    val user_name: String, // wire snake_case -> domain displayName
)
```

With several `@MapTo` targets, scope a rename to one target:

```kotlin
@MapTo(User::class)
@MapTo(AuditEntry::class)
data class UserResponse(
    @FieldMap("displayName", targetClass = User::class)
    @FieldMap("actorName", targetClass = AuditEntry::class)
    val user_name: String,
)
```

## @IgnoreMap — break the match on purpose

`@IgnoreMap` makes the mapper pretend the field doesn't exist for auto-matching. Its value
never flows through the mapping; the target slot falls back to its constructor default — or,
with no default, becomes a **required parameter** of the generated function:

```kotlin
data class Account(
    val email: String,
    val passwordHash: String, // no default…
)

@MapTo(Account::class)
data class SignUpRequest(
    val email: String,
    @IgnoreMap
    val passwordHash: String, // same name, but you do NOT want this copied raw
)

// generated: fun SignUpRequest.toAccountResult(passwordHash: String): Result<Account>
val account = request.toAccountResult(passwordHash = hash(request.passwordHash))
```

## @IgnoreDefaultValue — "the default is not a wire fallback"

A constructor default normally doubles as rung 2 of the [ladder](null-safety.md): absence
quietly becomes the default. Sometimes the default is just construction convenience and the
wire **must** send the value. `@IgnoreDefaultValue` (on the target field) makes mapping treat
the default as nonexistent — absence is a hard `RequiredFieldMissing` again:

```kotlin
data class Account(
    @IgnoreDefaultValue
    val plan: String = "FREE", // Account() in code defaults to FREE; the wire must always send a plan
)
```

## Caller-supplied parameters

A target field with **no matching source field and no default** doesn't compile-error — it
becomes a required parameter of the generated function. That's the mechanism for context the
wire can't know:

```kotlin
data class Payment(
    val id: Long,
    val fetchedAt: Instant, // not on the wire
)

@MapTo(Payment::class)
data class PaymentResponse(val id: Long)

// generated:
fun PaymentResponse.toPaymentResult(fetchedAt: Instant): Result<Payment>
```

## Constructor defaults are the fallback mechanism

There is no `@MapDefaultValue`-style annotation: **the Kotlin constructor default *is* the
fallback**, and KMapper uses it by *omitting the argument* — the same default your hand-written
code sees, defined once, in one place:

```kotlin
data class Settings(
    val theme: String = "system", // absent/broken theme on the wire -> "system"
)
```

> Next: **[Null-Safety and the Fallback Ladder →](null-safety.md)**
