# kmap

KMP-friendly compile-time object mapping for Kotlin Multiplatform, powered by KSP.

`com.sahsenvar.kmapper`:

| Artifact | Platform | Purpose |
|----------|----------|---------|
| `core` | KMP | Annotations, `MappingException`, `MapTypeConverter` + registry, built-in primitive converters, `MappableEnum`, `KMapper`/`MappingListener` |
| `processor` | JVM | KSP code generator (`@MapTo`/`@MapFrom` → `toX()` extensions) |
| `converters-compose` | KMP | `List` → `PersistentList`/`ImmutableList`/`ImmutableSet` wrappers |
| `converters-arrow` | KMP | (reserved) Arrow `NonEmptyList` etc. |

**Status:** pre-release (`0.1.0-SNAPSHOT`).

Design & implementation plan live in [`docs/superpowers/`](docs/superpowers/).
