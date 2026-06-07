# Giriş

**KMapper**, Kotlin Multiplatform için **derleme-zamanı (compile-time)** çalışan, KSP tabanlı bir nesne eşleme (object mapping) kütüphanesidir. Katmanlar arası model dönüşümlerini (`RemoteModel → DomainModel`, `DomainModel → UiModel` vb.) elle yazmak yerine, anotasyonlardan otomatik `toX()` uzantı fonksiyonları üretir.

```kotlin
@MapTo(UserDomain::class)
data class UserRemote(val id: String, val email: String) : RemoteModel

// KMapper şunu üretir:
fun UserRemote.toUserDomain(): UserDomain = UserDomain(id = id, email = email)
```

> **Not:** Örneklerde sadeleştirilmiş gövde gösterilir. Üretilen gerçek kod ayrıca `KMapper.hasListeners` korumalı gözlemleme guard'ları içerir ve gövdesi `val result = …; return result` biçimindedir — bkz. [MappingListener](gozlemleme/listener.md).

## Neden KMapper?

- **Reflection yok.** Tüm eşleme kodu derleme zamanında üretilir. Bu, çalışma-zamanı maliyetini sıfırlar ve **Kotlin/Native (iOS) dostu** kılar — reflection'ın kısıtlı olduğu platformlarda sorunsuz çalışır.
- **Tip ve null güvenli.** Tip uyuşmazlıkları, eksik converter'lar ve eşlenemeyen alanlar **derleme hatası** olur; runtime'da sürpriz yaşamazsınız.
- **Sıfır boilerplate.** Elle mapper fonksiyonu yazmazsınız; bakım yükü kalkar.
- **KMP-yerli.** `commonMain`'de tanımlarsınız; Android ve iOS aynı üretilen kodu paylaşır.

## Tasarım İlkeleri

1. **Sessiz yanlış davranış düşmandır.** Bir dönüşüm belirsiz veya eksikse, kütüphane sessizce yanlış bir değer üretmez — ya derleme zamanında durdurur ya da tipli bir istisna (`MappingException`) fırlatır. (Enum'larda `ordinal`/`name` gibi kırılgan varsayılanlar bu yüzden **yoktur**.)
2. **Derleme-zamanı güvenlik önce gelir.** Eksik converter, eşlenemeyen alan, garantili-sonsuz döngü → hepsi derleme hatası.
3. **Modüler converter'lar.** Çekirdek küçük kalır; `kotlinx.collections.immutable`, Arrow gibi bağımlılıklar yalnızca ihtiyaç duyulan ek artifact'larda yaşar.
4. **Açık niyet.** Global converter listesi ve alan-bazlı override; "sihir" değil, okunabilir ve izlenebilir kurallar.

## Modüller

| Artifact | Platform | Sorumluluk |
|----------|----------|-----------|
| `io.github.sahsenvar:kmapper-core` | KMP | Anotasyonlar, `MapTypeConverter`, `TypeConverterRegistry`, built-in converter'lar, `MappableEnum`, `MappingException`, `KMapper`/`MappingListener` |
| `io.github.sahsenvar:kmapper-processor` | JVM | KSP kod üreteci (`@MapTo`/`@MapFrom` → `toX()`) |
| `io.github.sahsenvar:kmapper-converters-immutable` | KMP | `List` → `PersistentList`/`ImmutableList`/`ImmutableSet`/`PersistentSet` sarmalayıcıları |
| `io.github.sahsenvar:kmapper-converters-arrow` | KMP | Arrow `NonEmptyList`, `NonEmptySet`, `Option<T>` eşleştirme |
| `io.github.sahsenvar:kmapper-converters-datetime` | KMP (kotlinx) / JVM+Android | `String`/`Long` ↔ `LocalDate`, `LocalDateTime`, `Instant` vb. |
| `io.github.sahsenvar:kmapper-converters-bignumber` | KMP (ionspin) / JVM+Android | `String`/`Double`/`Long`/`Int` ↔ `BigDecimal`, `BigInteger` |
| `io.github.sahsenvar:kmapper-converters-uuid` | KMP / JVM+Android | `String` ↔ `kotlin.uuid.Uuid`; `String`/`Uuid` ↔ `java.util.UUID` |
| `io.github.sahsenvar:kmapper-converters-okio` | KMP | `String`/`ByteArray` ↔ `okio.ByteString`; `String` ↔ `okio.Path` |
| `io.github.sahsenvar:kmapper-converters-uri` | JVM / Android / iOS | `String` ↔ `java.net.URI` / `android.net.Uri` / `NSURL` |
| `io.github.sahsenvar:kmapper-validators` | KMP | `EmailValidator`, `UrlValidator` — `@ValidateFrom`/`@ValidateTo` için |

## Sürüm Durumu

KMapper **1.0.0**, [Maven Central](https://central.sonatype.com/artifact/io.github.sahsenvar/kmapper-core)'da yayınlanmıştır — tüm 10 modül dahil. Bkz. [Kurulum](baslarken/kurulum.md).

> Sonraki adım: **[Kurulum →](baslarken/kurulum.md)**
