# MappableEnum

Enum'lar mapping'e **wire değerlerini** açıkça beyan ederek katılır. KMapper bir enum'u asla
`name` ya da `ordinal` ile eşlemez — bir sabiti yeniden adlandırmak ya da enum'u yeniden
sıralamak bu yüzden veriyi asla sessizce bozamaz.

## Tanımlama

```kotlin
import com.sahsenvar.kmapper.MappableEnum

enum class OrderStatus(override val wireValue: String) : MappableEnum<String> {
    PENDING("pending"),
    SHIPPED("shipped"),
    DELIVERED("delivered"),
}
```

`MappableEnum<W>` wire tipi üzerinde generic'tir — string tipik olandır ama `Int` kodlar da
aynı şekilde çalışır.

## Eşleme

`String ↔ OrderStatus` alan çifti artık iki yönde de ek kurulum olmadan dönüşür — formatlama
`wireValue` yazar, parse onunla eşleştirir:

```kotlin
@MapTo(TrackedOrder::class)
data class OrderEvent(val id: Long, val status: String)

data class TrackedOrder(val id: Long, val status: OrderStatus)
```

## Bilinmeyen wire değerleri: politikayı tiple seçersiniz

Bilinmeyen değer (`"teleported"`) bozukluktur ve her bozukluk gibi
[ladder'a](../temel-kullanim/null-safety.md) biner:

```kotlin
data class TrackedOrder(val status: OrderStatus)   // katı: bilinmeyen -> UnknownEnumValue hatası
data class OrderPreview(val status: OrderStatus?)  // hoşgörülü: bilinmeyen -> null + sink raporu
```

```
katı düşer       -> Unknown wire value 'teleported' for enum OrderStatus at status
preview emer     -> OrderPreview(id=2, status=null)   (+ sink'e AbsorbedConversionError)
```

Nullable varyant standart **ileri uyumluluk kalıbıdır**: sunucu siz yayınlamadan yeni
durumlar ekleyebilir; UI'nız "bilinmeyen" gösterir, telemetriniz kaç kez olduğunu sayar.

Koleksiyon içindeki enum elemanları da eleman başına aynı muameleyi görür — bkz.
[Koleksiyonlar](../temel-kullanim/koleksiyonlar.md).

> Sıradaki: **[Result Sınırı ve MappingException →](../hata-yonetimi/mapping-exception.md)**
