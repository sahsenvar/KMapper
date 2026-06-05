# Okio Converters — converters-okio

The `converters-okio` module provides **scalar converters** for Okio types (`ByteString`, `Path`).
Like other scalar add-ons, they must be listed in `@KMapperConfig(converters = [...])` — they are
not auto-discovered.

> **Note:** `converters-okio` is new in version **0.2.0** and is not yet published to Maven Central.
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
            implementation("io.github.sahsenvar:kmapper-converters-okio:0.2.0")
        }
    }
}
```

Okio 3.9.1 (a known-stable KMP release) is a transitive dependency of this module. It covers JVM,
Android, `iosArm64`, and `iosSimulatorArm64` targets.

---

## Provided Converters (commonMain)

All converters live in the `com.sahsenvar.kmapper.okio` package.

| Object | S | T | Forward | Reverse | Round-trip |
|--------|---|---|---------|---------|------------|
| `StringByteStringConverter` | `String` | `okio.ByteString` | `value.encodeUtf8()` | `value.utf8()` | exact |
| `ByteArrayByteStringConverter` | `ByteArray` | `okio.ByteString` | `value.toByteString()` | `value.toByteArray()` | compare with `contentEquals` |
| `StringPathConverter` | `String` | `okio.Path` | `value.toPath()` | `value.toString()` | exact on normalized strings |

All three converters are available on all supported platforms (JVM, Android, iOS).

---

## Usage

```kotlin
import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.okio.StringByteStringConverter
import com.sahsenvar.kmapper.okio.StringPathConverter

@KMapperConfig(converters = [StringByteStringConverter::class, StringPathConverter::class])
object AppMappers
```

Then use `okio.ByteString` or `okio.Path` in your domain models:

```kotlin
import okio.ByteString
import okio.Path

@MapTo(FileDomain::class)
data class FileRemote(
    val name: String,
    val content: String,     // base64 or raw UTF-8 payload
    val location: String,    // "/var/data/file.bin"
)

data class FileDomain(
    val name: String,
    val content: ByteString,
    val location: Path,
)
```

Generated mapping:

```kotlin
public fun FileRemote.toFileDomain(): FileDomain = FileDomain(
    name     = name,
    content  = StringByteStringConverter.convertToNonNull(content),
    location = StringPathConverter.convertToNonNull(location),
)
```

---

Next: [URI Converters →](uri.md) | Other references: [@KMapperConfig](kmapperconfig.md)
