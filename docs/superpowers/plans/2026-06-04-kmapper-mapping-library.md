# KMapper Mapping Library — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract DomatApp's KSP object-mapping system into a standalone, Maven-Central-publishable library `KMapper` (`com.sahsenvar.kmapper`) in a fresh GitHub repo, decoupled from DomatApp and hardened with correctness fixes + enum support.

**Architecture:** Multi-module Gradle build — `core` (KMP: annotations + runtime + `MappingException` + primitive converters + `MappableEnum` + listener), `processor` (JVM-only KSP codegen, ported & decoupled), `converters-compose` (KMP add-on), `converters-arrow` (empty slot). Processor logic is verified with JVM `kotlin-compile-testing`.

**Tech Stack:** Kotlin 2.3.10 (Multiplatform), KSP, KotlinPoet, kotlinx-datetime, kotlinx-collections-immutable, kotlin-compile-testing (Square), Gradle `maven-publish` + signing.

**Source spec:** `docs/superpowers/specs/2026-06-04-KMapper-mapping-library-design.md` (lives in DomatApp repo; this plan's code is built in the NEW `KMapper` repo).

---

## Plan Decomposition (sequenced sub-plans)

Each phase produces working, testable software and ends with commits.

- **Phase 0 — Repo & Gradle skeleton.** New GitHub repo + multi-module Gradle that builds empty modules. *Checkpoint:* `./gradlew build` green.
- **Phase 1 — `core` module.** All annotations, `MappingException`, `MapTypeConverter` + registry, primitive converters, `MappableEnum`, `KMapper`/`MappingListener`. *Checkpoint:* `publishToMavenLocal` green.
- **Phase 2 — `processor` port + walking skeleton.** Port `MappingProcessor` pipeline, decouple all `com.domatapp` FQNs → `com.sahsenvar.kmapper`, stand up `kotlin-compile-testing`. *Checkpoint:* first green compile-test (basic `@MapTo` generates correct mapper).
- **Phase 3 — Correctness fixes & enum (TDD).** `TypeConversionFailed` wrapping, compile-time cycle detection, `@KMapperConfig` + `@UseMapTypeConverter` (kill regex), `MappableEnum` codegen, `@CollectionWrapper` cross-module discovery. Each fix is a red→green compile-test.
- **Phase 4 — `converters-compose` + DomatApp migration.** Compose wrappers via `@CollectionWrapper`; then in DomatApp consume `com.sahsenvar.kmapper:*`, convert `MapperConfiguration` → `@KMapperConfig`, extend `toAuthError`, delete `core:mapping` + mapping processor.

> Phases 1–4 are detailed below in sequence. Implement strictly in order; later phases depend on earlier artifacts.

---

## Phase 0 — Repo & Gradle Skeleton

**Goal:** A new `KMapper` GitHub repo with a multi-module Gradle build that compiles four empty modules.

**Files (all created):**
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- `core/build.gradle.kts`, `processor/build.gradle.kts`, `converters-compose/build.gradle.kts`, `converters-arrow/build.gradle.kts`
- `.gitignore`, `README.md`

### Task 0.1: Create local project + GitHub repo

- [ ] **Step 1: Resolve the personal GitHub handle**

Run: `gh api user --jq .login`
Expected: prints the personal account login (referred to below as `<gh-user>`; groupId implies `sahsenvar`). If it prints a work account, run `gh auth switch` to the personal account first.

- [ ] **Step 2: Create the project directory** (sibling to DomatApp)

```bash
mkdir -p /Users/sahansenvar/StudioProjects/KMapper
cd /Users/sahansenvar/StudioProjects/KMapper
git init -b main
```

- [ ] **Step 3: Create the empty GitHub repo (no push yet)**

```bash
gh repo create <gh-user>/KMapper --private --description "KMP-friendly compile-time object mapper (KSP)" 
```
Expected: "✓ Created repository <gh-user>/KMapper". (Private now; can be made public at first release. Do NOT pass `--source/--push` yet — we push after the first commit.)

- [ ] **Step 4: Add remote**

```bash
git remote add origin https://github.com/<gh-user>/KMapper.git
```

### Task 0.2: Version catalog (authoritative versions from DomatApp)

- [ ] **Step 1: Read DomatApp's catalog for exact versions**

Run: `grep -E 'kotlin|ksp|kotlinpoet|datetime|collections-immutable' /Users/sahansenvar/StudioProjects/DomatApp/gradle/libs.versions.toml`
Use the printed `kotlin`, `ksp`, `kotlinpoet`, `kotlinx-datetime`, `kotlinx-collections-immutable` values verbatim (DomatApp builds with them — correct by construction). Substitute them where marked `<from-domatapp>` below.

- [ ] **Step 2: Create `gradle/libs.versions.toml`**

```toml
[versions]
kotlin = "<from-domatapp>"            # e.g. 2.3.10
ksp = "<from-domatapp>"               # MUST match the kotlin version
kotlinpoet = "<from-domatapp>"        # e.g. 1.14.2
kotlinx-datetime = "<from-domatapp>"
kotlinx-collections-immutable = "<from-domatapp>"
compile-testing = "0.5.1"             # com.github.tschuchortdev kotlin-compile-testing-ksp (verify latest in Step 3)

[libraries]
ksp-api = { group = "com.google.devtools.ksp", name = "symbol-processing-api", version.ref = "ksp" }
kotlinpoet = { group = "com.squareup", name = "kotlinpoet", version.ref = "kotlinpoet" }
kotlinpoet-ksp = { group = "com.squareup", name = "kotlinpoet-ksp", version.ref = "kotlinpoet" }
kotlinx-datetime = { group = "org.jetbrains.kotlinx", name = "kotlinx-datetime", version.ref = "kotlinx-datetime" }
kotlinx-collections-immutable = { group = "org.jetbrains.kotlinx", name = "kotlinx-collections-immutable", version.ref = "kotlinx-collections-immutable" }
compile-testing-ksp = { group = "com.github.tschuchortdev", name = "kotlin-compile-testing-ksp", version.ref = "compile-testing" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 3: Confirm compile-testing coordinate resolves**

Run: `gh api -X GET search/repositories -f q=kotlin-compile-testing --jq '.items[0].full_name'` (sanity) and verify `com.github.tschuchortdev:kotlin-compile-testing-ksp` exists on Maven Central (https://central.sonatype.com). If the latest version differs from `0.5.1`, update the `compile-testing` ref. (Square's fork; KSP variant required because we test a KSP processor.)

### Task 0.3: Root Gradle files

- [ ] **Step 1: Create `settings.gradle.kts`**

```kotlin
rootProject.name = "KMapper"

pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}

include(":core", ":processor", ":converters-compose", ":converters-arrow")
```

- [ ] **Step 2: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
}

allprojects {
    group = "com.sahsenvar.kmapper"
    version = "0.1.0-SNAPSHOT"
}
```

- [ ] **Step 3: Create `gradle.properties`**

```properties
kotlin.code.style=official
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
org.gradle.caching=true
kotlin.mpp.stability.nowarn=true
```

- [ ] **Step 4: Create `.gitignore`**

```gitignore
.gradle/
build/
.idea/
.kotlin/
local.properties
*.iml
.DS_Store
```

- [ ] **Step 5: Create `README.md`**

```markdown
# KMapper

KMP-friendly compile-time object mapping for Kotlin Multiplatform, powered by KSP.

`com.sahsenvar.kmapper` — `core`, `processor`, `converters-compose`, `converters-arrow`.

Status: pre-release (0.1.0-SNAPSHOT).
```

### Task 0.4: Empty module build files

- [ ] **Step 1: Create `core/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    androidTarget { /* configured fully in Phase 1 if Android needed; jvm covers tests */ }
    jvm()
    iosArm64(); iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
        }
    }
}
```
> Note: `androidTarget` requires the Android Gradle Plugin. To keep Phase 0 dependency-light, comment out `androidTarget` until Phase 1 Step where AGP is added; `jvm()` + iOS are enough to compile and to run JVM tests. (Decision recorded in Phase 1 Task 1.1.)

- [ ] **Step 2: Create `processor/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    implementation(project(":core"))
}
```

- [ ] **Step 3: Create `converters-compose/build.gradle.kts` and `converters-arrow/build.gradle.kts`**

```kotlin
// converters-compose/build.gradle.kts
plugins { alias(libs.plugins.kotlin.multiplatform) }
kotlin {
    jvm(); iosArm64(); iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.kotlinx.collections.immutable)
        }
    }
}
```
```kotlin
// converters-arrow/build.gradle.kts  (empty slot this round)
plugins { alias(libs.plugins.kotlin.multiplatform) }
kotlin {
    jvm(); iosArm64(); iosSimulatorArm64()
    sourceSets { commonMain.dependencies { api(project(":core")) } }
}
```

