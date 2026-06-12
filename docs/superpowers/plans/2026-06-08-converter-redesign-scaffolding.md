# Converter Redesign Implementation Plan (reconciled 2026-06-11)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax. **TDD is mandatory:** write the failing test, watch it fail, implement, watch it pass. Cover every edge case listed.

**Goal:** Implement the locked converter redesign — fallback ladder, degradation sink, `Result` boundary, path-carrying errors, omit/copy defaults, collections element-ladder — per the spec (`docs/superpowers/specs/2026-06-08-converter-redesign-design.md`) and ledger (`docs/converter-redesign.md`).

**Architecture:** Core gains the 2-method `MapTypeConverter` (+ sanctioned-null variants), path-carrying `MappingException`, degradation events on the existing `KMapper` listener registry, and public conversion seams. The processor resolves converters pair-keyed/orientation-aware with two compile-time errors, then the generator emits `toXResult(): Result<X>` bodies that pick seams from the ladder table and apply defaults via omit/copy.

**Tech Stack:** Kotlin Multiplatform, KSP2, KotlinPoet, Kotest (FunSpec+withData / BehaviorSpec), kctfork.

**Test conventions (project rules):** Kotest in `commonTest`; converter tests `FunSpec` + `withData`; processor tests `BehaviorSpec` over kctfork; property (`checkAll`) + example mix; fixtures named `DataModel`/`DomainModel`; descriptive names only.

> **Commits:** project rule is *commit only when the user asks*. Each task ends with a commit message suggestion — run `git commit` only if the user opted in.

> ⚠️ **Phase 0 is a GATE.** If Task 1 fails, STOP and report — omit/copy and ladder rows 2/4/6/8 must be redesigned before anything else is built.

---

## File Structure

**Modules (Task 2.5 establishes this):** `:core` (artifact `kmapper-core`, standalone) ·
`:annotations` (NEW, artifact `kmapper-annotations`, depends on `:core`) · `:processor`
(Gradle path unchanged; artifact renamed `kmapper-compiler`). Packages never change.

**Core (`core/src/commonMain/kotlin/com/sahsenvar/kmapper/`)**
- `MappingException.kt` — **rewrite**: path-carrying sealed family + `withPathPrefix` + `UnsupportedConversion`.
- `ConverterErrors.kt` — **create**: `missingConverterMessage`, `unsupportedConversionMessage`.
- `MappingDegradation.kt` — **create**: degradation event family.
- `KMapper.kt` — **modify**: `MappingListener.onDegradation` default method.
- `ConversionSeams.kt` — **create** (replaces `ConvertOrFail.kt` — delete it): scalar seams.
- `CollectionSeams.kt` — **create**: `convertEachOr*` / `convertEntriesOr*` seams.
- `converter/MapTypeConverter.kt` — **rewrite**: 2 total methods + `OrNull` variants + `unsupported()`/`unsupported(message)`.
- `converter/UnsupportedDirection.kt` — **create**: FUNCTION-level annotation (reason only — stays in core: converter contract).
- `converter/builtin/PrimitiveConverters.kt` — **rewrite**: 28 pair objects. `converter/builtin/DateTimeConverters.kt` — **rewrite**: 2 Instant objects.

**Annotations (`annotations/src/commonMain/kotlin/com/sahsenvar/kmapper/annotations/`)** — module created in Task 2.5 (existing annotation files move here, same package)
- `OnFail.kt`, `ConvertWith.kt`, `ConvertTo.kt`, `ConvertFrom.kt` — **create** (Task 6).
- `IgnoreMap.kt` (rename of `Ignore.kt`), `IgnoreDefaultValue.kt`, `Validate.kt` — **create** (Task 6).
- `UseMapTypeConverter.kt`, `MapDefaultValue.kt`, `ValidateFrom.kt`, `ValidateTo.kt` — **delete** (Task 6).

**Processor (`processor/src/main/kotlin/com/sahsenvar/kmapper/processor/`)**
- `analyzer/ConverterIntrospector.kt` — **create**. `analyzer/FieldAnalyzer.kt` — **modify** (directives, drop MapDefaultValue). `analyzer/TypeMatcher.kt` — **modify** (pair registry, orientation, preconditions, warning). `model/FieldInfo.kt`, `model/MappingStrategy.kt` — **modify**. `generator/MappingCodeGenerator.kt` — **modify** (seam selection). `generator/FunctionNameGenerator.kt` — **modify** (`toXResult`). `MappingProcessor.kt` — **modify** (Result boundary, omit/copy).

**Tests** — core `commonTest` per task below; processor `src/test/kotlin/...` BehaviorSpec suites.

---

## Phase 0 — GATE: cross-module `hasDefault`

### Task 1: Empirical cross-module default-flag test

**Files:** Create `processor/src/test/kotlin/com/sahsenvar/kmapper/processor/CrossModuleHasDefaultGateTest.kt`

- [ ] **Step 1: Write the test** — a probe processor + two-stage compilation (stage 1 = "library" module, stage 2 = consumer with stage 1 on the classpath). Mirror the KSP2 wiring of the existing `compile()` helper used by `ConverterConfigTest` for compiler/KSP setup; the only addition is `classpaths += stage1.outputDirectory`.

```kotlin
@file:OptIn(ExperimentalCompilerApi::class)
package com.sahsenvar.kmapper.processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

class HasDefaultProbeProcessor(private val logger: KSPLogger) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val declaration = resolver.getClassDeclarationByName(
            resolver.getKSNameFromString("lib.LibDomainModel"),
        ) ?: run { logger.warn("PROBE:declaration-not-found"); return emptyList() }
        declaration.primaryConstructor?.parameters?.forEach { parameter ->
            logger.warn("HASDEFAULT:${parameter.name?.asString()}=${parameter.hasDefault}")
        }
        return emptyList()
    }
}

class HasDefaultProbeProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        HasDefaultProbeProcessor(environment.logger)
}

class CrossModuleHasDefaultGateTest : BehaviorSpec({
    given("a library module with constructor defaults, compiled separately") {
        val libraryCompilation = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin(
                    "LibDomainModel.kt",
                    """
                    package lib
                    data class LibDomainModel(
                        val id: Long,
                        val plan: String = "FREE",
                        val tags: List<String> = emptyList(),
                    )
                    """.trimIndent(),
                ),
            )
            inheritClassPath = true
        }
        val libraryResult = libraryCompilation.compile()
        libraryResult.exitCode shouldBe KotlinCompilation.ExitCode.OK

        `when`("a consumer module compiles against the library classes with the probe processor") {
            val consumerResult = KotlinCompilation().apply {
                sources = listOf(
                    SourceFile.kotlin("Consumer.kt", "package consumer\nval marker = lib.LibDomainModel(1L)"),
                )
                classpaths = listOf(libraryCompilation.classesDir)
                inheritClassPath = true
                // KSP2 wiring: copy EXACTLY from the existing compile() helper in this module,
                // registering HasDefaultProbeProvider as the symbol processor provider.
                configureKsp(useKsp2 = true) { symbolProcessorProviders += HasDefaultProbeProvider() }
            }.compile()

            then("hasDefault is readable across the module boundary") {
                consumerResult.exitCode shouldBe KotlinCompilation.ExitCode.OK
                consumerResult.messages shouldContain "HASDEFAULT:id=false"
                consumerResult.messages shouldContain "HASDEFAULT:plan=true"
                consumerResult.messages shouldContain "HASDEFAULT:tags=true"
            }
        }
    }
})
```

