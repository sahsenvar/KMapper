# kmap Add-on Converters R1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Ship four converter add-on modules (`converters-immutable` [renamed], `converters-arrow`, `converters-datetime`, `converters-bignumber`) under `io.github.sahsenvar`, requiring no core processor mechanism change beyond re-adding `MappingException.EmptyCollection`.

**Architecture:** Two add-on shapes. (1) **Collection-wrapper** add-ons (`immutable`, `arrow`) ship `@CollectionWrapper`-annotated functions auto-discovered cross-module by the processor (no `@KMapperConfig` listing). (2) **Scalar-converter** add-ons (`datetime`, `bignumber`) ship `MapTypeConverter` objects the consumer lists in `@KMapperConfig`; they apply KSP only to themselves where needed (collection-wrapper modules) or not at all (scalar modules).

**Tech Stack:** Kotlin 2.3.10 KMP, KSP2, vanniktech maven-publish 0.33.0, kctfork (compile-tests), `kotlinx-collections-immutable`, `arrow-core`, `kotlinx-datetime` + `java.time`, `com.ionspin.kotlin:bignum` + `java.math`.

**Spec:** `docs/superpowers/specs/2026-06-04-addon-converters-r1-design.md`

**Repo:** `/Users/sahansenvar/StudioProjects/kmap` (HTTPS origin via gh credential helper — do not switch to ssh). Root version is `0.2.0-SNAPSHOT`.

---

## Phase Decomposition

- **Phase 0** — Core `EmptyCollection` re-add + module rename `converters-compose` → `converters-immutable`. *Checkpoint:* build + tests green, `:sample` builds.
- **Phase 1** — `converters-immutable`: add `asPersistentSet` wrapper; prove cross-kind via `:sample`.
- **Phase 2** — `converters-arrow`: `asNonEmptyList` wrapper (empty → `EmptyCollection`).
- **Phase 3** — `converters-datetime`: kotlinx (commonMain) + java.time (jvm/androidMain) scalar converters + bridges.
- **Phase 4** — `converters-bignumber`: ionspin (commonMain) + java.math (jvm/androidMain) scalar converters.
- **Phase 5** — Publishing config for new modules + docs/README updates + `publishToMavenLocal` verification.

Implement in order. Phases 2–4 are independent of each other (could parallelize) but each depends on Phase 0.

---

## Phase 0 — Core `EmptyCollection` + Rename

### Task 0.1: Re-add `MappingException.EmptyCollection`

**Files:** Modify `core/src/commonMain/kotlin/com/sahsenvar/kmapper/MappingException.kt`

- [ ] **Step 1: Add the subtype** (inside the sealed class, after `UnknownEnumValue`)

```kotlin
    class EmptyCollection(val detail: String)
        : MappingException("Collection cannot be empty: $detail")
```

- [ ] **Step 2: Build core**

Run: `./gradlew :core:compileKotlinJvm -q` → BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/src/commonMain/kotlin/com/sahsenvar/kmapper/MappingException.kt
git commit -m "feat(core): re-add MappingException.EmptyCollection (for arrow NonEmptyList)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 0.2: Rename `converters-compose` → `converters-immutable`

**Files:** rename dir + package; modify `settings.gradle.kts`, the module `build.gradle.kts`, `sample/*`, docs.

- [ ] **Step 1: Move the module dir and the package dir**

```bash
cd /Users/sahansenvar/StudioProjects/kmap
git mv converters-compose converters-immutable
git mv converters-immutable/src/commonMain/kotlin/com/sahsenvar/kmapper/compose \
       converters-immutable/src/commonMain/kotlin/com/sahsenvar/kmapper/immutable
```

- [ ] **Step 2: Rewrite package declarations + namespace + coordinates**

In `converters-immutable/src/commonMain/kotlin/com/sahsenvar/kmapper/immutable/ImmutableConverters.kt`, change `package com.sahsenvar.kmapper.compose` → `package com.sahsenvar.kmapper.immutable`.
In `converters-immutable/build.gradle.kts`: `namespace = "com.sahsenvar.kmapper.compose"` → `"com.sahsenvar.kmapper.immutable"`; in the `mavenPublishing { coordinates(...) }` block change artifactId `kmapper-converters-compose` → `kmapper-converters-immutable` and the POM `name`/`description` accordingly.
Verify: `grep -rn "kmapper.compose\|converters-compose\|kmapper-converters-compose" converters-immutable` → no matches.

