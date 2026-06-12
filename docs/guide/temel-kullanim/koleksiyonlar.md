# Koleksiyonlar

`List`, `Set` ve `Map` alanları eleman eleman eşlenir ve **her eleman kendi fallback
ladder'ına biner**. Tasarım hedefi: yüz elemandan biri bozuksa bedeli o eleman olsun, payload
değil — ve asla sessizce olmasın.

## List'ler

```kotlin
data class Sensors(val readings: List<Int>)

@MapTo(Sensors::class)
data class SensorsResponse(val readings: List<String>) // eleman başına "42" -> 42
```

Bozuk ya da null bir elemanın davranışı, **hedef eleman tipine** göre:

| Hedef eleman | Bozuk/null eleman ne olur | Raporu |
|--------------|----------------------------|--------|
| `List<T?>` | yerinde `null` (boyut korunur) | `AbsorbedConversionError` / konum korunur |
| `List<T>` | atılır (liste sıkışır) | `DroppedBrokenElement` / `DroppedNullElement` |

Skalerlerle aynı felsefe: kaçışı tip beyan eder, her kullanımını sink duyar.

## Alan bazlı eleman politikası: OnFail

[`@ConvertWith(onFail = …)`](../tip-donusumu/convert-with.md) tek bir koleksiyon alanını
ayarlar. Annotation, **üretilen yönün kaynak alanına** konur:

```kotlin
@MapTo(Measurements::class)
data class MeasurementsResponse(
    @ConvertWith(onFail = OnFail.Throw)
    val invoiceLines: List<String>, // hep-ya-hiç: tek bozuk satır mapping'i düşürür

    @ConvertWith(onFail = OnFail.Skip)
    val tagIds: List<String?>, // sıkıştır: hedef null tutabilse bile bozuk/null elemanları at
)
```

- `OnFail.Throw` — alanı sertleştirir: ilk bozuk eleman, `items[i]` tarzı yol taşıyan hatayla
  mapping'in tamamını düşürür.
- `OnFail.Skip` — sıkıştırır: bozuk/null elemanlar atılır (ve raporlanır).
- `OnFail.Auto` (varsayılan) — yukarıdaki tablo.

## Set'ler

Aynı ladder. Bir ek incelik: eleman dönüşümü, kaynakta farklı iki elemanı hedefte eşit hale
getirebilir (`"01"` ve `"1"` ikisi de → `1`). Set tekini tutar ve `ConvergedDuplicateElement`
raporlar — set semantiği bile olsa sessiz veri kaybı yok.

## Map'ler

Anahtarlar ve değerler ayrı ayrı ladder'a biner. Map'e özgü iki kural:

- **bozuk anahtar** girdinin tamamını düşürür (adressiz değer anlamsızdır) —
  `DroppedBrokenElement` olarak raporlanır;
- dönüşüm sonrası aynı hedef anahtara düşen iki kaynak anahtardan **sonuncusu** kalır ve
  `DuplicateKey` raporlanır.

Tipleri birebir uyuşan `Map<String, String> → Map<String, String>` olduğu gibi geçer.

## Stdlib'in ötesi: wrapper'lar

`PersistentList`, `NonEmptyList` ve benzerleri tek bir `@CollectionWrapper` kaydı
uzaklıkta — aynı eleman semantiği, farklı kap. Bkz.
[Immutable Koleksiyonlar](../tip-donusumu/immutable.md) ve
[Arrow](../tip-donusumu/arrow.md); kendi kap tipiniz için
[kendi wrapper'ınızı yazın](../tip-donusumu/ozel-converter.md).

> Sıradaki: **[Built-in Converter'lar →](../tip-donusumu/builtin.md)**
