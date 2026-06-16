# UUID — converters-uuid

`kotlin.uuid.Uuid` (KMP) ve `java.util.UUID` (JVM/Android) için converter'lar.

## Kurulum

```kotlin
commonMain.dependencies {
    implementation("io.github.sahsenvar:kmapper-converters-uuid:2.2.0")
}
```

```kotlin
@KMapperConfig(converters = [StringUuidConverter::class])
object AppMapperConfig
```

## Converter'lar (`com.sahsenvar.kmapper.uuid`)

| Object | Çift | Platform |
|--------|------|----------|
| `StringUuidConverter` | `String ↔ kotlin.uuid.Uuid` | tümü (commonMain) |
| `JavaStringUuidConverter` | `String ↔ java.util.UUID` | JVM/Android |
| `KotlinJavaUuidConverter` | `kotlin.uuid.Uuid ↔ java.util.UUID` | JVM/Android |

Parse, kanonik 8-4-4-4-12 hex biçimini kabul eder; bozuk girdi fırlatır ve
[ladder'a](../temel-kullanim/null-safety.md) biner. Tipi dönüştürmeden UUID biçimli bir
string'i yalnızca *doğrulamak* için `kmapper-validators`'taki
[`UuidStringValidator`](../dogrulama/validatorler.md)'ı kullanın.

## `Uuid ↔ String` neden core built-in değil?

`kotlin.uuid.Uuid`, Kotlin 2.3'te hâlâ `@ExperimentalUuidApi`. Core built-in üretilen koda
otomatik bağlanır; bu da deneysel opt-in'i bütün tüketicilere dayatırdı. Add-on bu seçimi
size bırakır; API mezun olduğunda çift core'a taşınacak.

> Sıradaki: **[Okio →](okio.md)**
