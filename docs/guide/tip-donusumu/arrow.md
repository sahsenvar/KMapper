# NonEmptyList — converters-arrow

Arrow'un `NonEmptyList<T>` tipini eşleme hedefi olarak kullanmak için **`converters-arrow`** modülünü ekleyin. Bu modül `@CollectionWrapper` mekanizmasını kullanır — scalar converter gibi `@KMapperConfig`'e eklemeniz **gerekmez**; bağımlılığı eklemek yeterlidir.

> **Not:** `converters-arrow` sürüm **0.2.0** ile gelir; henüz Maven Central'da değildir.
> Yayınlanana kadar `publishToMavenLocal` + `mavenLocal()` ile kullanın.
> `core` ve `processor` hâlâ Maven Central'dan `0.1.0` olarak çekilebilir.

---

## Kurulum

```kotlin
// settings.gradle.kts — pre-release için mavenLocal ekle
dependencyResolutionManagement {
    repositories {
        mavenLocal()        // 0.2.0 add-on'lar için
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts (tüketen modül)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.sahsenvar:kmapper-core:0.1.0")
            implementation("io.github.sahsenvar:kmapper-converters-arrow:0.2.0")
        }
    }
}
```

KSP bağımlılığı değişmez:

```kotlin
dependencies {
    add("kspCommonMainMetadata", "io.github.sahsenvar:kmapper-processor:0.1.0")
}
```

---

## Sağlanan Wrapper Fonksiyon

`converters-arrow` tek bir `@CollectionWrapper` wrapper tanımlar:

```kotlin
@CollectionWrapper(forType = NonEmptyList::class)
fun <T> List<T>.asNonEmptyList(): NonEmptyList<T> =
    toNonEmptyListOrNull() ?: throw MappingException.EmptyCollection("NonEmptyList source was empty")
```

Processor bu wrapper'ı descriptor mekanizmasıyla otomatik keşfeder; siz yalnızca bağımlılığı eklersiniz.

---

## Kullanım

Hedef sınıfta `NonEmptyList<T>` kullanmanız yeterlidir:

```kotlin
import arrow.core.NonEmptyList

@MapTo(PostDomain::class)
data class PostRemote(
    val title: String,
    val tags: List<TagRemote>,
)

data class TagDomain(val id: String, val name: String)

data class PostDomain(
    val title: String,
    val tags: NonEmptyList<TagDomain>,   // hedef: NonEmptyList
)
```

Processor, `tags` alanının hedef tipinin `NonEmptyList` olduğunu görür ve wrapper'ı zincirler:

```kotlin
public fun PostRemote.toPostDomain(): PostDomain = PostDomain(
    title = title,
    tags  = tags.map { it.toTagDomain() }.asNonEmptyList(),
)
```

---

## Boş Liste — MappingException.EmptyCollection

`source.tags` listesi boş ise `asNonEmptyList()` çalışma zamanında `MappingException.EmptyCollection` fırlatır. Bu Arrow'un **boş-olmayan** semantiğinin zorunlu bir sonucudur:

```kotlin
// Kaynak tags = [] ise:
// → MappingException.EmptyCollection("NonEmptyList source was empty")
```

Boş olabilecek kaynaklarda eşlemeyi `try/catch` veya `runCatching` ile sararak yönetin.

---

## @CollectionWrapper — Nasıl Çalışır?

`@CollectionWrapper(forType = NonEmptyList::class)` anotasyonu `BINARY` retention ile derlenir. `converters-arrow` kendi KSP run'ında bu wrapper'ı görür ve `com.sahsenvar.kmapper.generated` paketine bir **descriptor nesnesi** üretir. Tüketici modülün processor run'ı bu descriptor'ı `resolver.getDeclarationsFromPackage(...)` ile keşfeder.

Aynı `forType` için birden fazla wrapper classpath'te bulunursa processor **derleme hatası** verir.

---

## Yol Haritası

`Option<NonEmptyList<T>>` desteği (boş kaynak → `None`, dolu kaynak → `Some(nel)`) bir sonraki sürümde planlanmaktadır.

---

Sonraki adım: [Tarih/Saat Converter'ları →](datetime.md) | Diğer kaynaklar: [@KMapperConfig](kmapperconfig.md) | [Koleksiyonlar](../temel-kullanim/koleksiyonlar.md)
