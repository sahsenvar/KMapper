# Alan Eşleme ve Ignore Ailesi

İsim eşleşmesi alanların çoğunu bedavaya halleder. Gerisi için araçlar bunlar.

## @FieldMap — farklı isimler

```kotlin
@MapTo(User::class)
data class UserResponse(
    @FieldMap("displayName")
    val user_name: String, // wire'da snake_case -> domain'de displayName
)
```

Birden çok `@MapTo` hedefi varken bir yeniden adlandırma tek hedefe daraltılabilir:

```kotlin
@MapTo(User::class)
@MapTo(AuditEntry::class)
data class UserResponse(
    @FieldMap("displayName", targetClass = User::class)
    @FieldMap("actorName", targetClass = AuditEntry::class)
    val user_name: String,
)
```

## @IgnoreMap — eşleşmeyi bilerek kırmak

`@IgnoreMap`, mapper'ın bu alanı otomatik eşleme sırasında yok saymasını sağlar. Değeri asla
mapping'den akmaz; hedefteki yuva ya constructor default'una düşer ya da — default yoksa —
üretilen fonksiyonun **zorunlu parametresi** olur:

```kotlin
data class Account(
    val email: String,
    val passwordHash: String, // default'u yok…
)

@MapTo(Account::class)
data class SignUpRequest(
    val email: String,
    @IgnoreMap
    val passwordHash: String, // isim aynı ama bunun ham haliyle kopyalanmasını İSTEMİYORSUNUZ
)

// üretilen: fun SignUpRequest.toAccountResult(passwordHash: String): Result<Account>
val account = request.toAccountResult(passwordHash = hash(request.passwordHash))
```

## @IgnoreDefaultValue — "default, wire fallback'i değildir"

Constructor default'u normalde [ladder](null-safety.md)'ın 2. basamağıdır: eksiklik sessizce
default olur. Ama bazen default yalnızca elle nesne kurma kolaylığıdır ve wire o değeri
**mutlaka** göndermelidir. Hedef alandaki `@IgnoreDefaultValue`, mapping'in default'u yok
saymasını sağlar — eksiklik yeniden sert bir `RequiredFieldMissing` olur:

```kotlin
data class Account(
    @IgnoreDefaultValue
    val plan: String = "FREE", // kodda Account() FREE der; wire her zaman plan göndermek zorunda
)
```

## Çağıranın sağladığı parametreler

**Kaynakta karşılığı olmayan ve default'u bulunmayan** bir hedef alanı derleme hatası
üretmez — üretilen fonksiyonun zorunlu parametresi olur. Wire'ın bilemeyeceği bağlamı
enjekte etme mekanizması budur:

```kotlin
data class Payment(
    val id: Long,
    val fetchedAt: Instant, // wire'da yok
)

@MapTo(Payment::class)
data class PaymentResponse(val id: Long)

// üretilen:
fun PaymentResponse.toPaymentResult(fetchedAt: Instant): Result<Payment>
```

## Fallback mekanizması constructor default'larıdır

`@MapDefaultValue` tarzı bir annotation yok: **Kotlin constructor default'u, fallback'in
kendisidir** ve KMapper onu *argümanı atlayarak* kullanır — elle yazdığınız kodun gördüğü
default'un aynısı, tek yerde tanımlı:

```kotlin
data class Settings(
    val theme: String = "system", // wire'da eksik/bozuk tema -> "system"
)
```

> Sıradaki: **[Null Güvenliği ve Fallback Ladder →](null-safety.md)**
