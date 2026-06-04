# Giriş

**kmap**, Kotlin Multiplatform için **derleme-zamanı (compile-time)** çalışan, KSP tabanlı bir nesne eşleme (object mapping) kütüphanesidir. Katmanlar arası model dönüşümlerini (`RemoteModel → DomainModel`, `DomainModel → UiModel` vb.) elle yazmak yerine, anotasyonlardan otomatik `toX()` uzantı fonksiyonları üretir.

```kotlin
@MapTo(UserDomain::class)
data class UserRemote(val id: String, val email: String) : RemoteModel

// kmap şunu üretir:
fun UserRemote.toUserDomain(): UserDomain = UserDomain(id = id, email = email)
```

> **Not:** Örneklerde sadeleştirilmiş gövde gösterilir. Üretilen gerçek kod ayrıca `KMapper.hasListeners` korumalı gözlemleme guard'ları içerir ve gövdesi `val result = …; return result` biçimindedir — bkz. [MappingListener](gozlemleme/listener.md).

## Neden kmap?

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
| `com.sahsenvar.kmapper:core` | KMP | Anotasyonlar, `MapTypeConverter`, `TypeConverterRegistry`, built-in converter'lar, `MappableEnum`, `MappingException`, `KMapper`/`MappingListener` |
| `com.sahsenvar.kmapper:processor` | JVM | KSP kod üreteci (`@MapTo`/`@MapFrom` → `toX()`) |
| `com.sahsenvar.kmapper:converters-compose` | KMP | `List` → `PersistentList`/`ImmutableList`/`ImmutableSet` sarmalayıcıları |
| `com.sahsenvar.kmapper:converters-arrow` | KMP | (yakında) Arrow `NonEmptyList` vb. |

## Sürüm Durumu

kmap şu an **ön-sürüm** aşamasındadır (`0.1.0-SNAPSHOT`). Maven Central yayını hazırlık aşamasındadır; o zamana kadar yerel Maven (`mavenLocal`) üzerinden tüketilebilir. Bkz. [Kurulum](baslarken/kurulum.md).

> Sonraki adım: **[Kurulum →](baslarken/kurulum.md)**
