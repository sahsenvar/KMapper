# URI Converter'ları — converters-uri

`converters-uri` modülü, URI tipleri için **platforma özgü scalar converter'lar** sağlar. KMP
ortak bir URI tipi bulunmadığından, her platform kendi converter'ını alır:

| Platform | Tip | Converter |
|----------|-----|-----------|
| JVM | `java.net.URI` | `JavaStringUriConverter` |
| Android | `android.net.Uri` | `AndroidStringUriConverter` |
| iOS | `platform.Foundation.NSURL` | `NsUrlStringConverter` |

Bu modülün `commonMain`'inde converter bulunmaz. Diğer scalar add-on'lar gibi, converter'lar
`@KMapperConfig(converters = [...])` listesine eklenmelidir — otomatik keşfedilmezler.

---

## Kurulum

```kotlin
// build.gradle.kts (tüketen modül)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.sahsenvar:kmapper-core:1.0.0")
        }
        // converters-uri platforma özgü kaynak kümelerde kullanılır:
        jvmMain.dependencies {
            implementation("io.github.sahsenvar:kmapper-converters-uri:1.0.0")
        }
        androidMain.dependencies {
            implementation("io.github.sahsenvar:kmapper-converters-uri:1.0.0")
        }
        // iOS için: iosMain veya her iOS hedef kaynak kümesine ekleyin
    }
}
```

---

## Platforma Göre Converter'lar

### JVM — `java.net.URI`

| Nesne | K | H | İleri | Geri |
|-------|---|---|-------|------|
| `JavaStringUriConverter` | `String` | `java.net.URI` | `URI.create(value)` | `value.toString()` |

### Android — `android.net.Uri`

| Nesne | K | H | İleri | Geri |
|-------|---|---|-------|------|
| `AndroidStringUriConverter` | `String` | `android.net.Uri` | `Uri.parse(value)` | `value.toString()` |

### iOS — `platform.Foundation.NSURL`

| Nesne | K | H | İleri | Geri |
|-------|---|---|-------|------|
| `NsUrlStringConverter` | `String` | `platform.Foundation.NSURL` | `NSURL.URLWithString(value)` | `value.absoluteString ?: value.path ?: ""` |

`NSURL.URLWithString()`, hatalı biçimlendirilmiş girdi için `null` döner. Converter, null durumunda
`MappingException.TypeConversionFailed("String", "NSURL", cause)` fırlatır — sessizce boş bir
NSURL üretmez.

> **NSURL round-trip uyarısı:** `NSURL`, URL'leri normalize eder (ör. sondaki eğik çizgi
> standartlaştırması). Round-trip testleri, `"https://example.com/"` (sondaki eğik çizgi mevcut)
> gibi halihazırda normalize edilmiş URL'ler kullanmalıdır. `"https://example.com"` (eğik çizgi
> olmadan) iOS'ta farklı bir dizgiyle dönebilir.

---

## Kullanım (JVM/Android örneği)

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

Üretilen eşleştirme:

```kotlin
public fun ResourceRemote.toResourceDomain(): ResourceDomain = ResourceDomain(
    id       = id,
    endpoint = JavaStringUriConverter.convertToNonNull(endpoint),
)
```

---

## Hangi Converter'ı Seçmeli?

| Hedef tip | Converter |
|-----------|-----------|
| `java.net.URI` (JVM) | `JavaStringUriConverter` |
| `android.net.Uri` (Android) | `AndroidStringUriConverter` |
| `platform.Foundation.NSURL` (iOS) | `NsUrlStringConverter` |

---

Sonraki adım: [Doğrulama — @ValidateFrom / @ValidateTo →](../ileri/dogrulama.md) | Diğer kaynaklar: [@KMapperConfig](kmapperconfig.md)
