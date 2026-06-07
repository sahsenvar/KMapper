# KMapper Test Coverage — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Close the test gaps found in the 2026-06-04 audit — add `commonTest` runtime coverage for `core` + `converters-immutable`, make the `processor` tests actually **execute** generated mappers (not just assert source strings), add a KMP `:integration-test` module that runs generated mappers end-to-end on JVM **and iOS**, and add property round-trip tests.

**Architecture:** Test framework stays **`kotlin.test`** (KMP, runs on all targets incl. `iosSimulatorArm64Test`). Add **`io.kotest:kotest-assertions-core`** (rich matchers) and **`io.kotest:kotest-property`** (round-trip props) — both commonTest/iOS-safe. Processor keeps **kctfork** but new tests use `result.classLoader` to load + invoke generated mappers. No mocking (use a `RecordingListener` stub).

**Tech Stack:** kotlin.test, kotest-assertions-core 6.1.11, kotest-property 6.1.11, kctfork 0.12.1 (KSP2), Kotlin 2.3.10 KMP.

**Basis:** the approved test audit/report (this session). Repo `/Users/sahansenvar/StudioProjects/KMapper`, root version `0.2.0-SNAPSHOT`, HTTPS origin via gh helper.

---

## Phase Decomposition

- **P0** — Test infra: catalog (`kotest-assertions`, `kotest-property`); add `commonTest` source set + test deps to `core` and `converters-immutable`. *Checkpoint:* `./gradlew :core:compileTestKotlinJvm` configures.
- **P1** — `core` commonTest suites (registry, primitive + datetime built-ins incl. negative cases, `KMapper`, `MappingException`, `convertOrFail`) + property round-trips. Runs JVM + iOS.
- **P2** — `processor` runtime-execution tests (classload + invoke generated mappers).
- **P3** — Converters: `converters-immutable` commonTest; move `converters-arrow` test → commonTest; add property round-trips to `converters-datetime`/`-bignumber` commonTest.
- **P4** — New `:integration-test` KMP module: real `@MapTo` models + commonTest running generated mappers end-to-end on JVM + iOS.
- **P5** — Full verification incl. `iosSimulatorArm64Test` + commit.

Note: these tests exercise existing production code — a test that FAILS reveals a real bug (investigate/report, do not weaken the test).

---

## Phase 0 — Test infra

### Task 0.1: Catalog

- [ ] **Step 1:** Resolve `io.kotest:kotest-assertions-core` + `io.kotest:kotest-property` latest 6.x on Maven Central (expect `6.1.11`). Add to `gradle/libs.versions.toml`:
```toml
[versions]
kotest = "6.1.11"
[libraries]
kotest-assertions = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }
kotest-property   = { module = "io.kotest:kotest-property", version.ref = "kotest" }
```

### Task 0.2: `core` gets a commonTest source set

**File:** modify `core/build.gradle.kts`

- [ ] **Step 1:** In `kotlin { sourceSets { … } }` add (keep the existing `jvmTest`):
```kotlin
commonTest.dependencies {
    implementation(kotlin("test"))
    implementation(libs.kotest.assertions)
    implementation(libs.kotest.property)
}
```

- [ ] **Step 2: Verify it configures**

Run: `./gradlew :core:tasks -q` → BUILD SUCCESSFUL; `iosSimulatorArm64Test` + `jvmTest` tasks present. Commit (`test: add kotest deps + core commonTest source set`, trailer).

### Task 0.3: `converters-immutable` gets a commonTest source set

- [ ] **Step 1:** In `converters-immutable/build.gradle.kts` add `commonTest.dependencies { implementation(kotlin("test")); implementation(libs.kotest.assertions) }`. Verify `:converters-immutable:tasks` configures. (Commit with 0.2 or separately.)

---

## Phase 1 — `core` commonTest suites

All files under `core/src/commonTest/kotlin/com/sahsenvar/kmapper/...`. Use `kotlin.test` `@Test` + kotest `shouldBe`/`shouldThrow`/`shouldHaveMessage`.

### Task 1.1: `TypeConverterRegistryTest` (covers the iosMain actual too)

