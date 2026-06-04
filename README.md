# kmap

KMP-friendly compile-time object mapping for Kotlin Multiplatform, powered by KSP.

## 📖 Documentation

Full guide — also published on GitBook:

- **English:** [docs/guide-en](docs/guide-en/README.md)
- **Türkçe:** [docs/guide](docs/guide/README.md)

Covers installation, `@MapTo`/`@MapFrom`, field mapping, null-safety, type converters, `MappableEnum`, error handling, observability, multi-module setup, and the full annotation reference.

Group `io.github.sahsenvar`:

| Artifact | Platform | Purpose |
|----------|----------|---------|
| `kmapper-core` | KMP | Annotations, `MappingException`, `MapTypeConverter` + registry, built-in primitive converters, `MappableEnum`, `KMapper`/`MappingListener` |
| `kmapper-processor` | JVM | KSP code generator (`@MapTo`/`@MapFrom` → `toX()` extensions) |
| `kmapper-converters-immutable` | KMP | `List` → `PersistentList`/`ImmutableList`/`ImmutableSet`/`PersistentSet` wrappers (auto-discovered) |
| `kmapper-converters-arrow` | KMP | `List` → `NonEmptyList` wrapper; empty source throws `MappingException.EmptyCollection` (auto-discovered) |
| `kmapper-converters-datetime` | KMP (kotlinx) / JVM+Android (java.time, bridges) | Scalar converters: `String`/`Long` ↔ `LocalDate`, `LocalDateTime`, `LocalTime`, `ZonedDateTime`, `OffsetDateTime`, `Instant` |
| `kmapper-converters-bignumber` | KMP (ionspin) / JVM+Android (java.math) | Scalar converters: `String`/`Double`/`Long`/`Int` ↔ `BigDecimal`, `BigInteger` |

**Latest release:** `0.1.0` — on [Maven Central](https://central.sonatype.com/artifact/io.github.sahsenvar/kmapper-core) (`kmapper-core`, `kmapper-processor` only). See the [installation guide](docs/guide-en/getting-started/installation.md).

**0.2.0 (in development):** adds `kmapper-converters-immutable` (with `PersistentSet`), `kmapper-converters-arrow`, `kmapper-converters-datetime`, `kmapper-converters-bignumber`. Available via `publishToMavenLocal` until the Maven Central release.

Design & implementation plan live in [`docs/superpowers/`](docs/superpowers/).
