# Okio — converters-okio

[Okio](https://square.github.io/okio/) tipleri (`ByteString`, `Path`) için converter'lar —
`String ↔ ByteString` için üç **aynı-çift kodlama alternatifi** dahil.

## Kurulum

```kotlin
commonMain.dependencies {
    implementation("io.github.sahsenvar:kmapper-converters-okio:2.1.0")
}
```

```kotlin
@KMapperConfig(converters = [StringByteStringConverter::class, StringPathConverter::class])
object AppMapperConfig
```

## Converter'lar (`com.sahsenvar.kmapper.okio`)

| Object | Çift | Kodlama |
|--------|------|---------|
| `StringByteStringConverter` | `String ↔ ByteString` | UTF-8 |
| `ByteArrayByteStringConverter` | `ByteArray ↔ ByteString` | — |
| `StringPathConverter` | `String ↔ Path` | — |
| `Base64ByteStringConverter` | `String ↔ ByteString` | Base64 (RFC 4648; decode URL-safe'i de kabul eder) |
| `Base64UrlByteStringConverter` | `String ↔ ByteString` | URL-safe Base64 |
| `HexByteStringConverter` | `String ↔ ByteString` | hex (çıkışta küçük harf, girişte ikisi de) |

## Aynı çift, farklı kodlamalar — nasıl seçilir?

`UTF-8`, `Base64` ve `Hex`'in üçü de `String ↔ ByteString` çiftini ister; oysa
[keşif](kmapperconfig.md) çift başına tek converter'a izin verir. Çözüm: **modül geneli
varsayılanı** `@KMapperConfig`'e kaydedin, alternatifleri **alan bazında** seçin:

```kotlin
@MapTo(Document::class)
data class DocumentResponse(
    val title: String,    // modül varsayılanı: UTF-8
    @ConvertWith(use = Base64ByteStringConverter::class)
    val payload: String,  // bu wire alanı Base64
    @ConvertWith(use = HexByteStringConverter::class)
    val checksum: String, // bu da hex
)
```

Bozuk Base64/hex girdi `IllegalArgumentException` fırlatır ve
[ladder'a](../temel-kullanim/null-safety.md) biner. Dönüştürmeden yalnızca *string biçimini*
doğrulamak için bkz.
[`Base64Validator` / `HexStringValidator`](../dogrulama/validatorler.md).

> Sıradaki: **[URI →](uri.md)**