**File:** create `core/src/commonTest/kotlin/com/sahsenvar/kmapper/converter/TypeConverterRegistryTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.sahsenvar.kmapper.converter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import kotlin.test.Test

private object AB : MapTypeConverter<String, Int>(String::class, Int::class) {
    override fun convertToNonNull(value: String) = value.toInt()
    override fun convertFromNonNull(value: Int) = value.toString()
}
private object AB2 : MapTypeConverter<String, Int>(String::class, Int::class) {
    override fun convertToNonNull(value: String) = -1
    override fun convertFromNonNull(value: Int) = "x"
}

class TypeConverterRegistryTest {
    @Test fun `register then get returns it`() {
        TypeConverterRegistry.register(AB)
        TypeConverterRegistry.has(String::class, Int::class) shouldBe true
        TypeConverterRegistry.get(String::class, Int::class) shouldBe AB
    }
    @Test fun `second register of same pair does not overwrite (first-write-wins)`() {
        TypeConverterRegistry.register(AB); TypeConverterRegistry.register(AB2)
        TypeConverterRegistry.get(String::class, Int::class) shouldBe AB
    }
    @Test fun `get unknown pair is null`() {
        TypeConverterRegistry.get(Int::class, Boolean::class).shouldBeNull()
    }
}
```
> Note: the registry is a global object; tests must tolerate shared state (first-write-wins makes them order-independent for the same pair). If cross-test pollution is a problem, the implementer may add a test-only reset or use unique converter type-pairs per test.

- [ ] **Step 2: Run on JVM + iOS**

Run: `./gradlew :core:jvmTest --tests "*TypeConverterRegistryTest*" -q` → PASS. Then `./gradlew :core:iosSimulatorArm64Test -q` (runs all core commonTest on the simulator) → PASS (this is the first thing ever to exercise the `iosMain` actual). Commit.

### Task 1.2: `PrimitiveConvertersTest` (round-trips + negative cases + null passthrough)

**File:** create `core/src/commonTest/.../converter/builtin/PrimitiveConvertersTest.kt`

- [ ] **Step 1: Write the test** (representative; repeat the pattern for each built-in)

```kotlin
package com.sahsenvar.kmapper.converter.builtin
import com.sahsenvar.kmapper.converter.MapTypeConverter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import kotlin.test.Test

class PrimitiveConvertersTest {
    @Test fun `StringInt round-trip`() {
        StringIntConverter.convertToNonNull("42") shouldBe 42
        StringIntConverter.convertFromNonNull(42) shouldBe "42"
    }
    @Test fun `StringInt null passthrough`() {
        StringIntConverter.convertTo(null).shouldBeNull()
        StringIntConverter.convertFrom(null).shouldBeNull()
    }
    @Test fun `StringInt malformed throws NumberFormatException (NOT wrapped by the converter itself)`() {
        shouldThrow<NumberFormatException> { StringIntConverter.convertToNonNull("abc") }
    }
    // Repeat round-trip + null + malformed for: StringLongConverter, StringDoubleConverter,
    // StringFloatConverter, StringBooleanConverter (Boolean: "true"->true; "abc"-> false per kotlin toBoolean, document actual behavior)
}
```
Run `:core:jvmTest --tests "*PrimitiveConvertersTest*"` → PASS. Commit.

### Task 1.3: `DateTimeBuiltinConvertersTest`

**File:** create `core/src/commonTest/.../converter/builtin/DateTimeBuiltinConvertersTest.kt`
- [ ] Round-trips for `StringInstantConverter` ("2026-06-04T10:15:30Z") and `LongInstantConverter` (epoch millis); malformed ISO → `shouldThrow` (the exception type kotlinx-datetime throws). Run + commit.

### Task 1.4: `KMapperTest`

**File:** create `core/src/commonTest/.../KMapperTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.sahsenvar.kmapper
import io.kotest.matchers.shouldBe
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.Test

private class RecordingListener : MappingListener {
    val starts = mutableListOf<KClass<*>>()
    override fun onMapStart(source: Any, target: KClass<*>) { starts += target }
}

class KMapperTest {
    private val l = RecordingListener()
    @AfterTest fun cleanup() { KMapper.removeListener(l) }
    @Test fun `addListener toggles hasListeners and dispatch calls it`() {
        KMapper.hasListeners shouldBe false
        KMapper.addListener(l)
        KMapper.hasListeners shouldBe true
        KMapper.dispatch { onMapStart("src", Int::class) }
        l.starts shouldBe listOf(Int::class)
    }
    @Test fun `removeListener clears`() {
        KMapper.addListener(l); KMapper.removeListener(l)
        KMapper.hasListeners shouldBe false
    }
}
```
Run + commit. (Mind global state — `@AfterTest` removes the listener.)

### Task 1.5: `MappingExceptionTest` + `ConvertOrFailTest`

