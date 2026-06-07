# Çok Modüllü Projeler

KMapper, her modülü bağımsız bir KSP çalıştırmasıyla derler. Bu sayfa scalar converter'lar ve koleksiyon wrapper'larının çok modüllü bir projede nasıl yapılandırıldığını açıklar.

## @KMapperConfig — Modül Başına Konum

Her mapper üreten modül kendi `@KMapperConfig` nesnesini tanımlar. Processor yalnızca işlediği modülün içindeki `@KMapperConfig`'i görür; başka bir modüldeki config nesnesini otomatik olarak devralamaz.

```kotlin
// feature/order/data/src/commonMain/.../OrderMapperConfig.kt
@KMapperConfig(
    converters = [IsoStringToInstantConverter::class],
    wrappers   = [PersistentListWrapper::class],
)
object OrderMapperConfig
```

```kotlin
// feature/product/data/src/commonMain/.../ProductMapperConfig.kt
@KMapperConfig(
    converters = [IsoStringToInstantConverter::class],
)
object ProductMapperConfig
```

Her modül ihtiyacı olan converter ve wrapper'ları kendi config'inde listeler. Tekrar gibi görünse de bu kasıtlı bir tasarım kararıdır: her modülün bağımlılık grafiği derleme zamanında açıkça görünür olur.

## Modüller Arası @CollectionWrapper Kullanımı

`converters-immutable` veya `converters-arrow` gibi bir add-on modülü `@CollectionWrapper` anotasyonlu `object`'ler tanımlar:

```kotlin
// converters-immutable modülü
@CollectionWrapper(forType = PersistentList::class)
object PersistentListWrapper {
    fun <T> wrap(items: List<T>): PersistentList<T> = items.toPersistentList()
}
```

Tüketici modülün processor'ı bu wrapper'ları **otomatik keşfetmez**. Bunun yerine tüketici modül, kullanmak istediği wrapper'ları `@KMapperConfig.wrappers` listesinde açıkça belirtir:

```kotlin
// feature/order/data/src/commonMain/.../OrderMapperConfig.kt
import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.immutable.PersistentListWrapper
import com.sahsenvar.kmapper.arrow.NonEmptyListWrapper

@KMapperConfig(
    converters = [IsoStringToInstantConverter::class],
    wrappers   = [PersistentListWrapper::class, NonEmptyListWrapper::class],
)
object OrderMapperConfig
```

### Neden Açık Listeleme?

KSP2'nin per-module (modül başına) yalıtılmış çalıştırması nedeniyle `getSymbolsWithAnnotation` ve `getDeclarationsFromPackage` fonksiyonları **bağımlılık artifact'larındaki** sembolleri göremez — yalnızca o anki derleme birimini inceler. Bu, özellikle KMP'nin `kspCommonMainMetadata` çalıştırmasında ve iOS/Native hedeflerinde geçerlidir.

Çözüm: `@KMapperConfig(wrappers = [...])` listesi **tüketici modülün kendi KSP çalıştırmasında** okunur (in-module, her zaman görünür). Processor bu listeden her wrapper'ın `@CollectionWrapper.forType` değerini bağımlılık artifact'larından çözer — bu standart bir tür+anotasyon çözümlemesidir ve JVM, Android, iOS/Native dahil tüm platformlarda çalışır.

### KMP Tüketici İçin KSP Yapılandırması

KMP modüllerinde processor'ın her hedefe ayrı ayrı uygulanması gerekir:

```kotlin
// feature/order/data/build.gradle.kts
dependencies {
    add("kspCommonMainMetadata", "io.github.sahsenvar:kmapper-processor:<v>")
    add("kspJvm", "io.github.sahsenvar:kmapper-processor:<v>")             // JVM hedefi
    add("kspAndroid", "io.github.sahsenvar:kmapper-processor:<v>")         // Android hedefi
    add("kspIosArm64", "io.github.sahsenvar:kmapper-processor:<v>")        // iOS cihaz
    add("kspIosSimulatorArm64", "io.github.sahsenvar:kmapper-processor:<v>") // iOS simülatör
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}
```

`kspCommonMainMetadata` genel metadata mapper'larını üretir; diğer `ksp*` girişleri platform-spesifik kaynakların de derlenebilmesini sağlar.

### Çakışma Koruması

Aynı `forType` için `wrappers` listesinde birden fazla wrapper bulunursa processor **compile error** verir:

```
e: [KMapper] Multiple @CollectionWrapper found for 'PersistentList'. Remove one from the wrappers list.
```

## Örnek Çok Modüllü Yapı

```
:core:mappers           → @KMapperConfig (ortak converter'lar)
:feature:order:data     → @KMapperConfig (converters + wrappers) + @MapTo modelleri
:feature:product:data   → @KMapperConfig (converters) + @MapTo modelleri
converters-immutable    → @CollectionWrapper nesneleri (PersistentListWrapper vb.)
converters-arrow        → @CollectionWrapper nesneleri (NonEmptyListWrapper)
```

Her `:feature:*:data` modülünün `build.gradle.kts`'inde:

```kotlin
commonMain.dependencies {
    implementation("io.github.sahsenvar:kmapper-core:<v>")

    // Immutable koleksiyon desteği için:
    implementation("io.github.sahsenvar:kmapper-converters-immutable:<v>")
}

dependencies {
    add("kspCommonMainMetadata", "io.github.sahsenvar:kmapper-processor:<v>")
    // Hedeflere göre kspJvm / kspAndroid / kspIosArm64 / kspIosSimulatorArm64 ekleyin
}
```

---

Sonraki adım: [Mimari — Modüller ve Pipeline](./mimari.md)
