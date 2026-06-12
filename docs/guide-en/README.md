# KMapper

**Compile-time object mapping for Kotlin Multiplatform.** You annotate your wire models; KMapper
generates the mapping functions at build time with KSP — no reflection, no runtime registry, no
hand-written boilerplate to keep in sync.

```kotlin
data class User(val id: Long, val joined: LocalDate)

@MapTo(User::class)
data class UserResponse(val id: Long, val joined: String)

// Generated for you at compile time:
val user: Result<User> = UserResponse(7, "2026-06-12").toUserResult()
```

Three things make that snippet different from every mapper you have hand-written:

1. **Failures are values.** The generated function returns `Result<User>` — malformed wire data
   surfaces as a typed `MappingException`, never as a surprise crash deep in a parsing stack.
2. **Errors carry a path.** A bad date three objects deep reports
   `Cannot convert order.customer.joined: …` — you know *which field of which record* broke.
3. **The conversion is visible and replaceable.** `String → LocalDate` resolved to a built-in
   converter object. Your own converters plug into *the same* resolution, with the same
   priority rules and the same compile-time checks.

## Why KMapper?

- **No reflection, KMP-native.** Generated Kotlin runs on Android, JVM, and iOS alike. Declare
  mappings once in `commonMain`.
- **Compile-time safety.** A missing converter, an unmappable field, or a conversion that would
  silently lose data is a **build error with a guiding message** — not a production incident.
- **Honest error handling by design.** The *fallback ladder* keeps one malformed field from
  destroying a whole payload, while every absorbed error is reported to an observability sink.
  Nothing fails silently; nothing crashes by default.
- **User–author parity.** Every capability the library uses internally — converter objects,
  validators, collection wrappers, even "this direction is intentionally unsupported" — is
  available to your code through the exact same API.

## The design principle behind everything

> **A silently wrong value is worse than an error.**

KMapper never invents data (no `ordinal`-based enum mapping, no truncating `Long → Int`) and
never hides a failure (every leniency is declared in the type or an annotation, and every
absorbed error is observable). When something can't be done safely, you hear about it at
compile time.

## Artifacts

Group `io.github.sahsenvar`:

| Artifact | Platform | Purpose |
|----------|----------|---------|
| `kmapper-core` | KMP | Standalone runtime: exceptions, converter base + built-ins, validators, seams, observability. Usable without code generation. |
| `kmapper-annotations` | KMP | Declaration annotations (`@MapTo`, `@FieldMap`, `@ConvertWith`, …) |
| `kmapper-compiler` | JVM (KSP) | The code generator |
| `kmapper-converters-immutable` | KMP | kotlinx-collections-immutable wrappers |
| `kmapper-converters-arrow` | KMP | Arrow `NonEmptyList`/`NonEmptySet` wrappers, `Option` |
| `kmapper-converters-datetime` | JVM/Android | `java.time` converters and kotlinx ↔ java bridges |
| `kmapper-converters-bignumber` | KMP / JVM+Android | ionspin and `java.math` big numbers |
| `kmapper-converters-uuid` | KMP / JVM+Android | `kotlin.uuid.Uuid` and `java.util.UUID` |
| `kmapper-converters-okio` | KMP | `ByteString` (UTF-8/Base64/Hex), `Path` |
| `kmapper-converters-uri` | JVM / Android / iOS | platform URI types |
| `kmapper-validators` | KMP | Email, phone, IP, UUID, Luhn… for `@Validate` |

## Where to start

- New here? **[Installation](getting-started/installation.md)** →
  **[Your First Mapper](getting-started/first-mapper.md)** — running in five minutes.
- Want the philosophy in three rules? **[The Mental Model](getting-started/mental-model.md)**.
- Prefer reading code? The **[runnable example gallery](getting-started/examples.md)** covers
  every feature, ordered basic → advanced.
