# URI — converters-uri

`String ↔` platformun URI tipi, platform başına bir converter — domain modelleri ham string
yerine gerçek URI tipleri taşıyan KMP uygulamaları için.

## Kurulum

```kotlin
commonMain.dependencies {
    implementation("io.github.sahsenvar:kmapper-converters-uri:2.2.2")
}
```

## Converter'lar (`com.sahsenvar.kmapper.uri`)

| Object | Çift | Source set |
|--------|------|------------|
| `JavaStringUriConverter` | `String ↔ java.net.URI` | jvmMain |
| `AndroidStringUriConverter` | `String ↔ android.net.Uri` | androidMain |
| `NsUrlStringConverter` | `String ↔ platform.Foundation.NSURL` | iosMain |

URI tipinin kendisi platforma göre değiştiğinden bunlar tek bir `commonMain`
`@KMapperConfig`'i yerine **platform** source set'lerinde kaydedilir (`expect`/`actual` bir
config object'i ya da platform bazlı mapping tanımları).

Bozuk URI'ler platformun kendi exception'ını fırlatır ve
[ladder'a](../temel-kullanim/null-safety.md) biner; `commonMain`'de platform tipi olmadan
URL biçimli string doğrulamak için [`UrlValidator`](../dogrulama/validatorler.md).

> Sıradaki: **[@Validate →](../dogrulama/validate.md)**
