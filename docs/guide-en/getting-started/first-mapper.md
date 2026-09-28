# Your First Mapper

Five minutes from an API response to a safely mapped domain object — including your first
mapping error, on purpose.

## 1. Two models

A wire model (what the API sends) and a domain model (what your app wants):

```kotlin
import com.sahsenvar.kmapper.annotations.MapTo
import kotlinx.datetime.LocalDate

data class User(
    val id: Long,
    val email: String,
    val joined: LocalDate,
)

@MapTo(User::class)
data class UserResponse(
    val id: Long,
    val email: String,
    val joined: String, // ISO date on the wire
)
```

`@MapTo` lives on the **wire model** — the side you don't control is the side that declares
how it becomes the side you do control.

## 2. Build

```bash
./gradlew build
```

KSP generates an extension function:

```kotlin
fun UserResponse.toUser(): User
```

Two fields copied straight across; `joined` routed through the built-in
`LocalDateStringConverter` (`String ↔ LocalDate` is one of 35 built-in pairs — no
registration needed).

## 3. Use it

```kotlin
val user: User = UserResponse(7, "grace@navy.mil", "2026-06-12").toUser()
```

The generated function returns the plain `User` and throws a typed `MappingException` on a
hard failure. Want the failure as a value instead — `Result<User>`, a `Flow<User>`, or your
own wrapper type? See [Return Wrappers](../basic-usage/return-wrappers.md); it's an opt-in
added on top of `toUser()`, per mapping or module-wide, never a replacement for it.

## 4. Break it — on purpose

```kotlin
val broken = UserResponse(7, "grace@navy.mil", "not-a-date").toUser()
// throws MappingException.TypeConversionFailed:
// Cannot convert joined: String -> LocalDate failed for value "not-a-date" …
```

The exception names the exact field. In nested models the path grows with it
(`customer.address.zipCode`); see [Nested Models](../basic-usage/nested-models.md).

## 5. What if the wire value is missing?

Make the domain field tell KMapper what "missing" should mean:

```kotlin
data class User(
    val id: Long,
    val email: String,
    val joined: LocalDate? = null, // nullable: absent/broken date becomes null
)
```

A nullable or defaulted target field is a *declared escape*: absence flows into it silently,
and a **broken** value is absorbed into it too — but every absorption is reported to the
observability sink, so production telemetry still sees it. That ranking
(`value > default > null > error`) is the **fallback ladder**, the heart of KMapper.

> Next: **[The Mental Model →](mental-model.md)** — the three rules that explain everything
> you just saw.
