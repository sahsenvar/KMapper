# Installation

kmap consists of two required pieces: the runtime library (`core`) and the KSP processor (`processor`). Add-ons such as collection wrappers are optional.

## Requirements

- Kotlin **2.1+** (with KSP2)
- KSP (Kotlin Symbol Processing) Gradle plugin
- A Kotlin Multiplatform or pure JVM/Android project

## 1. Add Repositories

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

## 2. Apply the KSP Plugin

`build.gradle.kts` (consuming module):

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
}
```

## 3. Add Dependencies

### Kotlin Multiplatform module

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.sahsenvar:kmapper-core:1.0.0")
            // Optional: add any converter add-ons you need, e.g.:
            // implementation("io.github.sahsenvar:kmapper-converters-immutable:1.0.0")
        }
    }
}

dependencies {
    // Generate mapping code for commonMain
    add("kspCommonMainMetadata", "io.github.sahsenvar:kmapper-processor:1.0.0")
}

// KMP + KSP wiring: process commonMain metadata BEFORE compilation
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}
```

### Pure JVM / Android module

If you are not using KMP, the setup is simpler:

```kotlin
dependencies {
    implementation("io.github.sahsenvar:kmapper-core:1.0.0")
    ksp("io.github.sahsenvar:kmapper-processor:1.0.0")
}
```

## Where Does the Generated Code Go?

KSP writes the mapping extensions under the standard KSP output path:

```
build/generated/ksp/.../<SourceClass>Mappers.kt
```

These files are added to the compilation classpath automatically; you do not need to commit them or edit them by hand.

## Verification

To confirm the setup works, define a small model and build the project:

```kotlin
import com.sahsenvar.kmapper.annotations.MapTo

data class PingDomain(val message: String)

@MapTo(PingDomain::class)
data class PingRemote(val message: String)
```

After compilation, `PingRemote.toPingDomain()` should be callable. If it is not, verify that the KSP plugin is applied and (for KMP) that the `kspCommonMainMetadata` task dependency wiring above is in place.

> Next: **[Your First Mapper →](first-mapper.md)**
