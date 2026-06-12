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
    implementation("io.github.sahsenvar:kmapper-core:2.0.1")
    implementation("io.github.sahsenvar:kmapper-annotations:2.0.1")
    ksp("io.github.sahsenvar:kmapper-compiler:2.0.1")
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
            implementation("io.github.sahsenvar:kmapper-core:2.0.1")
            implementation("io.github.sahsenvar:kmapper-annotations:2.0.1")
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", "io.github.sahsenvar:kmapper-compiler:2.0.1")
    add("kspJvm", "io.github.sahsenvar:kmapper-compiler:2.0.1")
    add("kspIosArm64", "io.github.sahsenvar:kmapper-compiler:2.0.1")
    add("kspIosSimulatorArm64", "io.github.sahsenvar:kmapper-compiler:2.0.1")
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
implementation("io.github.sahsenvar:kmapper-converters-immutable:2.0.1") // PersistentList vb.
implementation("io.github.sahsenvar:kmapper-converters-arrow:2.0.1")     // NonEmptyList, Option
implementation("io.github.sahsenvar:kmapper-converters-datetime:2.0.1")  // java.time + köprüler
implementation("io.github.sahsenvar:kmapper-converters-bignumber:2.0.1") // BigDecimal/BigInteger
implementation("io.github.sahsenvar:kmapper-converters-uuid:2.0.1")      // Uuid / java.util.UUID
implementation("io.github.sahsenvar:kmapper-converters-okio:2.0.1")      // ByteString, Path
implementation("io.github.sahsenvar:kmapper-converters-uri:2.0.1")       // URI / Uri / NSURL
implementation("io.github.sahsenvar:kmapper-validators:2.0.1")           // Email, E.164, IP, …
```

kotlinx-datetime tipleri (`LocalDate`, `Instant`, …) için add-on gerekmez — bunların
`String`/`Long` converter'ları core built-in'dir ve `kmapper-core`, kotlinx-datetime'ı API
bağımlılığı olarak getirir.

## Sürüm uyumluluğu

| KMapper | Kotlin | KSP |
|---------|--------|-----|
| 2.x | 2.3+ | KSP2 (`2.3.x-2.x`) |

Çok modüllü projelerde compiler yalnızca mapping *tanımlayan* modüllere gerekir; üretilen
fonksiyonları yalnızca çağıran modüllere runtime yeter. Ayrıntılar:
[Çok Modüllü Projeler](../ileri/cok-modullu.md).

> Sıradaki: **[İlk Mapper'ınız →](ilk-mapper.md)**
