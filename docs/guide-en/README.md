# Introduction

**kmap** is a **compile-time**, KSP-based object mapping library for Kotlin Multiplatform. Instead of hand-writing model transformation functions between layers (`RemoteModel → DomainModel`, `DomainModel → UiModel`, etc.), kmap generates `toX()` extension functions from annotations automatically.

```kotlin
@MapTo(UserDomain::class)
data class UserRemote(val id: String, val email: String) : RemoteModel

// kmap generates:
fun UserRemote.toUserDomain(): UserDomain = UserDomain(id = id, email = email)
```

> **Note:** Examples show a simplified body. The actual generated code also includes `KMapper.hasListeners`-guarded observability hooks and uses a `val result = …; return result` form — see [MappingListener](observability/listener.md).

## Why kmap?

- **No reflection.** All mapping code is generated at compile time. This eliminates runtime overhead and makes kmap **Kotlin/Native (iOS) friendly** — it works seamlessly on platforms where reflection is restricted.
- **Type- and null-safe.** Type mismatches, missing converters, and unmappable fields become **compile errors**; no runtime surprises.
- **Zero boilerplate.** You never write mapper functions by hand; no maintenance burden.
- **KMP-native.** Define mappings in `commonMain`; Android and iOS share the same generated code.

## Design Principles

1. **Silent incorrect behavior is the enemy.** If a transformation is ambiguous or incomplete, the library never silently produces a wrong value — it either stops at compile time or throws a typed exception (`MappingException`). (Fragile defaults like `ordinal`/`name` for enums are **intentionally absent**.)
2. **Compile-time safety comes first.** Missing converter, unmappable field, guaranteed-infinite cycle → all are compile errors.
3. **Modular converters.** The core stays small; dependencies such as `kotlinx.collections.immutable` and Arrow live only in the optional add-on artifacts.
4. **Explicit intent.** A global converter list and per-field overrides make the rules readable and traceable — no magic.

## Modules

| Artifact | Platform | Responsibility |
|----------|----------|----------------|
| `io.github.sahsenvar:kmapper-core` | KMP | Annotations, `MapTypeConverter`, `TypeConverterRegistry`, built-in converters, `MappableEnum`, `MappingException`, `KMapper`/`MappingListener` |
| `io.github.sahsenvar:kmapper-processor` | JVM | KSP code generator (`@MapTo`/`@MapFrom` → `toX()`) |
| `io.github.sahsenvar:kmapper-converters-immutable` | KMP | `List` → `PersistentList`/`ImmutableList`/`ImmutableSet`/`PersistentSet` wrappers |
| `io.github.sahsenvar:kmapper-converters-arrow` | KMP | Arrow `NonEmptyList`, `NonEmptySet`, `Option<T>` mapping |
| `io.github.sahsenvar:kmapper-converters-datetime` | KMP (kotlinx) / JVM+Android | `String`/`Long` ↔ `LocalDate`, `LocalDateTime`, `Instant`, etc. |
| `io.github.sahsenvar:kmapper-converters-bignumber` | KMP (ionspin) / JVM+Android | `String`/`Double`/`Long`/`Int` ↔ `BigDecimal`, `BigInteger` |
| `io.github.sahsenvar:kmapper-converters-uuid` | KMP / JVM+Android | `String` ↔ `kotlin.uuid.Uuid`; `String`/`Uuid` ↔ `java.util.UUID` |
| `io.github.sahsenvar:kmapper-converters-okio` | KMP | `String`/`ByteArray` ↔ `okio.ByteString`; `String` ↔ `okio.Path` |
| `io.github.sahsenvar:kmapper-converters-uri` | JVM / Android / iOS | `String` ↔ `java.net.URI` / `android.net.Uri` / `NSURL` |
| `io.github.sahsenvar:kmapper-validators` | KMP | `EmailValidator`, `UrlValidator` for `@ValidateFrom`/`@ValidateTo` |

## Version Status

kmap **1.0.0** is published on [Maven Central](https://central.sonatype.com/artifact/io.github.sahsenvar/kmapper-core) — all 10 modules. See [Installation](getting-started/installation.md).

> Next: **[Installation →](getting-started/installation.md)**
