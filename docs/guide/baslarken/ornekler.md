# Örnek Galerisi

Depo, **25 çalıştırılabilir, kendi kendine yeten örnek** içeren bir
[sample modülü](https://github.com/sahsenvar/KMapper/tree/main/sample) ile gelir —
kütüphanenin her özelliği, her kategoride basitten gelişmişe sıralı. "Bunu nasıl yaparım?"
diye sorduğunuz her konunun bir örneği var.

Hepsini çalıştırın:

```bash
./gradlew sample:runSample
```

…ya da herhangi bir dosyayı IDE'de açıp `main`'inin yanındaki ▶ tuşuna basın.

## Öğrenme yolu

| # | Kategori | Öğrenecekleriniz |
|---|----------|-------------------|
| 1 | **Temeller** | `@MapTo`, `toXResult(): Result<X>` sınırı, `@MapFrom`, tek kaynaktan çok hedef |
| 2 | **Alanlar** | `@FieldMap` ile yeniden adlandırma, `@IgnoreMap`, `@IgnoreDefaultValue`, çağıranın sağladığı parametreler |
| 3 | **Null ve default'lar** | fallback ladder, production'da `Result` kullanım kalıpları |
| 4 | **Converter'lar** | otomatik keşif, kendi converter'ınız, `@ConvertWith(use, onFail)`, sanctioned null, parametreli converter'lar, `@UnsupportedDirection` |
| 5 | **Koleksiyonlar** | eleman ladder'ı, Set/Map semantiği, elemanlarda `OnFail.Throw`/`Skip`, `@CollectionWrapper` |
| 6 | **İç içe nesneler** | alt mapper'lar, derin hata yolları, hasar yarıçapını sınırlama |
| 7 | **Enum'lar** | `MappableEnum`, bilinmeyen wire değerleri |
| 8 | **Doğrulama** | alana bağlı `@Validate`, validator kütüphanesi, kendi validator'larınız |
| 9 | **Gözlemlenebilirlik** | `MappingListener`, degradation sink, "debug'da çök, prod'da gözle" |
| 10 | **Elle yazılmış mapper'lar** | tek başına `kmapper-core` — üretilen kodun kullandığı seam'lerin aynısı |

Her örnek dosyası beklenen çıktısını yorumlarında belgeler ve galeri normal proje build'inde
derlenir — örnekler kütüphanenin API'sinden sessizce kopamaz.

> Sıradaki: **[@MapTo ve @MapFrom →](../temel-kullanim/mapto-mapfrom.md)**
