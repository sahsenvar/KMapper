# Dönüş Sarmalayıcıları

Her `@MapTo`/`@MapFrom` mapping'i her zaman düz çekirdeği üretir: sert hatada bir
[`MappingException`](../hata-yonetimi/mapping-exception.md) fırlatan `fun Source.toX(): X`. Bir
**dönüş sarmalayıcısı (return wrapper)**, bu düz çekirdeğin *üzerine* bir ya da daha fazla ek
extension fonksiyonu ekler — onun yerine geçmez, ve iç içe mapping'ler her zaman düz çekirdeği
çağırır, asla bir sarmalayıcıyı değil; yani sarmalayıcılar mapper'ların nasıl bileşeceğini asla
değiştirmez.

```kotlin
@MapTo(User::class, wrapper = KMapperWrapper.KtResult::class)
data class UserResponse(val id: Long, val name: String)

// ikisi de üretilir:
// fun UserResponse.toUser(): User
// fun UserResponse.toUserResult(): Result<User>
```

## Built-in'ler

KMapper dört hazır wrapper ile gelir; `@MapTo`/`@MapFrom`'a `wrapper = KMapperWrapper.<W>::class`
olarak verilir (kendi wrapper'ınızı da ekleyebilirsiniz, aşağıya bakın):

| Sarmalayıcı | Ekler | Not |
|-------------|-------|-----|
| `Default` (varsayılan) | tek başına hiçbir şey | **modül geneli ayara** çözümlenir, hiçbir şey ayarlı değilse [`None`](#none)'a |
| `None` | hiçbir şey | yalnızca düz `toX(): X` |
| `KtResult` | `fun Source.toXResult(): Result<X>` | sert hatalar `Result.failure` olur |
| `Flow` | `fun Source.toXFlow(): Flow<X>` ve `fun Flow<Source>.toXFlow(): Flow<X>` | tek değerli soğuk bir flow (mapping, toplama sırasında çalışır ve fırlayabilir) ve yayılan her kaynağı eşleyen bir flow |

```kotlin
@MapTo(User::class, wrapper = KMapperWrapper.Flow::class)
data class UserResponse(val id: Long, val name: String)

val tekil: Flow<User> = response.toUserFlow()
val cogul: Flow<User> = responses.toUserFlow() // responses: Flow<UserResponse>
```

`kmapper-core`'un kotlinx-coroutines-core'a (`api`, `1.10.2`) bağımlı olmasının nedeni
`KMapperWrapper.Flow`'dur — bu sıradan bir bağımlılıktır, özel bir durum değil; bkz.
[Kendi sarmalayıcınızı yazmak](#kendi-sarmalayicinizi-yazmak).

## Mapping bazında mı, modül geneli mi?

`wrapper`'ı boş bırakmak, `wrapper = KMapperWrapper.Default::class` yazmakla aynıdır — bu,
*modülün* ne yapılandırdığına, hiçbir şey yapılandırılmamışsa `None`'a bırakılır. Bu modül
geneli varsayılanı ayarlamanın iki yolu, tercih sırasına göre:

**1. Gradle plugin'i.** KSP plugin'iyle birlikte, aynı modüle uygulayın:

```kotlin
// build.gradle.kts
plugins {
    id("com.google.devtools.ksp") version "2.3.10-2.0.5"
    id("io.github.sahsenvar.kmapper") version "3.0.1"
}

import com.sahsenvar.kmapper.gradle.KMapperWrapper

KMapper {
    wrapper = KMapperWrapper.KtResult // None (varsayılan) | KtResult | Flow | Custom("fqn")
}
```

Plugin (`io.github.sahsenvar:kmapper-gradle-plugin`, Maven Central'da) bunu işlemciye
`kmapper.wrapper` KSP seçeneği olarak iletir. KSP plugin'i olmadan uygulanırsa configuration
zamanında uyarır — ulaşacağı bir işlemci olmaz.

**2. Plugin olmadan doğrudan KSP seçeneği:**

```kotlin
ksp {
    arg("kmapper.wrapper", "KtResult") // ya da bir kullanıcı sarmalayıcısının tam nitelikli adı
}
```

Her iki yolla da, `wrapper = KMapperWrapper.Default::class`'ta bırakılan (yani aksini
söylemeyen) her `@MapTo`/`@MapFrom` bunu kullanır. Bir sarmalayıcıyı açıkça adlandıran bir
mapping — açıkça `KMapperWrapper.None::class` dahil — modül ayarını her zaman geçersiz kılar.

## Kendi sarmalayıcınızı yazmak

Built-in'ler **hiçbir ayrıcalıklı mekanizma kullanmaz** — bu, converter'lara ve validator'lara
uygulanan aynı kullanıcı–yazar eşitliği (parity) ilkesidir: kütüphanenin içeride yaptığı her
şeyi, siz de kendi kodunuzda aynı şekilde yapabilirsiniz. Bir sarmalayıcı, `KMapperWrapper`'ı
uygulayan, `@WrapperSuffix("...")` ile işaretlenmiş, şu şekilde bir ya da daha fazla public
`wrap` fonksiyonu olan bir `object`'tir:

```kotlin
fun <S, T> wrap(source: <S cinsinden R>, map: (S) -> T): <T cinsinden W>
```

Her `wrap` overload'u için derleyici bir receiver extension'ı üretir:
`fun R<Source>.to{Target}{suffix}(…dışsal parametreler): W<Target> = YourWrapper.wrap(this) { it.to{Target}(…) }`.

```kotlin
@WrapperSuffix("Either")
object EitherWrapper : KMapperWrapper {
    fun <S, T> wrap(source: S, map: (S) -> T): Either<Throwable, T> = Either.catch { map(source) }
}

@MapTo(UserDomain::class, wrapper = EitherWrapper::class)   // → toUserDomainEither()
data class UserDto(/* … */)
```

`KtResult` ve `Flow` tam olarak bu şekilde yazılmıştır — repodaki
`core/src/commonMain/kotlin/com/sahsenvar/kmapper/KMapperWrapper.kt` dosyasını açın, bir
kullanıcı sarmalayıcısının izlediği aynı şekli okuyorsunuz. Birden çok `wrap` overload'u olan
bir sarmalayıcı (ör. biri `S` biri `Flow<S>` için, built-in `Flow` sarmalayıcısı gibi) *her
overload için bir* receiver extension'ı üretir — tek bir sarmalayıcının hem
`Source.toXFlow()` hem `Flow<Source>.toXFlow()` eklemesinin sırrı budur.

`@WrapperSuffix`'in retention'ı **BINARY**'dir: sarmalayıcılar genellikle kendilerini
kullanan mapping'den farklı bir modülde yaşar (built-in'ler her zaman öyledir), bu yüzden
tüketen modülün KSP round'u suffix'i kaynaktan değil, derlenmiş classpath'ten okur.

Özel bir sarmalayıcıyı mapping bazında değil modül geneli varsayılan olarak kullanmak için tam
nitelikli adını verin: `KMapper { wrapper = KMapperWrapper.Custom("com.example.mapping.EitherWrapper") }`
(Gradle plugin'i) ya da `ksp { arg("kmapper.wrapper", "com.example.mapping.EitherWrapper") }`
(doğrudan KSP seçeneği).

## 2.x'ten geçiş

2.x'te her mapping yalnızca `toXResult(): Result<X>` üretiyordu, başka bir şey değil. 3.0.0'da
üretilen varsayılan fonksiyon fırlatıcı düz `toX()`'tir. Birini seçin:

- **Yeni varsayılanı benimseyin.** `dto.toUserResult().getOrThrow()` → `dto.toUser()`, ve hâlâ
  yerel olarak bir `Result` istediğiniz yerlerde `dto.toUserResult()` → `runCatching { dto.toUser() }`.
- **Eski API'yi, çağrı noktalarında hiçbir değişiklik yapmadan, modül genelinde koruyun.**
  Gradle plugin'ini uygulayıp `KMapper { wrapper = KMapperWrapper.KtResult }` ayarlayın, ya da
  plugin olmadan `ksp { arg("kmapper.wrapper", "KtResult") }`. `toXResult()`, artık ayrıca
  üretilen `toX()`'in yanında yeniden üretilir.

Her iki geçiş de mekaniktir ve ayar modül bazlı olduğundan modül modül, kademeli olarak
yapılabilir.

> Sıradaki: **[Built-in Converter'lar →](../tip-donusumu/builtin.md)**
