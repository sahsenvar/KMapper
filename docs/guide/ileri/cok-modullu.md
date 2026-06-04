# Çok Modüllü Projeler

kmap, her modülü bağımsız bir KSP çalıştırmasıyla derler. Modüller arası tip keşfi özel bir mekanizmaya dayanır; bu sayfada bu mekanizmanın nasıl çalıştığı açıklanmaktadır.

## @KMapperConfig — Modül Başına Konum

Her mapper üreten modül kendi `@KMapperConfig` nesnesini tanımlar. Processor yalnızca işlediği modülün içindeki `@KMapperConfig`'i görür; başka bir modüldeki config nesnesini otomatik olarak devralamaz.

```kotlin
// feature/order/data/src/commonMain/.../OrderMapperConfig.kt
@KMapperConfig(converters = [
    IsoStringToInstantConverter::class,
    PersistentListConverter::class,
])
object OrderMapperConfig
```

```kotlin
// feature/product/data/src/commonMain/.../ProductMapperConfig.kt
@KMapperConfig(converters = [
    IsoStringToInstantConverter::class,
])
object ProductMapperConfig
```

Her modül ihtiyacı olan converter'ları kendi config'inde listeler. Tekrar gibi görünse de bu kasıtlı bir tasarım kararıdır: her modülün bağımlılık grafiği derleme zamanında açıkça görünür olur.

## Modüller Arası @CollectionWrapper Keşfi

`converters-compose` gibi bir converter modülü `@CollectionWrapper` anotasyonlu fonksiyonlar tanımlar:

```kotlin
// converters-compose modülü
@CollectionWrapper(forType = PersistentList::class)
fun <T> List<T>.asPersistentList(): PersistentList<T> = toPersistentList()
```

KSP'nin `getSymbolsWithAnnotation` fonksiyonu **bağımlılık artifact'larındaki** anotasyonları göremez — yalnızca o anki derleme birimini inceler. Bu nedenle kmap farklı bir mekanizma kullanır:

### Descriptor Mekanizması

1. `converters-compose` build edilirken kendi KSP çalıştırması `@CollectionWrapper` fonksiyonlarını görür (in-module).
2. Processor her wrapper için `com.sahsenvar.kmapper.generated` paketine bir descriptor nesnesi üretir. Bu nesne `@CollectionWrapperDescriptor` ile anotasyonlanmıştır ve `BINARY` retention kullanır:

```kotlin
@CollectionWrapperDescriptor(
    forType  = "kotlinx.collections.immutable.PersistentList",
    wrapFunction = "com.sahsenvar.kmapper.converters.compose.asPersistentList"
)
object PersistentListWrapperDescriptor
```

3. Tüketici modülün processor'ı `resolver.getDeclarationsFromPackage("com.sahsenvar.kmapper.generated")` ile bu descriptor'ları bulur ve hangi wrapper'ların classpath'te mevcut olduğunu anlar.

Bu altyapı, converter runtime kaydıyla aynı mekanizmayı paylaşır; tasarım tutarlıdır.

### kspJvm Gerekliliği

Descriptor sınıflarının tüketici modüller tarafından bulunabilmesi için `converters-compose` modülü yayınlanan jar'a descriptor sınıflarını dahil etmelidir. Bu, KSP'nin JVM hedefi için de çalışmasını gerektirir:

```kotlin
// converters-compose/build.gradle.kts
dependencies {
    // KMP hedefleri için:
    add("kspCommonMainMetadata", "com.sahsenvar.kmapper:processor:<v>")
    // Yayınlanan jar'a descriptor'ların girmesi için:
    add("kspJvm", "com.sahsenvar.kmapper:processor:<v>")
}
```

`kspJvm` olmadan descriptor sınıfları jar'a girmez; tüketici modüller wrapper'ları keşfedemez.

### Çakışma Koruması

Aynı `forType` için classpath'te birden fazla `@CollectionWrapper` bulunursa processor **compile error** verir. Hangi wrapper'ın aktif olacağı sessiz kalmamalıdır:

```
e: [kmap] Multiple @CollectionWrapper found for 'PersistentList'. Remove one from the classpath.
```

## Örnek Çok Modüllü Yapı

```
:core:mappers           → @KMapperConfig (ortak converter'lar)
:feature:order:data     → @KMapperConfig (kendi converter'ları) + @MapTo modelleri
:feature:product:data   → @KMapperConfig (kendi converter'ları) + @MapTo modelleri
:converters-compose     → @CollectionWrapper fonksiyonları + üretilen descriptor'lar
```

Her `:feature:*:data` modülünün `build.gradle.kts`'inde:

```kotlin
dependencies {
    implementation("com.sahsenvar.kmapper:core:<v>")
    add("kspCommonMainMetadata", "com.sahsenvar.kmapper:processor:<v>")

    // Immutable koleksiyon desteği için:
    implementation("com.sahsenvar.kmapper:converters-compose:<v>")
}
```

---

Sonraki adım: [Mimari — Modüller ve Pipeline](./mimari.md)
