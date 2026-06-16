# URI — converters-uri

`String ↔` the platform's URI type, one converter per platform — for KMP apps whose domain
models carry real URI types instead of raw strings.

## Setup

```kotlin
commonMain.dependencies {
    implementation("io.github.sahsenvar:kmapper-converters-uri:2.2.1")
}
```

## Converters (`com.sahsenvar.kmapper.uri`)

| Object | Pair | Source set |
|--------|------|------------|
| `JavaStringUriConverter` | `String ↔ java.net.URI` | jvmMain |
| `AndroidStringUriConverter` | `String ↔ android.net.Uri` | androidMain |
| `NsUrlStringConverter` | `String ↔ platform.Foundation.NSURL` | iosMain |

Because the URI type itself differs per platform, these are registered in **platform**
source sets (an `expect`/`actual` config object, or per-platform mapping declarations) rather
than in a single `commonMain` `@KMapperConfig`.

Malformed URIs throw the platform's native exception and ride the
[ladder](../basic-usage/null-safety.md); to validate URL-shaped strings in `commonMain`
without a platform type, use [`UrlValidator`](../validation/validators.md).

> Next: **[@Validate →](../validation/validate.md)**