- [ ] **Step 3: Update `settings.gradle.kts`**

Change `include(... ":converters-compose" ...)` → `":converters-immutable"`.

- [ ] **Step 4: Update `:sample`**

In `sample/build.gradle.kts`: `implementation(project(":converters-compose"))` → `implementation(project(":converters-immutable"))`.
In `sample/src/main/kotlin/Sample.kt`: any `import com.sahsenvar.kmapper.compose.*` → `com.sahsenvar.kmapper.immutable.*` (the wrapper fns are discovered, but if imported explicitly, fix).

- [ ] **Step 5: Update docs references**

- `README.md`: artifact table `kmapper-converters-compose` → `kmapper-converters-immutable`.
- `docs/guide/baslarken/kurulum.md` + `docs/guide-en/getting-started/installation.md`: `io.github.sahsenvar:kmapper-converters-compose` → `...kmapper-converters-immutable`.
- `docs/guide/tip-donusumu/immutable.md` + `docs/guide-en/type-conversion/immutable.md` + `docs/guide/ileri/cok-modullu.md` + `docs/guide-en/advanced/multi-module.md`: any `com.sahsenvar.kmapper.compose.asPersistentList` → `com.sahsenvar.kmapper.immutable.asPersistentList`; `converters-compose` → `converters-immutable`.
Verify repo-wide (excluding build/.git and the orphan note): `grep -rn "converters-compose\|kmapper\.compose" --include='*.kt' --include='*.kts' --include='*.md' . | grep -v build/` → no matches.

- [ ] **Step 6: Build + sample + commit**

Run: `./gradlew :converters-immutable:build :sample:build --console=plain -q > /tmp/p0.log 2>&1; echo exit=$?` → 0. (On failure: `grep -nE '^e: |error:|FAILED' /tmp/p0.log`.)
```bash
git add -A
git commit -m "refactor: rename converters-compose -> converters-immutable

Old kmapper-converters-compose:0.1.0 stays orphaned on Maven Central (pre-1.0).

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

---

## Phase 1 — `converters-immutable`: add `asPersistentSet`

**Files:** modify `converters-immutable/src/commonMain/kotlin/com/sahsenvar/kmapper/immutable/ImmutableConverters.kt`; modify `sample/src/main/kotlin/Sample.kt`.

- [ ] **Step 1: Add the wrapper**

```kotlin
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.toPersistentSet

@CollectionWrapper(forType = PersistentSet::class)
fun <T> List<T>.asPersistentSet(): PersistentSet<T> = toPersistentSet()
```
(Confirm the existing file already has `asPersistentList`, `asImmutableList`, `asImmutableSet`; add `asPersistentSet` alongside.)

- [ ] **Step 2: Extend `:sample` to exercise List→PersistentSet (failing until rebuilt)**

In `sample/src/main/kotlin/Sample.kt`, add:
```kotlin
import kotlinx.collections.immutable.PersistentSet
data class CartD(val items: PersistentSet<TagD>)
@MapTo(CartD::class) data class CartR(val items: List<TagR>)
```

- [ ] **Step 3: Build sample + verify generated code**

Run: `./gradlew :converters-immutable:publishToMavenLocal :sample:build --console=plain -q > /tmp/p1.log 2>&1; echo exit=$?` → 0.
Then: `find sample/build -name 'CartRMappers.kt' -exec grep -l 'asPersistentSet' {} +` → file listed (generated mapper calls `.map { it.toTagD() }.asPersistentSet()`).

- [ ] **Step 4: Commit**

```bash
git add converters-immutable/src sample/src
git commit -m "feat(converters-immutable): add asPersistentSet wrapper (List/Set -> PersistentSet)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

## Phase 2 — `converters-arrow` (NonEmptyList)

### Task 2.1: catalog + module