**Files:** create `core/src/commonTest/.../MappingExceptionTest.kt`, `ConvertOrFailTest.kt`
- [ ] `MappingExceptionTest`: each subtype's `message` content + fields (`RequiredFieldMissing.field`, `TypeConversionFailed.from/to`, `UnknownEnumValue.enum/value`, `EmptyCollection.detail`).
- [ ] `ConvertOrFailTest`: `convertOrFail("a","b"){ 1 } shouldBe 1`; a block throwing a plain `RuntimeException` → `shouldThrow<MappingException.TypeConversionFailed>`; a block throwing a `MappingException.RequiredFieldMissing` → re-thrown unchanged (same type, not wrapped).
Run + commit.

### Task 1.6: Property round-trips (kotest-property)

**File:** create `core/src/commonTest/.../converter/builtin/PrimitiveRoundTripPropertyTest.kt`
- [ ] `forAll(Arb.int()) { n -> StringIntConverter.convertToNonNull(StringIntConverter.convertFromNonNull(n)) == n }` (and Long/Double analogues with appropriate `Arb`s). Run `:core:jvmTest` → PASS. Commit, push.

## Phase 2 — `processor` runtime-execution tests

The existing 25 processor tests only assert on generated source strings. These NEW tests compile, **load the generated class, and invoke the mapper** to verify real behavior. (Processor is a JVM module; use `kotlin.test` asserts — do not add kotest here.)

### Task 2.1: Runtime-exec helper

**File:** create `processor/src/test/kotlin/com/sahsenvar/kmapper/processor/RuntimeExecSupport.kt`

- [ ] **Step 1:**
```kotlin
package com.sahsenvar.kmapper.processor
import com.tschuchort.compiletesting.KotlinCompilation
import java.lang.reflect.InvocationTargetException

/** Generated top-level extension `fun Src.toX()` compiles to a static method on `<File>Kt`.
 *  e.g. source class `UserRemote` → file `UserRemoteMappers.kt` → class `UserRemoteMappersKt`. */
fun KotlinCompilation.Result.invokeMapper(fileKtClass: String, fnName: String, receiver: Any): Any? {
    val m = classLoader.loadClass(fileKtClass).declaredMethods.first { it.name == fnName }
    return try { m.invoke(null, receiver) } catch (e: InvocationTargetException) { throw e.targetException }
}
fun KotlinCompilation.Result.newInstance(className: String, vararg args: Any?): Any {
    val ctor = classLoader.loadClass(className).declaredConstructors.first { it.parameterCount == args.size }
    return ctor.newInstance(*args)
}
fun Any.prop(name: String): Any? =
    this::class.java.getMethod("get" + name.replaceFirstChar { it.uppercase() }).invoke(this)
```
(Existing `compile(...)` already sets `inheritClassPath = true` + `kspWithCompilation = true`, so `:core` is available and classes are produced.)

### Task 2.2: Basic + null-safety + converter runtime tests

**File:** create `processor/src/test/.../BasicMappingRuntimeTest.kt`

- [ ] **Step 1: Write the tests**
```kotlin
package com.sahsenvar.kmapper.processor
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.*

class BasicMappingRuntimeTest {
    @Test fun `mapper copies fields at runtime`() {
        val r = compile(SourceFile.kotlin("M.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            data class UserDomain(val id: String, val email: String)
            @MapTo(UserDomain::class) data class UserRemote(val id: String, val email: String)
        """.trimIndent()))
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode)
        val domain = r.invokeMapper("UserRemoteMappersKt", "toUserDomain",
            r.newInstance("UserRemote", "42", "a@b.com"))!!
        assertEquals("42", domain.prop("id")); assertEquals("a@b.com", domain.prop("email"))
    }
    @Test fun `nullable-to-nonnull null throws RequiredFieldMissing at runtime`() {
        val r = compile(SourceFile.kotlin("N.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            data class D(val id: String)
            @MapTo(D::class) data class R(val id: String?)
        """.trimIndent()))
        val ex = assertFails { r.invokeMapper("RMappersKt", "toD", r.newInstance("R", null as String?)) }
        assertTrue(ex::class.qualifiedName!!.contains("RequiredFieldMissing"), ex::class.qualifiedName)
    }
    @Test fun `built-in String to Int converts at runtime`() {
        val r = compile(SourceFile.kotlin("C.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            data class CountD(val n: Int)
            @MapTo(CountD::class) data class CountR(val n: String)
        """.trimIndent()))
        assertEquals(7, r.invokeMapper("CountRMappersKt", "toCountD", r.newInstance("CountR", "7"))!!.prop("n"))
    }
    @Test fun `bad conversion input wraps as TypeConversionFailed`() {
        val r = compile(SourceFile.kotlin("C2.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            data class CountD(val n: Int)
            @MapTo(CountD::class) data class CountR(val n: String)
        """.trimIndent()))
        val ex = assertFails { r.invokeMapper("CountRMappersKt", "toCountD", r.newInstance("CountR", "abc")) }
        assertTrue(ex::class.qualifiedName!!.contains("TypeConversionFailed"), ex::class.qualifiedName)
    }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew :processor:test --tests "*BasicMappingRuntimeTest*" -q` → PASS. **If `bad conversion` FAILS** (e.g. raw `NumberFormatException` escapes instead of `TypeConversionFailed`), that is a REAL bug in the generated `convertOrFail` wrapping — report it (do not weaken the test). Add a `@MapDefaultValue` runtime test (default substitutes when source is null). Commit + push.

