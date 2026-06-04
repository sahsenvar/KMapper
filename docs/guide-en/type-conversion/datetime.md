# Date and Time Converters — converters-datetime

The `converters-datetime` module provides **scalar converters** for mapping between `kotlinx-datetime` / `java.time` types and `String` or `Long`. Scalar converters are not auto-discovered like `@CollectionWrapper`; you must list the ones you need in `@KMapperConfig(converters = [...])`.

> **Note:** `converters-datetime` is new in version **0.2.0** and is not yet published to Maven Central.
> Until it is released, use `publishToMavenLocal` + `mavenLocal()`.
> `core` and `processor` are still available from Maven Central at `0.1.0`.

---

## Setup

```kotlin
// settings.gradle.kts — add mavenLocal for the pre-release add-on
dependencyResolutionManagement {
    repositories {
        mavenLocal()        // for 0.2.0 add-ons
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts (consuming module)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.sahsenvar:kmapper-core:0.1.0")
            implementation("io.github.sahsenvar:kmapper-converters-datetime:0.2.0")
        }
    }
}
```

---

## Platform Support

| Converter group | Source set | Runs on |
|-----------------|------------|---------|
| `kotlinx-datetime` converters | `commonMain` | All platforms (JVM, Android, iOS, JS, WASM) |
| `java.time` converters | `jvmAndroidMain` | JVM and Android only |
| Bridge converters (kotlinx ↔ java.time) | `jvmAndroidMain` | JVM and Android only |

> **Note:** `Instant` conversions (`StringInstantConverter`, `LongInstantConverter`) are built into `core` — you do not need `converters-datetime` for them.

---

## kotlinx-datetime Converters (commonMain)

Available on all platforms.

| Converter | Source | Target |
|-----------|--------|--------|
| `StringLocalDateConverter` | `String` (ISO-8601) | `kotlinx.datetime.LocalDate` |
| `StringLocalDateTimeConverter` | `String` (ISO-8601) | `kotlinx.datetime.LocalDateTime` |
| `StringLocalTimeConverter` | `String` (ISO-8601) | `kotlinx.datetime.LocalTime` |

---

## java.time Converters (jvmAndroidMain)

Available on JVM and Android only. Names are prefixed with `Java` to distinguish them from the kotlinx counterparts.

| Converter | Source | Target |
|-----------|--------|--------|
| `StringJavaInstantConverter` | `String` (ISO-8601) | `java.time.Instant` |
| `LongJavaInstantConverter` | `Long` (epoch-milli) | `java.time.Instant` |
| `StringJavaLocalDateConverter` | `String` (ISO-8601) | `java.time.LocalDate` |
| `StringJavaLocalDateTimeConverter` | `String` (ISO-8601) | `java.time.LocalDateTime` |
| `StringJavaLocalTimeConverter` | `String` (ISO-8601) | `java.time.LocalTime` |
| `StringJavaZonedDateTimeConverter` | `String` (ISO-8601) | `java.time.ZonedDateTime` |
| `StringJavaOffsetDateTimeConverter` | `String` (ISO-8601) | `java.time.OffsetDateTime` |

---

## Bridge Converters: kotlinx ↔ java.time (jvmAndroidMain)

Use these when one layer of your model uses `kotlinx.datetime.*` and another uses `java.time.*`.

| Converter | Source | Target |
|-----------|--------|--------|
| `KotlinJavaInstantConverter` | `kotlinx.datetime.Instant` | `java.time.Instant` |
| `KotlinJavaLocalDateConverter` | `kotlinx.datetime.LocalDate` | `java.time.LocalDate` |

---

## Usage

List the converters you need in `@KMapperConfig(converters = [...])`:

```kotlin
import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.datetime.StringLocalDateConverter
import com.sahsenvar.kmapper.datetime.StringLocalDateTimeConverter

@KMapperConfig(converters = [StringLocalDateConverter::class, StringLocalDateTimeConverter::class])
object MyMappers
```

Then use the types directly in your models:

```kotlin
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

@MapTo(EventDomain::class)
data class EventRemote(
    val name: String,
    val date: String,        // "2026-06-04"
    val createdAt: String,   // "2026-06-04T10:15:30"
)

data class EventDomain(
    val name: String,
    val date: LocalDate,
    val createdAt: LocalDateTime,
)
```

Generated mapping:

```kotlin
public fun EventRemote.toEventDomain(): EventDomain = EventDomain(
    name      = name,
    date      = StringLocalDateConverter.convertToNonNull(date),
    createdAt = StringLocalDateTimeConverter.convertToNonNull(createdAt),
)
```

---

## Which Converter Should You Use?

- **KMP project (iOS included):** use `kotlinx-datetime` converters.
- **JVM/Android only:** you can use either `kotlinx-datetime` or `java.time` converters.
- **`Instant`:** no extra dependency needed — use `StringInstantConverter`/`LongInstantConverter` from `core`.

---

Next: [Big Number Converters →](bignumber.md) | See also: [@KMapperConfig](kmapperconfig.md)
