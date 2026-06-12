# KMapper

**Kotlin Multiplatform için derleme zamanı object mapping.** Wire modellerinizi annotation'larla
işaretlersiniz; KMapper mapping fonksiyonlarını derleme sırasında KSP ile üretir — reflection
yok, çalışma zamanı registry'si yok, elde senkron tutulacak boilerplate yok.

```kotlin
data class User(val id: Long, val joined: LocalDate)

@MapTo(User::class)
data class UserResponse(val id: Long, val joined: String)

// Derleme zamanında sizin için üretilir:
val user: Result<User> = UserResponse(7, "2026-06-12").toUserResult()
```

Bu üç satırı elle yazdığınız her mapper'dan ayıran üç şey var:

1. **Hatalar birer değerdir.** Üretilen fonksiyon `Result<User>` döner — bozuk wire verisi,
   parsing yığınının derinliklerinde patlayan bir crash değil, tipli bir `MappingException`
   olarak gelir.
2. **Hatalar yol (path) taşır.** Üç nesne derinlikteki bozuk bir tarih
   `Cannot convert order.customer.joined: …` der — *hangi kaydın hangi alanı* kırıldı, bilirsiniz.
3. **Dönüşüm görünür ve değiştirilebilirdir.** `String → LocalDate` bir built-in converter
   nesnesine çözümlendi. Sizin converter'larınız da *aynı* çözümlemeye, aynı öncelik
   kurallarıyla ve aynı derleme zamanı kontrolleriyle katılır.

## Neden KMapper?

- **Reflection yok, KMP-native.** Üretilen Kotlin kodu Android, JVM ve iOS'ta aynı şekilde
  çalışır. Mapping'leri bir kez `commonMain`'de tanımlarsınız.
- **Derleme zamanı güvenliği.** Eksik converter, eşlenemeyen alan ya da sessizce veri
  kaybedecek bir dönüşüm — production'da bir olay değil, **yol gösteren mesajıyla bir build
  hatası**.
- **Tasarım gereği dürüst hata yönetimi.** *Fallback ladder* tek bir bozuk alanın bütün
  payload'u çökertmesini engeller; emilen her hata gözlemlenebilirlik kanalına raporlanır.
  Hiçbir şey sessizce kaybolmaz; hiçbir şey varsayılan olarak crash etmez.
- **Kullanıcı–yazar eşitliği (parity).** Kütüphanenin içeride kullandığı her yetenek —
  converter nesneleri, validator'lar, collection wrapper'ları, hatta "bu yön bilerek
  desteklenmiyor" deme hakkı — aynı API üzerinden sizin kodunuza da açık.

## Her şeyin arkasındaki tasarım ilkesi

> **Sessizce yanlış bir değer, bir hatadan daha kötüdür.**

KMapper asla veri uydurmaz (`ordinal` ile enum eşleme yok, sessizce kırpan `Long → Int` yok)
ve asla hata gizlemez (her esneklik tipte ya da bir annotation'da beyan edilir; her emilen
hata gözlemlenebilir). Güvenle yapılamayacak bir şey istediğinizde bunu derleme zamanında
duyarsınız.

## Artifact'ler

Grup `io.github.sahsenvar`:

| Artifact | Platform | Amaç |
|----------|----------|------|
| `kmapper-core` | KMP | Bağımsız runtime: exception'lar, converter taban sınıfı + built-in'ler, validator'lar, seam'ler, gözlemlenebilirlik. Kod üretimi olmadan da kullanılabilir. |
| `kmapper-annotations` | KMP | Tanım annotation'ları (`@MapTo`, `@FieldMap`, `@ConvertWith`, …) |
| `kmapper-compiler` | JVM (KSP) | Kod üreteci |
| `kmapper-converters-immutable` | KMP | kotlinx-collections-immutable wrapper'ları |
| `kmapper-converters-arrow` | KMP | Arrow `NonEmptyList`/`NonEmptySet` wrapper'ları, `Option` |
| `kmapper-converters-datetime` | JVM/Android | `java.time` converter'ları ve kotlinx ↔ java köprüleri |
| `kmapper-converters-bignumber` | KMP / JVM+Android | ionspin ve `java.math` büyük sayılar |
| `kmapper-converters-uuid` | KMP / JVM+Android | `kotlin.uuid.Uuid` ve `java.util.UUID` |
| `kmapper-converters-okio` | KMP | `ByteString` (UTF-8/Base64/Hex), `Path` |
| `kmapper-converters-uri` | JVM / Android / iOS | platform URI tipleri |
| `kmapper-validators` | KMP | `@Validate` için e-posta, telefon, IP, UUID, Luhn… |

## Nereden başlamalı?

- Yeni misiniz? **[Kurulum](baslarken/kurulum.md)** →
  **[İlk Mapper'ınız](baslarken/ilk-mapper.md)** — beş dakikada çalışır halde.
- Felsefeyi üç kuralda mı istiyorsunuz? **[Zihinsel Model](baslarken/zihinsel-model.md)**.
- Kod okumayı mı tercih edersiniz? **[Çalıştırılabilir örnek galerisi](baslarken/ornekler.md)**
  her özelliği basitten gelişmişe sırayla kapsar.