- [ ] **Step 4: Generate the Gradle wrapper**

Run: `gradle wrapper --gradle-version 8.11 || ./gradlew --version`
Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/` created. (If no system `gradle`, copy the wrapper from DomatApp: `cp -r /Users/sahansenvar/StudioProjects/DomatApp/gradle/wrapper gradle/ && cp /Users/sahansenvar/StudioProjects/DomatApp/gradlew* .`)

- [ ] **Step 5: Verify the empty build compiles**

Run: `./gradlew help -q && ./gradlew tasks -q` then `./gradlew build -x test`
Expected: BUILD SUCCESSFUL (empty modules, nothing to compile yet).

- [ ] **Step 6: First commit + push**

```bash
git add -A
git commit -m "chore: scaffold KMapper multi-module Gradle project

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push -u origin main
```
Expected: branch `main` pushed to `<gh-user>/KMapper`.

---

## Phase 1 — `core` Module

**Goal:** Publishable `core` artifact containing all annotations, the runtime converter system, `MappingException`, primitive converters, `MappableEnum`, and the listener API. No unit tests this round (spec §9 — runtime is thin; processor is tested in Phase 2+). *Checkpoint:* `./gradlew :core:publishToMavenLocal` green.

**Package root:** `com.sahsenvar.kmapper`

### Task 1.1: Finalize `core/build.gradle.kts` (KMP + Android + publish)

- [ ] **Step 1: Add AGP to the version catalog**

Add to `gradle/libs.versions.toml` `[versions]`: `agp = "<from-domatapp>"` (AGP version from DomatApp, e.g. 9.0.1) and `[plugins]`: `android-library = { id = "com.android.library", version.ref = "agp" }`. Add `google()` is already in repos (Task 0.3).

- [ ] **Step 2: Replace `core/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    `maven-publish`
}

kotlin {
    androidTarget { publishLibraryVariants("release") }
    jvm()
    iosArm64(); iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies { implementation(libs.kotlinx.datetime) }
    }
}

android {
    namespace = "com.sahsenvar.kmapper.core"
    compileSdk = 36
    defaultConfig { minSdk = 30 }
}
```

- [ ] **Step 3: Verify it configures**

Run: `./gradlew :core:tasks -q`
Expected: BUILD SUCCESSFUL; `publishToMavenLocal` task listed.

### Task 1.2: Annotations

**Files (create):** `core/src/commonMain/kotlin/com/sahsenvar/kmapper/annotations/*.kt`

- [ ] **Step 1: Create the annotation files**

```kotlin
// MapTo.kt
package com.sahsenvar.kmapper.annotations
import kotlin.reflect.KClass
@Target(AnnotationTarget.CLASS) @Retention(AnnotationRetention.SOURCE) @Repeatable
annotation class MapTo(val target: KClass<*>)
```
```kotlin
// MapFrom.kt
package com.sahsenvar.kmapper.annotations
import kotlin.reflect.KClass
@Target(AnnotationTarget.CLASS) @Retention(AnnotationRetention.SOURCE) @Repeatable
annotation class MapFrom(val source: KClass<*>)
```
```kotlin
// FieldMap.kt
package com.sahsenvar.kmapper.annotations
import kotlin.reflect.KClass
@Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.SOURCE) @Repeatable
annotation class FieldMap(val fieldName: String, val targetClass: KClass<*> = Nothing::class)
```
```kotlin
// MapDefaultValue.kt
package com.sahsenvar.kmapper.annotations
@Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.SOURCE)
annotation class MapDefaultValue(val expression: String)
```
```kotlin
// Ignore.kt
package com.sahsenvar.kmapper.annotations
@Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.SOURCE)
annotation class Ignore
```
```kotlin
// UseMapTypeConverter.kt
package com.sahsenvar.kmapper.annotations
import com.sahsenvar.kmapper.converter.MapTypeConverter
import kotlin.reflect.KClass
@Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.SOURCE)
annotation class UseMapTypeConverter(val converter: KClass<out MapTypeConverter<*, *>>)
```
```kotlin
// KMapperConfig.kt  (NEW — replaces old @KMapperConfiguration; carries the converter list)
package com.sahsenvar.kmapper.annotations
import kotlin.reflect.KClass
@Target(AnnotationTarget.CLASS) @Retention(AnnotationRetention.SOURCE)
annotation class KMapperConfig(val converters: Array<KClass<*>> = [])
```
```kotlin
// CollectionWrapper.kt  (NEW — marks a wrap fn in converters-compose/arrow)
package com.sahsenvar.kmapper.annotations
import kotlin.reflect.KClass
@Target(AnnotationTarget.FUNCTION) @Retention(AnnotationRetention.SOURCE)
annotation class CollectionWrapper(val forType: KClass<*>)
```

- [ ] **Step 2: Commit**

```bash
git add core/src core/build.gradle.kts gradle/libs.versions.toml
git commit -m "feat(core): add mapping annotations

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 1.3: `MappingException`

**File (create):** `core/src/commonMain/kotlin/com/sahsenvar/kmapper/MappingException.kt`

- [ ] **Step 1: Create the sealed hierarchy**

```kotlin
package com.sahsenvar.kmapper

sealed class MappingException(message: String, cause: Throwable? = null)
    : RuntimeException(message, cause) {

    class RequiredFieldMissing(val field: String)
        : MappingException("Required field missing: $field")

    class TypeConversionFailed(val from: String, val to: String, cause: Throwable)
        : MappingException("Cannot convert $from -> $to", cause)

    class UnknownEnumValue(val enum: String, val value: Any)
        : MappingException("Unknown wire value '$value' for enum $enum")
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/commonMain/kotlin/com/sahsenvar/kmapper/MappingException.kt
git commit -m "feat(core): add MappingException hierarchy

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 1.4: `MapTypeConverter` + `TypeConverterRegistry`

**Files (create):**
- `core/src/commonMain/kotlin/com/sahsenvar/kmapper/converter/MapTypeConverter.kt`
- `core/src/commonMain/kotlin/com/sahsenvar/kmapper/converter/TypeConverterRegistry.kt` (expect)
- `core/src/androidMain/.../TypeConverterRegistry.android.kt`, `jvmMain`, `iosMain` (actual)

- [ ] **Step 1: `MapTypeConverter`**

```kotlin
package com.sahsenvar.kmapper.converter
import kotlin.reflect.KClass

abstract class MapTypeConverter<S : Any, T : Any>(
    val sourceType: KClass<S>,
    val targetType: KClass<T>,
) {
    abstract fun convertToNonNull(value: S): T
    abstract fun convertFromNonNull(value: T): S
    fun convertTo(value: S?): T? = value?.let { convertToNonNull(it) }
    fun convertFrom(value: T?): S? = value?.let { convertFromNonNull(it) }
}
```

- [ ] **Step 2: `expect object TypeConverterRegistry`**

```kotlin
package com.sahsenvar.kmapper.converter
import kotlin.reflect.KClass

expect object TypeConverterRegistry {
    fun <S : Any, T : Any> register(converter: MapTypeConverter<S, T>)
    fun <S : Any, T : Any> get(sourceType: KClass<S>, targetType: KClass<T>): MapTypeConverter<S, T>?
    fun <S : Any, T : Any> has(sourceType: KClass<S>, targetType: KClass<T>): Boolean
}
```

- [ ] **Step 3: JVM + Android actual (shared impl via a common internal map)**

Create identical `actual` bodies in `jvmMain` and `androidMain` (both JVM-backed). Use a thread-safe map keyed by qualified-name pairs:
```kotlin
// jvmMain & androidMain (same content, package com.sahsenvar.kmapper.converter)
package com.sahsenvar.kmapper.converter
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

actual object TypeConverterRegistry {
    private val map = ConcurrentHashMap<Pair<String, String>, MapTypeConverter<*, *>>()
    private fun key(s: KClass<*>, t: KClass<*>) = (s.qualifiedName ?: s.toString()) to (t.qualifiedName ?: t.toString())
    actual fun <S : Any, T : Any> register(converter: MapTypeConverter<S, T>) {
        map.putIfAbsent(key(converter.sourceType, converter.targetType), converter)
    }
    @Suppress("UNCHECKED_CAST")
    actual fun <S : Any, T : Any> get(sourceType: KClass<S>, targetType: KClass<T>) =
        map[key(sourceType, targetType)] as? MapTypeConverter<S, T>
    actual fun <S : Any, T : Any> has(sourceType: KClass<S>, targetType: KClass<T>) =
        map.containsKey(key(sourceType, targetType))
}
```

- [ ] **Step 4: iOS actual (single-threaded map; Native has no ConcurrentHashMap)**

```kotlin
// iosMain
package com.sahsenvar.kmapper.converter
import kotlin.reflect.KClass

actual object TypeConverterRegistry {
    private val map = mutableMapOf<Pair<String, String>, MapTypeConverter<*, *>>()
    private fun key(s: KClass<*>, t: KClass<*>) = (s.qualifiedName ?: s.toString()) to (t.qualifiedName ?: t.toString())
    actual fun <S : Any, T : Any> register(converter: MapTypeConverter<S, T>) {
        map.getOrPut(key(converter.sourceType, converter.targetType)) { converter }
    }
    @Suppress("UNCHECKED_CAST")
    actual fun <S : Any, T : Any> get(sourceType: KClass<S>, targetType: KClass<T>) =
        map[key(sourceType, targetType)] as? MapTypeConverter<S, T>
    actual fun <S : Any, T : Any> has(sourceType: KClass<S>, targetType: KClass<T>) =
        map.containsKey(key(sourceType, targetType))
}
```

- [ ] **Step 5: Verify compile + commit**

Run: `./gradlew :core:compileKotlinJvm :core:compileKotlinIosSimulatorArm64 -q`
Expected: BUILD SUCCESSFUL.
```bash
git add core/src
git commit -m "feat(core): add MapTypeConverter + multiplatform TypeConverterRegistry

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 1.5: Built-in primitive + datetime converters

**Files (create):** `core/src/commonMain/kotlin/com/sahsenvar/kmapper/converter/builtin/PrimitiveConverters.kt`, `DateTimeConverters.kt`

> Converters are **bilateral** (one object per unordered type pair; direction chosen by `convertTo`/`convertFrom`). The Phase-2 processor built-in table maps both directions of each pair to these FQNs.

- [ ] **Step 1: `PrimitiveConverters.kt`**

```kotlin
package com.sahsenvar.kmapper.converter.builtin
import com.sahsenvar.kmapper.converter.MapTypeConverter

object StringIntConverter : MapTypeConverter<String, Int>(String::class, Int::class) {
    override fun convertToNonNull(value: String) = value.toInt()
    override fun convertFromNonNull(value: Int) = value.toString()
}
object StringLongConverter : MapTypeConverter<String, Long>(String::class, Long::class) {
    override fun convertToNonNull(value: String) = value.toLong()
    override fun convertFromNonNull(value: Long) = value.toString()
}
object StringDoubleConverter : MapTypeConverter<String, Double>(String::class, Double::class) {
    override fun convertToNonNull(value: String) = value.toDouble()
    override fun convertFromNonNull(value: Double) = value.toString()
}
object StringFloatConverter : MapTypeConverter<String, Float>(String::class, Float::class) {
    override fun convertToNonNull(value: String) = value.toFloat()
    override fun convertFromNonNull(value: Float) = value.toString()
}
object StringBooleanConverter : MapTypeConverter<String, Boolean>(String::class, Boolean::class) {
    override fun convertToNonNull(value: String) = value.toBoolean()
    override fun convertFromNonNull(value: Boolean) = value.toString()
}
object IntLongConverter : MapTypeConverter<Int, Long>(Int::class, Long::class) {
    override fun convertToNonNull(value: Int) = value.toLong()
    override fun convertFromNonNull(value: Long) = value.toInt()
}
```

- [ ] **Step 2: `DateTimeConverters.kt`** (uses `kotlinx.datetime.Instant`, already a core dep)

```kotlin
package com.sahsenvar.kmapper.converter.builtin
import com.sahsenvar.kmapper.converter.MapTypeConverter
import kotlinx.datetime.Instant

object StringInstantConverter : MapTypeConverter<String, Instant>(String::class, Instant::class) {
    override fun convertToNonNull(value: String): Instant = Instant.parse(value)        // ISO-8601
    override fun convertFromNonNull(value: Instant): String = value.toString()
}
object LongInstantConverter : MapTypeConverter<Long, Instant>(Long::class, Instant::class) {
    override fun convertToNonNull(value: Long): Instant = Instant.fromEpochMilliseconds(value)
    override fun convertFromNonNull(value: Instant): Long = value.toEpochMilliseconds()
}
```

- [ ] **Step 3: Compile + commit**

Run: `./gradlew :core:compileKotlinJvm -q` → BUILD SUCCESSFUL.
```bash
git add core/src/commonMain/kotlin/com/sahsenvar/kmapper/converter/builtin
git commit -m "feat(core): add built-in primitive and datetime converters

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 1.6: `MappableEnum`

**File (create):** `core/src/commonMain/kotlin/com/sahsenvar/kmapper/MappableEnum.kt`

- [ ] **Step 1: Create the interface**

```kotlin
package com.sahsenvar.kmapper

/** Enums opt into mapping by implementing this; the processor maps via [wireValue],
 *  never ordinal/name. W is the wire type (String or Int). */
