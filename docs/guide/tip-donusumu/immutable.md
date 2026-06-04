# Immutable Koleksiyonlar — converters-immutable

kmap'in `core` modülü yalnızca stdlib `List`/`Set` eşleştirmesini bilir. `PersistentList`, `ImmutableList`, `ImmutableSet`, `PersistentSet` gibi `kotlinx.collections.immutable` tiplerini hedef olarak kullanmak için **`converters-immutable`** modülünü ekleyin.

> **Not:** `converters-immutable` sürüm **0.2.0** ile güncellendi; henüz Maven Central'da değildir.
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

KSP bağımlılığı değişmez. Ancak **wrapper'ları `@KMapperConfig`'in `wrappers` listesinde açıkça belirtmeniz gerekir** (bağımlılığı eklemek yeterli değildir):

```kotlin
import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.immutable.PersistentListWrapper
import com.sahsenvar.kmapper.immutable.ImmutableListWrapper
import com.sahsenvar.kmapper.immutable.ImmutableSetWrapper
import com.sahsenvar.kmapper.immutable.PersistentSetWrapper

@KMapperConfig(
    wrappers = [
        PersistentListWrapper::class,
        ImmutableListWrapper::class,
        ImmutableSetWrapper::class,
        PersistentSetWrapper::class,
    ]
)
object AppMapperConfig
```

Yalnızca gerçekten kullandığınız wrapper'ları eklemeniz yeterlidir.

---

## Sağlanan Wrapper Nesneler

`converters-immutable`, her immutable koleksiyon tipi için `@CollectionWrapper` anotasyonlu dört `object` tanımlar:

```kotlin
@CollectionWrapper(forType = PersistentList::class)
object PersistentListWrapper {
    fun <T> wrap(items: List<T>): PersistentList<T> = items.toPersistentList()
}

@CollectionWrapper(forType = ImmutableList::class)
object ImmutableListWrapper {
    fun <T> wrap(items: List<T>): ImmutableList<T> = items.toImmutableList()
}

@CollectionWrapper(forType = ImmutableSet::class)
object ImmutableSetWrapper {
    fun <T> wrap(items: List<T>): ImmutableSet<T> = items.toImmutableSet()
}

@CollectionWrapper(forType = PersistentSet::class)
object PersistentSetWrapper {
    fun <T> wrap(items: List<T>): PersistentSet<T> = items.toPersistentSet()
}
```

Bu nesneleri doğrudan çağırmazsınız; processor hedef alanın tipini görünce `@KMapperConfig.wrappers` listesinden uygun wrapper'ı seçer ve `wrap(...)` çağrısını üretir.

**Çapraz tür dönüşümü:** Kaynak `List<T>`, `Set<T>` veya `PersistentList<T>` olsa bile, hedef `PersistentSet<T>` ise processor `PersistentSetWrapper.wrap(...)` çağrısını üretir. Kaynak ile hedef koleksiyon türlerinin eşleşmesine gerek yoktur.

---

## Kullanım

Hedef sınıfta immutable koleksiyon tipi kullanın ve kullandığınız wrapper'ı `@KMapperConfig.wrappers`'a ekleyin:

```kotlin
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

@KMapperConfig(wrappers = [PersistentListWrapper::class])
object AppMapperConfig
```

Processor `tags` alanının hedef tipinin `PersistentList` olduğunu görür, `wrappers` listesindeki `PersistentListWrapper`'ı seçer ve şunu üretir:

```kotlin
public fun ArticleRemote.toArticleDomain(): ArticleDomain = ArticleDomain(
    title = title,
    tags  = PersistentListWrapper.wrap(tags.map { it.toTagDomain() }),
)
```

`ImmutableList`, `ImmutableSet` ve `PersistentSet` için de aynı mekanizma çalışır.

---

## @CollectionWrapper — Nasıl Çalışır?

`@CollectionWrapper(forType = PersistentList::class)` anotasyonu bir `object` üzerine eklenir ve `BINARY` retention ile derlenir. Tüketici modülün KSP çalıştırmasında processor, `@KMapperConfig(wrappers = [...])` listesini okur ve her sınıfın `@CollectionWrapper.forType` değerini bağımlılık artifact'larından çözer — bu standart bir tür+anotasyon çözümlemesidir ve tüm platformlarda (JVM, Android, iOS/Native) çalışır.

`getDeclarationsFromPackage` veya otomatik keşif kullanılmaz; wrapper'ların listede açıkça yer alması gerekir.

Aynı `forType` için `wrappers` listesinde birden fazla wrapper bulunursa processor **derleme hatası** verir.

---

## Kendi @CollectionWrapper Nesnenizi Yazmak

Kütüphane tarafından sağlanmayan bir immutable koleksiyon tipi için kendi wrapper'ınızı yazabilirsiniz:

```kotlin
// kendi modülünüzde:
@CollectionWrapper(forType = MyImmutableList::class)
object MyImmutableListWrapper {
    fun <T> wrap(items: List<T>): MyImmutableList<T> = MyImmutableList.copyOf(items)
}
```

Ardından tüketen modülün `@KMapperConfig.wrappers` listesine ekleyin:

```kotlin
@KMapperConfig(wrappers = [MyImmutableListWrapper::class])
object AppMapperConfig
```

---

Diğer kaynaklar: [Koleksiyonlar (Temel Kullanım)](../temel-kullanim/koleksiyonlar.md) | [@KMapperConfig](kmapperconfig.md)
