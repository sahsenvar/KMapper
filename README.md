# KMapper

KMP-friendly compile-time object mapping for Kotlin Multiplatform, powered by KSP.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.sahsenvar/kmapper-core?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.sahsenvar/kmapper-core) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE) [![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-blue.svg?logo=kotlin)](https://kotlinlang.org) [![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20JVM%20%7C%20iOS-lightgrey)](https://kotlinlang.org/docs/multiplatform.html) [![CI](https://img.shields.io/github/actions/workflow/status/sahsenvar/KMapper/ci.yml?branch=main&label=CI)](https://github.com/sahsenvar/KMapper/actions)

## 📖 Documentation

Full guide on **GitBook**: **https://kmapper.gitbook.io/docs** (Türkçe)

Source Markdown in the repo: **[English](docs/guide-en/README.md)** · **[Türkçe](docs/guide/README.md)**.

Covers installation, `@MapTo`/`@MapFrom`, field mapping, null-safety, type converters, validation (`@ValidateFrom`/`@ValidateTo`), `MappableEnum`, error handling, observability, multi-module setup, and the full annotation reference.

Group `io.github.sahsenvar`:

| Artifact | Platform | Purpose |
|----------|----------|---------|
| `kmapper-core` | KMP | Standalone runtime: `MappingException`, `MapTypeConverter` + registry, built-in primitive converters, `MappableEnum`, `KMapper`/`MappingListener` — usable without code generation |
| `kmapper-annotations` | KMP | Mapping declaration annotations (`@MapTo`/`@MapFrom`/`@FieldMap`/`@KMapperConfig`/…); depends on `kmapper-core` |
| `kmapper-compiler` | JVM | KSP code generator (`@MapTo`/`@MapFrom` → `toX()` extensions) |
| `kmapper-converters-immutable` | KMP | `List` → `PersistentList`/`ImmutableList`/`ImmutableSet`/`PersistentSet` wrappers (listed in `@KMapperConfig(wrappers)`) |
| `kmapper-converters-arrow` | KMP | `List` → `NonEmptyList` wrapper; empty source throws `MappingException.EmptyCollection` (listed in `@KMapperConfig(wrappers)`) |
| `kmapper-converters-datetime` | KMP (kotlinx) / JVM+Android (java.time, bridges) | Scalar converters: `String`/`Long` ↔ `LocalDate`, `LocalDateTime`, `LocalTime`, `ZonedDateTime`, `OffsetDateTime`, `Instant` |
| `kmapper-converters-bignumber` | KMP (ionspin) / JVM+Android (java.math) | Scalar converters: `String`/`Double`/`Long`/`Int` ↔ `BigDecimal`, `BigInteger` |
| `kmapper-converters-uuid` | KMP (commonMain) / JVM+Android | Scalar converters: `String` ↔ `kotlin.uuid.Uuid`; `String`/`kotlin.uuid.Uuid` ↔ `java.util.UUID` (JVM/Android) |
| `kmapper-converters-okio` | KMP | Scalar converters: `String`/`ByteArray` ↔ `okio.ByteString`; `String` ↔ `okio.Path` |
| `kmapper-converters-uri` | JVM / Android / iOS (platform-split) | Scalar converters: `String` ↔ `java.net.URI` (JVM), `android.net.Uri` (Android), `platform.Foundation.NSURL` (iOS) |
| `kmapper-validators` | KMP | `EmailValidator`, `UrlValidator` for use with `@ValidateFrom`/`@ValidateTo` field validation |

**Latest release:** `1.0.0` — on [Maven Central](https://central.sonatype.com/artifact/io.github.sahsenvar/kmapper-core) (all 10 modules). See the [installation guide](docs/guide-en/getting-started/installation.md).

Design & implementation plan live in [`docs/superpowers/`](docs/superpowers/).
