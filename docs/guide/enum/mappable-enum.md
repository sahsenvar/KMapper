# MappableEnum — Güvenli Enum Eşleştirme

`ordinal` ve `name` ile enum eşleştirmek sessiz bir tuzaktır. Sabitleri yeniden sıraladığınızda `ordinal` değişir; sabit adını yeniden adlandırdığınızda `name` değişir. Her iki durumda da derleme hatası almaz, test yazmazsanız runtime'da da hata almayabilirsiniz — yanlış değer sessizce eşlenir.

KMapper bu riski tamamen ortadan kaldırır: `ordinal` ya da `name` **asla** kullanılmaz.

## MappableEnum\<W\> Interface'i

`com.sahsenvar.kmapper` paketindeki bu interface, enum sabitlerini bir **wire değerine** bağlar:

```kotlin
interface MappableEnum<W : Any> {
    val wireValue: W
}
```

`W` tip parametresi wire tarafındaki alanın tipiyle eşleşmek zorundadır (`String` ya da `Int` en yaygın kullanımlardır).

### String Wire Değeri

```kotlin
enum class OrderStatus(override val wireValue: String) : MappableEnum<String> {
    PENDING("PENDING"),
    SHIPPED("in_transit"),   // sabit adı ile wire değeri farklı olabilir
    DELIVERED("DELIVERED"),
}
```

### Int Wire Değeri

```kotlin
enum class Priority(override val wireValue: Int) : MappableEnum<Int> {
    LOW(10),
    MEDIUM(20),
    HIGH(30),
}
```

## Üretilen Kod

Processor, `MappableEnum<W>` implement eden bir enum gördüğünde forward extension üretir:

```kotlin
// Forward: wire değeri → enum sabiti
fun String.toOrderStatus(): OrderStatus =
    OrderStatus.entries.firstOrNull { it.wireValue == this }
        ?: throw MappingException.UnknownEnumValue("OrderStatus", this)
```

**Ters yön (enum → wire):** Processor ayrı bir `toWire()` fonksiyonu üretmez. Enum'dan wire değerine dönüşüm, çağrı noktasında doğrudan `status.wireValue` (nullable alan için `status?.wireValue`) olarak satır içi yayılır.

## Bilinmeyen Wire Değeri

Wire kaynağı enum tanımında bulunmayan bir değer gönderirse `MappingException.UnknownEnumValue` fırlatılır. Bu exception'ı feature katmanında kendi domain hatasına dönüştürün:

```kotlin
fun Throwable.toOrderError(): OrderError = when (this) {
    is MappingException.UnknownEnumValue -> OrderError.InvalidStatus(value.toString())
    // diğer dallar...
    else -> OrderError.Unknown(message, this)
}
```

Ayrıntılar için bkz. [Hata Yönetimi](../hata-yonetimi/mapping-exception.md).

## Zorunluluk — Compile Error

Bir alan enum türündeyse ve o enum `MappableEnum` implement etmiyorsa, `@UseMapTypeConverter` ile de override edilmiyorsa processor **derleme hatası** verir:

```
enum 'PaymentStatus' must implement MappableEnum<...> or use @UseMapTypeConverter
```

Bu garanti, enum'ları haritasız bırakmayı imkânsız kılar.

## Üçüncü Taraf / Değiştiremeyeceğiniz Enum'lar — Escape Hatch

Kendi kontrolünüzde olmayan bir enum (bağımlılıktan gelen) için `MappableEnum` ekleyemezsiniz. Bu durumda `@UseMapTypeConverter` ile alan bazlı bir converter tanımlayın:

```kotlin
// Converter: dış kütüphanedeki ThirdPartyStatus → kendi StatusDomain'iniz
object ThirdPartyStatusConverter : MapTypeConverter<ThirdPartyStatus, StatusDomain>(ThirdPartyStatus::class, StatusDomain::class) {
    override fun convertToNonNull(value: ThirdPartyStatus): StatusDomain = when (value) {
        ThirdPartyStatus.ACTIVE   -> StatusDomain.ACTIVE
        ThirdPartyStatus.INACTIVE -> StatusDomain.INACTIVE
    }
    override fun convertFromNonNull(value: StatusDomain): ThirdPartyStatus = when (value) {
        StatusDomain.ACTIVE   -> ThirdPartyStatus.ACTIVE
        StatusDomain.INACTIVE -> ThirdPartyStatus.INACTIVE
    }
}

@MapTo(OrderDomain::class)
data class OrderRemote(
    @UseMapTypeConverter(ThirdPartyStatusConverter::class)
    val status: ThirdPartyStatus,
) : RemoteModel
```

## Nullable Enum Alanları

Nullable enum alanları null geçişli üretim alır — forward ve reverse her ikisinde de null korunur:

```kotlin
@MapTo(OrderDomain::class)
data class OrderRemote(
    val status: String?,   // nullable wire değeri
) : RemoteModel

data class OrderDomain(val status: OrderStatus?)

// üretilen:
fun OrderRemote.toOrderDomain(): OrderDomain = OrderDomain(
    status = status?.toOrderStatus(),
)
```

## Aynı wireValue Çakışması — Uyarı

İki sabit aynı `wireValue` değerini paylaşıyorsa forward yön (`firstOrNull`) listedeki ilkini seçer; ikincisi **sessizce erişilemez** hale gelir:

```kotlin
// YANLIŞ — çakışma
enum class Color(override val wireValue: String) : MappableEnum<String> {
    RED("red"),
    CRIMSON("red"),   // aynı wire değeri — CRIMSON asla seçilmez
}
```

KSP, constructor argümanlarının runtime değerlerini güvenilir okuyamadığından bu durum derleme zamanında yakalanamaz. Çakışma kontrolü (`verifyEnums`) yol haritasında yer almaktadır; henüz implement edilmemiştir. Enum tanımlarınızda her sabite benzersiz `wireValue` verdiğinizden emin olun.

---

Sonraki adım: [MappingException — Hata Yönetimi](../hata-yonetimi/mapping-exception.md)
