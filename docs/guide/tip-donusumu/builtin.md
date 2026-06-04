# Built-in Converter'lar

kmap, en yaygın tip dönüşümlerini kutudan çıkar çıkmaz destekler. Bu converter'lar `com.sahsenvar.kmapper.converter.builtin` paketindedir ve `@KMapperConfig`'e eklemenize gerek kalmadan processor tarafından otomatik tanınır.

---

## Built-in Converter Tablosu

| Converter | `S → T` (ileri) | `T → S` (ters) |
|-----------|-----------------|----------------|
| `StringIntConverter` | `String → Int` (`toInt()`) | `Int → String` (`toString()`) |
| `StringLongConverter` | `String → Long` (`toLong()`) | `Long → String` |
| `StringDoubleConverter` | `String → Double` (`toDouble()`) | `Double → String` |
| `StringFloatConverter` | `String → Float` (`toFloat()`) | `Float → String` |
| `StringBooleanConverter` | `String → Boolean` (`toBoolean()`) | `Boolean → String` |
| `IntLongConverter` | `Int → Long` | `Long → Int` ¹ |
| `StringInstantConverter` | `String → Instant` (ISO-8601) | `Instant → String` |
| `LongInstantConverter` | `Long → Instant` (epoch ms) | `Instant → Long` |

¹ Ters yön (`Long → Int`) değer `Int` aralığı dışındaysa `TypeConversionFailed` fırlatır.

`Instant` tipi `kotlinx.datetime.Instant`'tır (`kotlinx-datetime` bağımlılığı gerekir).

---

## Bilateral (İki Yönlü) Converter Kavramı

Her converter nesnesi **hem ileri hem ters yönü** tek sınıfta barındırır:

```kotlin
object StringIntConverter : MapTypeConverter<String, Int>(String::class, Int::class) {
    override fun convertToNonNull(value: String): Int = value.toInt()
    override fun convertFromNonNull(value: Int): String = value.toString()
}
```

Processor, hangi yönde dönüşüm gerektiğini analiz eder ve doğru metodu çağırır. `String → Int` eşleşmesi için `convertToNonNull`, `Int → String` için `convertFromNonNull` kullanılır.

---

## convertOrFail Sarmalayıcısı

Built-in converter'lar dahil tüm converter çağrıları üretilen kodda `convertOrFail` ile sarılır. Bu, ham platform istisnasının (örn. `NumberFormatException`) dışarı sızmasını engeller:

```kotlin
// Üretilen kod şu şekilde görünür:
count = convertOrFail("String", "Int") { StringIntConverter.convertToNonNull(count) }
```

> **Not:** `convertOrFail`'e geçilen tip adları üretilen kodda tam niteliktedir (fully-qualified) — örn. `"kotlin.String"`, `"kotlin.Int"`, `"kotlinx.datetime.Instant"`. Örneklerde kısa form kullanılmıştır.

Dönüşüm başarısız olursa `MappingException.TypeConversionFailed` fırlatılır; `cause` alanında orijinal istisna yer alır.

---

## Kullanım Örneği

```kotlin
@MapTo(ProductDomain::class)
data class ProductRemote(
    val id: String,
    val price: String,     // API'den string geldi, domain'de Double
    val stock: String,     // API'den string geldi, domain'de Int
)

data class ProductDomain(
    val id: String,
    val price: Double,
    val stock: Int,
)
```

`String → Double` ve `String → Int` built-in oldukları için ek kayıt gerekmez. Üretilen:

```kotlin
public fun ProductRemote.toProductDomain(): ProductDomain = ProductDomain(
    id    = id,
    price = convertOrFail("String", "Double") { StringDoubleConverter.convertToNonNull(price) },
    stock = convertOrFail("String", "Int")    { StringIntConverter.convertToNonNull(stock) },
)
```

---

## Kayıt Dışı Tip Çifti → Derleme Hatası

Built-in tabloda olmayan ve `@KMapperConfig`'e de eklenmeyen bir tip çifti kullanılırsa processor **derleme hatası** üretir:

```
no converter for MyCustomType -> MyTargetType; add it to @KMapperConfig(converters=[...]) or annotate the field with @UseMapTypeConverter
```

Kendi converter'ınızı yazmak için bkz. [Kendi Converter'ını Yazmak](ozel-converter.md).

---

Sonraki adım: [Kendi Converter'ını Yazmak](ozel-converter.md)
