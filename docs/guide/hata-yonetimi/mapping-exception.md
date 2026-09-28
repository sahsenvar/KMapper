# Hata Yönetimi ve MappingException

KMapper'ın hata sözleşmesi tek cümlede: **çalışma zamanında başarısız olabilecek her şey, düz
`toX()`'ten yol taşıyan, tipli bir `MappingException` olarak fırlar; daha erken bilinebilecek
her şey ise build'i düşürür.** Hatanın fırlamak yerine bir değer olarak teslim edilmesini mi
istiyorsunuz? Bu, üzerine eklenen isteğe bağlı bir katman — bkz.
[Dönüş Sarmalayıcıları](../temel-kullanim/donus-sarmalayicilari.md).

## Düz mapper fırlatır

Üretilen her mapper, önce ve her zaman, `fun Source.toX(): X`'tir:

```kotlin
val user: User = response.toUser() // sert hatada MappingException fırlatır
```

`Result` yok, `getOrThrow()` sıçraması yok — sert hata, Kotlin'de başarısız olabilen herhangi
bir fonksiyon gibi bir exception'dır. Anlamlı olduğu yerde yakalayın, ya da yukarı taşınmasına
izin verin:

```kotlin
val user = try {
    response.toUser()
} catch (exception: MappingException) {
    log(exception)
    User.GUEST
}
```

## Hatayı değer olarak mı istiyorsunuz? Bir sarmalayıcı ekleyin

Eski `Result<X>` dönen şekli — ya da bir `Flow<X>`'i, ya da tamamen başka bir şeyi — istiyorsanız
bunu mapping bazında ya da modül genelinde beyan edin; extension, `toX()`'in **yerine değil,
yanına** üretilir:

```kotlin
@MapTo(User::class, wrapper = KMapperWrapper.KtResult::class)
data class UserResponse(/* … */)

// ikisi de üretilir:
// fun UserResponse.toUser(): User
// fun UserResponse.toUserResult(): Result<User>

val result: Result<User> = response.toUserResult()
result.fold(
    onSuccess = { render(it) },
    onFailure = { e -> showError(); log(e) },
)
```

Built-in sarmalayıcıların (`None`, `KtResult`, `Flow`) tam kapsamı, modül geneli Gradle/KSP
ayarı ve kendi sarmalayıcınızı yazmak için: [Dönüş Sarmalayıcıları](../temel-kullanim/donus-sarmalayicilari.md).

Hiçbir sarmalayıcı gerektirmeyen pratik bir kalıp: debug build'lerinde ve testlerde `toX()`'in
fırlamasına izin verin (bozuk wire verisi gece build'ini gürültülü şekilde çökertir), ve yalnızca
production telemetrisiyle konuşan çağrı noktasını `runCatching { … }` ile ya da `KtResult` ile
sarmalanmış bir mapping ile sarın.

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
