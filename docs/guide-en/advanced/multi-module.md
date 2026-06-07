# Multi-Module Projects

KMapper compiles each module in an independent KSP run. This page explains how scalar converters and collection wrappers are configured in a multi-module project.

## `@KMapperConfig` — One per Module

Every module that generates mappers defines its own `@KMapperConfig` object. The processor only sees the `@KMapperConfig` inside the module it is currently processing; it cannot automatically inherit a config object from another module.

```kotlin
// feature/order/data/src/commonMain/.../OrderMapperConfig.kt
@KMapperConfig(
    converters = [IsoStringToInstantConverter::class],
    wrappers   = [PersistentListWrapper::class],
)
object OrderMapperConfig
```

```kotlin
// feature/product/data/src/commonMain/.../ProductMapperConfig.kt
@KMapperConfig(
    converters = [IsoStringToInstantConverter::class],
)
object ProductMapperConfig
```

Each module lists the converters and wrappers it needs in its own config. Although this looks like repetition, it is an intentional design decision: the dependency graph of every module is explicitly visible at compile time.

## Cross-Module `@CollectionWrapper` Usage

Add-on modules such as `converters-immutable` or `converters-arrow` define `@CollectionWrapper`-annotated `object`s:

```kotlin
// converters-immutable module
@CollectionWrapper(forType = PersistentList::class)
object PersistentListWrapper {
    fun <T> wrap(items: List<T>): PersistentList<T> = items.toPersistentList()
}
```

The consumer module's processor does **not** discover these wrappers automatically. Instead the consumer module lists the wrappers it needs in `@KMapperConfig.wrappers`:

```kotlin
// feature/order/data/src/commonMain/.../OrderMapperConfig.kt
import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.immutable.PersistentListWrapper
import com.sahsenvar.kmapper.arrow.NonEmptyListWrapper

@KMapperConfig(
    converters = [IsoStringToInstantConverter::class],
    wrappers   = [PersistentListWrapper::class, NonEmptyListWrapper::class],
)
object OrderMapperConfig
```

### Why Explicit Listing?

Due to KSP2's per-module isolated compilation, `getSymbolsWithAnnotation` and `getDeclarationsFromPackage` cannot see symbols in **dependency artifacts** — they only inspect the current compilation unit. This limitation applies in particular to KMP's `kspCommonMainMetadata` run and to iOS/Native targets.

The solution: `@KMapperConfig(wrappers = [...])` is read **in the consumer's own KSP run** (in-module, always visible). The processor resolves each wrapper object's `@CollectionWrapper.forType` value from the compiled dependency artifact — this is standard type+annotation resolution and works on JVM, Android, and iOS/Native alike.

### KSP Configuration for a KMP Consumer

In KMP modules, the processor must be applied separately for each target:

```kotlin
// feature/order/data/build.gradle.kts
dependencies {
    add("kspCommonMainMetadata", "io.github.sahsenvar:kmapper-processor:<v>")
    add("kspJvm", "io.github.sahsenvar:kmapper-processor:<v>")                // JVM target
    add("kspAndroid", "io.github.sahsenvar:kmapper-processor:<v>")            // Android target
    add("kspIosArm64", "io.github.sahsenvar:kmapper-processor:<v>")           // iOS device
    add("kspIosSimulatorArm64", "io.github.sahsenvar:kmapper-processor:<v>")  // iOS simulator
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}
```

`kspCommonMainMetadata` generates the shared mappers; the remaining `ksp*` entries ensure platform-specific sources can compile.

### Conflict Protection

If more than one `@CollectionWrapper` for the same `forType` appears in the `wrappers` list, the processor emits a **compile error**:

```
e: [KMapper] Multiple @CollectionWrapper found for 'PersistentList'. Remove one from the wrappers list.
```

## Example Multi-Module Layout

```
:core:mappers           → @KMapperConfig (shared converters)
:feature:order:data     → @KMapperConfig (converters + wrappers) + @MapTo models
:feature:product:data   → @KMapperConfig (converters) + @MapTo models
converters-immutable    → @CollectionWrapper objects (PersistentListWrapper etc.)
converters-arrow        → @CollectionWrapper objects (NonEmptyListWrapper)
```

In each `:feature:*:data` module's `build.gradle.kts`:

```kotlin
commonMain.dependencies {
    implementation("io.github.sahsenvar:kmapper-core:<v>")

    // For immutable collection support:
    implementation("io.github.sahsenvar:kmapper-converters-immutable:<v>")
}

dependencies {
    add("kspCommonMainMetadata", "io.github.sahsenvar:kmapper-processor:<v>")
    // Add kspJvm / kspAndroid / kspIosArm64 / kspIosSimulatorArm64 as needed
}
```

---

Next: [Architecture — Modules and Pipeline](./architecture.md)
