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
| `com.sahsenvar.kmapper:core` | KMP | Annotations, `MapTypeConverter`, `TypeConverterRegistry`, built-in converters, `MappableEnum`, `MappingException`, `KMapper`/`MappingListener` |
| `com.sahsenvar.kmapper:processor` | JVM | KSP code generator (`@MapTo`/`@MapFrom` → `toX()`) |
| `com.sahsenvar.kmapper:converters-compose` | KMP | `List` → `PersistentList`/`ImmutableList`/`ImmutableSet` wrappers |
| `com.sahsenvar.kmapper:converters-arrow` | KMP | (coming soon) Arrow `NonEmptyList`, etc. |

## Version Status

kmap is currently in **pre-release** (`0.1.0-SNAPSHOT`). Maven Central publication is being prepared; until then it can be consumed via local Maven (`mavenLocal`). See [Installation](getting-started/installation.md).

> Next: **[Installation →](getting-started/installation.md)**