interface MappableEnum<W : Any> { val wireValue: W }
```

- [ ] **Step 2: Commit**

```bash
git add core/src/commonMain/kotlin/com/sahsenvar/kmapper/MappableEnum.kt
git commit -m "feat(core): add MappableEnum interface

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 1.7: `KMapper` + `MappingListener` + `LoggingMappingListener`

**File (create):** `core/src/commonMain/kotlin/com/sahsenvar/kmapper/KMapper.kt`

- [ ] **Step 1: Create the listener API + registry facade**

```kotlin
package com.sahsenvar.kmapper
import com.sahsenvar.kmapper.converter.MapTypeConverter
import com.sahsenvar.kmapper.converter.TypeConverterRegistry
import kotlin.reflect.KClass

interface MappingListener {
    fun onMapStart(source: Any, target: KClass<*>) {}
    fun onMapComplete(source: Any, result: Any) {}
    fun onError(source: Any, error: MappingException) {}
    // forward-compatible (default no-op): onFieldDefaulted / onConversion added in a later round
}

object KMapper {
    private val listeners = mutableListOf<MappingListener>()

    /** Generated mapper code guards dispatch with this for ~zero cost when unused. */
    val hasListeners: Boolean get() = listeners.isNotEmpty()
    fun addListener(listener: MappingListener) { listeners += listener }
    fun removeListener(listener: MappingListener) { listeners -= listener }
    fun dispatch(block: MappingListener.() -> Unit) { listeners.toList().forEach(block) }

    /** Runtime escape-hatch (NOT compile-time safe; prefer @KMapperConfig). */
    fun <S : Any, T : Any> addConverter(converter: MapTypeConverter<S, T>) =
        TypeConverterRegistry.register(converter)
}

class LoggingMappingListener(private val log: (String) -> Unit) : MappingListener {
    override fun onMapStart(source: Any, target: KClass<*>) =
        log("KMapper start: ${source::class.simpleName} -> ${target.simpleName}")
    override fun onMapComplete(source: Any, result: Any) =
        log("KMapper done: ${source::class.simpleName} -> ${result::class.simpleName}")
    override fun onError(source: Any, error: MappingException) =
        log("KMapper error: ${error.message}")
}
```
> Thread-safety: listeners are expected to be registered once at startup; `dispatch` iterates a snapshot. Hardening (atomic list) is a later concern, not this round.

- [ ] **Step 2: Compile all targets + commit**

Run: `./gradlew :core:compileKotlinJvm :core:compileKotlinIosSimulatorArm64 -q` → BUILD SUCCESSFUL.
```bash
git add core/src/commonMain/kotlin/com/sahsenvar/kmapper/KMapper.kt
git commit -m "feat(core): add KMapper listener API + LoggingMappingListener

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 1.8: Phase 1 checkpoint — publish `core` locally

- [ ] **Step 1: Publish to Maven Local**

Run: `./gradlew :core:publishToMavenLocal -q`
Expected: BUILD SUCCESSFUL; artifacts under `~/.m2/repository/com/sahsenvar/kmapper/core/0.1.0-SNAPSHOT/` (android + jvm + iosArm64 + iosSimulatorArm64 + metadata + `.module`).

- [ ] **Step 2: Verify the published coordinates**

Run: `ls ~/.m2/repository/com/sahsenvar/kmapper/`
Expected: directories for `core`, `core-jvm`, `core-iosarm64`, `core-iossimulatorarm64`, `core-android` (names per KMP publication).

- [ ] **Step 3: Push**

```bash
git push
```

## Phase 2 — `processor` Port + Walking Skeleton

**Goal:** Port the DomatApp KSP pipeline into `KMapper`, decouple every `com.domatapp` FQN, stand up `kotlin-compile-testing`, and get a first green test where a basic `@MapTo` (incl. nested + null-safety + built-in primitive conversion + `@MapFrom`) generates the correct mapper. **Excluded here (→ Phase 3):** custom-converter `@KMapperConfig` discovery (the regex code is NOT ported — it gets deleted), enum, cycle detection, `TypeConversionFailed` wrapping.

**Source to port from:** `/Users/sahansenvar/StudioProjects/DomatApp/core/processor/src/main/kotlin/com/domatapp/core/processor/mapping/`

### Task 2.1: `processor` build + KSP service registration

- [ ] **Step 1: Replace `processor/build.gradle.kts`**

```kotlin
plugins { alias(libs.plugins.kotlin.jvm) }

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    implementation(project(":core"))
    testImplementation(kotlin("test"))
    testImplementation(libs.compile.testing.ksp)
}

