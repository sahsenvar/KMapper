# Okio — converters-okio

Converters for [Okio](https://square.github.io/okio/) types (`ByteString`, `Path`), including
three **same-pair encoding alternates** for `String ↔ ByteString`.

## Setup

```kotlin
commonMain.dependencies {
    implementation("io.github.sahsenvar:kmapper-converters-okio:2.0.1")
}
```

```kotlin
@KMapperConfig(converters = [StringByteStringConverter::class, StringPathConverter::class])
object AppMapperConfig
```

## Converters (`com.sahsenvar.kmapper.okio`)

| Object | Pair | Encoding |
|--------|------|----------|
| `StringByteStringConverter` | `String ↔ ByteString` | UTF-8 |
| `ByteArrayByteStringConverter` | `ByteArray ↔ ByteString` | — |
| `StringPathConverter` | `String ↔ Path` | — |
| `Base64ByteStringConverter` | `String ↔ ByteString` | Base64 (RFC 4648; decode accepts URL-safe too) |
| `Base64UrlByteStringConverter` | `String ↔ ByteString` | URL-safe Base64 |
| `HexByteStringConverter` | `String ↔ ByteString` | hex (lower-case out, either case in) |

## Same pair, different encodings — how to choose

`UTF-8`, `Base64`, and `Hex` all claim `String ↔ ByteString`, and
[discovery](kmapperconfig.md) allows only one converter per pair. So: register the
**module-wide default** in `@KMapperConfig`, select alternates **per field**:

```kotlin
@MapTo(Document::class)
data class DocumentResponse(
    val title: String,    // module default: UTF-8
    @ConvertWith(use = Base64ByteStringConverter::class)
    val payload: String,  // this wire field is Base64
    @ConvertWith(use = HexByteStringConverter::class)
    val checksum: String, // and this one is hex
)
```

Malformed Base64/hex input throws `IllegalArgumentException` and rides the
[ladder](../basic-usage/null-safety.md). To validate the *string shape* without converting,
see [`Base64Validator` / `HexStringValidator`](../validation/validators.md).

> Next: **[URI →](uri.md)**
