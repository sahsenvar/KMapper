# Immutable Koleksiyonlar — converters-immutable

kmap'in `core` modülü yalnızca stdlib `List`/`Set` eşleştirmesini bilir. `PersistentList`, `ImmutableList`, `ImmutableSet`, `PersistentSet` gibi `kotlinx.collections.immutable` tiplerini hedef olarak kullanmak için **`converters-immutable`** modülünü ekleyin.

> **Not:** `converters-immutable` sürüm **0.2.0** ile güncellendi (`asPersistentSet` eklendi); henüz Maven Central'da değildir.
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

`converters-immutable` bağımlılığını ilgili modüle ekleyin:

```kotlin
// build.gradle.kts
commonMainImplementation("io.github.sahsenvar:kmapper-converters-immutable:0.2.0")
```

KSP bağımlılığı değişmez; processor `converters-immutable`'daki wrapper'ları descriptor mekanizmasıyla otomatik keşfeder.

---

## Sağlanan Wrapper Fonksiyonlar

`converters-immutable`, her immutable koleksiyon tipi için `@CollectionWrapper` anotasyonlu dört fonksiyon tanımlar:

```kotlin
@CollectionWrapper(forType = PersistentList::class)
fun <T> List<T>.asPersistentList(): PersistentList<T> = toPersistentList()

@CollectionWrapper(forType = ImmutableList::class)
fun <T> List<T>.asImmutableList(): ImmutableList<T> = toImmutableList()

@CollectionWrapper(forType = ImmutableSet::class)
fun <T> List<T>.asImmutableSet(): ImmutableSet<T> = toImmutableSet()

@CollectionWrapper(forType = PersistentSet::class)
fun <T> List<T>.asPersistentSet(): PersistentSet<T> = toPersistentSet()
```

Kullanıcı bu fonksiyonları doğrudan çağırmaz; processor hedef alan tipini görünce uygun wrapper'ı otomatik seçer.

**Çapraz tür dönüşümü:** Kaynak `List<T>`, `Set<T>` veya `PersistentList<T>` olsa bile, hedef `PersistentSet<T>` ise processor `asPersistentSet()` wrapper'ını otomatik seçer. Kaynak ile hedef koleksiyon türlerinin eşleşmesine gerek yoktur.

---

## Kullanım

Hedef sınıfta immutable koleksiyon tipi kullanmanız yeterlidir:

```kotlin
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList

@MapTo(ArticleDomain::class)
data class ArticleRemote(
    val title: String,
    val tags: List<TagRemote>,
)

data class TagDomain(val id: String, val name: String)

data class ArticleDomain(
    val title: String,
    val tags: PersistentList<TagDomain>,    // hedef: PersistentList
)
```

Processor `tags` alanının hedef tipinin `PersistentList` olduğunu görür, classpath'te `asPersistentList` wrapper'ını bulur ve zincirler:

```kotlin
public fun ArticleRemote.toArticleDomain(): ArticleDomain = ArticleDomain(
    title = title,
    tags  = tags.map { it.toTagDomain() }.asPersistentList(),
)
```

`ImmutableList` ve `ImmutableSet` için de aynı mekanizma çalışır.

---

## @CollectionWrapper — Nasıl Çalışır?

`@CollectionWrapper(forType = PersistentList::class)` anotasyonu bir fonksiyon üzerine eklenir ve `BINARY` retention ile derlenir. `converters-immutable` kendi KSP run'ında bu fonksiyonları görür ve `com.sahsenvar.kmapper.generated` paketine **descriptor nesneleri** üretir. Tüketici modülün processor run'ı bu descriptor'ları `resolver.getDeclarationsFromPackage(...)` ile keşfeder.

Aynı `forType` için birden fazla `@CollectionWrapper` classpath'te bulunursa processor **derleme hatası** verir — hangi wrapper'ın aktif olduğu sessiz kalmamalıdır.

---

## Kendi @CollectionWrapper Fonksiyonunuzu Yazmak

Kütüphane tarafından sağlanmayan bir immutable koleksiyon tipi için kendi wrapper'ınızı yazabilirsiniz:

```kotlin
// kendi modülünüzde:
@CollectionWrapper(forType = MyImmutableList::class)
fun <T> List<T>.asMyImmutableList(): MyImmutableList<T> = MyImmutableList.copyOf(this)
```

Bu fonksiyon `BINARY` retention'la derlendikten sonra processor onu otomatik keşfeder. Listesini `@KMapperConfig`'e eklemenize gerek yoktur.

---

Diğer kaynaklar: [Koleksiyonlar (Temel Kullanım)](../temel-kullanim/koleksiyonlar.md) | [@KMapperConfig](kmapperconfig.md)
