# Built-in Converter'lar

Eşleşen bir alan çiftinin tipleri farklıysa KMapper **derleme zamanında** bir converter
çözümler. 35 tip çifti core ile gelir ve **kayıt gerektirmeden** otomatik çözümlenir — ve en
az onun kadar önemlisi: veri bozacak yönler derleme zamanında *reddedilir*.

## Katalog

Bütün built-in'ler `com.sahsenvar.kmapper.converter.builtin` paketinde public object'lerdir;
isimde **zengin tip önce** gelir (daha fazlasını tutabilen tip: `LongIntConverter`,
`InstantStringConverter`).

**Sayısal genişletme (12 çift)** — kayıpsız yön dönüştürür; daraltan yön reddedilir
(aşağıya bakın):

| Zengin | Dar |
|--------|-----|
| `Short`, `Int`, `Long`, `Float`, `Double` | `Byte` |
| `Int`, `Long`, `Float`, `Double` | `Short` |
| `Long`, `Double` | `Int` |
| `Double` | `Float` |

**String çiftleri (7)** — `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `Boolean` ↔
`String`. Formatlama totaldir; parse, bozuk girdide fırlatır (ve
[ladder'a](../temel-kullanim/null-safety.md) biner). `Boolean` parse katıdır: yalnızca
`"true"`/`"false"` — `"TRUE"`, `"1"`, `"yes"` belirsiz wire formatları olarak reddedilir.

**Çapraz çiftler (9)** — `Float ↔ Int`, `Float ↔ Long`, `Double ↔ Long` (kayıpsız yön
dönüştürür) ve `Boolean` ↔ tüm sayısal tipler. `Boolean`/sayısal çiftler özeldir: **iki yön de
reddedilir** — `Byte → Boolean`'ın kanonik bir anlamı yok (`2` true mu?), `Boolean → Byte`'ın
kanonik bir kodlaması yok (`0/1`? `-1`?). Registry'de durmalarının nedeni, jenerik "converter
yok" hatası yerine *gerekçeli* reddi almanız ve **kendi** wire kuralınızı kodlayan tek
satırlık converter'ı yazmanız.

**kotlinx-datetime (5)** — `Instant ↔ String` (ISO-8601), `Instant ↔ Long` (epoch ms),
`LocalDate ↔ String`, `LocalDateTime ↔ String`, `LocalTime ↔ String`.

**kotlin.time (2)** — `Duration ↔ String` (ISO-8601, ör. `PT1H30M`), `Duration ↔ Long` (tam
milisaniye; milisaniye altı hassasiyet kırpılır — `Instant ↔ Long` ile aynı, belgelenmiş
takas).

## Reddedilen yönler bir özelliktir

`LongIntConverter`, `Int → Long`'u seve seve dönüştürür. `Long → Int` isterseniz **build
düşer**:

```
Long -> Int conversion is unsupported! This relates to our policy on lossy conversions
(e.g. Long -> Int, Double -> Float). What you can do:
  1. Check the converter add-ons
  2. Create your own converter
  3. Rethink your source or target type using supported types.
```

Bu, `@UnsupportedDirection`'dır — sessiz kırpma yerine beyan edilmiş, gerekçeli bir ret.
Domain'iniz aralığı *gerçekten* garanti ediyorsa üç satırlık custom converter'ı yazar, kararı
açıkça üstlenirsiniz. Aynı mekanizma
[kendi converter'larınızda](ozel-converter.md) da emrinizde.

## Çözümleme sırası

`A → B` isteyen bir alan için:

1. alan üzerindeki [`@ConvertWith`](convert-with.md) — açık override kazanır
2. [`@KMapperConfig`](kmapperconfig.md) converter'larınız — aynı çift için custom,
   built-in'i **gölgeler**
3. core built-in'leri (bu sayfa)
4. hiçbiri yoksa → çifti ve nereye kayıt ekleyeceğinizi söyleyen `MissingConverter` derleme
   hatası

> Sıradaki: **[Kendi Converter'ınızı Yazmak →](ozel-converter.md)**
