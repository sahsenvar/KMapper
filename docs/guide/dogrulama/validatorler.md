# Validator Kütüphanesi

İki katman, tek ayrım kuralı: **yapısal, bağımlılıksız kontroller core'da**
(`com.sahsenvar.kmapper.validation.builtin`, ekstra artifact yok); **görüş bildiren format
bilgisi `kmapper-validators` add-on'unda** (`com.sahsenvar.kmapper.validators`).

## Core built-in'leri — hazır object'ler

| Validator | Tip | Reddettiği |
|-----------|-----|------------|
| `NotBlankValidator` | `String` | boş/yalnızca whitespace |
| `NotEmptyStringValidator` | `String` | `""` |
| `NotEmptyCollectionValidator` | `Collection<*>` | boş koleksiyon |
| `PositiveIntValidator` / `PositiveLongValidator` / `PositiveDoubleValidator` | sayısal | `<= 0` (ve `NaN`) |
| `NonNegativeIntValidator` / `NonNegativeLongValidator` / `NonNegativeDoubleValidator` | sayısal | `< 0` (ve `NaN`) |
| `FiniteDoubleValidator` | `Double` | `NaN`, `±Infinity` |

## Core built-in'leri — parametreli tabanlar

Constructor parametreli açık sınıflar; **kendi** sınırlarınızla `object` olarak türetin
([parametreli converter'larla](../tip-donusumu/ozel-converter.md) aynı reçete):

| Taban | Kural |
|-------|-------|
| `RegexValidator(pattern, reason)` | değer kalıbın tamamıyla eşleşmeli |
| `StringLengthValidator(minLength, maxLength)` | uzunluk aralıkta |
| `IntRangeValidator(range)` / `LongRangeValidator(range)` | değer aralıkta |
| `DoubleRangeValidator(min, max)` | değer aralıkta (NaN her zaman reddedilir) |
| `CollectionSizeValidator(minSize, maxSize)` | boyut aralıkta |

```kotlin
object UsernameLengthValidator : StringLengthValidator(minLength = 3, maxLength = 20)
object QuantityValidator : IntRangeValidator(1..999)
object SkuValidator : RegexValidator(Regex("[A-Z]{3}-\\d{4}"), "must be a SKU like ABC-1234")

data class Product(
    @Validate(SkuValidator::class) val sku: String,
    @Validate(QuantityValidator::class) val quantity: Int,
)
```

Hatalı yapılandırılmış sınırlar (`min > max`, negatif uzunluk) **kuruluş anında** patlar —
bozuk bir validator modelde sessizce oturamaz.

## kmapper-validators add-on'u

```kotlin
commonMain.dependencies {
    implementation("io.github.sahsenvar:kmapper-validators:2.1.0")
}
```

Aksi belirtilmedikçe hepsi `String` üzerinde:

| Validator | Kabul ettiği |
|-----------|--------------|
| `EmailValidator` | pragmatik RFC tarzı e-postalar |
| `UrlValidator` | `http(s)://…` URL'leri |
| `PhoneE164Validator` | `+905551112233` — kanonik E.164, ayraçsız |
| `Ipv4Validator` | katı noktalı ondalık, öncü sıfır yok |
| `Ipv6Validator` | tam/`::` sıkıştırmalı gruplar, gömülü IPv4 kuyruğu |
| `HostnameValidator` | RFC 1123 hostname'leri |
| `UuidStringValidator` | kanonik 8-4-4-4-12 UUID, iki harf boyutu da |
| `SlugValidator` | `kucuk-harf-tireli-slug` |
| `Base64Validator` | standart ya da URL-safe alfabe, padding'li/padding'siz |
| `HexStringValidator` | çift uzunluklu hex |
| `LatitudeValidator` (`Double`) | `-90.0..90.0` |
| `LongitudeValidator` (`Double`) | `-180.0..180.0` |
| `PortNumberValidator` (`Int`) | `1..65535` |
| `CreditCardNumberValidator` | Luhn kontrolünden geçen 12-19 hane (boşluk/tire hoş görülür) |

Doğrula-ya-da-dönüştür pratiği: *domain tipi* daha zengin olabiliyorsa (gerçek bir `Uuid`,
gerçek bir `ByteString`) [converter](../tip-donusumu/builtin.md) kullanın — dönüşüm bozuk
girdiyi zaten reddeder. Alan **string/sayı olarak kalacaksa** ama belirli bir biçim tutması
gerekiyorsa validator kullanın.

> Sıradaki: **[MappableEnum →](../enum/mappable-enum.md)**
