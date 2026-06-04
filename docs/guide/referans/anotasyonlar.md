# Anotasyon Referansı

kmap'in tüm anotasyonları `com.sahsenvar.kmapper.annotations` paketindedir. Hepsi `SOURCE` retention'a sahiptir — yayınlanan binary'e dahil edilmezler; yalnızca KSP derleme adımında tüketilir. `@CollectionWrapper` istisnai olarak `BINARY` retention kullanır (dependency artifact'larından tür+anotasyon çözümlemesi için).

## Özet Tablo

| Anotasyon | Hedef | Parametreler | Açıklama |
|---|---|---|---|
| `@MapTo` | Sınıf | `target: KClass<*>` | Bu sınıftan hedef sınıfa `toX()` extension üretir. `@Repeatable` — birden fazla hedef için tekrar kullanılabilir. |
| `@MapFrom` | Sınıf | `source: KClass<*>` | Kaynak sınıf yerine hedef sınıfa konur; ters yön mapping üretir. |
| `@FieldMap` | Property | `fieldName: String`, `targetClass: KClass<*> = Nothing::class` | Alan adını hedefte farklı bir isimle eşler. `targetClass` parametresi `@Repeatable` kullanımında hangi hedef için geçerli olduğunu belirtir. |
| `@MapDefaultValue` | Property | `expression: String` | Nullable kaynak alan `null` geldiğinde kullanılacak Kotlin expression. Üretilen koda literal olarak eklenir — geçerli Kotlin olması zorunludur. |
| `@Ignore` | Property | — | Bu alanı mapping dışında bırakır. Hedef constructor'da karşılık gelen alan bulunmamalı ya da default değeri olmalıdır. |
| `@UseMapTypeConverter` | Property | `converter: KClass<out MapTypeConverter<*, *>>` | Bu alan için global `@KMapperConfig` listesindeki converter'ı ezer. `@KMapperConfig`'e eklenmek zorunda değildir. |
| `@KMapperConfig` | Nesne/Sınıf | `converters: Array<KClass<*>> = []`, `wrappers: Array<KClass<*>> = []` | Bu modülde kullanılacak global converter ve wrapper listesini tanımlar. Processor `@KMapperConfig`'i modül içinde `getSymbolsWithAnnotation` ile bulur. |
| `@CollectionWrapper` | Sınıf (object) | `forType: KClass<*>` | `BINARY` retention. Bir `object`'i koleksiyon wrapper'ı olarak işaretler; `fun <T> wrap(items: List<T>): WrappedCollection<T>` metodunu barındırır. Tüketici modül bu wrapper'ı `@KMapperConfig.wrappers` listesinde açıkça belirtir. |

## @MapTo

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class MapTo(val target: KClass<*>)
```

Aynı kaynaktan birden fazla hedefe mapping:

```kotlin
@MapTo(UserDomain::class)
@MapTo(UserUiModel::class)
data class UserRemote(val id: String, val name: String) : RemoteModel
```

Bkz. [@MapTo ve @MapFrom](../temel-kullanim/mapto-mapfrom.md).

## @MapFrom

Hedef sınıfa konur; kaynak sınıfa `@MapTo` ekleyemediğiniz durumlarda kullanışlıdır:

```kotlin
@MapFrom(UserRemote::class)
data class UserDomain(val id: String, val name: String) : DomainModel
```

Bkz. [@MapTo ve @MapFrom](../temel-kullanim/mapto-mapfrom.md).

## @FieldMap

```kotlin
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class FieldMap(val fieldName: String, val targetClass: KClass<*> = Nothing::class)
```

Alan adı farklı olduğunda:

```kotlin
@MapTo(UserDomain::class)
data class UserRemote(
    @FieldMap(fieldName = "id")   // userId → id
    val userId: String,
) : RemoteModel
```

Birden fazla hedef için `targetClass` ile hedef belirtilir:

```kotlin
@FieldMap(fieldName = "id",       targetClass = UserDomain::class)
@FieldMap(fieldName = "userId",   targetClass = UserUiModel::class)
val userId: String,
```

Bkz. [Alan Eşleştirme](../temel-kullanim/alan-eslestirme.md).

## @MapDefaultValue

```kotlin
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class MapDefaultValue(val expression: String)
```

```kotlin
@MapTo(UserDomain::class)
data class UserRemote(
    @MapDefaultValue("Clock.System.now()")
    val createdAt: Instant?,
) : RemoteModel
```

Üretilen kodda `expression` literal olarak yer alır. Bkz. [Null-Safety](../temel-kullanim/null-safety.md).

## @Ignore

```kotlin
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Ignore
```

```kotlin
@MapTo(UserDomain::class)
data class UserRemote(
    val id: String,
    @Ignore val internalFlag: Boolean,  // UserDomain'e aktarılmaz
) : RemoteModel
```

Bkz. [Alan Eşleştirme](../temel-kullanim/alan-eslestirme.md).

## @UseMapTypeConverter

```kotlin
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class UseMapTypeConverter(val converter: KClass<out MapTypeConverter<*, *>>)
```

```kotlin
@MapTo(EventDomain::class)
data class EventRemote(
    val startsAt: String,                                       // global: ISO-8601 converter
    @UseMapTypeConverter(EpochStringToInstantConverter::class)  // alan bazlı override
    val legacyTime: String,
) : RemoteModel
```

Bkz. [@KMapperConfig ve @UseMapTypeConverter](../tip-donusumu/kmapperconfig.md).

## @KMapperConfig

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class KMapperConfig(
    val converters: Array<KClass<*>> = [],
    val wrappers: Array<KClass<*>> = [],
)
```

```kotlin
@KMapperConfig(
    converters = [IsoStringToInstantConverter::class],
    wrappers   = [PersistentListWrapper::class, NonEmptyListWrapper::class],
)
object AppMapperConfig
```

Bkz. [@KMapperConfig ve @UseMapTypeConverter](../tip-donusumu/kmapperconfig.md) ve [Çok Modüllü Projeler](../ileri/cok-modullu.md).

## @CollectionWrapper

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class CollectionWrapper(val forType: KClass<*>)
```

`@CollectionWrapper` bir `object` üzerine konur ve o nesnenin hangi koleksiyon tipini sardığını (`forType`) belirtir. Nesne `fun <T> wrap(items: List<T>): WrappedCollection<T>` metodunu barındırmalıdır. `converters-immutable` ve `converters-arrow` artifact'larında kullanılır; kendi wrapper'ınızı tanımlamak için de kullanabilirsiniz. Tüketici modül bu wrapper'ı `@KMapperConfig.wrappers` listesinde açıkça belirtmelidir. Bkz. [Çok Modüllü Projeler](../ileri/cok-modullu.md).

---

Sonraki adım: [Sınırlamalar ve Yol Haritası](./sinirlamalar.md)
