# Immutable Koleksiyonlar — converters-immutable

kmap'in `core` modülü yalnızca stdlib `List`/`Set` eşleştirmesini bilir. `PersistentList`, `ImmutableList`, `ImmutableSet` gibi `kotlinx.collections.immutable` tiplerini hedef olarak kullanmak için **`converters-immutable`** modülünü ekleyin.

---

## Kurulum

`converters-immutable` bağımlılığını ilgili modüle ekleyin:

```kotlin
// build.gradle.kts
commonMainImplementation("io.github.sahsenvar:kmapper-converters-immutable:<versiyon>")
```

KSP bağımlılığı değişmez; processor `converters-immutable`'daki wrapper'ları descriptor mekanizmasıyla otomatik keşfeder.

---

## Sağlanan Wrapper Fonksiyonlar

`converters-immutable`, her immutable koleksiyon tipi için `@CollectionWrapper` anotasyonlu üç fonksiyon tanımlar:

```kotlin
@CollectionWrapper(forType = PersistentList::class)
fun <T> List<T>.asPersistentList(): PersistentList<T> = toPersistentList()

@CollectionWrapper(forType = ImmutableList::class)
fun <T> List<T>.asImmutableList(): ImmutableList<T> = toImmutableList()

@CollectionWrapper(forType = ImmutableSet::class)
fun <T> List<T>.asImmutableSet(): ImmutableSet<T> = toImmutableSet()
```

Kullanıcı bu fonksiyonları doğrudan çağırmaz; processor hedef alan tipini görünce uygun wrapper'ı otomatik seçer.

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
