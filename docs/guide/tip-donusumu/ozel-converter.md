# Kendi Converter'ınızı Yazmak

Bir converter, `MapTypeConverter<S, T>`'yi genişleten bir `object`'tir. Sizinki built-in'lerle
**birebir aynı raylarda** koşar: aynı keşif, aynı override'lar, aynı derleme zamanı
kontrolleri, aynı hata taksonomisi. Bu sayfa sözleşmenin tamamıdır.

## Şekil

```kotlin
import com.sahsenvar.kmapper.converter.MapTypeConverter

object MoneyStringConverter : MapTypeConverter<Money, String>(Money::class, String::class) {
    override fun convertTo(source: Money): String = source.format()        // Money  -> String
    override fun convertFrom(target: String): Money = Money.parse(target)  // String -> Money
}
```

Kurallar:

- **`object`, class değil** — üretilen kod `MoneyStringConverter.convertFrom(x)` diye
  doğrudan FQN ile çağırır; nesne kurulumu yok, reflection yok.
- **Zengin tip önce** (`<Money, String>`): `convertTo` ikinci tipe *doğru* gider,
  `convertFrom` oradan döner.
- **Bozuk girdide fırlatın.** `convertFrom("garbage")` fırlatmalı
  (`IllegalArgumentException` uygundur) — [ladder](../temel-kullanim/null-safety.md) ve
  `Result` sınırı onu tipli, yol taşıyan bir hataya çevirir. Asla tahmini değer döndürmeyin.

[@KMapperConfig](kmapperconfig.md)'e bir kez kaydedin — sonrasında modüldeki her
`Money`/`String` alan çifti otomatik ona çözümlenir.

## İki opsiyonel metot: sanctioned null

`convertToOrNull` / `convertFromOrNull`, **null'un hata değil geçerli bir cevap olduğu**
dönüşümler içindir — ör. meşru biçimde sonuçsuz kalabilen bir arama. Varsayılan halleri total
metotlara delege eder; "sonuç yok" anlamlıysa override edin:

```kotlin
object CountryCodeConverter : MapTypeConverter<Country, String>(Country::class, String::class) {
    override fun convertTo(source: Country): String = source.isoCode
    override fun convertFrom(target: String): Country =
        Country.byIso(target) ?: throw IllegalArgumentException("Unknown ISO code: $target")

    // sanctioned null: bilinmeyen kod burada beklenen bir sonuçtur, hata değil
    override fun convertFromOrNull(target: String): Country? = Country.byIso(target)
}
```

Hedef alan nullable olduğunda üretilen kod `OrNull` varyantını çağırır — bilinmeyen ülke,
degradation raporu *olmadan* `null` olarak akar (bozuk değil, onaylı).

## Bir yönü reddetmek

Bir yön dürüstçe gerçeklenemiyorsa yaklaşık değer üretmek yerine **gürültüyle reddedin** —
built-in'lerin kullandığı mekanizmanın aynısı:

```kotlin
import com.sahsenvar.kmapper.converter.UnsupportedDirection

object SearchQueryConverter : MapTypeConverter<SearchQuery, String>(SearchQuery::class, String::class) {
    override fun convertTo(source: SearchQuery): String = source.serialize()

    @UnsupportedDirection("ham sorgu string'ini parse etmek kayıplıdır; SearchQuery'yi DSL ile kurun")
    override fun convertFrom(target: String): SearchQuery = unsupported()
}
```

Bir mapping reddedilen yöne *ihtiyaç duyarsa* **build düşer** ve mesajda sizin gerekçeniz
görünür. (Tespit annotation'ladır; `unsupported()` tutarlı runtime arka planını sağlar. Total
metodu işaretleyin, `OrNull` olanı değil — tersini yaparsanız derleyici yol gösterir.)

## Parametreli converter'lar

`@KMapperConfig`/`@ConvertWith` object referansı aldığından, parametreleme **constructor
parametreli bir abstract taban** + tabandan türeyen tek satırlık object'lerle yapılır —
kuralı bir kez tanımlayın, varyantları damgalayın:

```kotlin
abstract class FormattedDoubleStringConverter(
    private val decimalDigits: Int,
    private val suffix: String = "",
) : MapTypeConverter<Double, String>(Double::class, String::class) {
    override fun convertTo(source: Double): String = source.format(decimalDigits) + suffix
    override fun convertFrom(target: String): Double = target.removeSuffix(suffix).trim().toDouble()
}

/** 12.345 -> "12.35"  */ object PriceFormatConverter : FormattedDoubleStringConverter(decimalDigits = 2)
/** 12.345 -> "12.3%" */ object PercentFormatConverter : FormattedDoubleStringConverter(decimalDigits = 1, suffix = "%")
```

Her varyant isimli, test edilebilir bir şeydir; alan bazında
`@ConvertWith(use = PriceFormatConverter::class)` ile seçilir — annotation argümanı sihri
yok. (Çalışan tam sürüm: [galerideki](../baslarken/ornekler.md) `ParameterizedConverters.kt`.)
[Validator kütüphanesi](../dogrulama/validatorler.md) birebir aynı reçeteyi kullanır
(`RegexValidator`, `IntRangeValidator`, …).

## Collection wrapper'ları

`List<T>`'yi kendi kap tipinize eşlemek için `@CollectionWrapper` ile bir wrapper tanımlayın:

```kotlin
@CollectionWrapper(forType = PersistentList::class)
object PersistentListWrapper {
    fun <T> wrap(source: List<T>): PersistentList<T> = source.toPersistentList()
    fun <T> unwrap(source: PersistentList<T>): List<T> = source.toList()
}
```

İki yön de zorunludur ve **derlemede denetlenir** (imza kuralını processor doğrular).
`@KMapperConfig(wrappers = [...])` ile kaydedin. Eleman dönüşümü yine normal eleman
ladder'ına biner — wrapper yalnızca kabı değiştirir.

> Sıradaki: **[@KMapperConfig →](kmapperconfig.md)**
