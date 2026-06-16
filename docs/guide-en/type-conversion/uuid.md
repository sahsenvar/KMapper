# UUID — converters-uuid

Converters for `kotlin.uuid.Uuid` (KMP) and `java.util.UUID` (JVM/Android).

## Setup

```kotlin
commonMain.dependencies {
    implementation("io.github.sahsenvar:kmapper-converters-uuid:2.2.1")
}
```

```kotlin
@KMapperConfig(converters = [StringUuidConverter::class])
object AppMapperConfig
```

## Converters (`com.sahsenvar.kmapper.uuid`)

| Object | Pair | Platform |
|--------|------|----------|
| `StringUuidConverter` | `String ↔ kotlin.uuid.Uuid` | all (commonMain) |
| `JavaStringUuidConverter` | `String ↔ java.util.UUID` | JVM/Android |
| `KotlinJavaUuidConverter` | `kotlin.uuid.Uuid ↔ java.util.UUID` | JVM/Android |

Parsing accepts the canonical 8-4-4-4-12 hex form; malformed input throws and rides the
[ladder](../basic-usage/null-safety.md). To *validate* a UUID-shaped string without
converting the type, use
[`UuidStringValidator`](../validation/validators.md) from `kmapper-validators` instead.

## Why isn't `Uuid ↔ String` a core built-in?

`kotlin.uuid.Uuid` is still `@ExperimentalUuidApi` in Kotlin 2.3. A core built-in is wired
into generated code automatically, which would force the experimental opt-in onto every
consumer. The add-on keeps that choice yours; the pair graduates to core when the API does.

> Next: **[Okio →](okio.md)**