- [ ] **Step 1: Add `arrow-core` to the catalog**

Resolve the latest stable `io.arrow-kt:arrow-core` from Maven Central (Arrow 2.x). Add to `gradle/libs.versions.toml`: `arrow = "<resolved>"` and `arrow-core = { module = "io.arrow-kt:arrow-core", version.ref = "arrow" }`.

- [ ] **Step 2: Create `converters-arrow/build.gradle.kts`** (mirror `converters-immutable`'s plugin/android/ksp/vanniktech pattern)

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.vanniktech.publish)
}
kotlin {
    android { namespace = "com.sahsenvar.kmapper.arrow"; compileSdk = 36; minSdk = 30 }
    jvm(); iosArm64(); iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies { api(project(":core")); implementation(libs.arrow.core) }
    }
}
dependencies {
    add("kspCommonMainMetadata", project(":processor"))
    add("kspJvm", project(":processor")) // descriptors into the jvm jar for cross-module discovery
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") dependsOn("kspCommonMainKotlinMetadata")
}
mavenPublishing {
    publishToMavenCentral(); signAllPublications()
    coordinates("io.github.sahsenvar", "kmapper-converters-arrow", version.toString())
    pom { /* same shape as other modules; name "kmap converters-arrow" */ }
}
```
Add `":converters-arrow"` to `settings.gradle.kts` `include(...)`.

### Task 2.2: the wrapper + tests

**Files:** create `converters-arrow/src/commonMain/kotlin/com/sahsenvar/kmapper/arrow/NonEmptyListConverters.kt`; extend `sample`; create `converters-arrow/src/jvmTest/kotlin/.../NonEmptyListWrapperTest.kt`.

- [ ] **Step 1: Write the wrapper**

```kotlin
package com.sahsenvar.kmapper.arrow
import arrow.core.NonEmptyList
import arrow.core.toNonEmptyListOrNull
import com.sahsenvar.kmapper.MappingException
import com.sahsenvar.kmapper.annotations.CollectionWrapper

@CollectionWrapper(forType = NonEmptyList::class)
fun <T> List<T>.asNonEmptyList(): NonEmptyList<T> =
    toNonEmptyListOrNull() ?: throw MappingException.EmptyCollection("NonEmptyList source was empty")
```

- [ ] **Step 2: Runtime test (empty → EmptyCollection; non-empty → Nel)**

```kotlin
// converters-arrow/src/jvmTest/kotlin/com/sahsenvar/kmapper/arrow/NonEmptyListWrapperTest.kt
package com.sahsenvar.kmapper.arrow
import com.sahsenvar.kmapper.MappingException
import kotlin.test.*

class NonEmptyListWrapperTest {
    @Test fun `non-empty list wraps`() { assertEquals(listOf(1,2), listOf(1,2).asNonEmptyList().toList()) }
    @Test fun `empty list throws EmptyCollection`() {
        assertFailsWith<MappingException.EmptyCollection> { emptyList<Int>().asNonEmptyList() }
    }
}
```
Add `jvmTest.dependencies { implementation(kotlin("test")) }` to the module's sourceSets if not present; ensure `tasks.withType<Test> { useJUnitPlatform() }` or rely on kotlin.test default.

- [ ] **Step 3: Extend `:sample` for the cross-module generated-code check**

`sample/src/main/kotlin/Sample.kt` (add): `import arrow.core.NonEmptyList`, and `sample/build.gradle.kts` add `implementation(project(":converters-arrow"))` + `implementation(libs.arrow.core)`:
```kotlin
data class TeamD(val members: NonEmptyList<TagD>)
@MapTo(TeamD::class) data class TeamR(val members: List<TagR>)
```

- [ ] **Step 4: Build + verify + commit**

Run: `./gradlew :converters-arrow:build :converters-arrow:publishToMavenLocal :sample:build --console=plain -q > /tmp/p2.log 2>&1; echo exit=$?` → 0.
Verify: `find sample/build -name 'TeamRMappers.kt' -exec grep -l 'asNonEmptyList' {} +` → listed.
Run jvmTest: `./gradlew :converters-arrow:jvmTest -q` → PASS.
```bash
git add gradle/libs.versions.toml settings.gradle.kts converters-arrow sample/build.gradle.kts sample/src
git commit -m "feat(converters-arrow): NonEmptyList wrapper (empty source -> EmptyCollection)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

