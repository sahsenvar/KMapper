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

## Alternatif: kotlinx.serialization `@Serializable` enum'ları

Enum'unuz zaten kotlinx.serialization `@Serializable` enum'uysa, `MappableEnum` implement edip
wire değerlerini tekrar yazmanıza gerek yok — KMapper bunları doğrudan annotation'lardan okur:

```kotlin
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class OrderStatus {
    @SerialName("pending") PENDING,
    SHIPPED, // @SerialName yok → wire değeri entry adı, "SHIPPED"
}
```

`String ↔ OrderStatus` alan çifti iki yönde de tıpkı `MappableEnum`'daki gibi eşlenir. Her
entry'nin wire değeri **`@SerialName` argümanı, yoksa entry'nin kendi adıdır** — enum'un JSON'da
(de)serialize olma biçiminin birebir aynısı, yani mapping ve serileştirme yapısı gereği uyumlu.

Ayrıntılar:

- **`MappableEnum` kazanır.** Enum'da ikisi de varsa `MappableEnum.wireValue` yolu kullanılır
  (`@SerialName` yok sayılır). Hangisi uygunsa onu kullanın; ikisine birden asla gerek yok.
- **Yalnızca String wire.** `@Serializable` enum'lar string olarak serialize olur, dolayısıyla
  karşı taraf `String` olmalı (String olmayan taraf bilinen `enum wire type mismatch` derleme
  hatası). `Int`-kodlu enum için `MappableEnum<Int>` kullanın.
- **Bilinmeyen değerler** yukarıdaki gibi davranır — ladder'a biner (non-null hedefte sert,
  nullable hedefte null'a emilir + raporlanır).
- **Çalışma zamanı bağımlılığı yok.** KMapper annotation'ları derleme zamanında okur ve düz bir
  `when` üretir — `kmapper-core`/`kmapper-compiler` kotlinx-serialization'a **bağımlı değildir**.
- **Farklı değerler şart.** İki entry aynı wire değerine çözümlenirse derleme hatasıdır (decode
  belirsiz olurdu).

> Sıradaki: **[Result Sınırı ve MappingException →](../hata-yonetimi/mapping-exception.md)**
