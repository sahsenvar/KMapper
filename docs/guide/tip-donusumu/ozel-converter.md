# Kendi Converter'ını Yazmak

Built-in tabloda bulunmayan bir tip çiftini dönüştürmeniz gerektiğinde `MapTypeConverter<S, T>` soyut sınıfından kalıtım alarak kendi converter'ınızı yazarsınız.

---

## MapTypeConverter Arayüzü

```kotlin
abstract class MapTypeConverter<S : Any, T : Any>(
    val sourceType: KClass<S>,
    val targetType: KClass<T>,
) {
    abstract fun convertToNonNull(value: S): T
    abstract fun convertFromNonNull(value: T): S

    // Null-safe yardımcılar (override etmenize gerek yok):
    fun convertTo(value: S?): T? = value?.let { convertToNonNull(it) }
    fun convertFrom(value: T?): S? = value?.let { convertFromNonNull(it) }
}
```

Tek bir converter nesnesi **her iki yönü** de kapsar:

- `convertToNonNull(S): T` → `S → T` dönüşümü (ileri yön)
- `convertFromNonNull(T): S` → `T → S` dönüşümü (ters yön)
- `convertTo` / `convertFrom` → null-safe sarmalayıcılar, yeniden implement etmek zorunda değilsiniz

Processor hangi yönde dönüşüm gerektiğini analiz eder ve doğru metodu çağırır. İkisini tek seferde yazmanız yeterlidir.

---

## Örnek: UUID ↔ String

```kotlin
import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuidFrom
import com.sahsenvar.kmapper.converter.MapTypeConverter

object UuidStringConverter : MapTypeConverter<Uuid, String>(Uuid::class, String::class) {
    override fun convertToNonNull(value: Uuid): String = value.toString()
    override fun convertFromNonNull(value: String): Uuid = uuidFrom(value)
}
```

---

## Örnek: Enum Wire Değeri ↔ Domain Enum

```kotlin
enum class StatusRemote { ACTIVE, INACTIVE, UNKNOWN }
enum class StatusDomain { Active, Inactive }

object StatusConverter : MapTypeConverter<StatusRemote, StatusDomain>(
    StatusRemote::class, StatusDomain::class
) {
    override fun convertToNonNull(value: StatusRemote): StatusDomain = when (value) {
        StatusRemote.ACTIVE   -> StatusDomain.Active
        StatusRemote.INACTIVE -> StatusDomain.Inactive
        StatusRemote.UNKNOWN  -> throw IllegalArgumentException("Unknown status: $value")
    }

    override fun convertFromNonNull(value: StatusDomain): StatusRemote = when (value) {
        StatusDomain.Active   -> StatusRemote.ACTIVE
        StatusDomain.Inactive -> StatusRemote.INACTIVE
    }
}
```

> Enum eşleştirmesi için kmap'in `MappableEnum<W>` arayüzü de mevcuttur. Ayrıntılar için bkz. [MappableEnum](../enum/mappable-enum.md).

---

## Hata Yönetimi

`convertToNonNull` veya `convertFromNonNull` içinde fırlattığınız her istisna, üretilen kod tarafından `MappingException.TypeConversionFailed` içine sarılır (`convertOrFail` mekanizması):

```kotlin
// Üretilen sarma:
field = convertOrFail("Uuid", "String") { UuidStringConverter.convertToNonNull(rawId) }
```

Dönüşümde herhangi bir `Throwable` fırlatırsanız çağıran her zaman `MappingException.TypeConversionFailed` alır. `cause` alanında orijinal istisnanız yer alır.

---

## Converter'ı Kaydetmek

Yazdığınız converter'ı `@KMapperConfig` listesine eklemeniz gerekir; aksi halde processor onu göremez ve derleme hatası verir:

```kotlin
@KMapperConfig(converters = [UuidStringConverter::class, StatusConverter::class])
object AppMapperConfig
```

Belirli bir alan için global listeden farklı bir converter kullanmak istiyorsanız `@UseMapTypeConverter` uygulayabilirsiniz — bkz. [@KMapperConfig ve @UseMapTypeConverter](kmapperconfig.md).

---

Sonraki adım: [@KMapperConfig ve @UseMapTypeConverter](kmapperconfig.md)
