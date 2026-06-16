# Büyük Sayılar — converters-bignumber

Keyfi hassasiyetli sayılar için converter'lar:
[ionspin kotlin-multiplatform-bignum](https://github.com/ionspin/kotlin-multiplatform-bignum)
(`commonMain`, tüm hedefler) ve `java.math` (JVM/Android).

## Kurulum

```kotlin
commonMain.dependencies {
    implementation("io.github.sahsenvar:kmapper-converters-bignumber:2.2.1")
}
```

```kotlin
@KMapperConfig(converters = [StringBigDecimalConverter::class])
object AppMapperConfig
```

## Converter'lar (`com.sahsenvar.kmapper.bignumber`)

ionspin (KMP):

| Object | Çift | Not |
|--------|------|-----|
| `StringBigDecimalConverter` | `String ↔ BigDecimal` | para/hassasiyet için önerilen wire formatı |
| `StringBigIntegerConverter` | `String ↔ BigInteger` | |
| `DoubleBigDecimalConverter` | `Double → BigDecimal` | ters yön **reddedilir** (aşağıda) |
| `LongBigIntegerConverter` | `Long → BigInteger` | tersi **reddedilir** |
| `IntBigIntegerConverter` | `Int → BigInteger` | tersi **reddedilir** |
| `BigIntegerBigDecimalConverter` | `BigInteger → BigDecimal` | tersi **reddedilir** |

`java.math` (JVM/Android): `StringJavaBigDecimalConverter` (düz string, asla bilimsel
gösterim değil), `StringJavaBigIntegerConverter`, `DoubleJavaBigDecimalConverter`,
`LongJavaBigIntegerConverter`, `JavaBigIntegerBigDecimalConverter` — aynı çiftler, aynı ret
politikası.

## Bazı yönler neden reddediliyor?

`BigDecimal → Double` hassasiyet kaybeder; `BigInteger → Long`/`Int` taşar;
`BigDecimal → BigInteger` kesri atar. Önceki sürümler bunları **sessizce** kırpıyordu — tam
da KMapper'ın yok etmek için var olduğu "makul görünen yanlış değer" hata sınıfı. Artık
[`@UnsupportedDirection`](ozel-converter.md): bu yönlerden birine ihtiyaç duyan mapping,
gerekçe ve seçeneklerle birlikte **derleme zamanında** düşer.

Domain'iniz aralığı gerçekten garanti ediyorsa kararı üç satırlık custom converter'la açıkça
üstlenin:

```kotlin
object CentsBigIntegerConverter : MapTypeConverter<Long, BigInteger>(Long::class, BigInteger::class) {
    override fun convertTo(source: Long): BigInteger = BigInteger.fromLong(source)
    override fun convertFrom(target: BigInteger): Long = target.longValue(exactRequired = true) // taşmada fırlatır
}
```

String çiftleri birebir gidip döner — wire'da para için `Double`'dan geçen her şeye karşı
`String ↔ BigDecimal`'ı tercih edin.

> Sıradaki: **[UUID →](uuid.md)**
