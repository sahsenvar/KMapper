# Kurulum

KMapper birlikte çalışan üç artifact'ten ve isteğe bağlı add-on'lardan oluşur. Hepsi
[Maven Central](https://central.sonatype.com/artifact/io.github.sahsenvar/kmapper-core)'da,
`io.github.sahsenvar` grubu altında.

| Artifact | Ne zaman gerekli… |
|----------|--------------------|
| `kmapper-core` | her zaman — runtime (exception'lar, converter'lar, validator'lar, seam'ler) |
| `kmapper-annotations` | mapping'leri annotation'la tanımlıyorsanız (neredeyse her zaman) |
| `kmapper-compiler` | yukarıdakiyle birlikte — annotation'ları okuyan KSP işlemcisi |

> Yalnızca `kmapper-core` da geçerli bir kurulum: KSP olmadan, elle yazılmış mapper'lar için
> üretilen kodun kullandığı seam'lerin aynısını sunar. Bkz.
> [`CoreOnlyMapping` örneği](ornekler.md).

## JVM / Android (tek platform)

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.3.10" // ya da com.android.application / kotlin("android")
    id("com.google.devtools.ksp") version "2.3.10-2.0.5"
}

dependencies {
    implementation("io.github.sahsenvar:kmapper-core:3.0.0")
    implementation("io.github.sahsenvar:kmapper-annotations:3.0.0")
    ksp("io.github.sahsenvar:kmapper-compiler:3.0.0")
}
```

## Kotlin Multiplatform

Modelleri ve mapping'leri `commonMain`'de tanımlayın; işlemciyi her derleme hedefi için
kaydedin:

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform") version "2.3.10"
    id("com.google.devtools.ksp") version "2.3.10-2.0.5"
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("io.github.sahsenvar:kmapper-core:3.0.0")
            implementation("io.github.sahsenvar:kmapper-annotations:3.0.0")
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", "io.github.sahsenvar:kmapper-compiler:3.0.0")
    add("kspJvm", "io.github.sahsenvar:kmapper-compiler:3.0.0")
    add("kspIosArm64", "io.github.sahsenvar:kmapper-compiler:3.0.0")
    add("kspIosSimulatorArm64", "io.github.sahsenvar:kmapper-compiler:3.0.0")
}

// Her derlemenin commonMain'de üretilen kaynakları görmesi için:
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}
```

## Add-on'lar (isteğe bağlı)

Her add-on bağımsız bir KMP artifact'idir; yalnızca modellerinizin kullandığını ekleyin:

```kotlin
implementation("io.github.sahsenvar:kmapper-converters-immutable:3.0.0") // PersistentList vb.
implementation("io.github.sahsenvar:kmapper-converters-arrow:3.0.0")     // NonEmptyList, Option
implementation("io.github.sahsenvar:kmapper-converters-datetime:3.0.0")  // java.time + köprüler
implementation("io.github.sahsenvar:kmapper-converters-bignumber:3.0.0") // BigDecimal/BigInteger
implementation("io.github.sahsenvar:kmapper-converters-uuid:3.0.0")      // Uuid / java.util.UUID
implementation("io.github.sahsenvar:kmapper-converters-okio:3.0.0")      // ByteString, Path
implementation("io.github.sahsenvar:kmapper-converters-uri:3.0.0")       // URI / Uri / NSURL
implementation("io.github.sahsenvar:kmapper-validators:3.0.0")           // Email, E.164, IP, …
```

kotlinx-datetime tipleri (`LocalDate`, `Instant`, …) için add-on gerekmez — bunların
`String`/`Long` converter'ları core built-in'dir ve `kmapper-core`, kotlinx-datetime'ı API
bağımlılığı olarak getirir. `kmapper-core`, `KMapperWrapper.Flow` için kotlinx-coroutines-core'u
da (`api`) getirir — bkz. [Dönüş Sarmalayıcıları](../temel-kullanim/donus-sarmalayicilari.md).

## İsteğe bağlı: Gradle plugin'i

Her `@MapTo`/`@MapFrom`'da `wrapper = …` tekrarlamadan modül geneli bir
[dönüş sarmalayıcısı](../temel-kullanim/donus-sarmalayicilari.md) istiyorsanız (ör. modüldeki
her mapping'in ayrıca `toXResult()` üretmesi), `ksp { arg(...) }` yerine (ya da onunla birlikte)
plugin'i uygulayın:

```kotlin
// build.gradle.kts
plugins {
    id("io.github.sahsenvar.kmapper") version "3.0.0"
}

import com.sahsenvar.kmapper.gradle.KMapperWrapper

KMapper {
    wrapper = KMapperWrapper.KtResult // None (varsayılan) | KtResult | Flow | Custom("fqn")
}
```

Plugin, Maven Central'dan çözümlenen `io.github.sahsenvar:kmapper-gradle-plugin`'dir. KSP
plugin'inin de uygulandığı bir modüle uygulanmalıdır — ayarını işlemciye `kmapper.wrapper`
seçeneği olarak iletir. Plugin olmadan aynı seçeneği doğrudan verin:
`ksp { arg("kmapper.wrapper", "KtResult") }`.

## Sürüm uyumluluğu

| KMapper | Kotlin | KSP |
|---------|--------|-----|
| 3.x | 2.3+ | KSP2 (`2.3.x-2.x`) |

Çok modüllü projelerde compiler yalnızca mapping *tanımlayan* modüllere gerekir; üretilen
fonksiyonları yalnızca çağıran modüllere runtime yeter. Ayrıntılar:
[Çok Modüllü Projeler](../ileri/cok-modullu.md).

> Sıradaki: **[İlk Mapper'ınız →](ilk-mapper.md)**
