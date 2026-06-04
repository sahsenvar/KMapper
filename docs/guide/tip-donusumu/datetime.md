# Tarih ve Saat Converter'ları — converters-datetime

`converters-datetime` modülü, `kotlinx-datetime` ve `java.time` tiplerini `String`/`Long` ile eşlemek için **scalar converter'lar** sağlar. Scalar converter'lar `@CollectionWrapper` gibi otomatik keşfedilmez; kullanacağınız converter'ları `@KMapperConfig(converters = [...])` listesine eklemeniz gerekir.

> **Not:** `converters-datetime` sürüm **0.2.0** ile gelir; henüz Maven Central'da değildir.
> Yayınlanana kadar `publishToMavenLocal` + `mavenLocal()` ile kullanın.
> `core` ve `processor` hâlâ Maven Central'dan `0.1.0` olarak çekilebilir.

---

## Kurulum

```kotlin
// settings.gradle.kts — pre-release için mavenLocal ekle
dependencyResolutionManagement {
    repositories {
        mavenLocal()        // 0.2.0 add-on'lar için
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts (tüketen modül)
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

## Platform Desteği

| Converter grubu | Modül kaynağı | Çalışır |
|-----------------|--------------|---------|
| `kotlinx-datetime` converter'ları | `commonMain` | Tüm platformlar (JVM, Android, iOS, JS, WASM) |
| `java.time` converter'ları | `jvmAndroidMain` | Yalnızca JVM ve Android |
| Köprü converter'lar (kotlinx ↔ java.time) | `jvmAndroidMain` | Yalnızca JVM ve Android |

> **Not:** `Instant` dönüşümleri (`StringInstantConverter`, `LongInstantConverter`) core modülünde yerleşik olarak gelir — `converters-datetime`'a gerek yoktur.

---

## kotlinx-datetime Converter'ları (commonMain)

Tüm platformlarda kullanılabilir.

| Converter | Kaynak | Hedef |
|-----------|--------|-------|
| `StringLocalDateConverter` | `String` (ISO-8601) | `kotlinx.datetime.LocalDate` |
| `StringLocalDateTimeConverter` | `String` (ISO-8601) | `kotlinx.datetime.LocalDateTime` |
| `StringLocalTimeConverter` | `String` (ISO-8601) | `kotlinx.datetime.LocalTime` |

---

## java.time Converter'ları (jvmAndroidMain)

Yalnızca JVM ve Android'de kullanılabilir. İsimlendirme: `Java`-öneki ile kotlinx converter'lardan ayrışır.

| Converter | Kaynak | Hedef |
|-----------|--------|-------|
| `StringJavaInstantConverter` | `String` (ISO-8601) | `java.time.Instant` |
| `LongJavaInstantConverter` | `Long` (epoch-milli) | `java.time.Instant` |
| `StringJavaLocalDateConverter` | `String` (ISO-8601) | `java.time.LocalDate` |
| `StringJavaLocalDateTimeConverter` | `String` (ISO-8601) | `java.time.LocalDateTime` |
| `StringJavaLocalTimeConverter` | `String` (ISO-8601) | `java.time.LocalTime` |
| `StringJavaZonedDateTimeConverter` | `String` (ISO-8601) | `java.time.ZonedDateTime` |
| `StringJavaOffsetDateTimeConverter` | `String` (ISO-8601) | `java.time.OffsetDateTime` |

---

## Köprü Converter'lar: kotlinx ↔ java.time (jvmAndroidMain)

Modelinizde bir katmanda `kotlinx.datetime.*`, diğer katmanda `java.time.*` kullanıyorsanız bu köprü converter'ları kullanın.

| Converter | Kaynak | Hedef |
|-----------|--------|-------|
| `KotlinJavaInstantConverter` | `kotlinx.datetime.Instant` | `java.time.Instant` |
| `KotlinJavaLocalDateConverter` | `kotlinx.datetime.LocalDate` | `java.time.LocalDate` |

---

## Kullanım

Scalar converter'lar `@KMapperConfig(converters = [...])` listesine eklenmelidir:

```kotlin
import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.datetime.StringLocalDateConverter
import com.sahsenvar.kmapper.datetime.StringLocalDateTimeConverter

@KMapperConfig(converters = [StringLocalDateConverter::class, StringLocalDateTimeConverter::class])
object MyMappers
```

Ardından modellerinizde bu tipleri doğrudan kullanın:

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

Üretilen eşleme:

```kotlin
public fun EventRemote.toEventDomain(): EventDomain = EventDomain(
    name      = name,
    date      = StringLocalDateConverter.convertToNonNull(date),
    createdAt = StringLocalDateTimeConverter.convertToNonNull(createdAt),
)
```

---

## Hangi Converter'ı Seçmeli?

- Modeliniz KMP (iOS dahil) ise → `kotlinx-datetime` converter'larını kullanın.
- Modeliniz yalnızca JVM/Android ise → `java.time` converter'larını kullanabilirsiniz.
- `Instant` için ekstra bağımlılık gerekmez — core'daki `StringInstantConverter`/`LongInstantConverter` kullanın.

---

Sonraki adım: [Büyük Sayı Converter'ları →](bignumber.md) | Diğer kaynaklar: [@KMapperConfig](kmapperconfig.md)
