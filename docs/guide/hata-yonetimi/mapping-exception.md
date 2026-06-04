# Hata Yönetimi — MappingException

kmap'in ürettiği tüm kodlar tek bir exception hiyerarşisi kullanır: `com.sahsenvar.kmapper.MappingException`. Feature katmanınız bu exception'ları kendi domain hatalarına dönüştürür — kütüphane tiplerine doğrudan bağımlı kalmazsınız.

## Hiyerarşi

```kotlin
sealed class MappingException(message: String, cause: Throwable? = null)
    : RuntimeException(message, cause) {

    class RequiredFieldMissing(val field: String)
        : MappingException("Required field missing: $field")

    class TypeConversionFailed(val from: String, val to: String, cause: Throwable)
        : MappingException("Cannot convert $from -> $to", cause)

    class UnknownEnumValue(val enum: String, val value: Any)
        : MappingException("Unknown wire value '$value' for enum $enum")
}
```

### RequiredFieldMissing

Nullable bir kaynak alan, non-null bir hedef alana eşleniyorsa ve değer `null` ise üretilen null-check fırlatır:

```kotlin
// kaynak: String?  →  hedef: String (non-null)
id = id ?: throw MappingException.RequiredFieldMissing("id")
```

`@MapDefaultValue` ile varsayılan değer verirseniz bu exception yerine default expression kullanılır. Bkz. [Null-Safety](../temel-kullanim/null-safety.md).

### TypeConversionFailed

Converter (`MapTypeConverter`) çalışma zamanında bir exception fırlatırsa, üretilen kod onu `TypeConversionFailed`'e sarar. Ham platform exception sızmaz:

```kotlin
// üretilen (kavramsal):
startsAt = convertOrFail("String", "Instant") {
    IsoStringToInstantConverter.convertToNonNull(startsAt)
}
// "abc" gibi geçersiz bir değer gelirse:
// TypeConversionFailed(from="String", to="Instant", cause=DateTimeParseException)
```

`cause` alanı orijinal exception'ı taşır; loglama için kullanabilirsiniz.

### UnknownEnumValue

Wire kaynağı, enum tanımında bulunmayan bir değer gönderdiğinde `MappableEnum` forward yolu fırlatır:

```kotlin
// wire'dan "UNKNOWN_STATUS" geldi, ama OrderStatus'te bu wireValue yok:
MappingException.UnknownEnumValue(enum = "OrderStatus", value = "UNKNOWN_STATUS")
```

Bkz. [MappableEnum](../enum/mappable-enum.md).

## Domain Hatasına Dönüştürme

Feature katmanınızda bir `Throwable.toX()` extension'ı yazarak `MappingException`'ı kendi hiyerarşinize çevirin. `when` bloğu ile sealed hiyerarşinin tüm dalları işlenir:

```kotlin
// feature/order/data/mapper/OrderMapper.kt
fun Throwable.toOrderError(): OrderError = when (this) {
    is MappingException.RequiredFieldMissing ->
        OrderError.DataCorruption("Zorunlu alan eksik: $field")
    is MappingException.TypeConversionFailed ->
        OrderError.DataCorruption("Dönüştürme başarısız: $from → $to", cause)
    is MappingException.UnknownEnumValue ->
        OrderError.InvalidStatus("Bilinmeyen durum değeri: $value")
    is RemoteError.Timeout ->
        OrderError.NetworkTimeout
    else ->
        OrderError.Unknown(message, this)
}
```

Repository katmanınızdaki `catch` bloğunda çağırın:

```kotlin
override fun getOrder(id: String): Flow<Order> = flow {
    val dto = remoteDataSource.fetchOrder(id)
    emit(dto.toOrder())           // toOrder() içinde üretilen mapper çalışır
}.catch { exception ->
    throw exception.toOrderError()
}
```

## Derleme Zamanı Güvenlik Garantileri

`MappingException`'ların büyük çoğunluğu **derleme zamanında** önlenir:

| Durum | Davranış |
|---|---|
| Gereken converter ne global listede ne field'da | **Compile error** |
| Enum `MappableEnum` implement etmiyor, `@UseMapTypeConverter` da yok | **Compile error** |
| Koşulsuz mapping döngüsü (A→B→A, hepsi non-null) | **Compile error** |
| Nullable → non-null, `@MapDefaultValue` yok | Üretilen `RequiredFieldMissing` (runtime) |
| Bilinmeyen wire enum değeri | Üretilen `UnknownEnumValue` (runtime) |
| Converter runtime exception fırlatırsa | Üretilen `TypeConversionFailed` (runtime) |

### Koşulsuz Döngü — Compile Error

Processor `@MapTo`/`@MapFrom` tip grafiğini analiz eder. Halkadaki tüm kenarlar non-null ve non-collection alandan geçiyorsa nesne inşa edilemez; derleme hatası verilir:

```kotlin
// HATA — garantili sonsuz döngü
@MapTo(BDomain::class) data class A(val b: B)
@MapTo(ADomain::class) data class B(val a: A)
// e: [kmap] Mapping cycle A -> B -> A (guaranteed infinite).
//    Break it with @Ignore or @UseMapTypeConverter.
```

Döngü en az bir nullable ya da collection alandan geçiyorsa (ağaç, opsiyonel geri-referans) izin verilir:

```kotlin
// OK — koşullu; nullable parent
@MapTo(CategoryDomain::class)
data class Category(val parent: Category?)

// OK — koşullu; collection
@MapTo(NodeDomain::class)
data class Node(val children: List<Node>)
```

---

Sonraki adım: [Gözlemleme — MappingListener](../gozlemleme/listener.md)
