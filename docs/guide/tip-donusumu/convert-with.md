# @ConvertWith — Alan Bazlı Override ve OnFail

Kuralı otomatik keşif halleder; istisnayı `@ConvertWith`. Birlikte ya da ayrı kullanılabilen
iki bağımsız işi vardır:

```kotlin
annotation class ConvertWith(
    val use: KClass<out MapTypeConverter<*, *>> = /* otomatik keşfe bırak */,
    val onFail: OnFail = OnFail.Auto,
)
```

## İş 1 — `use`: BU alan için farklı bir converter seç

```kotlin
@MapTo(Document::class)
data class DocumentResponse(
    val title: String,            // String -> ByteString: modül geneli UTF-8 converter'ı
    @ConvertWith(use = Base64ByteStringConverter::class)
    val payload: String,          // …ama BU alanın wire formatı Base64
)
```

`@ConvertWith` **yalnızca override içindir**: normal durumda gerekmez —
[`@KMapperConfig`](kmapperconfig.md) kaydı + keşif onu kapsar. Tek bir alan modül genel
kuralından saptığında uzanın (format varyantları,
[parametreli converter'lar](ozel-converter.md)).

## İş 2 — `onFail`: bu alanın hata politikasını ayarla

| Politika | Skaler alan | Koleksiyon alanı |
|----------|-------------|-------------------|
| `Auto` (varsayılan) | [ladder](../temel-kullanim/null-safety.md) | [eleman ladder'ı](../temel-kullanim/koleksiyonlar.md) |
| `Throw` | asla emme — bozuk değer nullable/default'lu yuvaya bile gitmez, mapping düşer | ilk bozuk eleman mapping'i düşürür (hep-ya-hiç) |
| `Skip` | — derleme hatası (skaleri atlamak *eksiklik uydurmak* olurdu) | bozuk/null elemanları at, sıkıştır, raporla |

```kotlin
@MapTo(Measurements::class)
data class MeasurementsResponse(
    @ConvertWith(onFail = OnFail.Throw)
    val invoiceLines: List<String>, // para: kısmi başarıyı reddet

    @ConvertWith(onFail = OnFail.Skip)
    val tagIds: List<String?>,      // etiketler: eldekiyle yetin, sıkıştır
)
```

## Yöne daraltılmış varyantlar

`@ConvertTo(target, use, onFail)` ve `@ConvertFrom(source, use, onFail)`, `@ConvertWith`'in
tek bir mapping yönüne/hedefe daraltılmış halidir — aynı alanın farklı üretilen mapping'lerde
farklı muamele görmesi gerektiğinde. `@ConvertWith`, alanın katıldığı bütün yönlere uygulanır.

## Yerleşim kuralı (ezberlemeye değer)

Direktifler, **üretilen yönün kaynak alanından** okunur. `@MapTo` wire modelindeyse
*wire* alanını işaretleyin — domain tarafındaki annotation o yön için görünmezdir.
(Alana çapalanan `@Validate` bilinçli istisnadır: alanı mapping'in hangi tarafındaysa orada
ateşler.)

> Sıradaki: **[Immutable Koleksiyonlar →](immutable.md)**
