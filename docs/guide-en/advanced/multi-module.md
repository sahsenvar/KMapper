# Multi-Module Projects

kmap compiles each module in an independent KSP run. Cross-module type discovery relies on a special mechanism; this page explains how that mechanism works.

## `@KMapperConfig` — One per Module

Every module that generates mappers defines its own `@KMapperConfig` object. The processor only sees the `@KMapperConfig` inside the module it is currently processing; it cannot automatically inherit a config object from another module.

```kotlin
// feature/order/data/src/commonMain/.../OrderMapperConfig.kt
@KMapperConfig(converters = [
    IsoStringToInstantConverter::class,
    PersistentListConverter::class,
])
object OrderMapperConfig
```

```kotlin
// feature/product/data/src/commonMain/.../ProductMapperConfig.kt
@KMapperConfig(converters = [
    IsoStringToInstantConverter::class,
])
object ProductMapperConfig
```

Each module lists the converters it needs in its own config. Although this looks like repetition, it is an intentional design decision: the dependency graph of every module is explicitly visible at compile time.

## Cross-Module `@CollectionWrapper` Discovery

A converter module such as `converters-compose` defines functions annotated with `@CollectionWrapper`:

```kotlin
// converters-compose module
@CollectionWrapper(forType = PersistentList::class)
fun <T> List<T>.asPersistentList(): PersistentList<T> = toPersistentList()
```

KSP's `getSymbolsWithAnnotation` function cannot see annotations in **dependency artifacts** — it only inspects the current compilation unit. For this reason kmap uses a different mechanism:

### The Descriptor Mechanism

1. When `converters-compose` is built, its own KSP run sees the `@CollectionWrapper` functions (in-module).
2. The processor generates a descriptor object in the `com.sahsenvar.kmapper.generated` package for each wrapper. This object is annotated with `@CollectionWrapperDescriptor` and uses `BINARY` retention:

```kotlin
@CollectionWrapperDescriptor(
    forType  = "kotlinx.collections.immutable.PersistentList",
    wrapFunction = "com.sahsenvar.kmapper.compose.asPersistentList"
)
object PersistentListWrapperDescriptor
```

3. The consumer module's processor uses `resolver.getDeclarationsFromPackage("com.sahsenvar.kmapper.generated")` to find these descriptors and determine which wrappers are available on the classpath.

This infrastructure shares the same mechanism as the converter runtime registry; the design is consistent.

### Why `kspJvm` Is Required

For descriptor classes to be discoverable by consumer modules, the `converters-compose` module must include those descriptor classes in its published jar. This requires KSP to run for the JVM target as well:

```kotlin
// converters-compose/build.gradle.kts
dependencies {
    // For KMP targets:
    add("kspCommonMainMetadata", "com.sahsenvar.kmapper:processor:<v>")
    // To include descriptors in the published jar:
    add("kspJvm", "com.sahsenvar.kmapper:processor:<v>")
}
```

Without `kspJvm`, descriptor classes do not end up in the jar and consumer modules cannot discover the wrappers.

### Conflict Protection

If more than one `@CollectionWrapper` for the same `forType` is found on the classpath, the processor emits a **compile error**. Which wrapper is active must never be left ambiguous:

```
e: [kmap] Multiple @CollectionWrapper found for 'PersistentList'. Remove one from the classpath.
```

## Example Multi-Module Layout

```
:core:mappers           → @KMapperConfig (shared converters)
:feature:order:data     → @KMapperConfig (own converters) + @MapTo models
:feature:product:data   → @KMapperConfig (own converters) + @MapTo models
:converters-compose     → @CollectionWrapper functions + generated descriptors
```

In each `:feature:*:data` module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.sahsenvar.kmapper:core:<v>")
    add("kspCommonMainMetadata", "com.sahsenvar.kmapper:processor:<v>")

    // For immutable collection support:
    implementation("com.sahsenvar.kmapper:converters-compose:<v>")
}
```

---

Next: [Architecture — Modules and Pipeline](./architecture.md)
