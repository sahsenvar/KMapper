# Okio Converter'ları — converters-okio

`converters-okio` modülü, Okio tipleri (`ByteString`, `Path`) için **scalar converter'lar** sağlar.
Diğer scalar add-on'lar gibi, `@KMapperConfig(converters = [...])` listesine eklenmeleri gerekir —
otomatik keşfedilmezler.

---

## Kurulum


```kotlin
// build.gradle.kts (tüketen modül)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.sahsenvar:kmapper-core:1.0.0")
            implementation("io.github.sahsenvar:kmapper-converters-okio:1.0.0")
        }
    }
}
```

Okio 3.9.1 (kararlı bir KMP sürümü), bu modülün geçişli bağımlılığıdır. JVM, Android,
`iosArm64` ve `iosSimulatorArm64` hedeflerini kapsar.

---

## Sağlanan Converter'lar (commonMain)

Tüm converter'lar `com.sahsenvar.kmapper.okio` paketinde yer alır.

| Nesne | K | H | İleri | Geri | Round-trip |
|-------|---|---|-------|------|------------|
| `StringByteStringConverter` | `String` | `okio.ByteString` | `value.encodeUtf8()` | `value.utf8()` | tam |
| `ByteArrayByteStringConverter` | `ByteArray` | `okio.ByteString` | `value.toByteString()` | `value.toByteArray()` | `contentEquals` ile karşılaştırın |
| `StringPathConverter` | `String` | `okio.Path` | `value.toPath()` | `value.toString()` | normalize edilmiş dizgilerde tam |

Üç converter da desteklenen tüm platformlarda (JVM, Android, iOS) kullanılabilir.

---

## Kullanım

```kotlin
import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.okio.StringByteStringConverter
import com.sahsenvar.kmapper.okio.StringPathConverter

@KMapperConfig(converters = [StringByteStringConverter::class, StringPathConverter::class])
object AppMappers
```

Alan modellerinizde `okio.ByteString` veya `okio.Path` kullanın:

```kotlin
import okio.ByteString
import okio.Path

@MapTo(FileDomain::class)
data class FileRemote(
    val name: String,
    val content: String,     // UTF-8 yük
    val location: String,    // "/var/data/file.bin"
)

data class FileDomain(
    val name: String,
    val content: ByteString,
    val location: Path,
)
```

Üretilen eşleştirme:

```kotlin
public fun FileRemote.toFileDomain(): FileDomain = FileDomain(
    name     = name,
    content  = StringByteStringConverter.convertToNonNull(content),
    location = StringPathConverter.convertToNonNull(location),
)
```

---

Sonraki adım: [URI Converter'ları →](uri.md) | Diğer kaynaklar: [@KMapperConfig](kmapperconfig.md)