tasks.test { useJUnitPlatform() }
```

- [ ] **Step 2: Create the SymbolProcessorProvider service file**

Create `processor/src/main/resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider` containing exactly:
```
com.sahsenvar.kmapper.processor.MappingProcessorProvider
```

### Task 2.2: Port the pipeline source (copy + decouple)

- [ ] **Step 1: Copy the mapping package**

```bash
mkdir -p processor/src/main/kotlin/com/sahsenvar/kmapper/processor
cp -R /Users/sahansenvar/StudioProjects/DomatApp/core/processor/src/main/kotlin/com/domatapp/core/processor/mapping/. \
      processor/src/main/kotlin/com/sahsenvar/kmapper/processor/
```
Ported files: `MappingProcessor.kt`, `MappingProcessorProvider.kt`, `analyzer/FieldAnalyzer.kt`, `analyzer/TypeMatcher.kt`, `generator/MappingCodeGenerator.kt`, `generator/FunctionNameGenerator.kt`, `model/{FieldInfo,MappingStrategy,TypeInfo}.kt`, `validator/BuiltInConverterValidator.kt`.

- [ ] **Step 2: Rewrite package declarations**

In every copied file, replace `package com.domatapp.core.processor.mapping` → `package com.sahsenvar.kmapper.processor` and `import com.domatapp.core.processor.mapping` → `import com.sahsenvar.kmapper.processor`. Verify none remain:
Run: `grep -rn "com.domatapp.core.processor.mapping" processor/src` → expect no matches.

- [ ] **Step 3: Decouple annotation FQN constants**

In `MappingProcessor.kt` and `FieldAnalyzer.kt`, replace the FQN string constants:
- `com.domatapp.core.mapping.annotations.MapTo` → `com.sahsenvar.kmapper.annotations.MapTo`
- `...annotations.MapFrom` → `com.sahsenvar.kmapper.annotations.MapFrom`
- `...annotations.FieldMap` → `com.sahsenvar.kmapper.annotations.FieldMap`
- `...annotations.UseMapTypeConverter` → `com.sahsenvar.kmapper.annotations.UseMapTypeConverter`
- `...annotations.Ignore` → `com.sahsenvar.kmapper.annotations.Ignore`

Run: `grep -rn "com.domatapp.core.mapping.annotations" processor/src` → expect no matches.

- [ ] **Step 4: Decouple the generated error-type FQN**

In `MappingCodeGenerator.kt`, replace the `ClassName(...)` for the thrown error:
```kotlin
// before: ClassName("com.domatapp.core.resulting.error", "MappingError", "RequiredFieldMissing")
ClassName("com.sahsenvar.kmapper", "MappingException", "RequiredFieldMissing")
```

- [ ] **Step 5: Decouple + align the built-in converter table**

In `TypeMatcher.kt`, replace the built-in converter lookup table so each unordered pair points to the bilateral `core` converter (note new bilateral names; reverse direction reuses the same FQN with `convertFrom`):
```kotlin
// "kotlin.String->kotlin.Int"   -> com.sahsenvar.kmapper.converter.builtin.StringIntConverter
// "kotlin.String->kotlin.Long"  -> ...StringLongConverter
// "kotlin.String->kotlin.Double"-> ...StringDoubleConverter
// "kotlin.String->kotlin.Float" -> ...StringFloatConverter
// "kotlin.String->kotlin.Boolean"-> ...StringBooleanConverter
// "kotlin.Int->kotlin.Long"     -> ...IntLongConverter
// "kotlin.String->kotlinx.datetime.Instant" -> ...StringInstantConverter
// "kotlin.Long->kotlinx.datetime.Instant"   -> ...LongInstantConverter
```
For reverse pairs (e.g. `Int->String`), match the same converter and emit `convertFromNonNull`/`convertFrom` (the existing `isReverse` flow already supports this). In `BuiltInConverterValidator.kt`, replace the base-class FQN `com.domatapp.core.mapping.converter.MapTypeConverter` → `com.sahsenvar.kmapper.converter.MapTypeConverter`.

Run: `grep -rn "com.domatapp" processor/src` → expect **no matches** (full decoupling gate).

- [ ] **Step 6: Delete the regex converter-config code (deferred to Phase 3)**

In `MappingProcessor.kt`, remove `parseKMapperConfiguration(...)`, the `Regex(...)` for `registerGlobalTypeConverter`, the `customConverters` field, and any call site. For now the processor uses **built-in converters only**; custom-converter discovery returns empty. (Phase 3 reintroduces it via `@KMapperConfig`.)

Run: `grep -rn "Regex\|registerGlobalTypeConverter\|KMapperConfiguration" processor/src` → expect no matches.

- [ ] **Step 7: Compile the processor**

Run: `./gradlew :processor:compileKotlin -q`
Expected: BUILD SUCCESSFUL. Fix any residual unresolved references (usually leftover FQNs or the deleted-regex hole).

- [ ] **Step 8: Commit**

```bash
git add processor/src processor/build.gradle.kts
git commit -m "feat(processor): port mapping KSP pipeline, decoupled from domatapp

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 2.3: compile-testing harness

**File (create):** `processor/src/test/kotlin/com/sahsenvar/kmapper/processor/CompileTestSupport.kt`

- [ ] **Step 1: Create the harness**

```kotlin
package com.sahsenvar.kmapper.processor
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.symbolProcessorProviders
import java.io.File

fun compile(vararg sources: SourceFile): KotlinCompilation.Result =
    KotlinCompilation().apply {
        this.sources = sources.toList()
        symbolProcessorProviders = listOf(MappingProcessorProvider())
        inheritClassPath = true          // brings :core (annotations, MappingException, converters) onto the test classpath
        kspWithCompilation = true
        messageOutputStream = System.out
    }.compile()

/** Reads a generated file's text by file name (e.g. "UserRemoteMappers.kt"). */
fun KotlinCompilation.Result.generated(fileName: String): String =
    (outputDirectory.parentFile.walkTopDown() + File(".").walkTopDown())
        .firstOrNull { it.name == fileName }
        ?.readText()
        ?: error("generated file $fileName not found")
```
> If `kspSourcesDir`/`generated()` lookup misbehaves on the resolved compile-testing version, switch the reader to walk `compilation.kspSourcesDir` (the import is included for that fallback). Verify the exact API against the version pinned in Task 0.2/Step 3.

### Task 2.4: First green test — basic `@MapTo` (nested + null-safety)

**File (create):** `processor/src/test/kotlin/com/sahsenvar/kmapper/processor/BasicMappingTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sahsenvar.kmapper.processor
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals

class BasicMappingTest {
    @Test fun `nested mapping with required-field null check`() {
        val src = SourceFile.kotlin("Models.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            data class AddressDomain(val city: String)
            data class UserDomain(val id: String, val address: AddressDomain)
            @MapTo(AddressDomain::class) data class AddressRemote(val city: String?)
            @MapTo(UserDomain::class)    data class UserRemote(val id: String?, val address: AddressRemote)
        """.trimIndent())

        val result = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val gen = result.generated("UserRemoteMappers.kt")
        assert(gen.contains("fun UserRemote.toUserDomain(): UserDomain")) { gen }
        assert(gen.contains("id = id ?: throw")) { gen }
        assert(gen.contains("address = address.toAddressDomain()")) { gen }
    }
}
```

- [ ] **Step 2: Run — expect it to FAIL first**

Run: `./gradlew :processor:test --tests "*BasicMappingTest*" -q`
Expected: FAIL (until the ported pipeline compiles & generates correctly — likely a harness/API mismatch or a residual decoupling gap).

- [ ] **Step 3: Make it pass**

Fix harness API (`generated()` reader), residual FQNs, or pipeline holes from the regex deletion until the assertions pass. Do NOT add features — only get the basic nested + null-check path green.

Run: `./gradlew :processor:test --tests "*BasicMappingTest*" -q`
Expected: PASS.

- [ ] **Step 4: Add a `@MapFrom` (reverse) test + a built-in conversion test**

