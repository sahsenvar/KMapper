# NonEmptyList — converters-arrow

Arrow'un `NonEmptyList<T>` tipini eşleme hedefi olarak kullanmak için **`converters-arrow`** modülünü ekleyin. Bu modül `@CollectionWrapper` mekanizmasını kullanır — **scalar converter'lardan farklı olarak `@KMapperConfig.wrappers` listesinde açıkça belirtmeniz gerekir**; yalnızca bağımlılığı eklemek yeterli değildir.

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

KSP bağımlılığı değişmez; ancak wrapper'ı `@KMapperConfig.wrappers` listesine eklemeyi unutmayın:

```kotlin
import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.arrow.NonEmptyListWrapper

@KMapperConfig(wrappers = [NonEmptyListWrapper::class])
object AppMapperConfig
```

KSP yapılandırması:

```kotlin
dependencies {
    add("kspCommonMainMetadata", "io.github.sahsenvar:kmapper-processor:0.1.0")
}
```

---

## Sağlanan Wrapper Nesne

`converters-arrow` tek bir `@CollectionWrapper` wrapper tanımlar:

```kotlin
@CollectionWrapper(forType = NonEmptyList::class)
object NonEmptyListWrapper {
    fun <T> wrap(items: List<T>): NonEmptyList<T> =
        items.toNonEmptyListOrNull()
            ?: throw MappingException.EmptyCollection("NonEmptyList source was empty")
}
```

Bu nesneyi doğrudan çağırmazsınız; processor hedef alanın `NonEmptyList` olduğunu görünce `NonEmptyListWrapper.wrap(...)` çağrısını otomatik üretir.

---

## Kullanım

Hedef sınıfta `NonEmptyList<T>` kullanın ve `@KMapperConfig.wrappers`'a `NonEmptyListWrapper::class` ekleyin:

```kotlin
import arrow.core.NonEmptyList
import com.sahsenvar.kmapper.arrow.NonEmptyListWrapper

@KMapperConfig(wrappers = [NonEmptyListWrapper::class])
object AppMapperConfig

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

Processor, `tags` alanının hedef tipinin `NonEmptyList` olduğunu görür ve şunu üretir:

```kotlin
public fun PostRemote.toPostDomain(): PostDomain = PostDomain(
    title = title,
    tags  = NonEmptyListWrapper.wrap(tags.map { it.toTagDomain() }),
)
```

---

## Boş Liste — MappingException.EmptyCollection

`source.tags` listesi boş ise `NonEmptyListWrapper.wrap(...)` çalışma zamanında `MappingException.EmptyCollection` fırlatır. Bu Arrow'un **boş-olmayan** semantiğinin zorunlu bir sonucudur:

```kotlin
// Kaynak tags = [] ise:
// → MappingException.EmptyCollection("NonEmptyList source was empty")
```

Boş olabilecek kaynaklarda eşlemeyi `try/catch` veya `runCatching` ile sararak yönetin.

---

## @CollectionWrapper — Nasıl Çalışır?

`@CollectionWrapper(forType = NonEmptyList::class)` anotasyonu bir `object` üzerine eklenir ve `BINARY` retention ile derlenir. Tüketici modülün KSP çalıştırmasında processor, `@KMapperConfig(wrappers = [NonEmptyListWrapper::class])` listesini okur ve `NonEmptyListWrapper`'ın `@CollectionWrapper.forType = NonEmptyList::class` değerini bağımlılık artifact'larından çözer — bu standart bir tür+anotasyon çözümlemesidir ve KMP/iOS dahil tüm platformlarda çalışır.

`getDeclarationsFromPackage` veya otomatik keşif kullanılmaz.

---

## Option\<T\> Eşleştirme

`NonEmptyList`'e ek olarak, classpath'te `converters-arrow` bulunması `Option<T>` eşleştirmesini
de etkinleştirir. Bu bir **processor kuralıdır** — converter nesnesi veya `@KMapperConfig` girişi
gerekmez.

### T? / T → Option\<T\>

Hedef alan tipi `Option<T>` ise processor `Option.fromNullable(...)` üretir:

```kotlin
@MapTo(UserDomain::class)
data class UserRemote(
    val name: String?,      // nullable kaynak
    val role: String,       // null olmayan kaynak
)

data class UserDomain(
    val name: Option<String>,
    val role: Option<String>,
)
```

Üretilen:

```kotlin
public fun UserRemote.toUserDomain(): UserDomain = UserDomain(
    name = Option.fromNullable(name),
    role = Option.fromNullable(role),
)
```

### Option\<T\> → T?

Kaynak alan tipi `Option<T>` ise processor `getOrNull()` üretir:

```kotlin
@MapTo(UserRemote::class)
data class UserDomain(
    val name: Option<String>,
)

data class UserRemote(
    val name: String?,
)
```

Üretilen:

```kotlin
public fun UserDomain.toUserRemote(): UserRemote = UserRemote(
    name = name.getOrNull(),
)
```

Hedef alan null olamaz ise (ör. `val name: String`) `getOrNull()` sonrasında standart null güvenlik
kuralları devreye girer: `name.getOrNull() ?: throw MappingException.RequiredFieldMissing("name")`.

### Option İçinde İç İçe Modeller

İç tip `T` kendi başına eşlenebilir bir tipse (üretilen `toT()` uzantısı varsa), processor
sarmalama/açma işleminin içine mapper çağrısını ekler:

```kotlin
// kaynak alan: role: RoleRemote?  →  hedef: Option<RoleDomain>
// Üretilen: Option.fromNullable(role?.toRoleDomain())
```

---

## Yol Haritası

`Option<NonEmptyList<T>>` desteği (boş kaynak → `None`, dolu kaynak → `Some(nel)`) bir sonraki sürümde planlanmaktadır.

---

Sonraki adım: [Tarih/Saat Converter'ları →](datetime.md) | Diğer kaynaklar: [@KMapperConfig](kmapperconfig.md) | [Koleksiyonlar](../temel-kullanim/koleksiyonlar.md)
