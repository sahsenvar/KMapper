# Immutable Koleksiyonlar — converters-immutable

[kotlinx-collections-immutable](https://github.com/Kotlin/kotlinx.collections.immutable) için
[`@CollectionWrapper`](ozel-converter.md) object'leri: wire `List`'lerini doğrudan immutable
domain koleksiyonlarına eşleyin.

## Kurulum

```kotlin
commonMain.dependencies {
    implementation("io.github.sahsenvar:kmapper-converters-immutable:2.0.1")
}
```

Kullandığınız wrapper'ları [`@KMapperConfig`](kmapperconfig.md)'e kaydedin:

```kotlin
@KMapperConfig(wrappers = [PersistentListWrapper::class, PersistentSetWrapper::class])
object AppMapperConfig
```

## Wrapper'lar (`com.sahsenvar.kmapper.immutable`)

| Wrapper | `List<T>` ↔ |
|---------|--------------|
| `PersistentListWrapper` | `PersistentList<T>` |
| `ImmutableListWrapper` | `ImmutableList<T>` |
| `PersistentSetWrapper` | `PersistentSet<T>` |
| `ImmutableSetWrapper` | `ImmutableSet<T>` |

## Kullanım

```kotlin
data class User(val tags: PersistentList<Tag>)

@MapTo(User::class)
data class UserResponse(val tags: List<TagResponse>) // elemanlar Tag alt mapper'ından geçer
```

Eleman dönüşümü (iç içe `@MapTo` çiftleri ve
[eleman ladder'ı](../temel-kullanim/koleksiyonlar.md) dahil) değişmez — wrapper yalnızca kabı
değiştirir. İki yön de çalışır: `PersistentList` kaynağı, ters mapping için `List`'e geri
açılır.

> Sıradaki: **[Arrow →](arrow.md)**
