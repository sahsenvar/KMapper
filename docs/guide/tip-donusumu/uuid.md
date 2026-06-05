# UUID Converter'ları — converters-uuid

`converters-uuid` modülü, `kotlin.uuid.Uuid` (KMP ortak) ve `java.util.UUID` (JVM/Android) tipleri
için **scalar converter'lar** sağlar. Diğer scalar add-on'lar gibi, `@KMapperConfig(converters = [...])`
listesine eklenmeleri gerekir — otomatik keşfedilmezler.

> **Not:** `converters-uuid` sürüm **0.2.0** ile gelir; henüz Maven Central'da değildir.
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
            implementation("io.github.sahsenvar:kmapper-converters-uuid:0.2.0")
        }
    }
}
```

---

## Platform Desteği

| Kaynak küme | Kullanılabilir converter'lar |
|-------------|------------------------------|
| `commonMain` | `StringUuidConverter` (`String` ↔ `kotlin.uuid.Uuid`) |
| `jvmAndroidMain` | `JavaStringUuidConverter` (`String` ↔ `java.util.UUID`), `KotlinJavaUuidConverter` (`kotlin.uuid.Uuid` ↔ `java.util.UUID`) |

---

## commonMain Converter'ları

| Nesne | K | H | İleri | Geri |
|-------|---|---|-------|------|
| `StringUuidConverter` | `String` | `kotlin.uuid.Uuid` | `Uuid.parse(value)` | `value.toString()` |

`kotlin.uuid.Uuid`, Kotlin 2.1'den itibaren kararlıdır. Bu proje Kotlin 2.3.10 hedefler — `@OptIn` anotasyonu gerekmez.

---

## jvmAndroidMain Converter'ları

| Nesne | K | H | İleri | Geri |
|-------|---|---|-------|------|
| `JavaStringUuidConverter` | `String` | `java.util.UUID` | `UUID.fromString(value)` | `value.toString()` |
| `KotlinJavaUuidConverter` | `kotlin.uuid.Uuid` | `java.util.UUID` | `value.toJavaUuid()` | `value.toKotlinUuid()` |

---

## Kullanım

Converter'ları `@KMapperConfig(converters = [...])` listesine ekleyin:

```kotlin
import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.uuid.StringUuidConverter

@KMapperConfig(converters = [StringUuidConverter::class])
object AppMappers
```

Ardından modellerinizde `kotlin.uuid.Uuid` kullanın:

```kotlin
import kotlin.uuid.Uuid

@MapTo(UserDomain::class)
data class UserRemote(
    val id: String,           // "550e8400-e29b-41d4-a716-446655440000"
    val name: String,
)

data class UserDomain(
    val id: Uuid,
    val name: String,
)
```

Üretilen eşleştirme:

```kotlin
public fun UserRemote.toUserDomain(): UserDomain = UserDomain(
    id   = StringUuidConverter.convertToNonNull(id),
    name = name,
)
```

JVM/Android'de `kotlin.uuid.Uuid` ile `java.util.UUID` arasında köprü kurmak için:

```kotlin
import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.uuid.KotlinJavaUuidConverter

@KMapperConfig(converters = [KotlinJavaUuidConverter::class])
object AppMappers
```

---

## Hangi Converter'ı Seçmeli?

| Hedef tip | Converter |
|-----------|-----------|
| `kotlin.uuid.Uuid` (KMP ortak) | `StringUuidConverter` |
| `java.util.UUID` (yalnızca JVM/Android) | `JavaStringUuidConverter` |
| Köprü: `kotlin.uuid.Uuid` ↔ `java.util.UUID` | `KotlinJavaUuidConverter` |

---

Sonraki adım: [Okio Converter'ları →](okio.md) | Diğer kaynaklar: [@KMapperConfig](kmapperconfig.md)
