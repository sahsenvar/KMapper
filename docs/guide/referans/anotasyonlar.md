# Annotation Referansı

Bütün annotation'lar `com.sahsenvar.kmapper.annotations` paketinde (`kmapper-annotations`
artifact'i).

## Mapping tanımı

| Annotation | Hedef | Amaç |
|------------|-------|------|
| `@MapTo(target)` | sınıf (tekrarlanabilir) | `Source.toTargetResult()` üret — kaynakta tanımlanır |
| `@MapFrom(source)` | sınıf (tekrarlanabilir) | aynı üretim, hedefte tanımlanır |

→ [@MapTo ve @MapFrom](../temel-kullanim/mapto-mapfrom.md)

## Alan direktifleri

| Annotation | Hedef | Amaç |
|------------|-------|------|
| `@FieldMap(fieldName, targetClass)` | property (tekrarlanabilir) | farklı adlı hedef alanla eşle; istenirse tek hedefe daralt |
| `@IgnoreMap` | property | alanı otomatik eşlemeden çıkar; hedef yuva default'a düşer ya da çağıran parametresi olur |
| `@IgnoreDefaultValue` | property | constructor default'u yalnızca kurma kolaylığıdır — eksiklik `RequiredFieldMissing` olur |

→ [Alan Eşleme](../temel-kullanim/alan-eslestirme.md)

Yerleşim kuralı: alan direktifleri **üretilen yönün kaynak alanından** okunur
([ayrıntı](../tip-donusumu/convert-with.md)).

## Dönüşüm kontrolü

| Annotation | Hedef | Amaç |
|------------|-------|------|
| `@ConvertWith(use, onFail)` | property | alan bazlı converter override'ı ve/veya hata politikası |
| `@ConvertTo(target, use, onFail)` | property (tekrarlanabilir) | tek mapping yönüne daraltılmış `@ConvertWith` |
| `@ConvertFrom(source, use, onFail)` | property (tekrarlanabilir) | ters yönde daraltma |
| `OnFail` (enum) | — | `Auto` (ladder), `Throw` (asla emme), `Skip` (koleksiyonları sıkıştır) |

→ [@ConvertWith ve OnFail](../tip-donusumu/convert-with.md)

## Kayıt

| Annotation | Hedef | Amaç |
|------------|-------|------|
| `@KMapperConfig(converters, wrappers)` | object | modül geneli converter/wrapper kaydı; tip çiftiyle keşif |
| `@CollectionWrapper(forType)` | object | özel kap tipi için `wrap`/`unwrap` çifti tanımla |

→ [@KMapperConfig](../tip-donusumu/kmapperconfig.md),
[Collection wrapper'ları](../tip-donusumu/ozel-converter.md)

## Doğrulama

| Annotation | Hedef | Amaç |
|------------|-------|------|
| `@Validate(vararg validators)` | property | alana bağlı değişmezler; kaynak tarafında önce / hedef tarafında sonra çalışır |

→ [@Validate](../dogrulama/validate.md)

## Converter yazarlığı (`kmapper-core`'da)

| Annotation | Hedef | Amaç |
|------------|-------|------|
| `@UnsupportedDirection(reason)` | fonksiyon (`convertTo`/`convertFrom`) | yönü bilerek desteklenmiyor ilan et; gerekçe derleme hatasında görünür |

→ [Bir yönü reddetmek](../tip-donusumu/ozel-converter.md)

## 2.0'da kaldırılanlar

`@Ignore` → `@IgnoreMap` · `@MapDefaultValue` → constructor default'ları ·
`@UseMapTypeConverter` → `@ConvertWith` · `@ValidateFrom`/`@ValidateTo` → `@Validate`.
Eksiksiz harita: [1.x'ten Geçiş](gecis-1x.md).