---

## Phase 3 — `converters-datetime` (platform-split scalar converters)

**Naming convention:** `MapTypeConverter<S, T>` with the primitive/`String` side as `S` (matching core's `StringIntConverter`). So `String↔X` ⇒ `object StringXConverter : MapTypeConverter<String, X>`. java.time variants are `Java`-prefixed in their type name to disambiguate (`StringJavaLocalDateConverter`). These modules apply **NO KSP** (scalar converters, not `@CollectionWrapper`).

### Task 3.1: module with shared jvm+android source set

- [ ] **Step 1: Create `converters-datetime/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.publish)
}
kotlin {
    androidLibrary { namespace = "com.sahsenvar.kmapper.datetime"; compileSdk = 36; minSdk = 30 } // use the same `android {}`/`androidLibrary {}` form the other modules use
    jvm(); iosArm64(); iosSimulatorArm64()
    applyDefaultHierarchyTemplate()
    sourceSets {
        commonMain.dependencies { api(project(":core")); implementation(libs.kotlinx.datetime) }
        // shared source set so java.time converters are written ONCE for jvm + android:
        val jvmAndroidMain by creating { dependsOn(commonMain.get()) }
        jvmMain.get().dependsOn(jvmAndroidMain)
        androidMain.get().dependsOn(jvmAndroidMain)
        jvmTest.dependencies { implementation(kotlin("test")) }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
mavenPublishing {
    publishToMavenCentral(); signAllPublications()
    coordinates("io.github.sahsenvar", "kmapper-converters-datetime", version.toString())
    pom { /* name "kmap converters-datetime"; same shape */ }
}
```
> If `val jvmAndroidMain by creating { … }` with the AGP-KMP plugin's android source set names mismatches, fall back to placing the java.time converters identically in both `jvmMain` and `androidMain` (duplication) — but prefer the shared set. Use whatever `android {}` block form the existing `core`/`converters-immutable` modules use (mirror them exactly). Add `":converters-datetime"` to `settings.gradle.kts`.

### Task 3.2: kotlinx-datetime converters (commonMain) + tests

**File:** `converters-datetime/src/commonMain/kotlin/com/sahsenvar/kmapper/datetime/KotlinxDateTimeConverters.kt`

- [ ] **Step 1: Write the converters** (representative + table)

```kotlin
package com.sahsenvar.kmapper.datetime
import com.sahsenvar.kmapper.converter.MapTypeConverter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

object StringLocalDateConverter : MapTypeConverter<String, LocalDate>(String::class, LocalDate::class) {
    override fun convertToNonNull(value: String): LocalDate = LocalDate.parse(value)
    override fun convertFromNonNull(value: LocalDate): String = value.toString()
}
// Same pattern for the rest (S=String):
//   StringLocalDateTimeConverter : <String, LocalDateTime>  — LocalDateTime.parse / toString
//   StringLocalTimeConverter     : <String, LocalTime>      — LocalTime.parse / toString
```
(Do NOT add Instant converters — `StringInstantConverter`/`LongInstantConverter` already exist in core.)

- [ ] **Step 2: commonTest round-trips**

```kotlin
// converters-datetime/src/commonTest/kotlin/.../KotlinxDateTimeConvertersTest.kt
package com.sahsenvar.kmapper.datetime
import kotlinx.datetime.LocalDate
import kotlin.test.*
class KotlinxDateTimeConvertersTest {
    @Test fun localDate() {
        val s = "2026-06-04"
        assertEquals(LocalDate(2026,6,4), StringLocalDateConverter.convertToNonNull(s))
        assertEquals(s, StringLocalDateConverter.convertFromNonNull(LocalDate(2026,6,4)))
    }
    // analogous tests for LocalDateTime ("2026-06-04T10:15:30"), LocalTime ("10:15:30")
}
```
Run: `./gradlew :converters-datetime:jvmTest -q` → PASS. Commit.

### Task 3.3: java.time converters + bridges (jvmAndroidMain) + tests

**File:** `converters-datetime/src/jvmAndroidMain/kotlin/com/sahsenvar/kmapper/datetime/JavaTimeConverters.kt`

- [ ] **Step 1: Write the converters** (representative + table)

```kotlin
package com.sahsenvar.kmapper.datetime
import com.sahsenvar.kmapper.converter.MapTypeConverter
import java.time.Instant as JInstant
import java.time.LocalDate as JLocalDate

object StringJavaInstantConverter : MapTypeConverter<String, JInstant>(String::class, JInstant::class) {
    override fun convertToNonNull(value: String): JInstant = JInstant.parse(value)
    override fun convertFromNonNull(value: JInstant): String = value.toString()
}
object LongJavaInstantConverter : MapTypeConverter<Long, JInstant>(Long::class, JInstant::class) {
    override fun convertToNonNull(value: Long): JInstant = JInstant.ofEpochMilli(value)
    override fun convertFromNonNull(value: JInstant): Long = value.toEpochMilli()
}
object StringJavaLocalDateConverter : MapTypeConverter<String, JLocalDate>(String::class, JLocalDate::class) {
    override fun convertToNonNull(value: String): JLocalDate = JLocalDate.parse(value)
    override fun convertFromNonNull(value: JLocalDate): String = value.toString()
}
// Same pattern (S=String, parse/toString) for:
//   StringJavaLocalDateTimeConverter : <String, java.time.LocalDateTime>
//   StringJavaLocalTimeConverter     : <String, java.time.LocalTime>
//   StringJavaZonedDateTimeConverter : <String, java.time.ZonedDateTime>
//   StringJavaOffsetDateTimeConverter: <String, java.time.OffsetDateTime>
```

- [ ] **Step 2: Bridge converters** (kotlinx ↔ java.time, JVM extensions from kotlinx-datetime)

```kotlin
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.Instant as KInstant
object KotlinJavaInstantConverter : MapTypeConverter<KInstant, JInstant>(KInstant::class, JInstant::class) {
    override fun convertToNonNull(value: KInstant): JInstant = value.toJavaInstant()
    override fun convertFromNonNull(value: JInstant): KInstant = value.toKotlinInstant()
}
// Analogous: KotlinJavaLocalDateConverter using toJavaLocalDate()/toKotlinLocalDate()
```

- [ ] **Step 3: jvmTest round-trips** for the java.time + bridge converters (mirror 3.2's structure). Run `./gradlew :converters-datetime:jvmTest -q` → PASS.

- [ ] **Step 4: Build + publish + commit**

Run: `./gradlew :converters-datetime:build :converters-datetime:publishToMavenLocal -q > /tmp/p3.log 2>&1; echo exit=$?` → 0.
```bash
git add gradle/libs.versions.toml settings.gradle.kts converters-datetime
git commit -m "feat(converters-datetime): kotlinx (common) + java.time (jvm/android) scalar converters + bridges

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

## Phase 4 — `converters-bignumber` (ionspin + java.math)

Same module shape as `converters-datetime` (commonMain + shared `jvmAndroidMain`, NO KSP, vanniktech coordinates `kmapper-converters-bignumber`, namespace `com.sahsenvar.kmapper.bignumber`). String-first `S` naming; java.math variants `Java`-prefixed.

### Task 4.1: catalog + module

- [ ] **Step 1:** Resolve latest stable `com.ionspin.kotlin:bignum` from Maven Central. Add to catalog: `ionspin-bignum = "<resolved>"`, `ionspin-bignum = { module = "com.ionspin.kotlin:bignum", version.ref = "ionspin-bignum" }`.
- [ ] **Step 2:** Create `converters-bignumber/build.gradle.kts` mirroring `converters-datetime`'s (commonMain dep `implementation(libs.ionspin.bignum)`; shared `jvmAndroidMain`; vanniktech coordinates `kmapper-converters-bignumber`). Add `":converters-bignumber"` to `settings.gradle.kts`.

### Task 4.2: ionspin converters (commonMain) + tests

**File:** `converters-bignumber/src/commonMain/kotlin/com/sahsenvar/kmapper/bignumber/IonspinConverters.kt`

- [ ] **Step 1: Converters** (verify exact ionspin API: `BigDecimal.parseString`, `BigInteger.parseString`, `BigDecimal.fromDouble`, `BigInteger.fromLong`/`fromInt`, `toPlainString()`/`toString()`)

```kotlin
package com.sahsenvar.kmapper.bignumber
import com.sahsenvar.kmapper.converter.MapTypeConverter
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger

object StringBigDecimalConverter : MapTypeConverter<String, BigDecimal>(String::class, BigDecimal::class) {
    override fun convertToNonNull(value: String): BigDecimal = BigDecimal.parseString(value)
    override fun convertFromNonNull(value: BigDecimal): String = value.toPlainString()
}
// Same pattern (verify API) for:
//   StringBigIntegerConverter : <String, BigInteger>   — BigInteger.parseString / toString
//   DoubleBigDecimalConverter : <Double, BigDecimal>   — BigDecimal.fromDouble / doubleValue()
//   LongBigIntegerConverter   : <Long, BigInteger>     — BigInteger.fromLong / longValue()
//   IntBigIntegerConverter    : <Int, BigInteger>      — BigInteger.fromInt / intValue()
//   BigIntegerBigDecimalConverter : <BigInteger, BigDecimal> — BigDecimal.fromBigInteger / toBigInteger()
```

- [ ] **Step 2:** commonTest round-trips (parse + format, one per converter). Run `./gradlew :converters-bignumber:jvmTest -q` → PASS. Commit.

### Task 4.3: java.math converters (jvmAndroidMain) + tests

**File:** `converters-bignumber/src/jvmAndroidMain/kotlin/com/sahsenvar/kmapper/bignumber/JavaMathConverters.kt`

- [ ] **Step 1: Converters**

```kotlin
package com.sahsenvar.kmapper.bignumber
import com.sahsenvar.kmapper.converter.MapTypeConverter
import java.math.BigDecimal as JBigDecimal
import java.math.BigInteger as JBigInteger

object StringJavaBigDecimalConverter : MapTypeConverter<String, JBigDecimal>(String::class, JBigDecimal::class) {
    override fun convertToNonNull(value: String): JBigDecimal = JBigDecimal(value)
    override fun convertFromNonNull(value: JBigDecimal): String = value.toPlainString()
}
// Same pattern for:
//   StringJavaBigIntegerConverter : <String, java.math.BigInteger>  — JBigInteger(value) / toString
//   DoubleJavaBigDecimalConverter : <Double, java.math.BigDecimal>  — JBigDecimal.valueOf(v) / toDouble()
//   LongJavaBigIntegerConverter   : <Long, java.math.BigInteger>    — JBigInteger.valueOf(v) / toLong()
//   JavaBigIntegerBigDecimalConverter : <java.math.BigInteger, java.math.BigDecimal> — JBigDecimal(v) / toBigInteger()
```

- [ ] **Step 2:** jvmTest round-trips. Run `./gradlew :converters-bignumber:jvmTest -q` → PASS.
- [ ] **Step 3: Build + publish + commit**

Run: `./gradlew :converters-bignumber:build :converters-bignumber:publishToMavenLocal -q > /tmp/p4.log 2>&1; echo exit=$?` → 0.
```bash
git add gradle/libs.versions.toml settings.gradle.kts converters-bignumber
git commit -m "feat(converters-bignumber): ionspin (common) + java.math (jvm/android) scalar converters

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

---

## Phase 5 — Docs, README, full verification

### Task 5.1: README + guide pages

- [ ] **Step 1: README artifact table** — add the four add-on rows (`kmapper-converters-immutable`, `-arrow`, `-datetime`, `-bignumber`) with platform + purpose; remove any lingering `-compose`.

- [ ] **Step 2: Guide pages (TR + EN), mirroring existing page style** (Turkish prose / English in guide-en; "siz" voice; code blocks; `Sonraki adım`/`Next` footers; relative links; add to both `SUMMARY.md`s under Type Conversion / Tip Dönüşümü):
  - Update existing `immutable.md` (TR `tip-donusumu/immutable.md`, EN `type-conversion/immutable.md`): note it now includes `asPersistentSet` and that cross-kind (`List→PersistentSet` etc.) works; coordinates `kmapper-converters-immutable`.
  - New `arrow.md`: `converters-arrow`, `NonEmptyList` via `@CollectionWrapper` (auto-discovered), empty source → `MappingException.EmptyCollection`. (Option noted as roadmap.)
  - New `datetime.md`: scalar converters listed via `@KMapperConfig`; kotlinx (all platforms) vs java.time (JVM/Android only) split; the converter inventory; bridges.
  - New `bignumber.md`: ionspin (all platforms) vs java.math (JVM/Android only); inventory; how to pick by your model's type.
  - Each scalar-add-on page must show the `@KMapperConfig(converters = [...])` listing usage (these are NOT auto-discovered in R1).

> This doc task may be delegated to a dedicated doc-writing subagent (as in the original GitBook build); keep accuracy to the real converter names/signatures from Phases 1–4.

### Task 5.2: Full verification + commit

- [ ] **Step 1: Whole-project build + tests**

Run: `./gradlew build --console=plain -q > /tmp/p5.log 2>&1; echo exit=$?` → 0. (Includes all module tests; `grep -nE '^e: |error:|FAILED|> Task .* FAILED' /tmp/p5.log` on failure.)

- [ ] **Step 2: Publish all locally + verify add-on artifacts**

Run: `./gradlew publishToMavenLocal -q && ls ~/.m2/repository/io/github/sahsenvar/`
Expected dirs include `kmapper-converters-immutable`, `kmapper-converters-arrow`, `kmapper-converters-datetime`, `kmapper-converters-bignumber` (+ their `-jvm`/`-android`/`-ios*` variants where KMP).

- [ ] **Step 3: Commit docs + push**

```bash
git add README.md docs/guide docs/guide-en
git commit -m "docs: cover R1 add-on converters (immutable, arrow, datetime, bignumber)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

> **Release (user-driven, later):** bump root `0.2.0-SNAPSHOT` → `0.2.0`, then per-module `publishToMavenCentral` (or root) + Publish in the Portal (signing/token already configured in `~/.gradle/gradle.properties`). `kmapper-converters-compose` is not re-published; it remains at `0.1.0`.

---

## Self-Review

**Spec coverage:** immutable rename + `asPersistentSet` (P0.2, P1) ✓; cross-kind works via wrappers (P1, noted) ✓; arrow `NonEmptyList` + `EmptyCollection` revival (P0.1, P2) ✓; datetime kotlinx-common + java.time-jvm/android + bridges (P3) ✓; bignumber ionspin-common + java.math-jvm/android (P4) ✓; platform-split via shared `jvmAndroidMain` (P3.1, P4) ✓; scalar consumed via `@KMapperConfig`, wrappers auto-discovered (architecture note, P5 docs) ✓; publishing coordinates (each module's vanniktech block) ✓; deferred Map/Option/cross-kind-stdlib/scalar-auto-discovery (out of R1) ✓.

**Placeholder scan:** External versions (`arrow`, `ionspin-bignum`) are "resolve latest + add to catalog" steps (same pattern as kctfork in the main plan), not vague TODOs. Converter families use a full representative + an explicit type-pair/method table (not "similar to above") — each entry names its exact `<S, T>` and parse/format calls. The shared-source-set step has a concrete fallback. No "implement later".

**Type consistency:** Naming convention fixed (`String`/primitive = `S`; `String<X>Converter`; java variants `Java`-prefixed). `MapTypeConverter<S,T>` directions consistent (`convertToNonNull(S):T`). `@CollectionWrapper(forType = …::class)` consistent with Phase-3.5 mechanism. `MappingException.EmptyCollection(detail)` defined in P0.1, used in P2 — consistent.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-04-addon-converters-r1.md`. Two execution options:
1. **Subagent-Driven (recommended)** — fresh subagent per phase/task, review between.
2. **Inline Execution** — execute in this session with checkpoints.


