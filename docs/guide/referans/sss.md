# Sık Sorulan Sorular

## KMapper çalışma zamanında reflection kullanıyor mu?

Hayır. KMapper tamamen derleme zamanında çalışır. KSP processor anotasyonları analiz eder ve düz Kotlin extension fonksiyonları üretir. Üretilen kodda `KClass`, `::class.members`, `getDeclaredField` gibi reflection API'leri kullanılmaz. Bu nedenle iOS/Kotlin Native dahil tüm KMP hedeflerinde herhangi bir kısıtlama olmadan çalışır.

## iOS ve Kotlin/Native'de çalışıyor mu?

Evet. `core` artifact KMP'dir; üretilen extension fonksiyonlar standart Kotlin'dir ve tüm hedeflerde (Android, iOS/Native, JVM) derlenir. `processor` JVM-only'dir ama yalnızca build araçları tarafından çalıştırılır; dağıtılan koda dahil değildir.

## Neden ordinal veya name kullanılmıyor?

`ordinal`, sabitlerin sırasına bağlıdır. Enum sırası değiştiğinde ya da araya yeni bir sabit eklendiğinde `ordinal` sessizce yanlış değere eşlenir — derleme hatası ya da test olmadan fark etmek imkânsızdır. `name` ise yeniden adlandırmaya karşı aynı şekilde kırılgandır. `wireValue` sabite doğrudan bağlıdır; enum'u yeniden sıralayabilir, sabit adını değiştirebilirsiniz — mapping değişmez.

## Kaynak ve hedef alanın adı farklıysa ne yapmalıyım?

`@FieldMap(fieldName = "hedefAlanAdi")` kullanın:

```kotlin
@MapTo(UserDomain::class)
data class UserRemote(
    @FieldMap(fieldName = "id")
    val userId: String,
) : RemoteModel
```

Birden fazla hedef varsa `targetClass` parametresini ekleyin:

```kotlin
@FieldMap(fieldName = "id",     targetClass = UserDomain::class)
@FieldMap(fieldName = "userId", targetClass = UserUiModel::class)
val userId: String,
```

Bkz. [Alan Eşleştirme](../temel-kullanim/alan-eslestirme.md).

## Özel bir converter nasıl eklenir?

`MapTypeConverter<S, T>` sınıfından türetin ve `@KMapperConfig`'e ekleyin:

```kotlin
object IsoStringToInstantConverter : MapTypeConverter<String, Instant>(String::class, Instant::class) {
    override fun convertToNonNull(value: String): Instant =
        Instant.parse(value)
    override fun convertFromNonNull(value: Instant): String =
        value.toString()
}

@KMapperConfig(converters = [IsoStringToInstantConverter::class])
object AppMapperConfig
```

Yalnızca belirli bir alan için farklı bir dönüşüm gerekiyorsa `@UseMapTypeConverter` ile alan bazlı override yapın. Bkz. [@KMapperConfig ve @UseMapTypeConverter](../tip-donusumu/kmapperconfig.md).

## Çok modüllü projede her modül için ayrı @KMapperConfig mi gerekiyor?

Evet. KSP her modülü bağımsız olarak derler; bir modülün `@KMapperConfig`'i başka bir modülün processor'ı tarafından görülmez. Mapper üreten her modül kendi `@KMapperConfig` nesnesini tanımlamalıdır.

Bkz. [Çok Modüllü Projeler](../ileri/cok-modullu.md).

## Marker interface (RemoteModel, DomainModel vb.) zorunlu mu?

Hayır. `@MapTo` ya da `@MapFrom` anotasyonunu herhangi bir sınıfa uygulayabilirsiniz; belirli bir marker interface implement etmek zorunda değilsiniz. Marker interface'ler, büyük projelerde sınıfları katmanlarına göre sınıflandırmak için yararlı bir konvansiyondur; KMapper tarafından zorunlu tutulmaz.

## @Ignore ile bir alanı atlarsam hedef constructor'da ne olur?

Hedef sınıfın constructor'ında karşılık gelen alan ya bulunmamalı ya da default değeri olmalıdır. Default değer yoksa ve alan hedefte varsa derleme hatası alırsınız — üretilen kod o alanı atlayacağından hedef constructor çağrısı eksik argümanla başarısız olur.

```kotlin
data class UserDomain(
    val id: String,
    val role: String = "USER",  // default değer var → @Ignore ile id eşlenebilir
)
```

## convertOrFail nedir?

`convertOrFail` `core` artifact'ında tanımlı bir yardımcı fonksiyondur. Converter çağrısını `try/catch` ile sarar ve converter exception fırlatırsa `MappingException.TypeConversionFailed`'e dönüştürür. Doğrudan kullanmanıza gerek yoktur; üretilen kod tarafından kullanılır. Bkz. [Hata Yönetimi](../hata-yonetimi/mapping-exception.md).
