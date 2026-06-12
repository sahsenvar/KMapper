# @Validate — Alana Bağlı Doğrulama

Dönüşüm, *"bu değer şu tipe dönüşebilir mi?"* sorusunu yanıtlar. Doğrulama farklı bir soru
sorar: *"bu değer bu alan için kabul edilebilir mi?"* — e-postaya benzemesi gereken bir
e-posta, pozitif olması gereken bir miktar. `@Validate` bu kuralı **alana** çapalar.

## Tanımlama

```kotlin
import com.sahsenvar.kmapper.annotations.Validate
import com.sahsenvar.kmapper.validation.builtin.NotBlankValidator
import com.sahsenvar.kmapper.validators.EmailValidator

data class Member(
    @Validate(NotBlankValidator::class)
    val displayName: String,
    val email: String,
)

@MapTo(Member::class)
data class RegistrationForm(
    val displayName: String,
    @Validate(EmailValidator::class)
    val email: String,
)
```

`@Validate` istediğiniz sayıda validator object'i alır; sırayla çalışırlar, ilk hata kazanır.

## Alana bağlı: tek tanım, her yön

Validator bir mapping yönüne değil, alana aittir. Alanı *herhangi bir* üretilen mapping'e
katıldığında:

- **kaynak** alanıysa → değer dönüşümden **önce** doğrulanır;
- **hedef** alanıysa → üretilen değer dönüşümden **sonra** doğrulanır.

Validator'ları **kuralın sahibi olan modele** koyun — genellikle domain modeli. Tek tanım hem
`data → domain` hem `domain → presentation` yönünü korur.

## Hata her zaman serttir

```kotlin
val result = RegistrationForm("Grace", "not-an-email").toMemberResult()
println(result.exceptionOrNull()?.message)
// Validation failed for 'email': must be a valid email
```

Başarısız validator `MappingException.ValidationFailed`'dır — yol taşır, `Result` sınırından
teslim edilir ve **[fallback ladder](../temel-kullanim/null-safety.md) tarafından asla
emilmez**. Doğrulama kuralı beyan edilmiş bir değişmezdir; onu ihlal eden değerin modelinizde
işi yoktur — `null` olarak bile.

Bilinmeye değer iki semantik daha:

- **Null doğrulamayı atlar.** Validator'lar yalnızca non-null değer görür; eksiklik
  [ladder](../temel-kullanim/null-safety.md)'ın işidir, mevcut değerin kalitesi doğrulamanın.
- **Yalnızca mapping zamanı.** Sınıfı elle kurmak validator çalıştırmaz — onlar *sınırı*
  korur, constructor'ı değil.

## Kendi validator'ınız

`Validator<T>`'yi genişleten herhangi bir `object` olur — geçerliyse `null`, değilse gerekçe
dönün:

```kotlin
object EvenQuantityValidator : Validator<Int>(Int::class) {
    override fun validate(value: Int): String? =
        if (value % 2 == 0) null else "must be an even quantity (was $value)"
}
```

Parametreli kurallar için (uzunluk sınırı, regex, aralık) sıfırdan başlamak yerine
[built-in açık tabanları](validatorler.md) türetin.

> Sıradaki: **[Validator Kütüphanesi →](validatorler.md)**
