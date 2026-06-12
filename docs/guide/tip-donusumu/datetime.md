# Tarih ve Saat — converters-datetime

> **kotlinx-datetime için add-on gerekmez.** `Instant`, `LocalDate`, `LocalDateTime`,
> `LocalTime` (↔ `String`/`Long`) ve `kotlin.time.Duration`
> [core built-in](builtin.md)'dir — otomatik çözümlenir, sıfır kayıt. Bu add-on, **`java.time`**
> kullanan modeller ve JVM/Android'de iki dünyayı köprülemek içindir.

## Kurulum

```kotlin
dependencies {
    implementation("io.github.sahsenvar:kmapper-converters-datetime:2.0.0")
}
```

Kullandıklarınızı [`@KMapperConfig`](kmapperconfig.md)'e kaydedin (add-on converter'ları
otomatik keşfedilmez):

```kotlin
@KMapperConfig(converters = [StringJavaInstantConverter::class, KotlinJavaDurationConverter::class])
object AppMapperConfig
```

## `java.time` skaler converter'ları (JVM/Android)

Paket `com.sahsenvar.kmapper.datetime`; hepsi tipin kendi `parse`/`toString`'iyle ISO-8601
işler:

| Object | Çift |
|--------|------|
| `StringJavaInstantConverter` | `String ↔ java.time.Instant` |
| `LongJavaInstantConverter` | `Long` (epoch ms) `↔ java.time.Instant` |
| `StringJavaLocalDateConverter` | `String ↔ java.time.LocalDate` |
| `StringJavaLocalDateTimeConverter` | `String ↔ java.time.LocalDateTime` |
| `StringJavaLocalTimeConverter` | `String ↔ java.time.LocalTime` |
| `StringJavaZonedDateTimeConverter` | `String ↔ java.time.ZonedDateTime` |
| `StringJavaOffsetDateTimeConverter` | `String ↔ java.time.OffsetDateTime` |
| `StringJavaDurationConverter` | `String` (`"PT1H30M"`) `↔ java.time.Duration` |

## Köprüler: kotlinx ↔ java

İki kütüphaneyi birden kullanan kod tabanları için (kotlinx-datetime'lı KMP domain'i,
java.time'lı persistence katmanı):

| Object | Çift | Not |
|--------|------|-----|
| `KotlinJavaInstantConverter` | `kotlinx Instant ↔ java Instant` | birebir |
| `KotlinJavaLocalDateConverter` | `kotlinx LocalDate ↔ java LocalDate` | birebir |
| `KotlinJavaDurationConverter` | `kotlin.time.Duration ↔ java.time.Duration` | sonlu değerlerde ±146 yıl içinde ns hassasiyetiyle birebir |

## Alan bazında wire formatı seçmek

`String ↔ Instant` (ISO) ile `Long ↔ Instant` (epoch ms) farklı çiftlerdir; keşif üzerinden
yan yana yaşayabilirler. *Aynı çiftin* iki formatı (ISO string ve epoch-string gibi) alan
bazlı bir karardır — bkz. [parametreli converter'lar](ozel-converter.md) ve
[@ConvertWith](convert-with.md).

> Sıradaki: **[Büyük Sayılar →](bignumber.md)**