(Requires Task 2's Kotest runner in the processor module — if executing strictly in order, add `testImplementation(libs.kotest.runner.junit5)` to `processor/build.gradle.kts` now; Task 2 formalizes it.)

- [ ] **Step 2: Run** — `./gradlew processor:test --tests "com.sahsenvar.kmapper.processor.CrossModuleHasDefaultGateTest" --offline -q > /tmp/gate.log 2>&1; echo exit=$?; grep -E 'HASDEFAULT|FAILED|passed' /tmp/gate.log | head -20`
- [ ] **Step 3: Decide.** GREEN → record "gate PASSED" in the ledger §I and continue. RED (flags unreadable cross-module) → **STOP THE PLAN**, report to the user: rows 2/4/6/8 + omit/copy need redesign (candidate fallbacks: same-module-only defaults, or KSP option listing defaulted fields).
- [ ] **Step 4: Commit** — `test(processor): prove cross-module hasDefault flag readability (design gate)`.

---

## Phase 1 — Core error model & events

### Task 2: Kotest framework wiring

**Files:** Modify `gradle/libs.versions.toml`, `core/build.gradle.kts`, `processor/build.gradle.kts`.

- [ ] **Step 1:** Add to `[libraries]` in the catalog:

```toml
kotest-framework-engine = { module = "io.kotest:kotest-framework-engine", version.ref = "kotest" }
kotest-runner-junit5 = { module = "io.kotest:kotest-runner-junit5", version.ref = "kotest" }
```

- [ ] **Step 2:** `core/build.gradle.kts` — `commonTest.dependencies` += `implementation(libs.kotest.framework.engine)`; `jvmTest.dependencies` += `implementation(libs.kotest.runner.junit5)`; add at file level:

```kotlin
tasks.withType<Test>().configureEach { useJUnitPlatform() }
```

- [ ] **Step 3:** `processor/build.gradle.kts` — `testImplementation(libs.kotest.runner.junit5)` (keep existing `useJUnitPlatform()`).
- [ ] **Step 4: Verify** — temporary `core/src/commonTest/kotlin/com/sahsenvar/kmapper/KotestSmokeTest.kt` with `class KotestSmokeTest : FunSpec({ test("kotest runs") { 1 shouldBe 1 } })`; run `./gradlew core:jvmTest --tests "*KotestSmokeTest*" --offline -q` → PASS; delete the smoke test.
- [ ] **Step 5: Commit** — `build: wire Kotest engine/runner for core and processor`.

### Task 2.5: Module restructure — `:annotations` module + `kmapper-compiler` coordinates

**Files:** Create `annotations/build.gradle.kts`; Modify `settings.gradle.kts`, `processor/build.gradle.kts`, consumer build files; `git mv` the annotation sources.

- [ ] **Step 1:** `settings.gradle.kts` — add `include(":annotations")`.
- [ ] **Step 2: Create `annotations/build.gradle.kts`** — copy `core/build.gradle.kts`'s plugin/target skeleton (same KMP targets: android namespace `com.sahsenvar.kmapper.annotations`, jvm, iosArm64, iosSimulatorArm64; same publish/dokka/kover plugins) with:

```kotlin
kotlin {
    // ... same targets as core ...
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))   // typed `use: KClass<out MapTypeConverter<*, *>>`
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotest.assertions)
            implementation(libs.kotest.framework.engine)
        }
        jvmTest.dependencies { implementation(libs.kotest.runner.junit5) }
    }
}
tasks.withType<Test>().configureEach { useJUnitPlatform() }
// mavenPublishing block: coordinates("io.github.sahsenvar", "kmapper-annotations", version.toString())
// pom name "KMapper annotations", description "Mapping declaration annotations (@MapTo/@MapFrom/...)".
```

- [ ] **Step 3: Move sources** (package stays `com.sahsenvar.kmapper.annotations`):

```bash
mkdir -p annotations/src/commonMain/kotlin/com/sahsenvar/kmapper/annotations
git mv core/src/commonMain/kotlin/com/sahsenvar/kmapper/annotations/*.kt \
       annotations/src/commonMain/kotlin/com/sahsenvar/kmapper/annotations/
```

Also move the annotation-only test: `git mv core/src/commonTest/.../annotations/ValidateAnnotationsTest.kt` to `annotations/src/commonTest/.../annotations/` (core must not depend on `:annotations` — the dependency arrow is annotations → core).

- [ ] **Step 4:** `processor/build.gradle.kts` — add `implementation(project(":annotations"))`; change `coordinates(..., "kmapper-processor", ...)` → `coordinates(..., "kmapper-compiler", ...)` and update the pom `name`/`description` accordingly. Add `implementation(project(":annotations"))` to `sample`, `integration-test`, and any `converters-*` module that declares `@CollectionWrapper` objects (check: `grep -rln "kmapper.annotations" sample integration-test converters-* --include='*.kt' | grep -v build`).
- [ ] **Step 5: Verify** — `./gradlew annotations:compileKotlinJvm core:compileKotlinJvm processor:compileKotlin --offline -q` → PASS (core compiles WITHOUT the annotations module on its classpath — proves standalone).
- [ ] **Step 6: Commit** — `build!: split kmapper-annotations module; publish processor as kmapper-compiler`.

### Task 3: Path-carrying `MappingException` + message builders

**Files:** Rewrite `core/.../MappingException.kt`; Create `core/.../ConverterErrors.kt`; Rewrite test `core/src/commonTest/kotlin/com/sahsenvar/kmapper/MappingExceptionTest.kt` (and update `ValidationFailedExceptionTest.kt` constructor calls `field=` → `path=`).

- [ ] **Step 1: Failing test** (FunSpec):

```kotlin
package com.sahsenvar.kmapper

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class MappingExceptionTest : FunSpec({
    test("TypeConversionFailed message carries path and types") {
        val cause = NumberFormatException("abc")
        val failure = MappingException.TypeConversionFailed("score", "String", "Int", cause)
        failure.message shouldBe "Cannot convert score: String -> Int"
        failure.cause shouldBe cause
    }
    test("withPathPrefix prefixes dot segments and keeps the type") {
        val deep = MappingException.RequiredFieldMissing("zipCode").withPathPrefix("address")
        deep.message shouldBe "Required field missing: address.zipCode"
        (deep is MappingException.RequiredFieldMissing) shouldBe true
    }
    test("withPathPrefix joins index segments without a dot") {
        val inner = MappingException.TypeConversionFailed("price", "String", "Money", RuntimeException())
        inner.withPathPrefix("items[3]").path shouldBe "items[3].price"
        MappingException.RequiredFieldMissing("[3]").withPathPrefix("items").path shouldBe "items[3]"
    }
    test("message builders are well-formed; only UnsupportedConversion is a runtime type") {
        val unsupported = unsupportedConversionMessage("Long", "Int")
        unsupported shouldContain "Long -> Int conversion is unsupported"
        MappingException.UnsupportedConversion(unsupported).message shouldBe unsupported
        missingConverterMessage("String", "OccDomainModel") shouldContain
            "String -> OccDomainModel has no registered converter"
    }
})
```

- [ ] **Step 2: Run → fail** — `./gradlew core:jvmTest --tests "*MappingExceptionTest*" --offline -q`.
- [ ] **Step 3: Rewrite `MappingException.kt`:**

```kotlin
package com.sahsenvar.kmapper

sealed class MappingException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    /** Field path from the mapping root, e.g. "customer.address.zipCode" or "items[3].price". */
    abstract val path: String

    /** Same exception type with [prefix] prepended to the path. Used by seams — NOT wrapping. */
    abstract fun withPathPrefix(prefix: String): MappingException

    class RequiredFieldMissing(
        override val path: String,
    ) : MappingException("Required field missing: $path") {
        override fun withPathPrefix(prefix: String) = RequiredFieldMissing(joinPath(prefix, path))
    }

    class TypeConversionFailed(
        override val path: String,
        val from: String,
        val to: String,
        override val cause: Throwable,
    ) : MappingException("Cannot convert $path: $from -> $to", cause) {
        override fun withPathPrefix(prefix: String) =
            TypeConversionFailed(joinPath(prefix, path), from, to, cause)
    }

    class UnknownEnumValue(
        override val path: String,
        val enum: String,
        val value: Any,
    ) : MappingException("Unknown wire value '$value' for enum $enum at $path") {
        override fun withPathPrefix(prefix: String) = UnknownEnumValue(joinPath(prefix, path), enum, value)
    }

    class EmptyCollection(
        override val path: String,
        val detail: String,
    ) : MappingException("Collection cannot be empty: $detail (at $path)") {
        override fun withPathPrefix(prefix: String) = EmptyCollection(joinPath(prefix, path), detail)
    }

    class ValidationFailed(
        override val path: String,
        val reason: String,
    ) : MappingException("Validation failed for '$path': $reason") {
        override fun withPathPrefix(prefix: String) = ValidationFailed(joinPath(prefix, path), reason)
    }

    class UnsupportedConversion(
        message: String,
    ) : MappingException(message) {
        override val path: String get() = ""
        override fun withPathPrefix(prefix: String) = this
    }

    protected companion object {
        fun joinPath(prefix: String, path: String): String = when {
            path.isEmpty() -> prefix
            path.startsWith("[") -> "$prefix$path"
            else -> "$prefix.$path"
        }
    }
}
```

- [ ] **Step 4: Create `ConverterErrors.kt`** with the two builders exactly as in the spec §Errors (copy verbatim).
- [ ] **Step 5:** Fix in-repo callers that no longer compile: `ValidationFailedExceptionTest.kt` (`field=` → `path=`), `converters-arrow` NonEmpty converters' `EmptyCollection(detail)` → `EmptyCollection(path = "", detail = detail)` (path filled by seams later), and any `TypeConversionFailed(from,to,cause)` call sites (`ConvertOrFail.kt` is deleted in Task 9 — for now update it to `TypeConversionFailed("", from, to, e)`).
- [ ] **Step 6: Run → pass** (same filter), then `./gradlew core:jvmTest --offline -q` → green. **Commit** — `feat(core)!: path-carrying MappingException + converter error message builders`.

### Task 4: Degradation events + listener tap

**Files:** Create `core/.../MappingDegradation.kt`; Modify `core/.../KMapper.kt`; Test `core/src/commonTest/kotlin/com/sahsenvar/kmapper/MappingDegradationTest.kt`.

- [ ] **Step 1: Failing test:**

```kotlin
package com.sahsenvar.kmapper

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RecordingDegradationListener : MappingListener {
    val events = mutableListOf<MappingDegradation>()
    override fun onDegradation(event: MappingDegradation) { events.add(event) }
}

class MappingDegradationTest : FunSpec({
    test("degradation events dispatch through the existing KMapper listener registry") {
        val recorder = RecordingDegradationListener()
        KMapper.addListener(recorder)
        try {
            val event = MappingDegradation.AbsorbedConversionError("age", "String", "Int", NumberFormatException())
            if (KMapper.hasListeners) KMapper.dispatch { onDegradation(event) }
            recorder.events.single().path shouldBe "age"
        } finally { KMapper.removeListener(recorder) }
    }
    test("listeners that do not override onDegradation are unaffected (default no-op)") {
        val plainListener = object : MappingListener {}
        KMapper.addListener(plainListener)
        try {
            KMapper.dispatch { onDegradation(MappingDegradation.DroppedNullElement("items[0]")) }
        } finally { KMapper.removeListener(plainListener) }
    }
})
```

- [ ] **Step 2: Run → fail.** **Step 3: Create `MappingDegradation.kt`:**

```kotlin
package com.sahsenvar.kmapper

/** A reported-but-absorbed mapping event (report rule: lost data / breakage-born events only). */
sealed class MappingDegradation {
    abstract val path: String

    class AbsorbedConversionError(
        override val path: String, val from: String, val to: String, val cause: Throwable,
    ) : MappingDegradation()

    class DroppedBrokenElement(override val path: String, val cause: Throwable) : MappingDegradation()
    class DroppedNullElement(override val path: String) : MappingDegradation()
    class DuplicateKey(override val path: String, val key: String) : MappingDegradation()
    class ConvergedDuplicateElement(override val path: String) : MappingDegradation()
}
```

- [ ] **Step 4:** In `KMapper.kt`, add to `MappingListener` (replacing the forward-compat comment):

```kotlin
    /** Absorbed-leniency tap (skips, broken→null/default absorptions, duplicate keys). Default no-op. */
    fun onDegradation(event: MappingDegradation) {}
```

- [ ] **Step 5: Run → pass.** **Commit** — `feat(core): degradation event family on the KMapper listener registry`.

---

## Phase 2 — Converter type & annotations

### Task 5: `MapTypeConverter` rewrite + `@UnsupportedDirection`

**Files:** Rewrite `core/.../converter/MapTypeConverter.kt`; Create `core/.../converter/UnsupportedDirection.kt`; Test `core/src/commonTest/kotlin/com/sahsenvar/kmapper/converter/MapTypeConverterContractTest.kt`.

- [ ] **Step 1: Failing test:**

```kotlin
package com.sahsenvar.kmapper.converter

import com.sahsenvar.kmapper.MappingException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private object ForwardOnlyConverter : MapTypeConverter<Long, Int>(Long::class, Int::class) {
    override fun convertFrom(target: Int): Long = target.toLong()
}
private object SanctionedNullConverter : MapTypeConverter<Int, String>(Int::class, String::class) {
    override fun convertTo(source: Int): String = source.toString()
    override fun convertFrom(target: String): Int = target.toInt()
    override fun convertFromOrNull(target: String): Int? = if (target.isBlank()) null else target.toInt()
}

class MapTypeConverterContractTest : FunSpec({
    test("overridden direction works") { ForwardOnlyConverter.convertFrom(7) shouldBe 7L }
    test("non-overridden direction throws UnsupportedConversion with the pair in the message") {
        val failure = shouldThrow<MappingException.UnsupportedConversion> { ForwardOnlyConverter.convertTo(7L) }
        failure.message!! shouldContain "Long -> Int"
    }
    test("OrNull default delegates to the total method") {
        SanctionedNullConverter.convertToOrNull(5) shouldBe "5"
        shouldThrow<NumberFormatException> { SanctionedNullConverter.convertFromOrNull("abc") }
    }
    test("sanctioned null returns null instead of throwing for declared inputs") {
        SanctionedNullConverter.convertFromOrNull("") shouldBe null
        shouldThrow<NumberFormatException> { SanctionedNullConverter.convertFrom("") }  // total stays total
    }
})
```

(`""` hits `toInt()` in `convertFrom` → NumberFormatException; in `convertFromOrNull` it is sanctioned.)

- [ ] **Step 2: Run → fail** (old API). **Step 3: Rewrite `MapTypeConverter.kt`** exactly as the spec §The converter type (two total `open` methods, two `OrNull` delegating variants, `protected unsupported`, `defaultUnsupportedMessage`). **Core will not compile** until built-ins are rewritten (Task 7/8) — expected; proceed without commit.
- [ ] **Step 4: Create `UnsupportedDirection.kt`** (FUNCTION-level — the annotated function IS
  the direction; no `Direction` enum):

```kotlin
package com.sahsenvar.kmapper.converter

/**
 * Marks an overridden convertTo/convertFrom STUB as intentionally unsupported, with a
 * compile-time guiding reason. The stub body must be `= unsupported()`. Annotate the TOTAL
 * method only (annotating an OrNull variant is a compile error). The annotation wins over the
 * body: an annotated direction is treated as unsupported by the compiler regardless of body.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class UnsupportedDirection(val reason: String)
```

### Task 6: New/renamed annotations; delete dead ones

**Files** (all under `annotations/src/commonMain/kotlin/com/sahsenvar/kmapper/annotations/`):
Create `OnFail.kt`, `ConvertWith.kt`, `ConvertTo.kt`, `ConvertFrom.kt`, `IgnoreDefaultValue.kt`, `Validate.kt`;
Rename `Ignore.kt` → `IgnoreMap.kt` (class `Ignore` → `IgnoreMap`);
Delete `UseMapTypeConverter.kt`, `MapDefaultValue.kt`, `ValidateFrom.kt`, `ValidateTo.kt`.

- [ ] **Step 1: Create the four files:**

```kotlin
// OnFail.kt
package com.sahsenvar.kmapper.annotations

/** Brokenness policy. Absence is ALWAYS type-driven (nullable/default) — never a policy. */
enum class OnFail {
    /** Follow the fallback ladder (default). */
    Auto,
    /** Harden brokenness: a failed conversion is a hard error even with a declared escape. */
    Throw,
    /** Collection elements only: drop instead of null-in-place (reported). */
    Skip,
}
```

```kotlin
// ConvertWith.kt — ConvertTo.kt / ConvertFrom.kt are identical except for the class name & kdoc
package com.sahsenvar.kmapper.annotations

import com.sahsenvar.kmapper.converter.MapTypeConverter
import kotlin.reflect.KClass

/**
 * Per-field OVERRIDE of converter resolution and/or brokenness policy (both directions).
 * Never required for discovery — built-ins and @KMapperConfig converters are pair-discovered.
 * [use] left at its sentinel default means "keep the auto-discovered converter".
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ConvertWith(
    val use: KClass<out MapTypeConverter<*, *>> = MapTypeConverter::class,
    val onFail: OnFail = OnFail.Auto,
)
```

`ConvertTo` kdoc: "applies only to the @MapTo (forward) direction; beats @ConvertWith there."
`ConvertFrom` kdoc: "applies only to the @MapFrom (reverse) direction; beats @ConvertWith there."

- [ ] **Step 2: Create the Ignore family + `@Validate`:**

```kotlin
// IgnoreMap.kt (renamed from Ignore.kt; update the class name, keep target/retention)
package com.sahsenvar.kmapper.annotations

/** The mapper pretends this field does not exist for auto-matching: its value never flows
 *  through mapping; the target slot falls back to its constructor default or becomes a
 *  required external parameter on the generated function. */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class IgnoreMap
```

```kotlin
// IgnoreDefaultValue.kt
package com.sahsenvar.kmapper.annotations

/** Mapping treats this field's constructor default as nonexistent: the default is Kotlin
 *  construction convenience, NOT a wire fallback. Absence becomes RequiredFieldMissing; the
 *  field is built in the constructor stage (not omit/copy). No-op warning without a default. */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class IgnoreDefaultValue
```

```kotlin
// Validate.kt — replaces ValidateFrom/ValidateTo (field-anchored)
package com.sahsenvar.kmapper.annotations

import com.sahsenvar.kmapper.validation.Validator
import kotlin.reflect.KClass

/** Field-anchored validation: whenever this field participates in a mapping — as source
 *  (validated BEFORE conversion) or as target (validated AFTER) — its value runs through the
 *  validators. Fires at mapping time only; failure is a hard ValidationFailed. */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Validate(vararg val validators: KClass<out Validator<*>>)
```

- [ ] **Step 3: Delete** `UseMapTypeConverter.kt`, `MapDefaultValue.kt`, `ValidateFrom.kt`, `ValidateTo.kt`; rewrite the moved `ValidateAnnotationsTest.kt` against `@Validate`. The processor still references the deleted ones — processor compile breakage is fixed in Task 12; core must compile on its own after Task 7/8.
- [ ] **Step 4: Commit** (after Phase 3 makes core green) — bundled into Task 8's commit.

---

## Phase 3 — Built-in converters (core compiles again at the end of Task 8)

> **Ordering note:** Tasks 9–10 (seams) depend only on Task 3–4 types, not on built-ins; Tasks 7–8 restore core compilation first. The two pairs are independent of each other.

### Task 7: 12 numeric-widening + 9 X-pair objects

**Files:** Rewrite `core/.../converter/builtin/PrimitiveConverters.kt` (numeric part); Test `core/src/commonTest/kotlin/com/sahsenvar/kmapper/converter/builtin/NumericConvertersTest.kt` (replace `PrimitiveConvertersTest.kt` numeric assertions; delete `core/src/jvmTest/.../IntLongConverterTest.kt`).

- [ ] **Step 1: Failing test** (FunSpec + withData + property):

```kotlin
package com.sahsenvar.kmapper.converter.builtin

import com.sahsenvar.kmapper.MappingException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll

class NumericConvertersTest : FunSpec({
    context("widening converters: poorer -> richer via convertFrom, exact at boundaries") {
        withData(
            nameFn = { "${it.first}" },
            Triple("LongInt MAX", LongIntConverter.convertFrom(Int.MAX_VALUE), Int.MAX_VALUE.toLong()),
            Triple("LongInt MIN", LongIntConverter.convertFrom(Int.MIN_VALUE), Int.MIN_VALUE.toLong()),
            Triple("LongInt zero", LongIntConverter.convertFrom(0), 0L),
            Triple("ShortByte MAX", ShortByteConverter.convertFrom(Byte.MAX_VALUE), 127.toShort()),
            Triple("IntShort MIN", IntShortConverter.convertFrom(Short.MIN_VALUE), Short.MIN_VALUE.toInt()),
            Triple("DoubleInt MAX exact", DoubleIntConverter.convertFrom(Int.MAX_VALUE), 2147483647.0),
            Triple("FloatShort MAX exact", FloatShortConverter.convertFrom(Short.MAX_VALUE), Short.MAX_VALUE.toFloat()),
            Triple("DoubleFloat inf", DoubleFloatConverter.convertFrom(Float.POSITIVE_INFINITY), Double.POSITIVE_INFINITY),
        ) { (_, actual, expected) -> actual shouldBe expected }
        test("widening preserves the value (property)") {
            checkAll<Int> { value -> LongIntConverter.convertFrom(value).toInt() shouldBe value }
        }
        test("Float NaN survives DoubleFloat widening") {
            DoubleFloatConverter.convertFrom(Float.NaN).isNaN() shouldBe true
        }
    }
    context("narrowing direction is UnsupportedConversion") {
        withData(
            nameFn = { it.first }, 
            "Long->Int" to { LongIntConverter.convertTo(5L) },
            "Short->Byte" to { ShortByteConverter.convertTo(5.toShort()) },
            "Double->Float" to { DoubleFloatConverter.convertTo(1.0) },
        ) { (_, call) -> shouldThrow<MappingException.UnsupportedConversion> { call() } }
    }
    context("X-pairs refuse BOTH directions") {
        test("FloatInt") {
            shouldThrow<MappingException.UnsupportedConversion> { FloatIntConverter.convertTo(1f) }
            shouldThrow<MappingException.UnsupportedConversion> { FloatIntConverter.convertFrom(1) }
        }
        test("IntBoolean") {
            shouldThrow<MappingException.UnsupportedConversion> { IntBooleanConverter.convertTo(1) }
            shouldThrow<MappingException.UnsupportedConversion> { IntBooleanConverter.convertFrom(true) }
        }
        test("DoubleLong") {
            shouldThrow<MappingException.UnsupportedConversion> { DoubleLongConverter.convertFrom(1L) }
        }
    }
})
```

(`io.kotest.datatest.withData` ships inside `kotest-framework-engine` in Kotest 6.x — already wired in Task 2; the standalone datatest artifact stopped at 5.9.1 and must NOT be added.)

- [ ] **Step 2: Run → fail.** **Step 3: Rewrite `PrimitiveConverters.kt` numeric section** — richer-first; widening overrides `convertFrom`; lossy `convertTo` carries the annotation:

```kotlin
package com.sahsenvar.kmapper.converter.builtin

import com.sahsenvar.kmapper.converter.MapTypeConverter
import com.sahsenvar.kmapper.converter.UnsupportedDirection

// ===== Numeric widening (12): real convertFrom (poorer -> richer); narrowing = annotated stub =====
object ShortByteConverter : MapTypeConverter<Short, Byte>(Short::class, Byte::class) {
    override fun convertFrom(target: Byte): Short = target.toShort()

    @UnsupportedDirection("Short -> Byte narrows and can truncate; convert explicitly if intended.")
    override fun convertTo(source: Short): Byte = unsupported()
}
object IntByteConverter : MapTypeConverter<Int, Byte>(Int::class, Byte::class) {
    override fun convertFrom(target: Byte): Int = target.toInt()

    @UnsupportedDirection("Int -> Byte narrows and can truncate; convert explicitly if intended.")
    override fun convertTo(source: Int): Byte = unsupported()
}
object LongByteConverter : MapTypeConverter<Long, Byte>(Long::class, Byte::class) {
    override fun convertFrom(target: Byte): Long = target.toLong()

    @UnsupportedDirection("Long -> Byte narrows and can truncate; convert explicitly if intended.")
    override fun convertTo(source: Long): Byte = unsupported()
}
object IntShortConverter : MapTypeConverter<Int, Short>(Int::class, Short::class) {
    override fun convertFrom(target: Short): Int = target.toInt()

    @UnsupportedDirection("Int -> Short narrows and can truncate; convert explicitly if intended.")
    override fun convertTo(source: Int): Short = unsupported()
}
object LongShortConverter : MapTypeConverter<Long, Short>(Long::class, Short::class) {
    override fun convertFrom(target: Short): Long = target.toLong()

    @UnsupportedDirection("Long -> Short narrows and can truncate; convert explicitly if intended.")
    override fun convertTo(source: Long): Short = unsupported()
}
object LongIntConverter : MapTypeConverter<Long, Int>(Long::class, Int::class) {
    override fun convertFrom(target: Int): Long = target.toLong()

    @UnsupportedDirection("Long -> Int narrows and can truncate; convert explicitly if intended.")
    override fun convertTo(source: Long): Int = unsupported()
}
object FloatByteConverter : MapTypeConverter<Float, Byte>(Float::class, Byte::class) {
    override fun convertFrom(target: Byte): Float = target.toFloat()

    @UnsupportedDirection("Float -> Byte loses precision/range.")
    override fun convertTo(source: Float): Byte = unsupported()
}
object DoubleByteConverter : MapTypeConverter<Double, Byte>(Double::class, Byte::class) {
    override fun convertFrom(target: Byte): Double = target.toDouble()

    @UnsupportedDirection("Double -> Byte loses precision/range.")
    override fun convertTo(source: Double): Byte = unsupported()
}
object FloatShortConverter : MapTypeConverter<Float, Short>(Float::class, Short::class) {
    override fun convertFrom(target: Short): Float = target.toFloat()

    @UnsupportedDirection("Float -> Short loses precision/range.")
    override fun convertTo(source: Float): Short = unsupported()
}
object DoubleShortConverter : MapTypeConverter<Double, Short>(Double::class, Short::class) {
    override fun convertFrom(target: Short): Double = target.toDouble()

    @UnsupportedDirection("Double -> Short loses precision/range.")
    override fun convertTo(source: Double): Short = unsupported()
}
object DoubleIntConverter : MapTypeConverter<Double, Int>(Double::class, Int::class) {
    override fun convertFrom(target: Int): Double = target.toDouble()

    @UnsupportedDirection("Double -> Int loses precision/range.")
    override fun convertTo(source: Double): Int = unsupported()
}
object DoubleFloatConverter : MapTypeConverter<Double, Float>(Double::class, Float::class) {
    override fun convertFrom(target: Float): Double = target.toDouble()

    @UnsupportedDirection("Double -> Float loses precision.")
    override fun convertTo(source: Double): Float = unsupported()
}

// ===== X-pairs (9): both totals are annotated unsupported() stubs =====
object FloatIntConverter : MapTypeConverter<Float, Int>(Float::class, Int::class) {
    @UnsupportedDirection("Float -> Int truncates the fraction; decide floor/round/ceil explicitly.")
    override fun convertTo(source: Float): Int = unsupported()

    @UnsupportedDirection("Int -> Float is lossy above 2^24 (Float has a 24-bit mantissa).")
    override fun convertFrom(target: Int): Float = unsupported()
}
object FloatLongConverter : MapTypeConverter<Float, Long>(Float::class, Long::class) {
    @UnsupportedDirection("Float -> Long truncates the fraction; decide floor/round/ceil explicitly.")
    override fun convertTo(source: Float): Long = unsupported()

    @UnsupportedDirection("Long -> Float is lossy above 2^24.")
    override fun convertFrom(target: Long): Float = unsupported()
}
object DoubleLongConverter : MapTypeConverter<Double, Long>(Double::class, Long::class) {
    @UnsupportedDirection("Double -> Long truncates the fraction; decide floor/round/ceil explicitly.")
    override fun convertTo(source: Double): Long = unsupported()

    @UnsupportedDirection("Long -> Double is lossy above 2^53 (Double has a 53-bit mantissa).")
    override fun convertFrom(target: Long): Double = unsupported()
}
object ByteBooleanConverter : MapTypeConverter<Byte, Boolean>(Byte::class, Boolean::class) {
    @UnsupportedDirection("Byte -> Boolean has no canonical semantics (is 2 true?). Write a custom converter.")
    override fun convertTo(source: Byte): Boolean = unsupported()

    @UnsupportedDirection("Boolean -> Byte has no canonical encoding (0/1? -1?). Write a custom converter.")
    override fun convertFrom(target: Boolean): Byte = unsupported()
}
// ShortBooleanConverter, IntBooleanConverter, LongBooleanConverter, FloatBooleanConverter,
// DoubleBooleanConverter: identical shape — Short/Int/Long/Float/Double in place of Byte,
// with the same two reasons. Write all five out explicitly.
```

- [ ] **Step 4: Run → pass** (numeric test only; String tests still red until Task 8).

### Task 8: 7 String pairs + 2 Instant + core green

**Files:** Finish `PrimitiveConverters.kt` (String section); Rewrite `core/.../converter/builtin/DateTimeConverters.kt`; Tests `StringConvertersTest.kt`, `InstantConvertersTest.kt` (replace old `PrimitiveConvertersTest.kt`, `DateTimeBuiltinConvertersTest.kt`, `PrimitiveRoundTripPropertyTest.kt` → fold round-trips in).

- [ ] **Step 1: Failing tests:**

```kotlin
package com.sahsenvar.kmapper.converter.builtin

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll

class StringConvertersTest : FunSpec({
    context("format (convertTo) is total") {
        withData(
            "0" to IntStringConverter.convertTo(0),
            "-123" to IntStringConverter.convertTo(-123),
            "2147483647" to IntStringConverter.convertTo(Int.MAX_VALUE),
            "9223372036854775807" to LongStringConverter.convertTo(Long.MAX_VALUE),
            "true" to BooleanStringConverter.convertTo(true),
        ) { (expected, actual) -> actual shouldBe expected }
    }
    context("parse (convertFrom) accepts valid input incl. boundaries") {
        withData(
            7 to IntStringConverter.convertFrom("007"),
            5 to IntStringConverter.convertFrom("+5"),
            Int.MIN_VALUE to IntStringConverter.convertFrom("-2147483648"),
            127.toByte() to ByteStringConverter.convertFrom("127"),
            1.5f to FloatStringConverter.convertFrom("1.5"),
            1.5 to DoubleStringConverter.convertFrom("1.5"),
        ) { (expected, actual) -> actual shouldBe expected }
    }
    context("parse rejects malformed / out-of-range / wrong case") {
        withData(
            nameFn = { it.first },
            "int overflow" to { IntStringConverter.convertFrom("2147483648") },
            "byte overflow" to { ByteStringConverter.convertFrom("128") },
            "alpha" to { IntStringConverter.convertFrom("abc") },
            "empty" to { IntStringConverter.convertFrom("") },
            "decimal into int" to { IntStringConverter.convertFrom("1.5") },
            "padded" to { IntStringConverter.convertFrom(" 5 ") },
        ) { (_, call) -> shouldThrow<NumberFormatException> { call() } }
        withData(
            nameFn = { "boolean '$it'" },
            "TRUE", "True", "yes", "1", "", " true ",
        ) { bad -> shouldThrow<IllegalArgumentException> { BooleanStringConverter.convertFrom(bad) } }
    }
    test("round-trip property: parse(format(x)) == x") {
        checkAll<Int> { value -> IntStringConverter.convertFrom(IntStringConverter.convertTo(value)) shouldBe value }
        checkAll<Long> { value -> LongStringConverter.convertFrom(LongStringConverter.convertTo(value)) shouldBe value }
        checkAll<Boolean> { value -> BooleanStringConverter.convertFrom(BooleanStringConverter.convertTo(value)) shouldBe value }
    }
})

class InstantConvertersTest : FunSpec({
    test("ISO round-trip") {
        val iso = "2026-06-08T00:00:00Z"
        InstantStringConverter.convertTo(InstantStringConverter.convertFrom(iso)) shouldBe iso
    }
    test("malformed ISO throws") {
        shouldThrow<IllegalArgumentException> { InstantStringConverter.convertFrom("not-a-date") }
    }
    test("epoch millis round-trip (property)") {
        checkAll<Long> { millis ->
            InstantLongConverter.convertTo(InstantLongConverter.convertFrom(millis)) shouldBe millis
        }
    }
})
```

- [ ] **Step 2: Run → fail.** **Step 3: Append the String section** (richer-first: numeric/Boolean is S, String is T; `convertTo` = format, `convertFrom` = parse):

```kotlin
// ===== String pairs (7): format total / parse throws on malformed (rides the ladder) =====
object ByteStringConverter : MapTypeConverter<Byte, String>(Byte::class, String::class) {
    override fun convertTo(source: Byte): String = source.toString()
    override fun convertFrom(target: String): Byte = target.toByte()
}
object ShortStringConverter : MapTypeConverter<Short, String>(Short::class, String::class) {
    override fun convertTo(source: Short): String = source.toString()
    override fun convertFrom(target: String): Short = target.toShort()
}
object IntStringConverter : MapTypeConverter<Int, String>(Int::class, String::class) {
    override fun convertTo(source: Int): String = source.toString()
    override fun convertFrom(target: String): Int = target.toInt()
}
object LongStringConverter : MapTypeConverter<Long, String>(Long::class, String::class) {
    override fun convertTo(source: Long): String = source.toString()
    override fun convertFrom(target: String): Long = target.toLong()
}
object FloatStringConverter : MapTypeConverter<Float, String>(Float::class, String::class) {
    override fun convertTo(source: Float): String = source.toString()
    override fun convertFrom(target: String): Float = target.toFloat()
}
object DoubleStringConverter : MapTypeConverter<Double, String>(Double::class, String::class) {
    override fun convertTo(source: Double): String = source.toString()
    override fun convertFrom(target: String): Double = target.toDouble()
}
object BooleanStringConverter : MapTypeConverter<Boolean, String>(Boolean::class, String::class) {
    override fun convertTo(source: Boolean): String = source.toString()
    override fun convertFrom(target: String): Boolean = target.toBooleanStrict()
}
```

and rewrite `DateTimeConverters.kt`:

```kotlin
package com.sahsenvar.kmapper.converter.builtin

import com.sahsenvar.kmapper.converter.MapTypeConverter
import kotlinx.datetime.Instant

object InstantStringConverter : MapTypeConverter<Instant, String>(Instant::class, String::class) {
    override fun convertTo(source: Instant): String = source.toString()
    override fun convertFrom(target: String): Instant = Instant.parse(target)
}
object InstantLongConverter : MapTypeConverter<Instant, Long>(Instant::class, Long::class) {
    override fun convertTo(source: Instant): Long = source.toEpochMilliseconds()
    override fun convertFrom(target: Long): Instant = Instant.fromEpochMilliseconds(target)
}
```

- [ ] **Step 4:** Delete the superseded old tests; `./gradlew core:jvmTest --offline -q` → **core fully green**.
- [ ] **Step 5: Commit** — `feat(core)!: 2-method MapTypeConverter, @UnsupportedDirection, OnFail annotations, 30 built-in pair objects (richer-first)`.

---

## Phase 4 — Conversion seams (public, parity)

### Task 9: Scalar seams

**Files:** Delete `core/.../ConvertOrFail.kt` (+ its test `ConvertOrFailTest.kt`); Create `core/.../ConversionSeams.kt`; Test `core/src/commonTest/kotlin/com/sahsenvar/kmapper/ConversionSeamsTest.kt`.

- [ ] **Step 1: Failing test** — full behavior matrix (uses `RecordingDegradationListener` from Task 4's test; move it to a shared test fixture file `core/src/commonTest/kotlin/com/sahsenvar/kmapper/RecordingDegradationListener.kt`):

```kotlin
package com.sahsenvar.kmapper

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class ConversionSeamsTest : FunSpec({
    val parse: (String) -> Int = { it.toInt() }
    val parseOrNull: (String) -> Int? = { if (it.isBlank()) null else it.toInt() }

    lateinit var recorder: RecordingDegradationListener
    beforeTest { recorder = RecordingDegradationListener(); KMapper.addListener(recorder) }
    afterTest { KMapper.removeListener(recorder) }

    context("convertOrFail (hard cell)") {
        test("ok") { "5".convertOrFail("n", "String", "Int", parse) shouldBe 5 }
        test("broken -> TypeConversionFailed with path") {
            val failure = shouldThrow<MappingException.TypeConversionFailed> {
                "abc".convertOrFail("n", "String", "Int", parse)
            }
            failure.path shouldBe "n"; recorder.events shouldBe emptyList()
        }
        test("absent -> RequiredFieldMissing") {
            shouldThrow<MappingException.RequiredFieldMissing> {
                (null as String?).convertOrFail("n", "String", "Int", parse)
            }
        }
        test("inner MappingException propagates path-prefixed, NOT wrapped") {
            val inner = MappingException.RequiredFieldMissing("zipCode")
            val surfaced = shouldThrow<MappingException.RequiredFieldMissing> {
                "x".convertOrFail("address", "AddressData", "AddressDomain") { throw inner }
            }
            surfaced.path shouldBe "address.zipCode"
        }
    }
    context("convertOrNull (ladder row 3/7)") {
        test("ok / absent / sanctioned are silent") {
            ("5" as String?).convertOrNull("n", "String", "Int", parseOrNull) shouldBe 5
            (null as String?).convertOrNull("n", "String", "Int", parseOrNull) shouldBe null
            ("" as String?).convertOrNull("n", "String", "Int", parseOrNull) shouldBe null
            recorder.events shouldBe emptyList()
        }
        test("broken -> null + AbsorbedConversionError report") {
            ("abc" as String?).convertOrNull("n", "String", "Int", parseOrNull) shouldBe null
            recorder.events.single().shouldBeInstanceOf<MappingDegradation.AbsorbedConversionError>()
        }
    }
    context("convertOrElse (ladder row 2/4/6/8)") {
        test("absent -> fallback silent; broken -> fallback reported; sanctioned -> fallback silent") {
            (null as String?).convertOrElse(9, "n", "String", "Int", parseOrNull) shouldBe 9
            recorder.events.size shouldBe 0
            ("abc" as String?).convertOrElse(9, "n", "String", "Int", parseOrNull) shouldBe 9
            recorder.events.size shouldBe 1
            ("" as String?).convertOrElse(9, "n", "String", "Int", parseOrNull) shouldBe 9
            recorder.events.size shouldBe 1
        }
    }
    context("strict variants (OnFail.Throw)") {
        test("broken rethrows; absent and sanctioned stay soft") {
            shouldThrow<MappingException.TypeConversionFailed> {
                ("abc" as String?).convertOrNullStrict("n", "String", "Int", parseOrNull)
            }
            (null as String?).convertOrNullStrict("n", "String", "Int", parseOrNull) shouldBe null
            ("" as String?).convertOrNullStrict("n", "String", "Int", parseOrNull) shouldBe null
            shouldThrow<MappingException.TypeConversionFailed> {
                ("abc" as String?).convertOrElseStrict(9, "n", "String", "Int", parseOrNull)
            }
            (null as String?).convertOrElseStrict(9, "n", "String", "Int", parseOrNull) shouldBe 9
        }
    }
    test("orRequired") {
        5.orRequired("n") shouldBe 5
        shouldThrow<MappingException.RequiredFieldMissing> { (null as Int?).orRequired("n") }
    }
})
```

- [ ] **Step 2: Run → fail.** **Step 3: Create `ConversionSeams.kt`:**

```kotlin
package com.sahsenvar.kmapper

@PublishedApi
internal fun reportDegradation(event: MappingDegradation) {
    if (KMapper.hasListeners) KMapper.dispatch { onDegradation(event) }
}

@PublishedApi
internal fun toMappingException(path: String, from: String, to: String, cause: Throwable): MappingException =
    if (cause is MappingException) cause.withPathPrefix(path)
    else MappingException.TypeConversionFailed(path, from, to, cause)

/** Absence guard for Direct/seam-less spots: null → RequiredFieldMissing(path). */
fun <T : Any> T?.orRequired(path: String): T =
    this ?: throw MappingException.RequiredFieldMissing(path)

/** Hard cell (rows 1/5, and OnFail.Throw on non-null no-default targets). */
inline fun <S : Any, T : Any> S.convertOrFail(
    path: String, from: String, to: String, convert: (S) -> T,
): T = try {
    convert(this)
} catch (cause: Throwable) {
    throw toMappingException(path, from, to, cause)
}

inline fun <S : Any, T : Any> S?.convertOrFail(
    path: String, from: String, to: String, convert: (S) -> T,
): T = orRequired(path).convertOrFail(path, from, to, convert)

/** Nullable target, Auto: absent/sanctioned → null silent; broken → null + report. */
inline fun <S : Any, T : Any> S?.convertOrNull(
    path: String, from: String, to: String, convert: (S) -> T?,
): T? {
    if (this == null) return null
    return try {
        convert(this)
    } catch (cause: Throwable) {
        reportDegradation(MappingDegradation.AbsorbedConversionError(path, from, to, cause))
        null
    }
}

/** Nullable target, OnFail.Throw: broken rethrows; absence/sanctioned stay type-driven. */
inline fun <S : Any, T : Any> S?.convertOrNullStrict(
    path: String, from: String, to: String, convert: (S) -> T?,
): T? = this?.let { source ->
    try {
        convert(source)
    } catch (cause: Throwable) {
        throw toMappingException(path, from, to, cause)
    }
}

/** Defaulted target (omit/copy stage), Auto: absent/sanctioned → fallback silent; broken → fallback + report. */
inline fun <S : Any, T : Any> S?.convertOrElse(
    fallback: T, path: String, from: String, to: String, convert: (S) -> T?,
): T {
    if (this == null) return fallback
    return try {
        convert(this) ?: fallback
    } catch (cause: Throwable) {
        reportDegradation(MappingDegradation.AbsorbedConversionError(path, from, to, cause))
        fallback
    }
}

/** Defaulted target, OnFail.Throw: broken rethrows; absence/sanctioned → fallback. */
inline fun <S : Any, T : Any> S?.convertOrElseStrict(
    fallback: T, path: String, from: String, to: String, convert: (S) -> T?,
): T {
    if (this == null) return fallback
    return try {
        convert(this) ?: fallback
    } catch (cause: Throwable) {
        throw toMappingException(path, from, to, cause)
    }
}
```

- [ ] **Step 4:** Delete `ConvertOrFail.kt` + `ConvertOrFailTest.kt`. Run → pass. **Commit** — `feat(core)!: ladder conversion seams replace convertOrFail wrapper`.

### Task 10: Collection seams

**Files:** Create `core/.../CollectionSeams.kt`; Test `core/src/commonTest/kotlin/com/sahsenvar/kmapper/CollectionSeamsTest.kt`.

- [ ] **Step 1: Failing test:**

```kotlin
package com.sahsenvar.kmapper

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CollectionSeamsTest : FunSpec({
    val parseOrNull: (String) -> Int? = { if (it.isBlank()) null else it.toInt() }
    val parse: (String) -> Int = { it.toInt() }

    lateinit var recorder: RecordingDegradationListener
    beforeTest { recorder = RecordingDegradationListener(); KMapper.addListener(recorder) }
    afterTest { KMapper.removeListener(recorder) }

    context("convertEachOrSkip — List<T> default") {
        test("drops null (reported) and broken (reported), keeps order, sanctioned drops silently") {
            listOf("1", null, "abc", "4", "").convertEachOrSkip("tags", "String", "Int", parseOrNull) shouldBe
                listOf(1, 4)
            recorder.events.map { it::class.simpleName to it.path } shouldBe listOf(
                "DroppedNullElement" to "tags[1]",
                "DroppedBrokenElement" to "tags[2]",
            )
        }
        test("empty in, empty out, no events") {
            emptyList<String?>().convertEachOrSkip("tags", "String", "Int", parseOrNull) shouldBe emptyList()
            recorder.events shouldBe emptyList()
        }
    }
    context("convertEachOrNull — List<T?> default (alignment preserved)") {
        test("null passes silently, broken nulls with report, length preserved") {
            listOf("1", null, "abc").convertEachOrNull("xs", "String", "Int", parseOrNull) shouldBe
                listOf(1, null, null)
            recorder.events.single().shouldBeInstanceOf<MappingDegradation.AbsorbedConversionError>()
            recorder.events.single().path shouldBe "xs[2]"
        }
    }
    context("convertEachOrFail — OnFail.Throw on List<T>") {
        test("broken element is hard with indexed path; null element still skips (type-driven absence)") {
            val failure = shouldThrow<MappingException.TypeConversionFailed> {
                listOf("1", "abc").convertEachOrFail("xs", "String", "Int", parse)
            }
            failure.path shouldBe "xs[1]"
            listOf("1", null, "2").convertEachOrFail("xs", "String", "Int", parse) shouldBe listOf(1, 2)
        }
    }
    context("convertEachOrNullStrict — OnFail.Throw on List<T?>") {
        test("broken hard, null passes") {
            shouldThrow<MappingException.TypeConversionFailed> {
                listOf("abc").convertEachOrNullStrict("xs", "String", "Int", parseOrNull)
            }
            listOf("1", null).convertEachOrNullStrict("xs", "String", "Int", parseOrNull) shouldBe listOf(1, null)
        }
    }
    context("Set: always skip + convergence report") {
        test("convergent duplicates reported") {
            listOf("01", "1").convertEachOrSkipToSet("ids", "String", "Int", parseOrNull) shouldBe setOf(1)
            recorder.events.single().shouldBeInstanceOf<MappingDegradation.ConvergedDuplicateElement>()
        }
    }
    context("Map entries") {
        test("broken value drops entry with keyed path; key collision is last-wins + report") {
            mapOf("a" to "1", "b" to "abc").convertEntriesOrSkip(
                "prices", convertKey = { it }, convertValue = parseOrNull,
            ) shouldBe mapOf("a" to 1)
            recorder.events.first().shouldBeInstanceOf<MappingDegradation.DroppedBrokenElement>()
            recorder.events.first().path shouldBe """prices["b"]"""

            recorder.events.clear()
            mapOf("x" to "1", "X" to "2").convertEntriesOrSkip(
                "byKey", convertKey = { it.lowercase() }, convertValue = parseOrNull,
            ) shouldBe mapOf("x" to 2)
            recorder.events.single().shouldBeInstanceOf<MappingDegradation.DuplicateKey>()
        }
    }
})
```

- [ ] **Step 2: Run → fail.** **Step 3: Create `CollectionSeams.kt`:**

```kotlin
package com.sahsenvar.kmapper

/** Element ladder, List<T> default: null→skip+report, broken→skip+report, sanctioned→skip silent. */
inline fun <S : Any, T : Any> Iterable<S?>.convertEachOrSkip(
    path: String, from: String, to: String, convert: (S) -> T?,
): List<T> {
    val result = ArrayList<T>()
    forEachIndexed { index, element ->
        if (element == null) {
            reportDegradation(MappingDegradation.DroppedNullElement("$path[$index]"))
        } else {
            try {
                convert(element)?.let(result::add)
            } catch (cause: Throwable) {
                reportDegradation(MappingDegradation.DroppedBrokenElement("$path[$index]", cause))
            }
        }
    }
    return result
}

/** Element ladder, List<T?> default: null pass-through silent, broken→null-in-place+report. */
inline fun <S : Any, T : Any> Iterable<S?>.convertEachOrNull(
    path: String, from: String, to: String, convert: (S) -> T?,
): List<T?> = mapIndexed { index, element ->
    if (element == null) {
        null
    } else {
        try {
            convert(element)
        } catch (cause: Throwable) {
            reportDegradation(MappingDegradation.AbsorbedConversionError("$path[$index]", from, to, cause))
            null
        }
    }
}

/** OnFail.Throw on List<T>: broken→hard; null element still skips (absence is type-driven). */
inline fun <S : Any, T : Any> Iterable<S?>.convertEachOrFail(
    path: String, from: String, to: String, convert: (S) -> T,
): List<T> {
    val result = ArrayList<T>()
    forEachIndexed { index, element ->
        if (element == null) {
            reportDegradation(MappingDegradation.DroppedNullElement("$path[$index]"))
        } else {
            result.add(element.convertOrFail("$path[$index]", from, to, convert))
        }
    }
    return result
}

/** OnFail.Throw on List<T?>: broken→hard; null pass-through. */
inline fun <S : Any, T : Any> Iterable<S?>.convertEachOrNullStrict(
    path: String, from: String, to: String, convert: (S) -> T?,
): List<T?> = mapIndexed { index, element ->
    element?.let { source ->
        try {
            convert(source)
        } catch (cause: Throwable) {
            throw toMappingException("$path[$index]", from, to, cause)
        }
    }
}

/** Set: always skip; post-conversion convergence is reported. */
inline fun <S : Any, T : Any> Iterable<S?>.convertEachOrSkipToSet(
    path: String, from: String, to: String, convert: (S) -> T?,
): Set<T> {
    val result = LinkedHashSet<T>()
    forEachIndexed { index, element ->
        if (element == null) {
            reportDegradation(MappingDegradation.DroppedNullElement("$path[$index]"))
        } else {
            try {
                val converted = convert(element)
                if (converted != null && !result.add(converted)) {
                    reportDegradation(MappingDegradation.ConvergedDuplicateElement("$path[$index]"))
                }
            } catch (cause: Throwable) {
                reportDegradation(MappingDegradation.DroppedBrokenElement("$path[$index]", cause))
            }
        }
    }
    return result
}

/** OnFail.Throw to Set: broken→hard; null skips; convergence reported. */
inline fun <S : Any, T : Any> Iterable<S?>.convertEachOrFailToSet(
    path: String, from: String, to: String, convert: (S) -> T,
): Set<T> {
    val result = LinkedHashSet<T>()
    forEachIndexed { index, element ->
        if (element == null) {
            reportDegradation(MappingDegradation.DroppedNullElement("$path[$index]"))
        } else if (!result.add(element.convertOrFail("$path[$index]", from, to, convert))) {
            reportDegradation(MappingDegradation.ConvergedDuplicateElement("$path[$index]"))
        }
    }
    return result
}

/** Map default: unproducible key/value drops the entry (+report); collision last-wins (+report). */
inline fun <KS : Any, VS : Any, KT : Any, VT : Any> Map<KS, VS?>.convertEntriesOrSkip(
    path: String,
    convertKey: (KS) -> KT?,
    convertValue: (VS) -> VT?,
): Map<KT, VT> {
    val result = LinkedHashMap<KT, VT>()
    for ((key, value) in this) {
        val entryPath = "$path[\"$key\"]"
        val convertedKey = try {
            convertKey(key) ?: continue                       // sanctioned-null key → silent drop
        } catch (cause: Throwable) {
            reportDegradation(MappingDegradation.DroppedBrokenElement(entryPath, cause)); continue
        }
        if (value == null) {
            reportDegradation(MappingDegradation.DroppedNullElement(entryPath)); continue
        }
        val convertedValue = try {
            convertValue(value) ?: continue                   // sanctioned-null value → silent drop
        } catch (cause: Throwable) {
            reportDegradation(MappingDegradation.DroppedBrokenElement(entryPath, cause)); continue
        }
        if (result.put(convertedKey, convertedValue) != null) {
            reportDegradation(MappingDegradation.DuplicateKey(entryPath, key.toString()))
        }
    }
    return result
}

/** Map, OnFail.Throw: broken key/value is hard; null value skips (absence type-driven); collision reported. */
inline fun <KS : Any, VS : Any, KT : Any, VT : Any> Map<KS, VS?>.convertEntriesOrFail(
    path: String,
    convertKey: (KS) -> KT,
    convertValue: (VS) -> VT,
): Map<KT, VT> {
    val result = LinkedHashMap<KT, VT>()
    for ((key, value) in this) {
        val entryPath = "$path[\"$key\"]"
        val convertedKey = key.convertOrFail(entryPath, "key", "key", convertKey)
        if (value == null) {
            reportDegradation(MappingDegradation.DroppedNullElement(entryPath)); continue
        }
        val convertedValue = value.convertOrFail(entryPath, "value", "value", convertValue)
        if (result.put(convertedKey, convertedValue) != null) {
            reportDegradation(MappingDegradation.DuplicateKey(entryPath, key.toString()))
        }
    }
    return result
}

/** Map with nullable target values: value null-in-place; broken value→null+report; broken key drops entry. */
inline fun <KS : Any, VS : Any, KT : Any, VT : Any> Map<KS, VS?>.convertEntriesValueOrNull(
    path: String,
    convertKey: (KS) -> KT?,
    convertValue: (VS) -> VT?,
): Map<KT, VT?> {
    val result = LinkedHashMap<KT, VT?>()
    for ((key, value) in this) {
        val entryPath = "$path[\"$key\"]"
        val convertedKey = try {
            convertKey(key) ?: continue
        } catch (cause: Throwable) {
            reportDegradation(MappingDegradation.DroppedBrokenElement(entryPath, cause)); continue
        }
        val convertedValue = if (value == null) {
            null
        } else {
            try {
                convertValue(value)
            } catch (cause: Throwable) {
                reportDegradation(MappingDegradation.AbsorbedConversionError(entryPath, "value", "value", cause))
                null
            }
        }
        if (result.containsKey(convertedKey)) {
            reportDegradation(MappingDegradation.DuplicateKey(entryPath, key.toString()))
        }
        result[convertedKey] = convertedValue
    }
    return result
}
```

- [ ] **Step 4: Run → pass.** **Commit** — `feat(core): element-ladder collection seams (List/Set/Map) with degradation reporting`.

---

## Phase 5 — Processor resolution

### Task 11: `ConverterIntrospector`

**Files:** Create `processor/src/main/kotlin/com/sahsenvar/kmapper/processor/analyzer/ConverterIntrospector.kt` (exercised by Task 14's compile-tests).

- [ ] **Step 1: Implement:**

```kotlin
package com.sahsenvar.kmapper.processor.analyzer

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration

/** Which directions a converter provides, and any declared @UnsupportedDirection reasons. */
data class ConverterShape(
    val sourceFqn: String,
    val targetFqn: String,
    val providesTo: Boolean,
    val providesFrom: Boolean,
    val unsupportedToReason: String?,
    val unsupportedFromReason: String?,
    /** @UnsupportedDirection found on an OrNull variant — compile error at resolution. */
    val orNullAnnotated: Boolean,
)

class ConverterIntrospector(private val resolver: Resolver) {
    private val converterBaseFqn = "com.sahsenvar.kmapper.converter.MapTypeConverter"
    private val unsupportedDirectionFqn = "com.sahsenvar.kmapper.converter.UnsupportedDirection"

    fun shapeOf(converterFqn: String): ConverterShape? {
        val declaration = resolver.getClassDeclarationByName(
            resolver.getKSNameFromString(converterFqn),
        ) ?: return null
        val (sourceFqn, targetFqn) = typeArgumentsOf(declaration) ?: return null

        // Function-level detection: a direction is PROVIDED iff declared AND its total method
        // is not annotated @UnsupportedDirection (the annotation wins — bodies are opaque to KSP).
        var declaredTo = false
        var declaredFrom = false
        var reasonTo: String? = null
        var reasonFrom: String? = null
        var orNullAnnotated = false
        declaration.getDeclaredFunctions().forEach { function ->
            val reason = unsupportedReasonOf(function)
            when (function.simpleName.asString()) {
                "convertTo" -> { declaredTo = true; if (reason != null) reasonTo = reason }
                "convertFrom" -> { declaredFrom = true; if (reason != null) reasonFrom = reason }
                "convertToOrNull" -> { declaredTo = true; if (reason != null) orNullAnnotated = true }
                "convertFromOrNull" -> { declaredFrom = true; if (reason != null) orNullAnnotated = true }
            }
        }
        return ConverterShape(
            sourceFqn = sourceFqn,
            targetFqn = targetFqn,
            providesTo = declaredTo && reasonTo == null,
            providesFrom = declaredFrom && reasonFrom == null,
            unsupportedToReason = reasonTo,
            unsupportedFromReason = reasonFrom,
            orNullAnnotated = orNullAnnotated,
        )
    }

    private fun unsupportedReasonOf(function: com.google.devtools.ksp.symbol.KSFunctionDeclaration): String? =
        function.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == unsupportedDirectionFqn
        }?.arguments?.firstOrNull { it.name?.asString() == "reason" }?.value as? String

    private fun typeArgumentsOf(declaration: KSClassDeclaration): Pair<String, String>? {
        for (supertype in declaration.superTypes) {
            val resolved = supertype.resolve()
            if (resolved.declaration.qualifiedName?.asString() != converterBaseFqn) continue
            val source = resolved.arguments.getOrNull(0)?.type?.resolve()?.declaration?.qualifiedName?.asString()
            val target = resolved.arguments.getOrNull(1)?.type?.resolve()?.declaration?.qualifiedName?.asString()
            if (source != null && target != null) return source to target
        }
        return null
    }
}
```

- [ ] **Step 2:** `./gradlew processor:compileKotlin --offline -q` — expect remaining failures only from not-yet-updated callers (Tasks 12–13). No commit yet.

### Task 12: `FieldInfo` + `FieldAnalyzer` directives

**Files:** Modify `processor/.../model/FieldInfo.kt`, `processor/.../analyzer/FieldAnalyzer.kt`.

- [ ] **Step 1: `FieldInfo.kt`** — remove `defaultValue`; replace `useConverter` with three directives:

```kotlin
/** Per-field converter/policy override read from @ConvertWith / @ConvertTo / @ConvertFrom. */
data class ConverterDirective(
    /** null = keep auto-discovery (the `use` parameter was left at its sentinel). */
    val converterFqn: String?,
    /** "Auto" | "Throw" | "Skip" — mirror of com.sahsenvar.kmapper.annotations.OnFail. */
    val onFail: String,
)
```

In `FieldInfo`: replace `val defaultValue: String?` and `val useConverter: String?` with

```kotlin
    val convertWith: ConverterDirective? = null,
    val convertToDirective: ConverterDirective? = null,
    val convertFromDirective: ConverterDirective? = null,
```

and add helpers:

```kotlin
    /** Effective directive for the requested direction (direction-scoped beats bilateral). */
    fun directiveFor(isReverse: Boolean): ConverterDirective? =
        if (isReverse) convertFromDirective ?: convertWith else convertToDirective ?: convertWith

    fun onFailFor(isReverse: Boolean): String = directiveFor(isReverse)?.onFail ?: "Auto"
```

Also in `FieldInfo`: replace `val validateFrom: List<String>` / `val validateTo: List<String>`
with the single field-anchored list, and add the default-masking flag:

```kotlin
    /** FQNs of Validator<T> objects from @Validate — fire whenever this field enters a mapping. */
    val validators: List<String> = emptyList(),
    /** @IgnoreDefaultValue: the constructor default is invisible to mapping. */
    val ignoreDefaultValue: Boolean = false,
```

```kotlin
    /** The only default flag mapping decisions may consult (omit/copy, external params). */
    val usesDefaultInMapping: Boolean get() = hasDefault && !ignoreDefaultValue
```

- [ ] **Step 2: `FieldAnalyzer.kt`** — delete `extractMapDefaultValue` and `extractUseConverter`; add one extractor used for all three annotations:

```kotlin
    private fun extractConverterDirective(annotated: KSAnnotated, shortName: String): ConverterDirective? {
        val annotationFqn = "com.sahsenvar.kmapper.annotations.$shortName"
        val annotation = annotated.annotations.firstOrNull {
            it.shortName.asString() == shortName ||
                it.annotationType.resolve().declaration.qualifiedName?.asString() == annotationFqn
        } ?: return null

        val useArgument = annotation.arguments.firstOrNull { it.name?.asString() == "use" }?.value as? KSType
        val useFqn = useArgument?.declaration?.qualifiedName?.asString()
            ?.takeIf { it != "com.sahsenvar.kmapper.converter.MapTypeConverter" }   // sentinel = unset

        val onFail = annotation.arguments.firstOrNull { it.name?.asString() == "onFail" }
            ?.value?.toString()?.substringAfterLast('.') ?: "Auto"

        return ConverterDirective(converterFqn = useFqn, onFail = onFail)
    }
```

Wire it where `useConverter`/`mapDefaultValue` were read (constructor params merge with property annotations exactly like the old `extractUseConverter` pattern):

```kotlin
            val convertWith = extractConverterDirective(param, "ConvertWith")
                ?: property?.let { extractConverterDirective(it, "ConvertWith") }
            val convertToDirective = extractConverterDirective(param, "ConvertTo")
                ?: property?.let { extractConverterDirective(it, "ConvertTo") }
            val convertFromDirective = extractConverterDirective(param, "ConvertFrom")
                ?: property?.let { extractConverterDirective(it, "ConvertFrom") }
```

(and the same three lines for computed properties; pass them into both `FieldInfo(...)` constructions).

- [ ] **Step 3: Remaining extractors** — in `FieldAnalyzer`:
  - `extractIgnore` now matches `IgnoreMap` / `com.sahsenvar.kmapper.annotations.IgnoreMap`
    (drop the old `Ignore` names).
  - Delete `extractValidateFrom`/`extractValidateTo`; add `extractValidators(annotated)` reading
    `@Validate`'s vararg exactly like the old `extractValidatorFqns` (first argument is
    `List<KSType>`); wire into `FieldInfo.validators`.
  - Add `extractIgnoreDefaultValue(annotated): Boolean` (same presence-check pattern as
    `extractIgnore`, FQN `com.sahsenvar.kmapper.annotations.IgnoreDefaultValue`); wire into
    `FieldInfo.ignoreDefaultValue`.
  - Delete `extractMapDefaultValue` (annotation no longer exists).

- [ ] **Step 4:** Compile still red (TypeMatcher/codegen) — continue to Task 13.

### Task 13: `TypeMatcher` — pair registry, orientation, errors, preconditions, warning

**Files:** Modify `processor/.../analyzer/TypeMatcher.kt`, `processor/.../model/MappingStrategy.kt`, `processor/.../MappingProcessor.kt` (constructor wiring).

- [ ] **Step 1: `MappingStrategy.kt`** — replace `Convert` and extend `Collection`/`MapValues`:

```kotlin
    /** Converter call resolved orientation-aware. */
    data class Convert(
        val converterFqn: String,
        /** true → field source/target == converter S/T → convertTo; false → reverse → convertFrom. */
        val forward: Boolean,
    ) : MappingStrategy()
```

`Collection` gains element conversion: change `elementStrategy: MappingStrategy` semantics to allow `Convert` (no signature change needed — it already holds any `MappingStrategy`). Same for `MapValues.valueStrategy`.

- [ ] **Step 2: `TypeMatcher.kt`** — constructor gains `private val introspector: ConverterIntrospector? = null` and `isReverse` is now used for directive selection. Replace the per-field branch (step 1), the `@KMapperConfig` branch (step 6), the built-in branch (step 7), and the final error (step 8) with:

```kotlin
        // 1. Per-field directive override (use=…); policy-only directives do NOT short-circuit discovery
        val directive = sourceField.directiveFor(isReverse)
        if (directive?.converterFqn != null) {
            return resolveConverter(directive.converterFqn, sourceField, targetField)
        }
```

```kotlin
        // 6. @KMapperConfig (pair, either orientation)
        val customConverterFqn =
            customConverters[sourceField.type.fqn() to targetField.type.fqn()]
                ?: customConverters[targetField.type.fqn() to sourceField.type.fqn()]
        if (customConverterFqn != null) {
            return resolveConverter(customConverterFqn, sourceField, targetField)
        }

        // 7. Built-in registry (pair, either orientation)
        val builtInFqn = findBuiltInConverter(sourceField.type, targetField.type)
        if (builtInFqn != null) {
            return resolveConverter(builtInFqn, sourceField, targetField)
        }

        // 8. MissingConverter (compile error)
        logger.error("${sourceField.name}: " + missingConverterMessage(sourceField.type.fqn(), targetField.type.fqn()))
        return MappingStrategy.Unmappable
```

with imports `com.sahsenvar.kmapper.missingConverterMessage` / `unsupportedConversionMessage`, and:

```kotlin
    private fun resolveConverter(
        converterFqn: String,
        sourceField: FieldInfo,
        targetField: FieldInfo,
    ): MappingStrategy {
        val sourceFqn = sourceField.type.fqn()
        val targetFqn = targetField.type.fqn()
        val shape = introspector?.shapeOf(converterFqn)
        if (shape == null) {
            logger.error("${sourceField.name}: " + missingConverterMessage(sourceFqn, targetFqn))
            return MappingStrategy.Unmappable
        }
        if (shape.orNullAnnotated) {
            logger.error(
                "${sourceField.name}: @UnsupportedDirection must annotate the total method " +
                    "(convertTo/convertFrom), not an OrNull variant — converter $converterFqn",
            )
            return MappingStrategy.Unmappable
        }
        val forward = sourceFqn == shape.sourceFqn && targetFqn == shape.targetFqn
        val reverse = sourceFqn == shape.targetFqn && targetFqn == shape.sourceFqn
        if (!forward && !reverse) {
            logger.error(
                "${sourceField.name}: converter $converterFqn handles " +
                    "${shape.sourceFqn} <-> ${shape.targetFqn}, not $sourceFqn -> $targetFqn",
            )
            return MappingStrategy.Unmappable
        }
        val provided = if (forward) shape.providesTo else shape.providesFrom
        if (!provided) {
            val declaredReason = if (forward) shape.unsupportedToReason else shape.unsupportedFromReason
            val message = declaredReason ?: unsupportedConversionMessage(sourceFqn, targetFqn)
            logger.error("${sourceField.name}: $message")
            return MappingStrategy.Unmappable
        }
        return MappingStrategy.Convert(converterFqn, forward)
    }
```

- [ ] **Step 3: Pair-keyed `findBuiltInConverter`** — replace the direction-keyed `when` with the 30-entry registry (richer-first FQNs; prefix `com.sahsenvar.kmapper.converter.builtin.`):

```kotlin
    private val builtInPairs: List<Triple<String, String, String>> = run {
        val prefix = "com.sahsenvar.kmapper.converter.builtin."
        listOf(
            // numeric widening (12)
            Triple("kotlin.Short", "kotlin.Byte", prefix + "ShortByteConverter"),
            Triple("kotlin.Int", "kotlin.Byte", prefix + "IntByteConverter"),
            Triple("kotlin.Long", "kotlin.Byte", prefix + "LongByteConverter"),
            Triple("kotlin.Int", "kotlin.Short", prefix + "IntShortConverter"),
            Triple("kotlin.Long", "kotlin.Short", prefix + "LongShortConverter"),
            Triple("kotlin.Long", "kotlin.Int", prefix + "LongIntConverter"),
            Triple("kotlin.Float", "kotlin.Byte", prefix + "FloatByteConverter"),
            Triple("kotlin.Double", "kotlin.Byte", prefix + "DoubleByteConverter"),
            Triple("kotlin.Float", "kotlin.Short", prefix + "FloatShortConverter"),
            Triple("kotlin.Double", "kotlin.Short", prefix + "DoubleShortConverter"),
            Triple("kotlin.Double", "kotlin.Int", prefix + "DoubleIntConverter"),
            Triple("kotlin.Double", "kotlin.Float", prefix + "DoubleFloatConverter"),
            // String pairs (7)
            Triple("kotlin.Byte", "kotlin.String", prefix + "ByteStringConverter"),
            Triple("kotlin.Short", "kotlin.String", prefix + "ShortStringConverter"),
            Triple("kotlin.Int", "kotlin.String", prefix + "IntStringConverter"),
            Triple("kotlin.Long", "kotlin.String", prefix + "LongStringConverter"),
            Triple("kotlin.Float", "kotlin.String", prefix + "FloatStringConverter"),
            Triple("kotlin.Double", "kotlin.String", prefix + "DoubleStringConverter"),
            Triple("kotlin.Boolean", "kotlin.String", prefix + "BooleanStringConverter"),
            // X-pairs (9)
            Triple("kotlin.Float", "kotlin.Int", prefix + "FloatIntConverter"),
            Triple("kotlin.Float", "kotlin.Long", prefix + "FloatLongConverter"),
            Triple("kotlin.Double", "kotlin.Long", prefix + "DoubleLongConverter"),
            Triple("kotlin.Byte", "kotlin.Boolean", prefix + "ByteBooleanConverter"),
            Triple("kotlin.Short", "kotlin.Boolean", prefix + "ShortBooleanConverter"),
            Triple("kotlin.Int", "kotlin.Boolean", prefix + "IntBooleanConverter"),
            Triple("kotlin.Long", "kotlin.Boolean", prefix + "LongBooleanConverter"),
            Triple("kotlin.Float", "kotlin.Boolean", prefix + "FloatBooleanConverter"),
            Triple("kotlin.Double", "kotlin.Boolean", prefix + "DoubleBooleanConverter"),
            // Instant (2)
            Triple("kotlinx.datetime.Instant", "kotlin.String", prefix + "InstantStringConverter"),
            Triple("kotlinx.datetime.Instant", "kotlin.Long", prefix + "InstantLongConverter"),
        )
    }

    private fun findBuiltInConverter(source: KSType, target: KSType): String? {
        val sourceFqn = source.fqn()
        val targetFqn = target.fqn()
        return builtInPairs.firstOrNull { (first, second, _) ->
            (sourceFqn == first && targetFqn == second) || (sourceFqn == second && targetFqn == first)
        }?.third
    }
```

- [ ] **Step 4: Element conversion in collections/Map** — in the `Collection` branch, when element types differ and are NOT both data classes, resolve a converter for the element pair instead of falling back to `Direct`: build synthetic element `FieldInfo`s and recurse:

```kotlin
                val elementStrategy = when {
                    isSameType(sourceElementType, targetElementType) -> MappingStrategy.Direct
                    isDataClass(sourceElementType) && isDataClass(targetElementType) ->
                        MappingStrategy.Nested("to${targetElementType.declaration.simpleName.asString()}Result")
                    else -> determineMappingStrategy(
                        sourceField.copy(type = sourceElementType, isNullable = sourceElementType.isMarkedNullable),
                        targetField.copy(type = targetElementType, isNullable = targetElementType.isMarkedNullable),
                        isReverse,
                    )
                }
```

Apply the same replacement in the `MapValues` value-strategy block (and key conversion stays same-type in this plan — key converters parked; keep the existing key-mismatch error).

- [ ] **Step 5: Preconditions + dead-`?` warning** — at the top of `determineMappingStrategy`:

```kotlin
        val effectiveOnFail = sourceField.onFailFor(isReverse)
        val targetIsCollectionLike = isCollectionType(targetField.type) || isMapType(targetField.type)
        if (effectiveOnFail == "Skip" && !targetIsCollectionLike) {
            logger.error(
                "${sourceField.name}: OnFail.Skip applies to collection elements only; " +
                    "use OnFail.Throw or a nullable/defaulted target instead.",
            )
            return MappingStrategy.Unmappable
        }
        if (targetField.isNullable && !sourceField.isNullable) {
            logger.warn(
                "${sourceField.name}: target is nullable but mapping from a non-null source never produces null " +
                    "(dead '?'); consider dropping the '?' on the target.",
            )
        }
```

- [ ] **Step 6: Duplicate-pair normalization** — in `BuiltInConverterValidator`, normalize each
  `(sourceFqn, targetFqn)` before duplicate detection so orientation-flipped duplicates are
  caught too (`<A,B>` and `<B,A>` are the same pair):

```kotlin
private fun normalized(pair: Pair<String, String>): Pair<String, String> =
    if (pair.first <= pair.second) pair else pair.second to pair.first
```

Compare/group entries by `normalized(typePair)`; the error message lists both declared
orientations.

- [ ] **Step 7: Wire** — in `MappingProcessor.process`, build `TypeMatcher(logger, customConverters, collectionWrappers, ConverterIntrospector(resolver))`. `./gradlew processor:compileKotlin --offline -q` → expect codegen errors only (Task 14 area); fix signatures as needed to compile (codegen behavior comes next).
- [ ] **Step 8: Commit** (with Task 14) — `feat(processor): pair-keyed orientation-aware converter resolution, onFail directives, dead-? warning`.

---

## Phase 6 — Code generation

### Task 14: Scalar ladder + omit/copy + `Result` boundary

**Files:** Modify `processor/.../generator/FunctionNameGenerator.kt`, `processor/.../generator/MappingCodeGenerator.kt`, `processor/.../MappingProcessor.kt`. Test: `processor/src/test/kotlin/com/sahsenvar/kmapper/processor/ScalarLadderCodegenTest.kt` (BehaviorSpec, kctfork — reuse the module's existing `compile()` helper).

**Target generated shape (golden):**

```kotlin
public fun UserDataModel.toUserDomainModelResult(): Result<UserDomainModel> = runCatching {
  if (KMapper.hasListeners) KMapper.dispatch { onMapStart(this@toUserDomainModelResult, UserDomainModel::class) }
  val base = UserDomainModel(
    id = id.convertOrFail("id", "kotlin.String", "kotlin.Long") { LongStringConverter.convertFrom(it) },
    age = age.convertOrNull("age", "kotlin.String", "kotlin.Int") { IntStringConverter.convertFromOrNull(it) }
  )
  val result = base.copy(
    plan = plan.convertOrElse(base.plan, "plan", "kotlin.String", "kotlin.String") { it }
  )
  if (KMapper.hasListeners) KMapper.dispatch { onMapComplete(this@toUserDomainModelResult, result) }
  result
}
```

- [ ] **Step 1: Failing BehaviorSpec** — compile-tests asserting generated source content (use `err`/`ok` helpers as in the existing tests):

```kotlin
class ScalarLadderCodegenTest : BehaviorSpec({
    given("a DataModel with hard, nullable, and defaulted targets") {
        val source = SourceFile.kotlin("Models.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            data class UserDomainModel(val id: Long, val age: Int?, val plan: String = "FREE")
            @MapTo(UserDomainModel::class)
            data class UserDataModel(val id: String, val age: String?, val plan: String?)
        """.trimIndent())
        `when`("the processor runs") {
            val generated = okAndReadGenerated(source, "UserDataModelMappers.kt")
            then("the function returns Result and is named toXResult") {
                generated shouldContain "fun UserDataModel.toUserDomainModelResult(): Result<UserDomainModel>"
                generated shouldContain "runCatching"
            }
            then("hard cell uses convertOrFail with path/type literals") {
                generated shouldContain "convertOrFail(\"id\""
                generated shouldContain "LongStringConverter.convertFrom"
            }
            then("nullable target uses convertOrNull with the OrNull converter method") {
                generated shouldContain "convertOrNull(\"age\""
                generated shouldContain "IntStringConverter.convertFromOrNull"
            }
            then("defaulted target is omitted from the constructor and set via copy + convertOrElse") {
                generated shouldContain "val base = UserDomainModel("
                generated shouldContain "base.copy("
                generated shouldContain "convertOrElse(base.plan"
            }
        }
    }
    given("OnFail.Throw on a nullable target") {
        val source = SourceFile.kotlin("Strict.kt", """
            import com.sahsenvar.kmapper.annotations.*
            data class StrictDomainModel(val age: Int?)
            @MapTo(StrictDomainModel::class)
            data class StrictDataModel(@ConvertWith(onFail = OnFail.Throw) val age: String?)
        """.trimIndent())
        `when`("the processor runs") {
            val generated = okAndReadGenerated(source, "StrictDataModelMappers.kt")
            then("the strict seam is emitted") { generated shouldContain "convertOrNullStrict(\"age\"" }
        }
    }
    given("@IgnoreDefaultValue on a defaulted target field") {
        val source = SourceFile.kotlin("NoFallback.kt", """
            import com.sahsenvar.kmapper.annotations.*
            data class PlanDomainModel(@IgnoreDefaultValue val plan: String = "FREE")
            @MapTo(PlanDomainModel::class)
            data class PlanDataModel(val plan: String?)
        """.trimIndent())
        `when`("the processor runs") {
            val generated = okAndReadGenerated(source, "PlanDataModelMappers.kt")
            then("the field is built in the constructor stage with the hard seam (no copy)") {
                generated shouldContain "convertOrFail(\"plan\""
                (generated.contains("base.copy(")) shouldBe false
            }
        }
    }
    given("@Validate on source and target fields") {
        val source = SourceFile.kotlin("Validated.kt", """
            import com.sahsenvar.kmapper.annotations.*
            import com.sahsenvar.kmapper.validation.Validator
            object RawFormat : Validator<String> { override fun validate(value: String) =
                if (value.startsWith("v")) null else "must start with v" }
            object Positive : Validator<Int> { override fun validate(value: Int) =
                if (value > 0) null else "must be positive" }
            data class ScoreDomainModel(@Validate(Positive::class) val score: Int)
            @MapTo(ScoreDomainModel::class)
            data class ScoreDataModel(@Validate(RawFormat::class) val score: String)
        """.trimIndent())
        `when`("the processor runs") {
            val generated = okAndReadGenerated(source, "ScoreDataModelMappers.kt")
            then("source validator fires before, target validator after") {
                generated shouldContain "RawFormat.validate"
                generated shouldContain "Positive.validate"
            }
        }
    }
    given("OnFail.Skip on a scalar field") {
        val source = SourceFile.kotlin("BadSkip.kt", """
            import com.sahsenvar.kmapper.annotations.*
            data class SkipDomainModel(val age: Int?)
            @MapTo(SkipDomainModel::class)
            data class SkipDataModel(@ConvertWith(onFail = OnFail.Skip) val age: String?)
        """.trimIndent())
        then("compilation fails with the precondition message") {
            errMessages(source) shouldContain "OnFail.Skip applies to collection elements only"
        }
    }
})
```

- [ ] **Step 2: Implement.**
  - `FunctionNameGenerator.generateMapperFunctionName` → `"to${targetClass.simpleName.asString()}Result"`.
  - `MappingProcessor.generateMappingFunction` / `generateReverseMappingFunction`: return type `Result<Target>` (`ClassName("kotlin", "Result").parameterizedBy(targetClassName)`); body wrapped in `runCatching { ... }`; split `fieldsToEmit` into `constructorEntries` (no `usesDefaultInMapping`) and `copyEntries` (`usesDefaultInMapping && sourceField != null`); emit `val base = Target(constructorEntries...)`; if `copyEntries.isEmpty()` then `val result = base` else `val result = base.copy(copyEntries...)`; keep both listener dispatches; end with `result` (expression value of `runCatching`), drop `return`. **Every default-flag consultation switches from `hasDefault` to `usesDefaultInMapping`** (`@IgnoreDefaultValue` masking) — including the `externalFields` filters and the omit-unmapped-defaulted logic; emit a `logger.warn` when `ignoreDefaultValue && !hasDefault` (no-op annotation).
  - **Validation emission** (`wrapWithValidation`): source-side validators now come from `sourceField.validators` (fire on the source value BEFORE the mapping expr) and result-side validators from `targetField.validators` (fire on `__result` AFTER) — the field-anchored `@Validate` semantics. The old `sourceField.validateFrom`/`sourceField.validateTo` reads are gone.
  - `MappingCodeGenerator`: delete `applyNullableHandling` and the `defaultValue` logic; `generateFieldMapping` gains `onFail: String` and `inCopyStage: Boolean` parameters and dispatches per the **seam selection table** (spec §Conversion seams):
    - `Convert(forward)` → method = `forward ? "convertTo" : "convertFrom"`; OrNull-capable landing site (nullable target or `inCopyStage`) uses the `OrNull` method name.
    - target non-null, no default → `%N.convertOrFail(%S, %S, %S) { %T.%N(it) }` (works for both source nullabilities via the two receiver overloads).
    - target nullable → `convertOrNull` (Auto) / `convertOrNullStrict` (Throw).
    - `inCopyStage` → `convertOrElse(base.%N, …)` (Auto) / `convertOrElseStrict(base.%N, …)` (Throw).
    - `Direct` → `%N` as today, except nullable→non-null-no-default emits `%N.orRequired(%S)` and copy-stage emits `%N ?: base.%N`.
    - `Nested(fn)` → same seams with `convert = { it.%N().getOrThrow() }` and from/to = class simple names.
    - `EnumFromWire` → add the field-name path argument to `UnknownEnumValue(%S, %S, w.toString())`.
  - Path literals: `targetField.name` (single segment; nesting prefixes at runtime via `withPathPrefix`). Type literals: `sourceField.type.fqn()` / `targetField.type.fqn()`.
- [ ] **Step 3: Run → green:** `./gradlew processor:test --tests "*ScalarLadderCodegenTest*" --offline -q`.
- [ ] **Step 4: Commit** — `feat(processor)!: Result boundary, ladder seam selection, omit/copy defaults`.

### Task 15: Nested + behavior (runtime) compile-tests

**Files:** Test `processor/src/test/kotlin/com/sahsenvar/kmapper/processor/NestedLadderCodegenTest.kt`.

- [ ] **Step 1: Failing BehaviorSpec** — nested generation + runtime behavior via kctfork's loaded classes (compile, load the generated mapper, invoke reflectively as the existing repro tests do):

```kotlin
class NestedLadderCodegenTest : BehaviorSpec({
    given("a nested data-class field") {
        val source = SourceFile.kotlin("Nested.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            data class AddressDomainModel(val zipCode: Int)
            data class OrderDomainModel(val address: AddressDomainModel, val note: String = "-")
            @MapTo(AddressDomainModel::class) data class AddressDataModel(val zipCode: String)
            @MapTo(OrderDomainModel::class)
            data class OrderDataModel(val address: AddressDataModel, val note: String?)
        """.trimIndent())
        `when`("the processor runs") {
            val generated = okAndReadGenerated(source, "OrderDataModelMappers.kt")
            then("the sub-mapper rides the seams with getOrThrow") {
                generated shouldContain "toAddressDomainModelResult().getOrThrow()"
                generated shouldContain "convertOrFail(\"address\""
            }
        }
        `when`("the mapping runs with a broken deep field") {
            then("the failure path is prefixed: address.zipCode") {
                // load generated classes; build OrderDataModel(AddressDataModel("abc"), null);
                // invoke toOrderDomainModelResult(); assert Result.isFailure and
                // (exceptionOrNull() as MappingException).path == "address.zipCode"
            }
        }
    }
})
```

Implement the runtime half with the same class-loading pattern as `F2ListElementConverterRepro.kt` (the executor copies that file's loading utility — it exists in this module's tests).

- [ ] **Step 2:** Fix codegen until green. **Commit** — `feat(processor): nested mapping through seams with path accumulation`.

### Task 16: Collections codegen

**Files:** Modify `MappingCodeGenerator.generateCollectionMapping` / `generateMapValuesMapping`; Test `processor/src/test/kotlin/com/sahsenvar/kmapper/processor/CollectionLadderCodegenTest.kt`.

**Seam selection for elements** (element conversion = `Convert`/`Nested`; Direct same-type elements keep today's passthrough):

| target element | OnFail.Auto | OnFail.Skip | OnFail.Throw |
|---|---|---|---|
| `T` (List) | `convertEachOrSkip` | `convertEachOrSkip` | `convertEachOrFail` |
| `T?` (List) | `convertEachOrNull` | `convertEachOrSkip` | `convertEachOrNullStrict` |
| Set | `convertEachOrSkipToSet` | same | `convertEachOrFailToSet` |
| Map value `VT` | `convertEntriesOrSkip` | same | `convertEntriesOrFail` |
| Map value `VT?` | `convertEntriesValueOrNull` | `convertEntriesOrSkip` | `convertEntriesOrFail` |

Container handling: source container nullable → emit `%N?.convertEach…` then container ladder (`?: throw RequiredFieldMissing(path)` is **replaced by** `.orRequired(path)` on the chain for non-null no-default targets; copy-stage `?: base.%N`; nullable target stays as-is). The element `convert` lambda is `{ Converter.convertTo(it) }` / `{ it.toXResult().getOrThrow() }`.

- [ ] **Step 1: Failing BehaviorSpec** — cover: `List<String> → List<Long>` (built-in element converter via `convertEachOrSkip`), `List<String?> → List<Long>` (free filterNotNull), `List<String> → List<Long?>` (null-in-place), `@ConvertWith(onFail = OnFail.Throw)` on a list (`convertEachOrFail`), `Set` target (`convertEachOrSkipToSet`), `Map<String, String> → Map<String, Long>` (`convertEntriesOrSkip`), nullable source container with `= emptyList()` default (copy-stage `?: base.tags`), and a runtime case: 1 broken element of 3 → result list size 2 + a `DroppedBrokenElement` with path `tags[1]` (recording listener registered through the loaded classes). Assert generated text per the table above (e.g. `generated shouldContain "convertEachOrSkip(\"tags\""`).
- [ ] **Step 2: Wrapper golden cases** (same spec file) —
  1. **Composition:** `List<String> → PersistentList<Long>` via a test-fixture wrapper:
     generated contains `PersistentListWrapper.wrap(` AND `convertEachOrSkip("ids"` (element
     conversion on normal rails INSIDE the wrap call).
  2. **Unwrap direction:** fixture `class Box<T>(val values: List<T>)` +
     `@CollectionWrapper(forType = Box::class) object BoxWrapper { fun <T> wrap(source: List<T>): Box<T> = Box(source); fun <T> unwrap(source: Box<T>): List<T> = source.values }`;
     mapping `Box<ItemDataModel> → List<ItemDomainModel>` → generated contains
     `BoxWrapper.unwrap(` feeding the element seam.
  3. **Missing direction:** a wrapper declaring only `wrap`, used in a mapping that needs
     `unwrap` → compile error containing `"BoxWrapper"` and `"unwrap"`.
  4. **Bad signature:** a `@CollectionWrapper` object whose `wrap` returns a type other than
     `forType` → compile error mentioning the expected signature.
- [ ] **Step 3: Implement.**
  - `generateCollectionMapping`: when `elementStrategy` is `Convert`/`Nested`, emit the seam from the table with `(path, fromFqn, toFqn)` literals.
  - `WrappedCollection`: forward = `Wrapper.wrap(<element seam chain>)`; add the reverse path —
    when the SOURCE type is a registered `forType` and the target is a plain collection, emit
    `Wrapper.unwrap(source)` as the iterable feeding the element seam. Extend
    `MappingStrategy.WrappedCollection` with `val useUnwrap: Boolean = false` and TypeMatcher's
    wrapper branch to detect source-side wrapped types.
  - **Wrapper signature validation** (new `analyzer/CollectionWrapperValidator.kt`, called from
    `discoverWrappersFromConfig`): the object must declare `wrap(List<T>): ForType<T>` and/or
    `unwrap(ForType<T>): List<T>` with exactly one type parameter — else `logger.error` with the
    expected shapes; record which directions exist; a mapping needing a missing direction →
    `logger.error("<wrapper> declares no <direction> for <ForType>; add fun <T> <direction>(...)")`.
- [ ] **Step 4: Run → green.** **Commit** — `feat(processor): element-ladder collection codegen (List/Set/Map, onFail table, bidirectional wrappers)`.

### Task 17: Resolution-suite BehaviorSpec (discovery, orientation, both errors, reasons)

**Files:** Create `processor/src/test/kotlin/com/sahsenvar/kmapper/processor/ConverterResolutionTest.kt` (replace any older equivalent assertions).

- [ ] **Step 1: Write all cases** (ok/err helpers; each asserts generated text or error message):
  1. `Int → Long` (no annotation) → generated contains `LongIntConverter.convertFrom` (auto-discovery + orientation: Int→Long is the converter's FROM direction).
  2. `Long → Int` → compile error containing `"Long -> Int narrows"` (the `@UnsupportedDirection(TO)` reason, not the generic text).
  3. `Int → Float` → compile error containing `"lossy above 2^24"` (X-pair FROM reason).
  4. `Boolean → Int` → compile error containing `"no canonical"` (X-pair reason; NOT MissingConverter — the pair object exists).
  5. `String → SomeUserClass` with nothing registered → compile error containing `"has no registered converter"`.
  6. `@ConvertWith(use = SurnameConverter::class)` on a same-type `String → String` field → generated contains `SurnameConverter.convertTo` (override beats Direct).
  7. `@KMapperConfig(converters = [OccConverter::class])` → auto-discovered without a per-field annotation.
  8. `@ConvertFrom(onFail = OnFail.Throw)` asymmetry: forward (`@MapTo`) generation emits the Auto seam, reverse (`@MapFrom`) generation emits the strict seam.
  9. Dead-`?` warning: non-null source → nullable target produces a `warn` containing `"dead '?'"` and still compiles OK.
- [ ] **Step 2: Run → green** (iterate resolution/codegen). **Commit** — `test(processor): converter resolution suite (discovery, orientation, reasons, directives)`.

---

## Phase 7 — Migration & full green

### Task 18: Add-on converter modules

**Files:** every `*Converters.kt` under `converters-arrow`, `converters-bignumber`, `converters-datetime`, `converters-immutable`, `converters-okio`, `converters-uri`, `converters-uuid` (all source sets). `@CollectionWrapper` wrapper objects do NOT extend `MapTypeConverter` — leave them.

- [ ] **Step 1: Mechanical rename** in each converter object: `override fun convertToNonNull(value: X): Y` → `override fun convertTo(source: X): Y` (rename the parameter too) and `convertFromNonNull` → `convertFrom(target: …)`. Update their tests identically.
- [ ] **Step 2: Verify** — `rg -n "convert(To|From)NonNull" converters-* core processor --glob '!**/build/**' -m 5` → no matches.
- [ ] **Step 3:** `EmptyCollection` call sites in `converters-arrow` use the Task 3 signature.
- [ ] **Step 4: Compile all** — `./gradlew converters-arrow:compileKotlinJvm converters-bignumber:compileKotlinJvm converters-datetime:compileKotlinJvm converters-immutable:compileKotlinJvm converters-okio:compileKotlinJvm converters-uri:compileKotlinJvm converters-uuid:compileKotlinJvm --offline -q` → PASS. **Commit** — `refactor(converters)!: adopt convertTo/convertFrom`.

### Task 19: Existing tests, sample, integration-test

- [ ] **Step 1: Find stale call sites** — `rg -ln "convertToNonNull|convertFromNonNull|UseMapTypeConverter|MapDefaultValue|ValidateFrom|ValidateTo|@Ignore\b|\.to[A-Z][A-Za-z]*\(\)" processor/src/test integration-test sample --glob '!**/build/**'`.
- [ ] **Step 2:** Update: `@UseMapTypeConverter(X::class)` → `@ConvertWith(use = X::class)`; `@Ignore` → `@IgnoreMap`; `@ValidateFrom(X)` on a source field → `@Validate(X)` on that source field, `@ValidateTo(Y)` (old: declared on the source field, fired on the result) → `@Validate(Y)` **moved to the TARGET field** (semantic relocation — field-anchored); update `ValidateCodegenTest`/`ValidateRuntimeTest` accordingly; generated-call assertions to the new seam/method names; caller sites `dto.toX()` → `dto.toXResult().getOrThrow()`; tests that relied on auto `Long→Int` now expect the compile error with the narrowing reason; tests using `@MapDefaultValue` move the default into the target constructor.
- [ ] **Step 3: Full suite** — `./gradlew core:jvmTest processor:test --offline -q > /tmp/full.log 2>&1; echo exit=$?; grep -E 'FAILED|BUILD' /tmp/full.log | head` → green; then `./gradlew build -x lint --offline -q` (capture-then-slice) → green.
- [ ] **Step 4:** Record in the ledger: gate result, any deviations. **Commit** — `test: migrate suite to Result boundary and redesigned annotations`.

---

## Deferred (NOT in this plan — follow-up plan)

- **Arrow accumulated boundary** (`toXAccumulated(): IorNel<MappingError, X>`, `MappingError` value model, accumulating collection seams, generation trigger) — separate plan once core+processor land.
- Migration typealias `@Deprecated UseMapTypeConverter`; GitBook docs links; `OnAbsent`; strict-collision; summary sink event; Uuid/Duration stdlib add-ons; datetime module-boundary cleanup (ledger §J).

## Self-review notes

- **Spec coverage:** ladder rows 1–8 (Task 9 matrix tests + Task 14 codegen), sanctioned null (Tasks 5/9), report rule (Tasks 9/10 silent-vs-reported assertions), sink (Task 4), path/no-wrap (Tasks 3/9/15), compile errors + reasons (Tasks 13/17), preconditions + dead-`?` (Tasks 13/14/17), omit/copy + gate (Tasks 1/14), collections tables (Tasks 10/16), built-ins 30 objects (Tasks 7/8), parity (seams public, used by codegen), module trio (Task 2.5), function-level `@UnsupportedDirection` (Tasks 5/7/11/17), field-anchored `@Validate` + Ignore family (Tasks 6/12/14/19), bidirectional wrappers + signature validation (Task 16), duplicate-pair normalization (Task 13), migration (Tasks 18/19). Arrow boundary explicitly deferred.
- **Type consistency:** `ConverterShape`/`shapeOf` (T11) used in T13; `ConverterDirective`/`directiveFor`/`onFailFor` (T12) used in T13/T14; seam names of T9/T10 are the ones codegen emits in T14/T16 and tests assert in T14–T17; `MappingStrategy.Convert(converterFqn, forward)` consistent across T13/T14.
- **Known risks:** kctfork two-stage API details (Task 1) — mirror the existing helper; KotlinPoet `Result<T>` return + `runCatching` body shape (Task 14) — golden tests pin the output; KSP enum-argument reading (T12) uses string-suffix parsing to stay KSP1/KSP2-agnostic.
