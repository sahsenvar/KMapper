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
    implementation("io.github.sahsenvar:kmapper-core:3.0.1")
    implementation("io.github.sahsenvar:kmapper-annotations:3.0.1")
    ksp("io.github.sahsenvar:kmapper-compiler:3.0.1")
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
            implementation("io.github.sahsenvar:kmapper-core:3.0.1")
            implementation("io.github.sahsenvar:kmapper-annotations:3.0.1")
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", "io.github.sahsenvar:kmapper-compiler:3.0.1")
    add("kspJvm", "io.github.sahsenvar:kmapper-compiler:3.0.1")
    add("kspIosArm64", "io.github.sahsenvar:kmapper-compiler:3.0.1")
    add("kspIosSimulatorArm64", "io.github.sahsenvar:kmapper-compiler:3.0.1")
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
implementation("io.github.sahsenvar:kmapper-converters-immutable:3.0.1") // PersistentList & co.
implementation("io.github.sahsenvar:kmapper-converters-arrow:3.0.1")     // NonEmptyList, Option
implementation("io.github.sahsenvar:kmapper-converters-datetime:3.0.1")  // java.time + bridges
implementation("io.github.sahsenvar:kmapper-converters-bignumber:3.0.1") // BigDecimal/BigInteger
implementation("io.github.sahsenvar:kmapper-converters-uuid:3.0.1")      // Uuid / java.util.UUID
implementation("io.github.sahsenvar:kmapper-converters-okio:3.0.1")      // ByteString, Path
implementation("io.github.sahsenvar:kmapper-converters-uri:3.0.1")       // URI / Uri / NSURL
implementation("io.github.sahsenvar:kmapper-validators:3.0.1")           // Email, E.164, IP, …
```

kotlinx-datetime types (`LocalDate`, `Instant`, …) need no add-on — their `String`/`Long`
converters are core built-ins, and `kmapper-core` brings kotlinx-datetime in as an API
dependency. `kmapper-core` also brings in kotlinx-coroutines-core (`api`, for
`KMapperWrapper.Flow` — see [Return Wrappers](../basic-usage/return-wrappers.md)).

## Optional: the Gradle plugin

If you want a module-wide [return wrapper](../basic-usage/return-wrappers.md) — e.g. every
mapping in the module also gets `toXResult()` — without repeating `wrapper = …` on each
`@MapTo`/`@MapFrom`, apply the plugin instead of (or alongside) the `ksp { arg(...) }` form:

```kotlin
// build.gradle.kts
plugins {
    id("io.github.sahsenvar.kmapper") version "3.0.1"
}

import com.sahsenvar.kmapper.gradle.KMapperWrapper

KMapper {
    wrapper = KMapperWrapper.KtResult // None (default) | KtResult | Flow | Custom("fqn")
}
```

The plugin is `io.github.sahsenvar:kmapper-gradle-plugin`, resolved from Maven Central. It must
be applied to a module that also applies the KSP plugin — it forwards its setting to the
processor as the `kmapper.wrapper` option. Without the plugin, set that option directly:
`ksp { arg("kmapper.wrapper", "KtResult") }`.

## Version compatibility

| KMapper | Kotlin | KSP |
|---------|--------|-----|
| 3.x | 2.3+ | KSP2 (`2.3.x-2.x`) |

In multi-module projects only modules that *declare* mappings need the compiler; modules that
merely call generated functions need just the runtime. Details:
[Multi-Module Projects](../advanced/multi-module.md).

> Next: **[Your First Mapper →](first-mapper.md)**
