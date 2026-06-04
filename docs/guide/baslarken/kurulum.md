# Kurulum

kmap iki zorunlu parçadan oluşur: çalışma-zamanı kütüphanesi (`core`) ve KSP işlemcisi (`processor`). Koleksiyon sarmalayıcıları gibi ekler isteğe bağlıdır.

## Gereksinimler

- Kotlin **2.1+** (KSP2 ile)
- KSP (Kotlin Symbol Processing) Gradle eklentisi
- Kotlin Multiplatform veya saf JVM/Android projesi

## 1. Depoları ekle

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal() // ön-sürüm aşamasında: kmap'i lokal Maven'dan tüketmek için
    }
}
```

> **Ön-sürüm notu:** kmap henüz Maven Central'da yayınlanmadığı için şimdilik `./gradlew publishToMavenLocal` ile kendi makinende yayınlanır ve `mavenLocal()` üzerinden çekilir. Central yayını sonrası `mavenLocal()` gereksiz olacak.

## 2. KSP eklentisini uygula

`build.gradle.kts` (tüketen modül):

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
}
```

## 3. Bağımlılıkları ekle

### Kotlin Multiplatform modülü

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.sahsenvar.kmapper:core:0.1.0-SNAPSHOT")
            // İsteğe bağlı: immutable koleksiyon desteği
            implementation("com.sahsenvar.kmapper:converters-compose:0.1.0-SNAPSHOT")
        }
    }
}

dependencies {
    // Eşleme kodunu commonMain için üret
    add("kspCommonMainMetadata", "com.sahsenvar.kmapper:processor:0.1.0-SNAPSHOT")
}

// KMP + KSP bağlama: commonMain metadata'sı derlemeden ÖNCE işlensin
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}
```

### Saf JVM / Android modülü

KMP değilsen kurulum daha basittir:

```kotlin
dependencies {
    implementation("com.sahsenvar.kmapper:core:0.1.0-SNAPSHOT")
    ksp("com.sahsenvar.kmapper:processor:0.1.0-SNAPSHOT")
}
```

## Üretilen kod nereye gider?

KSP, eşleme uzantılarını standart KSP çıktısı altına yazar:

```
build/generated/ksp/.../<KaynakSınıf>Mappers.kt
```

Bu dosyalar otomatik olarak derleme yoluna eklenir; commit etmene veya elle düzenlemene gerek yoktur.

## Doğrulama

Kurulumu sınamak için küçük bir model tanımla ve projeyi derle:

```kotlin
import com.sahsenvar.kmapper.annotations.MapTo

data class PingDomain(val message: String)

@MapTo(PingDomain::class)
data class PingRemote(val message: String)
```

Derlemeden sonra `PingRemote.toPingDomain()` çağrılabilir olmalı. Olmuyorsa, KSP eklentisinin uygulandığını ve (KMP'de) yukarıdaki `kspCommonMainMetadata` bağlamasının yapıldığını kontrol et.

> Sonraki adım: **[5 Dakikada İlk Mapper →](ilk-mapper.md)**
