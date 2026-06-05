# Doğrulama — @ValidateFrom / @ValidateTo

kmap mapper'ı yalnızca dönüştürme ve null kontrolü yapmakla kalmaz: `@ValidateFrom` ve
`@ValidateTo` anotasyonları sayesinde eşleştirme anında doğrulama kuralları da uygulayabilir; bu
sayede eşleştirilmiş nesne çağrıya dönmeden önce geçerliliği garanti altına alınır.

> **Not:** `@ValidateFrom`, `@ValidateTo`, `Validator<T>` taban sınıfı ve yerleşik validator'lar
> sürüm **0.2.0** ile gelir; henüz Maven Central'da değildir.
> Yayınlanana kadar `publishToMavenLocal` + `mavenLocal()` ile kullanın.
> `core` ve `processor` hâlâ Maven Central'dan `0.1.0` olarak çekilebilir.

---

## Eşleştirmede Doğrulama Neden Gerekli?

Bir `RemoteModel`, `DomainModel`'e eşlenirken mapper zaten tip dönüşümünü ve null güvenliğini
ele alır. Doğrulamayı aynı adıma eklemek, ayrı bir doğrulama geçişini ortadan kaldırır ve
yapımını tamamlamış her domain nesnesinin geçerli olduğunu garanti eder.

Başarısız doğrulamalar `MappingException.ValidationFailed` fırlatır — bu, diğer eşleştirme
hataları ile aynı istisna yolundan geçer.

---

## İki Anotasyon

Her iki anotasyon da **kaynak property**'e yerleştirilir (`@UseMapTypeConverter` ve
`@MapDefaultValue` ile aynı kural):

```kotlin
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ValidateFrom(vararg val validators: KClass<*>)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ValidateTo(vararg val validators: KClass<*>)
```

| Anotasyon | Ne zaman çalışır | Neyi doğrular |
|-----------|-----------------|---------------|
| `@ValidateFrom` | Dönüşümden önce | **Kaynak** değeri, olduğu gibi |
| `@ValidateTo` | Dönüşümden sonra | Atamadan önce **üretilen nihai** değer |

Her iki anotasyon da `vararg` kabul eder — tek bir anotasyonda birden fazla validator listelenebilir.
Aynı property'de birlikte de kullanılabilirler. Yürütme sırası her zaman şöyledir:
`@ValidateFrom` kontrolleri önce, ardından dönüşüm, ardından `@ValidateTo` kontrolleri.

**Hızlı başarısızlık:** ilk başarısız validator anında fırlatır. Aynı alan üzerindeki sonraki
validator'lar değerlendirilmez.

---

## Validator\<T\>

`:core`'dan `Validator<T>`'yi `object` tekili olarak uygulayın:

```kotlin
abstract class Validator<T : Any>(val targetType: KClass<T>) {
    /**
     * [value] geçerliyse null döner.
     * Geçersizse insan tarafından okunabilir bir neden dizgisi döner.
     *
     * NULL OLMAYAN bir değer alır — null yönetimi mevcut
     * null-güvenlik mekanizması tarafından yapılır; validator'lar null görmez.
     */
    abstract fun validate(value: T): String?
}
```

Temel kurallar:
- **`object` olmalıdır** — processor, `MyValidator.validate(x)` gibi doğrudan çağrıları tam
  nitelikli adla derleme zamanında üretir. Reflection yok, factory yok, tam KMP güvenliği.
- **Yalnızca null olmayan değerler alır** — validator'lar `null` görmez. Kaynak ve hedef
  alanlardaki null yönetimi mevcut null-güvenlik kuralları tarafından yapılır.
- **`@KMapperConfig` kaydı gerekmez** — validator'lar kaynak property üzerinde doğrudan
  `@ValidateFrom(MyValidator::class)` biçiminde referans alınır. Processor, FQN'i tüketicinin
  kendi KSP derleme sürecinde çözümler — tıpkı `@UseMapTypeConverter`'ın converter FQN'ini
  okuması gibi. Bu durum, KSP2 çapraz modül izolasyonu nedeniyle `@KMapperConfig(converters=[...])`
  / `@KMapperConfig(wrappers=[...])` ile açıkça listelenmesi gereken converter ve wrapper'lardan
  farklıdır.
- **Tip güvenli** — `String` alanına `Validator<Int>` uygulamak derleme hatasına yol açar,
  çalışma zamanı hatasına değil.

