# URI Converters — converters-uri

The `converters-uri` module provides **platform-specific scalar converters** for URI types. Because
there is no KMP-common URI type, each platform gets its own converter:

| Platform | Type | Converter |
|----------|------|-----------|
| JVM | `java.net.URI` | `JavaStringUriConverter` |
| Android | `android.net.Uri` | `AndroidStringUriConverter` |
| iOS | `platform.Foundation.NSURL` | `NsUrlStringConverter` |

There are no converters in `commonMain` for this module. Like other scalar add-ons, converters must
be listed in `@KMapperConfig(converters = [...])` — they are not auto-discovered.

> **Note:** `converters-uri` is new in version **0.2.0** and is not yet published to Maven Central.
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
        }
        // converters-uri is consumed in the platform source set where you need it:
        jvmMain.dependencies {
            implementation("io.github.sahsenvar:kmapper-converters-uri:0.2.0")
        }
        androidMain.dependencies {
            implementation("io.github.sahsenvar:kmapper-converters-uri:0.2.0")
        }
        // for iOS: add to iosMain or each iOS target source set
    }
}
```

---

## Converters by Platform

### JVM — `java.net.URI`

| Object | S | T | Forward | Reverse |
|--------|---|---|---------|---------|
| `JavaStringUriConverter` | `String` | `java.net.URI` | `URI.create(value)` | `value.toString()` |

### Android — `android.net.Uri`

| Object | S | T | Forward | Reverse |
|--------|---|---|---------|---------|
| `AndroidStringUriConverter` | `String` | `android.net.Uri` | `Uri.parse(value)` | `value.toString()` |

### iOS — `platform.Foundation.NSURL`

| Object | S | T | Forward | Reverse |
|--------|---|---|---------|---------|
| `NsUrlStringConverter` | `String` | `platform.Foundation.NSURL` | `NSURL.URLWithString(value)` | `value.absoluteString ?: value.path ?: ""` |

`NSURL.URLWithString()` returns `null` for malformed input. The converter throws
`MappingException.TypeConversionFailed("String", "NSURL", cause)` on null — it does not silently
produce an empty NSURL.

> **NSURL round-trip caveat:** `NSURL` normalizes URLs (e.g. trailing slash canonicalization).
> Round-trip tests must use already-normalized URLs such as `"https://example.com/"` (with trailing
> slash). Using `"https://example.com"` (without trailing slash) may produce a different string
> after round-trip on iOS.

---

## Usage (JVM/Android example)

```kotlin
import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.uri.JavaStringUriConverter

@KMapperConfig(converters = [JavaStringUriConverter::class])
object AppMappers
```

```kotlin
import java.net.URI

@MapTo(ResourceDomain::class)
data class ResourceRemote(
    val id: String,
    val endpoint: String,    // "https://api.example.com/resource/1"
)

data class ResourceDomain(
    val id: String,
    val endpoint: URI,
)
```

Generated mapping:

```kotlin
public fun ResourceRemote.toResourceDomain(): ResourceDomain = ResourceDomain(
    id       = id,
    endpoint = JavaStringUriConverter.convertToNonNull(endpoint),
)
```

---

## Which Converter Should You Use?

| Target type | Converter |
|-------------|-----------|
| `java.net.URI` (JVM) | `JavaStringUriConverter` |
| `android.net.Uri` (Android) | `AndroidStringUriConverter` |
| `platform.Foundation.NSURL` (iOS) | `NsUrlStringConverter` |

---

Next: [Validation — @ValidateFrom / @ValidateTo →](../advanced/validation.md) | Other references: [@KMapperConfig](kmapperconfig.md)