---

## Phase 3 — Converters tests

### Task 3.1: `converters-immutable` commonTest

**File:** create `converters-immutable/src/commonTest/kotlin/com/sahsenvar/kmapper/immutable/ImmutableConvertersTest.kt`

- [ ] **Step 1:** (the wrappers are plain `List<T>` extension fns)
```kotlin
package com.sahsenvar.kmapper.immutable
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlin.test.Test

class ImmutableConvertersTest {
    @Test fun `asPersistentList preserves order`() {
        val r = listOf(1,2,3).asPersistentList()
        (r is PersistentList) shouldBe true
        r shouldContainExactly listOf(1,2,3)
    }
    @Test fun `asPersistentSet dedups`() {
        val r = listOf(1,2,2,3).asPersistentSet()
        (r is PersistentSet) shouldBe true
        r.toList().sorted() shouldBe listOf(1,2,3)
    }
    @Test fun `asImmutableList empty`() { emptyList<Int>().asImmutableList().size shouldBe 0 }
    // + asImmutableSet
}
```
Run `:converters-immutable:jvmTest` + `:converters-immutable:iosSimulatorArm64Test` → PASS. Commit.

### Task 3.2: Move `converters-arrow` test to commonTest (so it runs on iOS)

- [ ] **Step 1:** `git mv converters-arrow/src/jvmTest/kotlin/com/sahsenvar/kmapper/arrow/NonEmptyListWrapperTest.kt converters-arrow/src/commonTest/kotlin/com/sahsenvar/kmapper/arrow/NonEmptyListWrapperTest.kt`. In `converters-arrow/build.gradle.kts` move the `kotlin("test")` test dep from `jvmTest` to `commonTest` (add `commonTest.dependencies { implementation(kotlin("test")) }`; keep `jvmTest` empty or remove). The test already uses only `kotlin.test` + arrow-core (both KMP).
- [ ] **Step 2:** Run `:converters-arrow:iosSimulatorArm64Test -q` → PASS (NonEmptyList wrapper + EmptyCollection now verified on Native). Commit.

### Task 3.3: Property round-trips for datetime + bignumber

- [ ] **Step 1:** Add `implementation(libs.kotest.property)` (and `libs.kotest.assertions` if not present) to `commonTest` in `converters-datetime/build.gradle.kts` and `converters-bignumber/build.gradle.kts`.
- [ ] **Step 2:** Create `converters-bignumber/src/commonTest/.../BigNumberRoundTripPropertyTest.kt`:
```kotlin
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.forAll
import kotlin.test.Test
class BigNumberRoundTripPropertyTest {
    @Test fun `Long-BigInteger round trip`() = kotlinx.coroutines.runBlocking {
        forAll(Arb.long()) { n -> LongBigIntegerConverter.convertFromNonNull(LongBigIntegerConverter.convertToNonNull(n)) == n }
    }
}
```
(`forAll` is suspend → wrap in `runBlocking`, or use the non-suspend `checkAll`/blocking variant per the resolved kotest-property API — verify.) Add an analogous `String↔BigDecimal` property (filter `Arb.string()` to numeric, or use `Arb.bigDecimal`/`Arb.double`). Do the same in `converters-datetime` for an epoch/Long↔Instant round-trip. Run `:converters-bignumber:jvmTest`/`:converters-datetime:jvmTest` → PASS. Commit + push.

## Phase 4 — `:integration-test` module (end-to-end on JVM + iOS)

