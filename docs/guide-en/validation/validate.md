# @Validate — Field-Anchored Validation

Conversion answers *"can this value become that type?"*. Validation answers a different
question: *"is this value acceptable for this field?"* — an email that must look like an
email, a quantity that must be positive. `@Validate` anchors that rule to the **field**.

## Declaring

```kotlin
import com.sahsenvar.kmapper.annotations.Validate
import com.sahsenvar.kmapper.validation.builtin.NotBlankValidator
import com.sahsenvar.kmapper.validators.EmailValidator

data class Member(
    @Validate(NotBlankValidator::class)
    val displayName: String,
    val email: String,
)

@MapTo(Member::class)
data class RegistrationForm(
    val displayName: String,
    @Validate(EmailValidator::class)
    val email: String,
)
```

`@Validate` takes any number of validator objects; they run in order, first failure wins.

## Field-anchored: one declaration, every direction

The validator belongs to the field, not to a mapping direction. Whenever its field
participates in *any* generated mapping:

- as a **source** field → the value is validated **before** conversion;
- as a **target** field → the produced value is validated **after** conversion.

Put validators on the model that **owns the rule** — usually the domain model. One
declaration then guards both `data → domain` and `domain → presentation`.

## Failure is always hard

```kotlin
val result = RegistrationForm("Grace", "not-an-email").toMemberResult()
println(result.exceptionOrNull()?.message)
// Validation failed for 'email': must be a valid email
```

A failed validator is `MappingException.ValidationFailed` — path-carrying, delivered through
the `Result` boundary, and **never absorbed by the
[fallback ladder](../basic-usage/null-safety.md)**. A validation rule is a declared
invariant; a value that violates it has no business in your model, not even as `null`.

Two more semantics worth knowing:

- **Null skips validation.** Validators receive non-null values only; absence is the
  [ladder](../basic-usage/null-safety.md)'s job, presence-quality is validation's.
- **Mapping-time only.** Hand-constructing the class doesn't run validators — they guard the
  *boundary*, not the constructor.

## Writing your own

Any `object` extending `Validator<T>` works — return `null` for valid, a reason for invalid:

```kotlin
object EvenQuantityValidator : Validator<Int>(Int::class) {
    override fun validate(value: Int): String? =
        if (value % 2 == 0) null else "must be an even quantity (was $value)"
}
```

For parameterized rules (length bounds, regexes, ranges), subclass the
[built-in open bases](validators.md#parameterized-bases) instead of starting from scratch.

> Next: **[The Validator Library →](validators.md)**
