# İlk Mapper'ınız

API yanıtından güvenle eşlenmiş bir domain nesnesine beş dakika — bilerek tetiklenmiş ilk
mapping hatanız dahil.

## 1. İki model

Bir wire modeli (API'nin gönderdiği) ve bir domain modeli (uygulamanızın istediği):

```kotlin
import com.sahsenvar.kmapper.annotations.MapTo
import kotlinx.datetime.LocalDate

data class User(
    val id: Long,
    val email: String,
    val joined: LocalDate,
)

@MapTo(User::class)
data class UserResponse(
    val id: Long,
    val email: String,
    val joined: String, // wire'da ISO tarih
)
```

`@MapTo` **wire modelinin** üzerinde durur — kontrol etmediğiniz taraf, kontrol ettiğiniz
tarafa nasıl dönüşeceğini bildiren taraftır.

## 2. Derleyin

```bash
./gradlew build
```

KSP bir extension fonksiyonu üretir:

```kotlin
fun UserResponse.toUser(): User
```

İki alan doğrudan kopyalanır; `joined` ise built-in `LocalDateStringConverter`'dan geçer
(`String ↔ LocalDate`, 35 built-in çiftten biri — kayıt gerekmez).

## 3. Kullanın

```kotlin
val user: User = UserResponse(7, "grace@navy.mil", "2026-06-12").toUser()
```

Üretilen fonksiyon düz `User` döner ve sert bir hatada tipli bir `MappingException` fırlatır.
Hatayı yine de bir değer olarak mı istiyorsunuz — `Result<User>`, bir `Flow<User>` ya da kendi
sarmalayıcınız? Bkz. [Dönüş Sarmalayıcıları](../temel-kullanim/donus-sarmalayicilari.md); bu,
`toUser()`'ın yerine geçen değil, üzerine eklenen isteğe bağlı bir katman — mapping bazında ya
da modül genelinde.

## 4. Bilerek bozun

```kotlin
val broken = UserResponse(7, "grace@navy.mil", "not-a-date").toUser()
// fırlatır: MappingException.TypeConversionFailed:
// Cannot convert joined: String -> LocalDate failed for value "not-a-date" …
```

Exception, tam olarak hangi alanın bozulduğunu söyler. İç içe modellerde yol da onunla
birlikte büyür (`customer.address.zipCode`); bkz.
[İç İçe Modeller](../temel-kullanim/nested.md).

## 5. Peki wire'daki değer hiç gelmezse?

"Eksik"in ne anlama geleceğini domain alanının tipi söylesin:

```kotlin
data class User(
    val id: Long,
    val email: String,
    val joined: LocalDate? = null, // nullable: eksik/bozuk tarih null olur
)
```

Nullable ya da default'lu bir hedef alan *beyan edilmiş bir kaçış noktasıdır*: eksiklik ona
sessizce akar; **bozuk** bir değer de oraya emilir — ama her emilme gözlemlenebilirlik
kanalına raporlanır, yani production telemetriniz onu yine görür. Bu sıralama
(`değer > default > null > hata`) **fallback ladder**'dır — KMapper'ın kalbi.

> Sıradaki: **[Zihinsel Model →](zihinsel-model.md)** — az önce gördüğünüz her şeyi açıklayan
> üç kural.