```kotlin
    @Test fun `reverse mapping via MapFrom`() {
        val src = SourceFile.kotlin("Rev.kt", """
            import com.sahsenvar.kmapper.annotations.MapFrom
            data class TagDomain(val name: String)
            @MapFrom(TagDomain::class) data class TagRemote(val name: String)
        """.trimIndent())
        val r = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode)
        assert(r.generated("TagDomainMappers.kt").contains("fun TagDomain.toTagRemote()"))
    }

    @Test fun `built-in String to Int conversion`() {
        val src = SourceFile.kotlin("Conv.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            data class CountDomain(val n: Int)
            @MapTo(CountDomain::class) data class CountRemote(val n: String)
        """.trimIndent())
        val r = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode)
        assert(r.generated("CountRemoteMappers.kt").contains("StringIntConverter.convertToNonNull(n)"))
    }
```

Run: `./gradlew :processor:test -q` → all PASS. (Adjust generated-call assertion to the exact emitted form if needed.)

- [ ] **Step 5: Commit**

```bash
git add processor/src/test
git commit -m "test(processor): compile-testing harness + basic/nested/reverse/builtin-conversion

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

## Phase 3 — Correctness Fixes & Enum (TDD)

**Goal:** Each spec correctness fix as a red→green compile-test. Order matters: 3.1 → 3.5.

### Task 3.1: Wrap converter failures in `TypeConversionFailed`

**Files:** Create `core/src/commonMain/kotlin/com/sahsenvar/kmapper/ConvertOrFail.kt`; modify `processor/.../generator/MappingCodeGenerator.kt`.

- [ ] **Step 1: Add the core helper**

```kotlin
package com.sahsenvar.kmapper
inline fun <T> convertOrFail(from: String, to: String, block: () -> T): T =
    try { block() }
    catch (e: MappingException) { throw e }
    catch (e: Throwable) { throw MappingException.TypeConversionFailed(from, to, e) }
```
Commit: `git add core/src/.../ConvertOrFail.kt && git commit -m "feat(core): add convertOrFail helper"` (+ Co-Authored-By trailer).

- [ ] **Step 2: Write the failing test**

`processor/src/test/.../ConversionWrapTest.kt`:
```kotlin
package com.sahsenvar.kmapper.processor
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversionWrapTest {
    @Test fun `built-in conversion is wrapped in convertOrFail`() {
        val src = SourceFile.kotlin("M.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            data class CountDomain(val n: Int)
            @MapTo(CountDomain::class) data class CountRemote(val n: String)
        """.trimIndent())
        val r = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode)
        val gen = r.generated("CountRemoteMappers.kt")
        assert(gen.contains("convertOrFail(")) { gen }
        assert(gen.contains("StringIntConverter.convertToNonNull")) { gen }
    }
}
```

- [ ] **Step 3: Run — expect FAIL**

Run: `./gradlew :processor:test --tests "*ConversionWrapTest*" -q` → FAIL (raw converter call, no wrap).

- [ ] **Step 4: Implement the wrap in `MappingCodeGenerator`**

In `generateConvertMapping(...)`, wrap the emitted converter call. Replace the returned `CodeBlock`:
```kotlin
// non-null path was: %T.convertToNonNull(%N)
CodeBlock.of(
    "%M(%S,·%S)·{·%T.%N(%N)·}",
    MemberName("com.sahsenvar.kmapper", "convertOrFail"),
    sourceField.type.fqn(), targetField.type.fqn(),
    converterClassName, convertNonNullMethod, sourceField.name
)
```
(Use the existing helper that renders a type's FQN; if none, add a small `KSType.fqn(): String` extension. For nullable paths keep `convertTo`/`convertFrom` but still wrap.)

- [ ] **Step 5: Run — expect PASS**, then commit

Run: `./gradlew :processor:test --tests "*ConversionWrapTest*" -q` → PASS.
```bash
git add processor/src core/src
git commit -m "feat(processor): wrap converter failures in MappingException.TypeConversionFailed

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 3.2: Compile-time cycle detection (unconditional only)

**Files:** Create `processor/.../analyzer/CycleDetector.kt`; modify `MappingProcessor.kt`.

- [ ] **Step 1: Write the failing test**

`processor/src/test/.../CycleDetectionTest.kt`:
```kotlin
package com.sahsenvar.kmapper.processor
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals

class CycleDetectionTest {
    @Test fun `unconditional cycle fails compilation`() {
        val src = SourceFile.kotlin("Cyc.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            data class ADomain(val b: BDomain); data class BDomain(val a: ADomain)
            @MapTo(ADomain::class) data class A(val b: B)
            @MapTo(BDomain::class) data class B(val a: A)
        """.trimIndent())
        val r = compile(src)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode)
        assert(r.messages.contains("cycle", ignoreCase = true)) { r.messages }
    }

    @Test fun `nullable back-reference compiles`() {
        val src = SourceFile.kotlin("Ok.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            data class CatDomain(val parent: CatDomain?)
            @MapTo(CatDomain::class) data class Cat(val parent: Cat?)
        """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.OK, compile(src).exitCode)
    }

    @Test fun `collection self-reference compiles`() {
        val src = SourceFile.kotlin("Tree.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            data class NodeDomain(val children: List<NodeDomain>)
            @MapTo(NodeDomain::class) data class Node(val children: List<Node>)
        """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.OK, compile(src).exitCode)
    }
}
```

- [ ] **Step 2: Run — expect FAIL** (`unconditional cycle` test fails: today it compiles)

Run: `./gradlew :processor:test --tests "*CycleDetectionTest*" -q`

- [ ] **Step 3: Implement `CycleDetector`**

```kotlin
package com.sahsenvar.kmapper.processor
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration

/** Detects guaranteed-infinite mapping cycles: edges that traverse a field that is
 *  non-null AND not a collection (so the value must exist, forcing infinite construction). */
class CycleDetector(private val logger: KSPLogger) {

    fun check(mappedSources: List<KSClassDeclaration>) {
        val fqns = mappedSources.mapNotNull { it.qualifiedName?.asString() }.toSet()
        val edges: Map<String, List<String>> = mappedSources.associate { decl ->
            (decl.qualifiedName!!.asString()) to decl.primaryConstructor!!.parameters
                .filter { p -> !p.type.resolve().isMarkedNullable }
                .map { it.type.resolve() }
                .filterNot { it.isCollection() }
                .mapNotNull { it.declaration.qualifiedName?.asString() }
                .filter { it in fqns }
        }
        val visiting = mutableSetOf<String>(); val done = mutableSetOf<String>()
        val stack = ArrayDeque<String>()
        fun dfs(node: String) {
            if (node in done) return
            if (node in visiting) {
                val cycle = stack.toList().dropWhile { it != node } + node
                logger.error("Mapping cycle: ${cycle.joinToString(" -> ")} (guaranteed infinite). " +
                        "Break it with @Ignore or @UseMapTypeConverter.")
                return
            }
            visiting += node; stack.addLast(node)
            edges[node]?.forEach { dfs(it) }
            stack.removeLast(); visiting -= node; done += node
        }
        edges.keys.forEach(::dfs)
    }
}
```
Add a `KSType.isCollection()` helper (qualifiedName in `kotlin.collections.List`/`Set`/`Collection`/`Iterable`, or starts with `kotlinx.collections.immutable`). Wire it into `MappingProcessor.process()` **before** generation: collect all `@MapTo` source declarations and call `CycleDetector(logger).check(...)`. If `logger.error` was called, skip generation for that round (KSP marks the compilation failed).

- [ ] **Step 4: Run — expect PASS** (all three), then commit

Run: `./gradlew :processor:test --tests "*CycleDetectionTest*" -q` → PASS.
```bash
git add processor/src
git commit -m "feat(processor): compile-time detection of unconditional mapping cycles

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 3.3: `@KMapperConfig` converter discovery + `@UseMapTypeConverter` + missing-converter error

**Files:** modify `MappingProcessor.kt`, `analyzer/TypeMatcher.kt`.

- [ ] **Step 1: Write the failing tests**