---

## Yerleşik Validator'lar (core)

`:core` ile birlikte `com.sahsenvar.kmapper.validation.builtin` paketinde üç validator gelir.
Ekstra bağımlılık gerekmez.

| Nesne | T | Geçersiz koşul | Mesaj |
|-------|---|----------------|-------|
| `NotBlankValidator` | `String` | `value.isBlank()` | `"must not be blank"` |
| `NotEmptyStringValidator` | `String` | `value.isEmpty()` | `"must not be empty"` |
| `NotEmptyCollectionValidator` | `Collection<*>` | `value.isEmpty()` | `"must not be empty"` |

---

## validators Add-on'u (kmapper-validators)

`:validators` modülü, yaygın formatlar için alan odaklı validator'lar ekler.

> **Not:** `kmapper-validators` sürüm **0.2.0** ile gelir; henüz Maven Central'da değildir.
> Yayınlanana kadar `publishToMavenLocal` + `mavenLocal()` ile kullanın.

```kotlin
// build.gradle.kts (tüketen modül)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.sahsenvar:kmapper-core:0.1.0")
            implementation("io.github.sahsenvar:kmapper-validators:0.2.0")
        }
    }
}
```

Sağlanan validator'lar (`com.sahsenvar.kmapper.validators` paketi):

| Nesne | T | Geçersiz koşul | Mesaj |
|-------|---|----------------|-------|
| `EmailValidator` | `String` | `^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$` eşleşmesi yok | `"must be a valid email"` |
| `UrlValidator` | `String` | `^https?://[^\s/$.?#].[^\s]*$` eşleşmesi yok | `"must be a valid URL"` |

---

## Özel Validator'lar

Herhangi bir modülde `Validator<T>`'yi `object` olarak alt sınıflayın. `:validators`'a bağımlılık
gerekmez:

```kotlin
object MinAgeValidator : Validator<Int>(Int::class) {
    override fun validate(value: Int): String? =
        if (value < 18) "must be at least 18" else null
}
```

Doğrudan kaynak property üzerinde referans alın:

```kotlin
@MapTo(UserDomain::class)
data class UserRemote(
    @ValidateTo(MinAgeValidator::class)
    val age: Int,

    @ValidateFrom(NotBlankValidator::class)
    @ValidateTo(EmailValidator::class)
    val email: String?,
)
```

---

## Tam Örnek

```kotlin
import com.sahsenvar.kmapper.annotations.MapTo
import com.sahsenvar.kmapper.annotations.ValidateFrom
import com.sahsenvar.kmapper.annotations.ValidateTo
import com.sahsenvar.kmapper.validation.builtin.NotBlankValidator
import com.sahsenvar.kmapper.validators.EmailValidator

@MapTo(RegistrationDomain::class)
data class RegistrationRemote(
    @ValidateFrom(NotBlankValidator::class)
    @ValidateTo(EmailValidator::class)
    val email: String?,       // nullable kaynak → null olmayan hedef

    @ValidateTo(MinAgeValidator::class)
    val age: Int,
)

data class RegistrationDomain(
    val email: String,
    val age: Int,
)
```

Processor tarafından üretilen yaklaşık kod:

```kotlin
public fun RegistrationRemote.toRegistrationDomain(): RegistrationDomain = RegistrationDomain(
    email = run {
        email?.let { __s ->
            NotBlankValidator.validate(__s)?.let { m ->
                throw MappingException.ValidationFailed("email", m)
            }
        }
        val __result = email ?: throw MappingException.RequiredFieldMissing("email")
        EmailValidator.validate(__result)?.let { throw MappingException.ValidationFailed("email", it) }
        __result
    },
    age = run {
        val __result = age
        MinAgeValidator.validate(__result)?.let { throw MappingException.ValidationFailed("age", it) }
        __result
    },
)
```

---

## MappingException.ValidationFailed

```kotlin
class ValidationFailed(val field: String, val reason: String)
    : MappingException("Validation failed for '$field': $reason")
```

`field` her zaman **hedef** alan adını taşır (`RequiredFieldMissing` ile tutarlı). `reason`,
`Validator.validate()` tarafından döndürülen dizgidir.

---

Diğer kaynaklar: [Hata Yönetimi](../hata-yonetimi/istisnalar.md) | [Tip Dönüşümü](../tip-donusumu/)
