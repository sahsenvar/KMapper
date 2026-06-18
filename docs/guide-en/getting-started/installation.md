# Installation

KMapper is three artifacts working together, plus optional add-ons. All are on
[Maven Central](https://central.sonatype.com/artifact/io.github.sahsenvar/kmapper-core) under
the group `io.github.sahsenvar`.

| Artifact | You need it when… |
|----------|-------------------|
| `kmapper-core` | always — the runtime (exceptions, converters, validators, seams) |
| `kmapper-annotations` | you declare mappings with annotations (almost always) |
| `kmapper-compiler` | same as above — it is the KSP processor that reads them |

> `kmapper-core` alone is also a valid setup: it gives you the same conversion seams the
> generated code uses, for hand-written mappers without KSP. See the
> [`CoreOnlyMapping` example](examples.md).

## JVM / Android (single platform)

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.3.10" // or com.android.application / kotlin("android")
    id("com.google.devtools.ksp") version "2.3.10-2.0.5"
}

dependencies {
    implementation("io.github.sahsenvar:kmapper-core:2.2.2")
    implementation("io.github.sahsenvar:kmapper-annotations:2.2.2")
    ksp("io.github.sahsenvar:kmapper-compiler:2.2.2")
}
```

## Kotlin Multiplatform

Declare models and mappings in `commonMain`; register the processor per compilation target:

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
            implementation("io.github.sahsenvar:kmapper-core:2.2.2")
            implementation("io.github.sahsenvar:kmapper-annotations:2.2.2")
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", "io.github.sahsenvar:kmapper-compiler:2.2.2")
    add("kspJvm", "io.github.sahsenvar:kmapper-compiler:2.2.2")
    add("kspIosArm64", "io.github.sahsenvar:kmapper-compiler:2.2.2")
    add("kspIosSimulatorArm64", "io.github.sahsenvar:kmapper-compiler:2.2.2")
}

// Make every compilation see the commonMain-generated sources:
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}
```

## Add-ons (optional)

Each add-on is an independent KMP artifact; add only what your models use:

```kotlin
implementation("io.github.sahsenvar:kmapper-converters-immutable:2.2.2") // PersistentList & co.
implementation("io.github.sahsenvar:kmapper-converters-arrow:2.2.2")     // NonEmptyList, Option
implementation("io.github.sahsenvar:kmapper-converters-datetime:2.2.2")  // java.time + bridges
implementation("io.github.sahsenvar:kmapper-converters-bignumber:2.2.2") // BigDecimal/BigInteger
implementation("io.github.sahsenvar:kmapper-converters-uuid:2.2.2")      // Uuid / java.util.UUID
implementation("io.github.sahsenvar:kmapper-converters-okio:2.2.2")      // ByteString, Path
implementation("io.github.sahsenvar:kmapper-converters-uri:2.2.2")       // URI / Uri / NSURL
implementation("io.github.sahsenvar:kmapper-validators:2.2.2")           // Email, E.164, IP, …
```

kotlinx-datetime types (`LocalDate`, `Instant`, …) need no add-on — their `String`/`Long`
converters are core built-ins, and `kmapper-core` brings kotlinx-datetime in as an API
dependency.

## Version compatibility

| KMapper | Kotlin | KSP |
|---------|--------|-----|
| 2.x | 2.3+ | KSP2 (`2.3.x-2.x`) |

In multi-module projects only modules that *declare* mappings need the compiler; modules that
merely call generated functions need just the runtime. Details:
[Multi-Module Projects](../advanced/multi-module.md).

> Next: **[Your First Mapper →](first-mapper.md)**