`processor/src/test/.../ConverterConfigTest.kt`:
```kotlin
package com.sahsenvar.kmapper.processor
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals

private val CONVERTERS = SourceFile.kotlin("Converters.kt", """
    import com.sahsenvar.kmapper.converter.MapTypeConverter
    object IsoInstant : MapTypeConverter<String, kotlinx.datetime.Instant>(String::class, kotlinx.datetime.Instant::class) {
        override fun convertToNonNull(v: String) = kotlinx.datetime.Instant.parse(v)
        override fun convertFromNonNull(v: kotlinx.datetime.Instant) = v.toString()
    }
    object EpochInstant : MapTypeConverter<String, kotlinx.datetime.Instant>(String::class, kotlinx.datetime.Instant::class) {
        override fun convertToNonNull(v: String) = kotlinx.datetime.Instant.fromEpochMilliseconds(v.toLong())
        override fun convertFromNonNull(v: kotlinx.datetime.Instant) = v.toEpochMilliseconds().toString()
    }
""".trimIndent())

class ConverterConfigTest {
    @Test fun `@KMapperConfig converter is applied`() {
        val model = SourceFile.kotlin("M.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            import com.sahsenvar.kmapper.annotations.KMapperConfig
            import kotlinx.datetime.Instant
            @KMapperConfig(converters = [IsoInstant::class]) object Cfg
            data class EventDomain(val startsAt: Instant)
            @MapTo(EventDomain::class) data class EventRemote(val startsAt: String)
        """.trimIndent())
        val r = compile(CONVERTERS, model)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode)
        assert(r.generated("EventRemoteMappers.kt").contains("IsoInstant")) { r.generated("EventRemoteMappers.kt") }
    }

    @Test fun `per-field @UseMapTypeConverter overrides global`() {
        val model = SourceFile.kotlin("M2.kt", """
            import com.sahsenvar.kmapper.annotations.*
            import kotlinx.datetime.Instant
            @KMapperConfig(converters = [IsoInstant::class]) object Cfg
            data class EvDomain(val startsAt: Instant, val legacy: Instant)
            @MapTo(EvDomain::class) data class EvRemote(
                val startsAt: String,
                @UseMapTypeConverter(EpochInstant::class) val legacy: String,
            )
        """.trimIndent())
        val gen = compile(CONVERTERS, model).generated("EvRemoteMappers.kt")
        assert(gen.contains("IsoInstant") && gen.contains("EpochInstant")) { gen }
    }

    @Test fun `missing converter fails with clear error`() {
        val model = SourceFile.kotlin("M3.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            import kotlinx.datetime.Instant
            data class XDomain(val at: Instant)
            @MapTo(XDomain::class) data class XRemote(val at: String)
        """.trimIndent())
        val r = compile(model)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode)
        assert(r.messages.contains("no converter", ignoreCase = true)) { r.messages }
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

Run: `./gradlew :processor:test --tests "*ConverterConfigTest*" -q`

- [ ] **Step 3: Implement `@KMapperConfig` reading**

In `MappingProcessor.process()`, before generation:
```kotlin
val customConverters: Map<Pair<String,String>, String> = resolver
    .getSymbolsWithAnnotation("com.sahsenvar.kmapper.annotations.KMapperConfig")
    .filterIsInstance<KSClassDeclaration>()
    .flatMap { cfg ->
        val arg = cfg.annotations.first { it.shortName.asString() == "KMapperConfig" }
            .arguments.first { it.name?.asString() == "converters" }
        @Suppress("UNCHECKED_CAST")
        (arg.value as? List<KSType> ?: emptyList())
    }
    .mapNotNull { converterType -> converterType.toConverterPair() }  // resolve MapTypeConverter<S,T> supertype
    .toMap()
```
Add `KSType.toConverterPair(): Pair<Pair<String,String>, String>?` that walks the converter declaration's supertypes, finds `MapTypeConverter<S,T>`, and returns `((sFqn to tFqn) to converterFqn)`. Pass `customConverters` into `TypeMatcher`. In `TypeMatcher.findConverter`, consult `customConverters` **before** the built-in table (priority: per-field `@UseMapTypeConverter` already first in `determineMappingStrategy`, then custom, then built-in).

- [ ] **Step 4: Implement missing-converter error**

In `TypeMatcher.determineMappingStrategy`, when source/target types differ and the result would otherwise fall through to `Direct` (no same-type, collection, nested, enum, or converter match), call `logger.error("no converter for ${s.fqn()} -> ${t.fqn()}; add it to @KMapperConfig(converters=[...]) or annotate the field with @UseMapTypeConverter")` and return a sentinel `MappingStrategy.Unmappable` that the generator skips. (Pass `logger` into `TypeMatcher` if not already.)

- [ ] **Step 5: Run — expect PASS**, commit

Run: `./gradlew :processor:test --tests "*ConverterConfigTest*" -q` → PASS.
```bash
git add processor/src
git commit -m "feat(processor): @KMapperConfig converter discovery (AST) + missing-converter error

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 3.4: `MappableEnum` code generation

**Files:** modify `analyzer/TypeMatcher.kt` (+ `model/MappingStrategy.kt`), `generator/MappingCodeGenerator.kt`.

- [ ] **Step 1: Write the failing tests**

