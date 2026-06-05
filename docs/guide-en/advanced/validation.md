# Validation — @ValidateFrom / @ValidateTo

kmap's mapper can do more than convert and null-check fields: with `@ValidateFrom` and
`@ValidateTo` it can also enforce validation rules at mapping time, before the mapped object is
ever returned to the caller.

> **Note:** `@ValidateFrom`, `@ValidateTo`, the `Validator<T>` base class, and the built-in
> validators are all new in version **0.2.0** and are not yet published to Maven Central.
> Until released, use `publishToMavenLocal` + `mavenLocal()`.
> `core` and `processor` are still available from Maven Central at `0.1.0`.

---

## Why Validation at Mapping?

When a `RemoteModel` is mapped to a `DomainModel`, the mapper already handles type conversion and
null-safety. Adding validation to the same step avoids a separate validation pass and ensures
that any domain object that passes construction is guaranteed valid.

Failures throw `MappingException.ValidationFailed` (a `MappingException` subclass), which
propagates up the same exception path as other mapping errors.

---

## The Two Annotations

Both annotations go on the **source property** (same convention as `@UseMapTypeConverter` and
`@MapDefaultValue`):

```kotlin
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ValidateFrom(vararg val validators: KClass<*>)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ValidateTo(vararg val validators: KClass<*>)
```

| Annotation | When it fires | What it checks |
|------------|---------------|----------------|
| `@ValidateFrom` | Before conversion | The **source** value, as-is |
| `@ValidateTo` | After conversion | The **final produced** value, before assignment |

Both annotations accept `vararg` — you can list multiple validators in one annotation. They can
also be combined on the same property. Execution order is always: `@ValidateFrom` checks first,
then conversion runs, then `@ValidateTo` checks.

**Fail-fast:** the first failing validator throws immediately. Subsequent validators on the same
field are not evaluated.

---

## Validator\<T\>

Implement `Validator<T>` from `:core` as an `object` singleton:

```kotlin
abstract class Validator<T : Any>(val targetType: KClass<T>) {
    /**
     * Returns null if [value] is valid.
     * Returns a human-readable reason string if invalid.
     *
     * Receives a NON-NULL value — null handling is managed by the existing
     * null-safety machinery and does not involve validators.
     */
    abstract fun validate(value: T): String?
}
```

Key rules:
- **Must be an `object`** — the processor emits direct calls like `MyValidator.validate(x)` by
  fully qualified name at compile time. No reflection, no factory, fully KMP-safe.
- **Receives non-null values only** — validators never see `null`. Null handling on source and
  target fields is still governed by the existing nullability rules.
- **No `@KMapperConfig` registration needed** — validators are referenced directly on the source
  property via `@ValidateFrom(MyValidator::class)`. The processor resolves the FQN at compile time
  in the consumer's own KSP run, just like `@UseMapTypeConverter`. This is in contrast to
  converters and wrappers, which require explicit listing in `@KMapperConfig`.
- **Type-safe** — annotating a `String` field with a `Validator<Int>` produces a compile error,
  not a runtime failure.

---

## Built-in Validators (core)

Three validators ship with `:core` in package `com.sahsenvar.kmapper.validation.builtin`.
No extra dependency is needed.

| Object | T | Invalid when | Message |
|--------|----|--------------|---------|
| `NotBlankValidator` | `String` | `value.isBlank()` | `"must not be blank"` |
| `NotEmptyStringValidator` | `String` | `value.isEmpty()` | `"must not be empty"` |
| `NotEmptyCollectionValidator` | `Collection<*>` | `value.isEmpty()` | `"must not be empty"` |

---

## validators Add-on (converters-validators)

The `:validators` module adds domain-oriented validators for common formats.

> **Note:** `kmapper-validators` is new in version **0.2.0** and is not yet published to Maven Central.
> Until released, use `publishToMavenLocal` + `mavenLocal()`.

```kotlin
// build.gradle.kts (consuming module)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.sahsenvar:kmapper-core:0.1.0")
            implementation("io.github.sahsenvar:kmapper-validators:0.2.0")
        }
    }
}
```

Validators provided (package `com.sahsenvar.kmapper.validators`):

| Object | T | Invalid when | Message |
|--------|----|--------------|---------|
| `EmailValidator` | `String` | does not match `^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$` | `"must be a valid email"` |
| `UrlValidator` | `String` | does not match `^https?://[^\s/$.?#].[^\s]*$` | `"must be a valid URL"` |

---

## Custom Validators

Subclass `Validator<T>` as an `object` in any module. No dependency on `:validators` is required:

```kotlin
object MinAgeValidator : Validator<Int>(Int::class) {
    override fun validate(value: Int): String? =
        if (value < 18) "must be at least 18" else null
}
```

Reference it directly on the source property:

```kotlin
@MapTo(UserDomain::class)
data class UserRemote(
    @ValidateTo(MinAgeValidator::class)
    val age: Int,

    @ValidateFrom(NotBlankValidator::class)
    @ValidateTo(EmailValidator::class)
    val email: String?,
)
```

---

## Worked Example

```kotlin
import com.sahsenvar.kmapper.annotations.MapTo
import com.sahsenvar.kmapper.annotations.ValidateFrom
import com.sahsenvar.kmapper.annotations.ValidateTo
import com.sahsenvar.kmapper.validation.builtin.NotBlankValidator
import com.sahsenvar.kmapper.validators.EmailValidator

@MapTo(RegistrationDomain::class)
data class RegistrationRemote(
    @ValidateFrom(NotBlankValidator::class)
    @ValidateTo(EmailValidator::class)
    val email: String?,          // nullable source → non-null target; ValidateFrom on source, ValidateTo on converted result

    @ValidateTo(MinAgeValidator::class)
    val age: Int,
)

data class RegistrationDomain(
    val email: String,
    val age: Int,
)
```

The processor generates roughly:

```kotlin
public fun RegistrationRemote.toRegistrationDomain(): RegistrationDomain = RegistrationDomain(
    email = run {
        email?.let { __s ->
            NotBlankValidator.validate(__s)?.let { m ->
                throw MappingException.ValidationFailed("email", m)
            }
        }
        val __result = email ?: throw MappingException.RequiredFieldMissing("email")
        EmailValidator.validate(__result)?.let { throw MappingException.ValidationFailed("email", it) }
        __result
    },
    age = run {
        val __result = age
        MinAgeValidator.validate(__result)?.let { throw MappingException.ValidationFailed("age", it) }
        __result
    },
)
```

---

## MappingException.ValidationFailed

```kotlin
class ValidationFailed(val field: String, val reason: String)
    : MappingException("Validation failed for '$field': $reason")
```

`field` is always the **target** field name (consistent with `RequiredFieldMissing`). `reason` is
the string returned by `Validator.validate()`.

---

Other references: [Error Handling](../error-handling/exceptions.md) | [Type Conversion](./)
