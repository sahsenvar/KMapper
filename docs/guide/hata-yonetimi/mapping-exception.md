# Result Sınırı ve MappingException

KMapper'ın hata sözleşmesi tek cümlede: **çalışma zamanında başarısız olabilecek her şey,
yol taşıyan bir `MappingException` içeren `Result` hatası olarak gelir; daha erken
bilinebilecek her şey ise build'i düşürür.**

## Result sınırı

Üretilen her mapper `Result<T>` döner:

```kotlin
val result: Result<User> = response.toUserResult()
```

Hata politikasını *çağrı noktasında*, stdlib araçlarıyla seçersiniz:

```kotlin
// bozuk-veride-çök (testler, debug build'leri, gerçekten zorunlu veri):
val user = result.getOrThrow()

// geri düşüş:
val user = result.getOrElse { User.GUEST }

// dallanma:
result.fold(
    onSuccess = { render(it) },
    onFailure = { e -> showError(); log(e) },
)
```

Pratik bir kalıp: debug'da `getOrThrow()`, release'te `getOrElse` + telemetri — bozuk wire
verisi gece build'ini çökertir, kullanıcıyı değil.

## Exception taksonomisi

Bütün hatalar sealed `MappingException`'ın alt tipleridir; her biri mapping kökünden **alan
yolu** taşır (`customer.address.zipCode`, `items[3].price`):

| Tip | Anlamı |
|-----|--------|
| `RequiredFieldMissing` | eksik değer, hedefte kaçış yoktu ([ladder](../temel-kullanim/null-safety.md) tabanı) |
| `TypeConversionFailed` | converter fırlattı — orijinal nedeni taşır |
| `UnknownEnumValue` | wire değeri hiçbir [`MappableEnum`](../enum/mappable-enum.md) sabitine uymadı |
| `EmptyCollection` | boş-olamaz bir kap ([NonEmptyList](../tip-donusumu/arrow.md)) boş wire listesi aldı |
| `ValidationFailed` | bir [`@Validate`](../dogrulama/validate.md) kuralı değeri reddetti |
| `UnsupportedConversion` | reddedilmiş bir [`@UnsupportedDirection`](../tip-donusumu/ozel-converter.md) çalışma zamanında çağrıldı (elle yazılmış kod yolları; üretilen kod derlemede reddeder) |

Tip sealed olduğundan hata türleri üzerinde exhaustive bir `when` derlenir — ve gelecekteki
bir sürüm tür eklerse uyarı verir.

Yollar derleme zamanı string literal'i olarak üretilir: **R8/ProGuard'dan** aynen geçer.

## Çalışma zamanına hiç ulaşmayanlar

Bunlar tasarım gereği *build hatasıdır*:

- **`MissingConverter`** — bir alan çiftinin hiçbir yerde converter'ı yok
  (`Money -> String has no registered converter. Add one via @ConvertWith / @KMapperConfig…`)
- **`UnsupportedConversion`** — ihtiyaç duyulan yön beyanla reddedilmiş
  (`Long -> Int conversion is unsupported! …` yazarın gerekçesiyle)
- yapısal sorunlar: eşlenemeyen alan, wrapper imza ihlali, skalerde `OnFail.Skip`,
  yalnızca-`OrNull` override'ı, …

Derleme mesajları alanı, çifti ve çözümü söyler — sonradan akla gelen değil, API yüzeyinin
parçasıdırlar.

## Sink ile ilişkisi

`MappingException` **sert hata** kanalıdır. Beyan edilmiş bir kaçışın *emdiği* hatalar asla
fırlamaz — onlar [degradation sink](../gozlemleme/listener.md)'e gider. Aynı taksonomi
(`AbsorbedConversionError`, fırlayacak olan exception'ı neden olarak taşır), farklı şiddet.

> Sıradaki: **[Gözlemlenebilirlik →](../gozlemleme/listener.md)**