`processor/src/test/.../EnumMappingTest.kt`:
```kotlin
package com.sahsenvar.kmapper.processor
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals

class EnumMappingTest {
    @Test fun `string-backed MappableEnum`() {
        val src = SourceFile.kotlin("E.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            import com.sahsenvar.kmapper.MappableEnum
            enum class StatusDomain { PENDING, SHIPPED }
            enum class Status(override val wireValue: String) : MappableEnum<String> {
                PENDING("PENDING"), SHIPPED("in_transit");
            }
            data class OrderDomain(val status: StatusDomain)
            @MapTo(OrderDomain::class) data class OrderRemote(val status: String)
        """.trimIndent())
        // NOTE: enum-to-enum requires both ends mapped; this test maps wire String -> domain enum.
        val src2 = SourceFile.kotlin("E2.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            import com.sahsenvar.kmapper.MappableEnum
            enum class Status(override val wireValue: String) : MappableEnum<String> {
                PENDING("PENDING"), SHIPPED("in_transit");
            }
            data class OrderDomain(val status: Status)
            @MapTo(OrderDomain::class) data class OrderRemote(val status: String)
        """.trimIndent())
        val gen = compile(src2).generated("OrderRemoteMappers.kt")
        assert(gen.contains("entries.firstOrNull")) { gen }
        assert(gen.contains("UnknownEnumValue")) { gen }
    }

    @Test fun `unmapped enum fails compilation`() {
        val src = SourceFile.kotlin("E3.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            enum class Plain { A, B }
            data class DDomain(val p: Plain)
            @MapTo(DDomain::class) data class DRemote(val p: String)
        """.trimIndent())
        val r = compile(src)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode)
        assert(r.messages.contains("MappableEnum", ignoreCase = true)) { r.messages }
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

Run: `./gradlew :processor:test --tests "*EnumMappingTest*" -q`

- [ ] **Step 3: Implement enum strategy**

Add to `MappingStrategy`: `data class EnumFromWire(val enumFqn: String) : MappingStrategy()` and `object EnumToWire : MappingStrategy()`. In `TypeMatcher.determineMappingStrategy`, when either side is an enum (`(decl as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS`):
- Resolve the enum's `MappableEnum<W>` supertype → `wireFqn`. If absent → `logger.error("enum '<name>' must implement MappableEnum<...> or use @UseMapTypeConverter")` + `Unmappable`.
- If the **target** is the enum and the source type FQN == `wireFqn` → `EnumFromWire(targetFqn)`.
- If the **source** is the enum and the target type FQN == `wireFqn` → `EnumToWire`.
- If wire type ≠ field type → `logger.error("enum wire type mismatch: expected $wireFqn")`.

In `MappingCodeGenerator.generateFieldMapping`:
```kotlin
is MappingStrategy.EnumFromWire -> CodeBlock.of(
    "%T.entries.firstOrNull·{·it.wireValue·==·%N·}·?:·throw·%T(%S,·%N)",
    ClassName.bestGuess(strategy.enumFqn), sourceField.name,
    ClassName("com.sahsenvar.kmapper", "MappingException", "UnknownEnumValue"),
    targetField.type.shortName(), sourceField.name)
is MappingStrategy.EnumToWire -> CodeBlock.of("%N.wireValue", sourceField.name)
```
(Handle nullable enum fields with `?.`/null passthrough as the existing nullable logic does.)

- [ ] **Step 4: Run — expect PASS**, commit

Run: `./gradlew :processor:test --tests "*EnumMappingTest*" -q` → PASS.
```bash
git add processor/src
git commit -m "feat(processor): MappableEnum codegen (no silent ordinal/name default)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 3.5: `@CollectionWrapper` cross-module discovery

**Files:** create `core/.../annotations/CollectionWrapperDescriptor.kt`; modify `MappingProcessor.kt` (+ a `CollectionWrapperSupport.kt` helper); modify `generator`.

**Mechanism (spec §5.3 + §5.2):** KSP `getSymbolsWithAnnotation` can't see `@CollectionWrapper` in a dependency artifact. So: each module's KSP run that owns `@CollectionWrapper` functions **generates a descriptor** (annotated with a BINARY-retention `@CollectionWrapperDescriptor`) into package `com.sahsenvar.kmapper.generated`; consumers read those via `getDeclarationsFromPackage` (which DOES see dependencies).

- [ ] **Step 1: Add the descriptor annotation to `core`**

```kotlin
package com.sahsenvar.kmapper.annotations
@Target(AnnotationTarget.CLASS) @Retention(AnnotationRetention.BINARY)
annotation class CollectionWrapperDescriptor(val forType: String, val wrapFunction: String)
```
Commit to core.

- [ ] **Step 2: Write the failing (in-module) test**

`processor/src/test/.../CollectionWrapperTest.kt` (add `testImplementation(libs.kotlinx.collections.immutable)` to `processor/build.gradle.kts` first):
```kotlin
package com.sahsenvar.kmapper.processor
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionWrapperTest {
    @Test fun `List maps to PersistentList via @CollectionWrapper`() {
        val src = SourceFile.kotlin("W.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            import com.sahsenvar.kmapper.annotations.CollectionWrapper
            import kotlinx.collections.immutable.PersistentList
            import kotlinx.collections.immutable.toPersistentList

            @CollectionWrapper(forType = PersistentList::class)
            fun <T> List<T>.asPersistentList(): PersistentList<T> = toPersistentList()

            data class TagDomain(val name: String)
            data class ProductDomain(val tags: PersistentList<TagDomain>)
            @MapTo(TagDomain::class)     data class TagRemote(val name: String)
            @MapTo(ProductDomain::class) data class ProductRemote(val tags: List<TagRemote>)
        """.trimIndent())
        val r = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode)
        val gen = r.generated("ProductRemoteMappers.kt")
        assert(gen.contains(".map") && gen.contains("asPersistentList")) { gen }
    }

    @Test fun `duplicate wrapper for same type fails`() {
        val src = SourceFile.kotlin("Dup.kt", """
            import com.sahsenvar.kmapper.annotations.CollectionWrapper
            import kotlinx.collections.immutable.PersistentList
            import kotlinx.collections.immutable.toPersistentList
            @CollectionWrapper(forType = PersistentList::class)
            fun <T> List<T>.w1(): PersistentList<T> = toPersistentList()
            @CollectionWrapper(forType = PersistentList::class)
            fun <T> List<T>.w2(): PersistentList<T> = toPersistentList()
        """.trimIndent())
        val r = compile(src)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode)
        assert(r.messages.contains("CollectionWrapper", ignoreCase = true)) { r.messages }
    }
}
```

- [ ] **Step 3: Run — expect FAIL**

Run: `./gradlew :processor:test --tests "*CollectionWrapperTest*" -q`

- [ ] **Step 4: Implement descriptor generation + discovery**

In `MappingProcessor.process()`:
1. **Generate descriptors:** for each `getSymbolsWithAnnotation("com.sahsenvar.kmapper.annotations.CollectionWrapper")` function `f`, read `forType` (KSType→FQN) and build `wrapFqn = f.packageName + "." + f.simpleName`. Emit a file in package `com.sahsenvar.kmapper.generated`:
```kotlin
@CollectionWrapperDescriptor(forType = "<forTypeFqn>", wrapFunction = "<wrapFqn>")
public object KMapperWrapper_<sanitized-wrapName>
```
2. **Discover:** `resolver.getDeclarationsFromPackage("com.sahsenvar.kmapper.generated")` → for each class with `@CollectionWrapperDescriptor`, read `forType`/`wrapFunction`. Build `Map<forTypeFqn, wrapFqn>`. If a `forType` appears twice → `logger.error("multiple @CollectionWrapper for <forType>")`.
3. **Use in generation:** in `TypeMatcher`, when target field is a collection whose FQN is a known `forType`, return `MappingStrategy.WrappedCollection(elementStrategy, wrapFqn)`. Generator emits `source.map { <elem> }.let(::<wrapFn>)` or, since wrapFn is an extension, `source.map { <elem> }.<wrapSimpleName>()` via `MemberName(wrapPkg, wrapSimple)`.

> Multi-round note: descriptors are generated in an early round; `getDeclarationsFromPackage` picks them up in a later round (in-module) or directly from dependency artifacts (cross-module). Ensure the processor is re-entrant across rounds (guard against re-emitting the same descriptor file — track by name).

- [ ] **Step 5: Run — expect PASS**, commit

Run: `./gradlew :processor:test -q` (full suite) → all PASS.
```bash
git add core/src processor/src processor/build.gradle.kts
git commit -m "feat(processor): @CollectionWrapper descriptor generation + cross-module discovery

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

### Task 3.6: Listener dispatch emission (spec §7)

**Files:** modify `generator/MappingCodeGenerator.kt`.

- [ ] **Step 1: Write the failing test**

`processor/src/test/.../ListenerEmissionTest.kt`:
```kotlin
package com.sahsenvar.kmapper.processor
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals

class ListenerEmissionTest {
    @Test fun `generated mapper emits guarded listener dispatch`() {
        val src = SourceFile.kotlin("L.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            data class UserDomain(val id: String)
            @MapTo(UserDomain::class) data class UserRemote(val id: String)
        """.trimIndent())
        val r = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode)
        val gen = r.generated("UserRemoteMappers.kt")
        assert(gen.contains("KMapper.hasListeners")) { gen }
        assert(gen.contains("onMapStart") && gen.contains("onMapComplete")) { gen }
    }
}
```

- [ ] **Step 2: Run — expect FAIL** (`./gradlew :processor:test --tests "*ListenerEmissionTest*" -q`).

- [ ] **Step 3: Implement guarded dispatch in `generateMappingFunction`**

Change the emitted function from expression body to a block that dispatches around construction:
```kotlin
// pseudo of the FunSpec body builder:
addStatement("if·(%T.hasListeners)·%T.dispatch·{·onMapStart(this@%L,·%T::class)·}",
    KMAPPER, KMAPPER, functionName, targetClassName)
add("val·result·=·%T(\n", targetClassName); indent(); /* existing field assignments */ unindent(); add(")\n")
addStatement("if·(%T.hasListeners)·%T.dispatch·{·onMapComplete(this@%L,·result)·}",
    KMAPPER, KMAPPER, functionName)
addStatement("return·result")
```
where `KMAPPER = ClassName("com.sahsenvar.kmapper", "KMapper")`. (`onError` stays available on the interface for manual/`convertOrFail` use; per-field events deferred — spec §7.)

- [ ] **Step 4: Run — expect PASS**, then full suite + commit

Run: `./gradlew :processor:test -q` → all PASS.
```bash
git add processor/src
git commit -m "feat(processor): emit guarded MappingListener dispatch in generated mappers

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

---

## Phase 4 — `converters-compose` + Cross-Module Proof + DomatApp Migration

### Task 4.1: `converters-compose` wrappers

**Files:** `converters-compose/build.gradle.kts`, `converters-compose/src/commonMain/.../ImmutableConverters.kt`

- [ ] **Step 1: Apply KSP + publish to the module build**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    `maven-publish`
}
kotlin {
    androidTarget { publishLibraryVariants("release") }
    jvm(); iosArm64(); iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies { api(project(":core")); implementation(libs.kotlinx.collections.immutable) }
    }
}
android { namespace = "com.sahsenvar.kmapper.compose"; compileSdk = 36; defaultConfig { minSdk = 30 } }
dependencies { add("kspCommonMainMetadata", project(":processor")) }
// Standard KMP-KSP wiring so commonMain metadata is processed:
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") dependsOn("kspCommonMainKotlinMetadata")
}
```

- [ ] **Step 2: Create the wrappers**

```kotlin
package com.sahsenvar.kmapper.compose
import com.sahsenvar.kmapper.annotations.CollectionWrapper
import kotlinx.collections.immutable.*

@CollectionWrapper(forType = PersistentList::class)
fun <T> List<T>.asPersistentList(): PersistentList<T> = toPersistentList()
@CollectionWrapper(forType = ImmutableList::class)
fun <T> List<T>.asImmutableList(): ImmutableList<T> = toImmutableList()
@CollectionWrapper(forType = ImmutableSet::class)
fun <T> List<T>.asImmutableSet(): ImmutableSet<T> = toImmutableSet()
```

- [ ] **Step 3: Build + verify descriptors generated, then publish**

Run: `./gradlew :converters-compose:build -q`
Then verify: `find converters-compose/build -path '*com/sahsenvar/kmapper/generated*' -name '*.kt'`
Expected: descriptor files (one per wrapper, each carrying `@CollectionWrapperDescriptor`).
Run: `./gradlew :converters-compose:publishToMavenLocal :core:publishToMavenLocal :processor:publishToMavenLocal -q`
Commit + push.

### Task 4.2: Cross-module proof (`:sample`)

**Goal:** Prove a CONSUMER discovers `converters-compose`'s wrappers across the module boundary (not in-module).

- [ ] **Step 1: Add a throwaway `:sample` JVM module**

`settings.gradle.kts`: add `include(":sample")`. `sample/build.gradle.kts`:
```kotlin
plugins { alias(libs.plugins.kotlin.jvm); alias(libs.plugins.ksp) }
dependencies {
    implementation(project(":core"))
    implementation(project(":converters-compose"))
    ksp(project(":processor"))
    implementation(libs.kotlinx.collections.immutable)
}
```
`sample/src/main/kotlin/Sample.kt`:
```kotlin
import com.sahsenvar.kmapper.annotations.MapTo
import kotlinx.collections.immutable.PersistentList
data class TagD(val name: String)
data class ProductD(val tags: PersistentList<TagD>)
@MapTo(TagD::class)     data class TagR(val name: String)
@MapTo(ProductD::class) data class ProductR(val tags: List<TagR>)
```

- [ ] **Step 2: Build — proves cross-module wrapper discovery**

Run: `./gradlew :sample:build -q`
Expected: BUILD SUCCESSFUL; `find sample/build -name 'ProductRMappers.kt'` shows `.map { it.toTagD() }.asPersistentList()`.
If it fails with "no converter / cannot wrap PersistentList", the `getDeclarationsFromPackage` discovery (Task 3.5 Step 4.2) isn't reading the dependency artifact — fix there.

- [ ] **Step 3: Commit** (keep `:sample` as a living smoke test, or remove before release — decide at release time).

### Task 4.3: DomatApp migration

Work in `/Users/sahansenvar/StudioProjects/DomatApp` (separate commits on its current branch).

- [ ] **Step 1: Point DomatApp at the local artifacts**

In DomatApp `settings.gradle.kts` `dependencyResolutionManagement.repositories`, ensure `mavenLocal()` is present (first).

- [ ] **Step 2: Swap dependencies in `feature/auth/data/build.gradle.kts`**

Remove `implementation(projects.core.mapping)`; add:
```kotlin
commonMainImplementation("com.sahsenvar.kmapper:core:0.1.0-SNAPSHOT")
commonMainImplementation("com.sahsenvar.kmapper:converters-compose:0.1.0-SNAPSHOT")
add("kspCommonMainMetadata", "com.sahsenvar.kmapper:processor:0.1.0-SNAPSHOT")
```
Keep the existing `add("kspCommonMainMetadata", projects.core.processor)` ONLY if the module still uses remote/config/navigation generation; both KSP processors can coexist.

- [ ] **Step 3: Rewrite annotation imports**

In `feature/auth/data/.../remote/AuthSessionRemoteModel.kt` (and any other `@MapTo` user), replace `import com.domatapp.core.mapping.annotations.MapTo` → `import com.sahsenvar.kmapper.annotations.MapTo`.
Run: `grep -rn "com.domatapp.core.mapping" feature/` → expect no matches.

- [ ] **Step 4: Convert `MapperConfiguration.kt`**

`feature/auth/data/.../MapperConfiguration.kt` currently holds an empty `startKMapper {}`. Since auth registers no custom converters, **delete the file** (no `@KMapperConfig` needed when there are no converters). If any converters are later added, reintroduce as `@KMapperConfig(converters = [...]) object AuthMapperConfig`.

- [ ] **Step 5: Extend `toAuthError`**

In `feature/auth/data/.../mapper/AuthMapper.kt`, add branches:
```kotlin
import com.sahsenvar.kmapper.MappingException
// inside toAuthError() when-block, before the generic else:
is MappingException -> AuthError.Unknown(message ?: "Mapping error", this)
```

- [ ] **Step 6: Delete DomatApp's old mapping system**

- Remove `include(":core:mapping")` from DomatApp `settings.gradle.kts`; delete `core/mapping/`.
- In `core/processor`, delete the `mapping/` package and remove the line `com.domatapp.core.processor.mapping.MappingProcessorProvider` from `core/processor/src/main/resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider` (keep remote/config/navigation providers).

- [ ] **Step 7: Build DomatApp + verify regenerated mappers**

Run (per token rules, capture to log): `./gradlew :composeApp:compileDebugKotlin -I /tmp/agent-init.gradle --console=plain -q --warning-mode=summary --no-problems-report -x lintDebug > /tmp/g.log 2>&1; echo exit=$?`
Then: `find feature/auth/data/build -name 'AuthSessionRemoteModelMappers.kt' -exec head -20 {} +`
Expected: BUILD SUCCESSFUL; `toAuthSessionDomainModel()` regenerated with identical field mapping (now importing the KMapper-generated nested call). If failure, parse `grep -nE '^e: |error:' /tmp/g.log`.

- [ ] **Step 8: Commit DomatApp migration**

```bash
git add -A
git commit -m "refactor: migrate mapping to external com.sahsenvar.kmapper library

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 4.4: Maven Central publish scaffold (release prep)

- [ ] **Step 1: Add POM metadata + signing to published modules**

In each published module (`core`, `processor`, `converters-compose`, later `converters-arrow`) configure `publishing { publications.withType<MavenPublication> { pom { name; description; url; licenses; developers; scm } } }` and `signing { sign(publishing.publications) }`. Factor into a shared `gradle/publishing.gradle.kts` applied by each. Credentials (Central portal token, GPG key) come from the user's `~/.gradle/gradle.properties` / env — **the user supplies these; do not hardcode**.

- [ ] **Step 2: Dry-run local publication**

Run: `./gradlew publishToMavenLocal -q` (root) → all modules publish locally.
> Full Maven Central release (namespace verification on central.sonatype.com, signing keys, automated CI) is **out of scope this round** (spec §12). This task only establishes the publishing scaffold; the actual first release is a separate, user-driven step.

---

## Plan Self-Review

**1. Spec coverage:**
- §2 identity/dist → Phase 0 (repo, groupId), 4.4 (publish scaffold). ✓
- §3 artifacts (core/processor/converters-compose/arrow) → Phase 0.4, 1, 2, 4.1; arrow empty slot created in 0.4. ✓
- §4 MappingException (RequiredFieldMissing/TypeConversionFailed/UnknownEnumValue) → 1.3 + 3.1 + 3.4. ✓
- §5.1 @KMapperConfig + per-field override + missing-converter error → 3.3. ✓
- §5.2 runtime aggregation / generated package → used by 3.5 (descriptor discovery); **runtime converter `init` aggregation across modules is exercised only where custom converters exist; DomatApp has none, so no app-level init is generated this round** — noted, not a gap for current consumers. ✓
- §5.3 @CollectionWrapper + conflict guard → 3.5 + 4.1 + 4.2. ✓
- §5.4 MappableEnum (generic, mandatory, no silent default) → 3.4. ✓
- §6 compile-time cycle detection → 3.2. ✓
- §7 MappingListener/KMapper/LoggingMappingListener → 1.7. **Gap fixed:** generated mapper listener-dispatch emission was implied but not its own task — covered by note here: the `onMapStart/Complete` guarded emission is added in `MappingCodeGenerator` during 1.7→2.x; add an explicit assertion in a 3.x test. **Action:** see "Added" below.
- §8 reverse @MapFrom preserved → 2.4 Step 4. ✓
- §9 processor compile-testing → 2.3 + all 3.x tests. ✓
- §11 decoupling checklist (FQNs, MappingError, immutable, markers) → 2.2 (FQNs/error), 4.1 (immutable), markers need no work. ✓

**Added (closing the §7 gap):** Insert **Task 3.6 — Listener dispatch emission**: failing test asserting generated code contains `if·(KMapper.hasListeners)` + `onMapStart`/`onMapComplete`; implement guarded dispatch in `MappingCodeGenerator.generateMappingFunction` (wrap the `return Target(...)` so it dispatches start before and complete after); green; commit. (Same TDD shape as 3.1.)

**2. Placeholder scan:** `<from-domatapp>` and `<gh-user>` are explicit, sourced values (resolved in Task 0.2 Step 1 / 0.1 Step 1), not vague TODOs. No "implement later"/"handle edge cases" left.

**3. Type consistency:** `MappingException.{RequiredFieldMissing,TypeConversionFailed,UnknownEnumValue}`, `MapTypeConverter`, `MappableEnum<W>.wireValue`, `@KMapperConfig(converters)`, `@CollectionWrapper(forType)`, `@CollectionWrapperDescriptor(forType,wrapFunction)`, bilateral converter names (`StringIntConverter`…) — consistent across core (Phase 1), processor table (2.2 Step 5), and tests. ✓

> **Apply "Added" Task 3.6 before executing** (insert between 3.5 and Phase 4). It's specified above in full.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-06-04-KMapper-mapping-library.md`.** Implementation happens in the NEW `KMapper` repo (Phase 0 creates it); only the DomatApp migration (Task 4.3) touches this repo.