A non-published KMP module that declares real `@MapTo` models and, in `commonTest`, **runs the generated mappers and asserts mapped values** — on JVM and iOS Simulator. Highest-value addition: it exercises annotation → KSP → generated code → runtime, across the wrapper add-ons, the scalar `@KMapperConfig` path, enum, null-safety, and `EmptyCollection`, on both platforms.

### Task 4.1: Module setup

- [ ] **Step 1:** Create `integration-test/build.gradle.kts` (mirror `converters-arrow`'s KMP+KSP shape; NO publishing):
```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
}
kotlin {
    android { namespace = "com.sahsenvar.kmapper.itest"; compileSdk = 36; minSdk = 30 }
    jvm(); iosArm64(); iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(project(":converters-immutable"))
            implementation(project(":converters-arrow"))
            implementation(project(":converters-datetime"))
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.arrow.core)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies { implementation(kotlin("test")); implementation(libs.kotest.assertions) }
    }
}
dependencies {
    add("kspCommonMainMetadata", project(":processor"))
    // NOTE: integration-test consumes converter add-ons whose @CollectionWrapper descriptors live in
    // their JVM jars; if cross-module wrapper discovery needs the consumer's jvm KSP run, also add
    // add("kspJvm", project(":processor")) and verify the iOS path still resolves wrappers.
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") dependsOn("kspCommonMainKotlinMetadata")
}
```
Add `":integration-test"` to `settings.gradle.kts`.

### Task 4.2: Models (commonMain)

**File:** create `integration-test/src/commonMain/kotlin/com/sahsenvar/kmapper/itest/Models.kt`
```kotlin
package com.sahsenvar.kmapper.itest
import com.sahsenvar.kmapper.MappableEnum
import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.annotations.MapTo
import com.sahsenvar.kmapper.datetime.StringLocalDateConverter
import arrow.core.NonEmptyList
import kotlinx.collections.immutable.PersistentList
import kotlinx.datetime.LocalDate

enum class Status(override val wireValue: String) : MappableEnum<String> { ACTIVE("active"), BANNED("banned") }

data class TagD(val name: String)
data class UserD(
    val id: String, val joined: LocalDate, val status: Status,
    val tags: PersistentList<TagD>, val roles: NonEmptyList<String>,
)

@KMapperConfig(converters = [StringLocalDateConverter::class])
object ItestMapperConfig

@MapTo(TagD::class) data class TagR(val name: String)

@MapTo(UserD::class)
data class UserR(
    val id: String?,            // nullable → non-null (RequiredFieldMissing path)
    val joined: String,         // String → LocalDate (scalar converter via @KMapperConfig)
    val status: String,         // String → Status enum (MappableEnum)
    val tags: List<TagR>,       // List → PersistentList<TagD> (immutable add-on + nested element)
    val roles: List<String>,    // List → NonEmptyList<String> (arrow add-on)
)
```

### Task 4.3: End-to-end commonTest

**File:** create `integration-test/src/commonTest/kotlin/com/sahsenvar/kmapper/itest/EndToEndMappingTest.kt`
```kotlin
package com.sahsenvar.kmapper.itest
import com.sahsenvar.kmapper.MappingException
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFailsWith

class EndToEndMappingTest {
    private fun valid() = UserR(id = "42", joined = "2026-06-04", status = "active",
        tags = listOf(TagR("kotlin")), roles = listOf("admin", "user"))

    @Test fun `full happy-path mapping`() {
        val d = valid().toUserD()
        d.id shouldBe "42"
        d.joined shouldBe LocalDate(2026, 6, 4)
        d.status shouldBe Status.ACTIVE
        d.tags.map { it.name } shouldContainExactly listOf("kotlin")
        d.roles.toList() shouldBe listOf("admin", "user")
    }
    @Test fun `null required id throws RequiredFieldMissing`() {
        assertFailsWith<MappingException.RequiredFieldMissing> { valid().copy(id = null).toUserD() }
    }
    @Test fun `unknown enum value throws UnknownEnumValue`() {
        assertFailsWith<MappingException.UnknownEnumValue> { valid().copy(status = "???").toUserD() }
    }
    @Test fun `empty roles throws EmptyCollection`() {
        assertFailsWith<MappingException.EmptyCollection> { valid().copy(roles = emptyList()).toUserD() }
    }
    @Test fun `malformed date throws TypeConversionFailed`() {
        assertFailsWith<MappingException.TypeConversionFailed> { valid().copy(joined = "not-a-date").toUserD() }
    }
}
```

- [ ] **Step 1: Build + run on JVM**

Run: `./gradlew :integration-test:jvmTest --console=plain -q > /tmp/it.log 2>&1; echo exit=$?` → 0. **Any failure here is a real end-to-end bug** (cross-module wrapper discovery, scalar converter application, enum, null-safety, or EmptyCollection) — investigate/report; do not weaken tests. (If wrapper discovery fails for the consumer, revisit the `kspJvm`/discovery note in 4.1.)

- [ ] **Step 2: Run on iOS Simulator**

Run: `./gradlew :integration-test:iosSimulatorArm64Test --console=plain -q > /tmp/it-ios.log 2>&1; echo exit=$?` → 0 (requires Xcode/simulator; this proves the whole pipeline + add-ons + datetime converter work on Kotlin/Native).

- [ ] **Step 3: Commit + push**

```bash
git add settings.gradle.kts integration-test
git commit -m "test: add :integration-test module — end-to-end generated mappers on JVM + iOS

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

---

## Phase 5 — Full verification

### Task 5.1: Whole-suite run incl. iOS

- [ ] **Step 1: JVM build + all unit tests**

Run: `./gradlew build --console=plain -q > /tmp/all.log 2>&1; echo exit=$?` → 0. (Compiles every module + runs all `jvmTest`/`test`/commonTest-on-JVM.) On failure: `grep -nE '^e: |error:|FAILED|> Task .* FAILED' /tmp/all.log | head -30`.

- [ ] **Step 2: iOS Simulator tests across modules with commonTest**

Run: `./gradlew iosSimulatorArm64Test --console=plain -q > /tmp/all-ios.log 2>&1; echo exit=$?` → 0. (Aggregates `:core`, `:converters-immutable`, `:converters-arrow`, `:converters-datetime`, `:converters-bignumber`, `:integration-test` Native tests — first full Native test pass for the library.)

- [ ] **Step 3: Tally + commit any remaining**

Report the new test count per module and confirm the audit's HIGH/MED gaps are closed. Commit + push anything outstanding.

> **Out of scope (LOW, optional follow-up):** `sample` assertions (it stays a build-only smoke test; `:integration-test` supersedes its purpose), `MappingListener.onError`/`onFieldDefaulted`/`onConversion` runtime tests (those hooks aren't emitted yet — they're forward-compat stubs).

---

## Self-Review

**Coverage vs the audit's gaps:** HIGH-1 (processor never executes mappers) → P2 runtime-exec tests + P4 end-to-end ✓. HIGH-2 (core no commonTest / iOS registry untested) → P0.2 + P1.1 run on iOS ✓. HIGH-3 (core surface untested) → P1.1–1.6 ✓. HIGH-4 (immutable zero tests) → P3.1 ✓. HIGH-5 (no end-to-end runtime test) → P4 ✓. MED-6 (arrow JVM-only) → P3.2 move to commonTest ✓. MED-7 (registry priority) → P1.1 ✓. MED-8 (malformed-string negatives) → P1.2 + P2 (`abc`→TypeConversionFailed) ✓. MED-9 (`@MapDefaultValue` runtime) → P2.2 ✓. Property tests → P1.6 + P3.3 ✓. Library stack (kotlin.test + kotest-assertions + kotest-property + kctfork-runtime) → P0 + used throughout ✓.

**Placeholder scan:** kotest version "resolve latest 6.x (expect 6.1.11)" is a concrete resolve step. Repetitive converter tests give a representative + an explicit "repeat for X/Y/Z" list naming each. The `kspJvm`/cross-module-discovery note in P4.1 is a concrete contingency, not vague. No "implement later".

**Type/name consistency:** generated-class naming rule stated once (`<SourceClass>Mappers.kt` → `<SourceClass>MappersKt`, static method = the extension fn) and reused in P2/P4. `MappingException.{RequiredFieldMissing,TypeConversionFailed,UnknownEnumValue,EmptyCollection}` consistent with core. Converter object names (`StringIntConverter`, `StringLocalDateConverter`, `LongBigIntegerConverter`, wrapper fns `asPersistentList`/`asPersistentSet`/`asNonEmptyList`) match the implemented modules.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-04-test-coverage.md`. Two execution options:
1. **Subagent-Driven (recommended)** — fresh subagent per phase, review between (sequential to avoid concurrent commits).
2. **Inline Execution** — execute in this session with checkpoints.


