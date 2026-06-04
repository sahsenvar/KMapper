# Sınırlamalar ve Yol Haritası

## Mevcut Sınırlamalar

### Yalnızca Constructor `val` Alanları

kmap yalnızca primary constructor'daki `val` parametrelerini analiz eder. `var` property'ler, `init` bloğunda atanan alanlar ve constructor dışında tanımlanan property'ler eşleştirilmez.

```kotlin
data class OrderRemote(
    val id: String,       // ✓ eşlenir
    val total: Double,    // ✓ eşlenir
) : RemoteModel {
    var cached: Boolean = false  // ✗ eşlenmez, processor görmez
}
```

Bu, constructor-only analizi kasıtlı olarak sınırlı tutan bir tasarım kararıdır. Mutable property eşleştirme sonraki bir round'da değerlendirilebilir.

### Enum'lar MappableEnum Implement Etmek Zorundadır

Mapping hedefinde ya da kaynağında bir enum türü kullanılıyorsa o enum `MappableEnum<W>` implement etmelidir. Üçüncü taraf enum'lar için `@UseMapTypeConverter` escape hatch'i mevcuttur; bkz. [MappableEnum](../enum/mappable-enum.md).

### sealed class Mapping Yok

`sealed class` ve `sealed interface` hiyerarşileri henüz desteklenmemektedir. Her alt-sınıfı ayrı ayrı `@MapTo` ile işaretleyebilirsiniz; ancak sealed dispatch kodu otomatik üretilmez.

### Map\<K, V\> Koleksiyonu Yok

`Map<K, V>` alanları doğrudan desteklenmez. Geçici çözüm olarak alan bazlı `@UseMapTypeConverter` ile dönüşümü kendiniz yazabilirsiniz.

## Yol Haritası

Aşağıdaki özellikler gelecekteki bir round'a planlanmıştır; **henüz implement edilmemiştir**.

### sealed class Mapping

`sealed class` / `sealed interface` hiyerarşilerini tip güvenli biçimde eşleştirme. Her alt-sınıf ayrı `@MapTo`'ya ihtiyaç duymadan otomatik dispatch üretimi.

### Map\<K, V\> Koleksiyon Mapping

`Map` tipindeki alanların eleman bazlı dönüştürülmesi.

### converters-arrow — Nel Desteği

`converters-arrow` artifact'ının gerçek dolumu: `List<T>` → `NonEmptyList<T>` converter'ları. Bu noktada `EmptyCollection` hata tipi de geri gelebilir.

### verifyEnums() — Enum Çakışma Kontrolü

Aynı `wireValue` değerini paylaşan iki enum sabitini tespit eden bir yardımcı. KSP constructor argümanlarının runtime değerlerini güvenilir okuyamadığından bu kontrol derleme zamanında yapılamaz; bunun yerine debug/test başlangıcında çalıştırılan opsiyonel bir doğrulama fonksiyonu olarak planlanmaktadır:

```kotlin
// HENÜZ YOK — yol haritasında
KMapper.verifyEnums()  // tüm kayıtlı enum'larda wireValue benzersizliğini kontrol eder
```

### kmapper.verbose KSP Seçeneği

Processor'ın hangi mapper'ları ve eşleşmeleri ürettiğini build zamanında `logger.info`'a yazan bir KSP option:

```kotlin
// HENÜZ YOK — yol haritasında
ksp {
    arg("kmapper.verbose", "true")
}
```

---

Sonraki adım: [Sık Sorulan Sorular](./sss.md)
