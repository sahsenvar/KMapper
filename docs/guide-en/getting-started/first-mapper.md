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
fun UserResponse.toUserResult(): Result<User>
```

Two fields copied straight across; `joined` routed through the built-in
`LocalDateStringConverter` (`String ↔ LocalDate` is one of 35 built-in pairs — no
registration needed).

## 3. Use it

```kotlin
val user: User = UserResponse(7, "grace@navy.mil", "2026-06-12")
    .toUserResult()
    .getOrThrow()
```

The generated function returns `Result<User>`: *you* decide at the call site whether a failure
throws (`getOrThrow`), falls back (`getOrElse`), or branches (`fold`).

## 4. Break it — on purpose

```kotlin
val broken = UserResponse(7, "grace@navy.mil", "not-a-date").toUserResult()

println(broken.exceptionOrNull()?.message)
// Cannot convert joined: String -> LocalDate failed for value "not-a-date" …
```

No crash — the failure arrived as a value, naming the exact field. In nested models the path
grows with it (`customer.address.zipCode`); see
[Nested Models](../basic-usage/nested-models.md).

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
