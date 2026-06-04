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
| `kmapper-converters-compose` | KMP | `List` → `PersistentList`/`ImmutableList`/`ImmutableSet` wrappers |

**Latest release:** `0.1.0` — on [Maven Central](https://central.sonatype.com/artifact/io.github.sahsenvar/kmapper-core). See the [installation guide](docs/guide-en/getting-started/installation.md).

Design & implementation plan live in [`docs/superpowers/`](docs/superpowers/).
