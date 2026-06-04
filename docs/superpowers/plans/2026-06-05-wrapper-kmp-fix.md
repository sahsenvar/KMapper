# Collection-Wrapper KMP Fix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Checkbox (`- [ ]`) steps.

**Goal:** Make `@CollectionWrapper` add-ons (immutable, arrow) work for **KMP/iOS consumers**, not just JVM. Replace the broken cross-module *auto-discovery* (KSP2 isolates a consumer's `kspCommonMainMetadata` invocation → `getDeclarationsFromPackage`/`getSymbolsWithAnnotation` return 0 for dependency descriptors) with **explicit consumer-side listing in `@KMapperConfig`**, which resolves in the consumer's own KSP run (proven to work for scalar converters cross-platform).

**Design (approved):**
- A wrapper becomes an annotated **`object`** (not a top-level fun) with a generic `wrap`:
  ```kotlin
  @CollectionWrapper(forType = PersistentList::class)
  object PersistentListWrapper { fun <T> wrap(items: List<T>): PersistentList<T> = items.toPersistentList() }
  ```
- `@KMapperConfig` gains `wrappers: Array<KClass<*>> = []`; the consumer lists the wrapper objects it needs:
  ```kotlin
  @KMapperConfig(converters = [...], wrappers = [PersistentListWrapper::class, NonEmptyListWrapper::class])
  object AppMapperConfig
  ```
- Processor (consumer's KSP run): `getSymbolsWithAnnotation(@KMapperConfig)` is **in-module** (works on KMP) → read `wrappers` (List<KSType>) → for each, resolve its `@CollectionWrapper.forType` (reading an annotation on a resolved dependency class works cross-module) → `Map<forTypeFqn → wrapperObjectFqn>`. When a target collection field FQN matches, emit `WrapperObject.wrap(source.map { it.toX() })` (nullable: `source?.map { … }?.let { WrapperObject.wrap(it) }`).
- **Remove** the old machinery: `@CollectionWrapperDescriptor`, descriptor generation, `getDeclarationsFromPackage`, `inMemoryWrappers`, the static descriptor source files in the add-ons, and `kspJvm`-for-discovery hacks.

**Why it works on KMP/iOS:** no cross-module *symbol enumeration*; only (a) the consumer's own `@KMapperConfig` (in-module) and (b) standard dependency *type+annotation resolution* — both fine in KSP2's per-module common-metadata invocation.

**Repo:** `/Users/sahansenvar/StudioProjects/kmap`, root `0.2.0-SNAPSHOT`, HTTPS origin via gh helper.

---

## Phases

- **W1** — `core`: `@CollectionWrapper` `@Target` → `CLASS` (annotate objects); add `wrappers: Array<KClass<*>> = []` to `@KMapperConfig`; delete `@CollectionWrapperDescriptor`.
- **W2** — `processor`: read `@KMapperConfig.wrappers` + resolve `forType` from each wrapper object's annotation; emit `Wrapper.wrap(...)`; delete descriptor-gen + `getDeclarationsFromPackage` + `inMemoryWrappers` path in `CollectionWrapperSupport`; update `CollectionWrapperTest` to the object+`@KMapperConfig` form + add a runtime-exec test.
- **W3** — add-ons: convert wrapper funs → annotated objects (`converters-immutable`: PersistentList/ImmutableList/PersistentSet/ImmutableSet wrappers; `converters-arrow`: NonEmptyListWrapper, empty→`EmptyCollection`); remove static descriptor files; fix their tests to call `Wrapper.wrap(...)`.
- **W4** — consumers: `:sample` + `:integration-test` add `@KMapperConfig(wrappers=[...])`; **restore `:integration-test` android+iOS targets + `kspCommonMainMetadata`**; verify iOS end-to-end now works.
- **W5** — docs (immutable/arrow/multi-module pages: wrappers are listed in `@KMapperConfig`, not auto-discovered) + full verification incl. `iosSimulatorArm64Test` (esp. `:integration-test`).

Sequential (shared repo, avoid concurrent commits). W4 is the payoff: the previously-blocked `:integration-test` iOS end-to-end must pass.

---

## W1 — core

**Files:** `core/.../annotations/CollectionWrapper.kt`, `KMapperConfig.kt`; delete `CollectionWrapperDescriptor.kt`.

- [ ] **Step 1:** `CollectionWrapper.kt` → `@Target(AnnotationTarget.CLASS)` (keep `@Retention(BINARY)`, `forType: KClass<*>`).
- [ ] **Step 2:** `KMapperConfig.kt`: add `val wrappers: Array<KClass<*>> = []` (keep `converters`).
- [ ] **Step 3:** Delete `core/.../annotations/CollectionWrapperDescriptor.kt`.
- [ ] **Step 4:** `./gradlew :core:compileKotlinJvm -q` → may break the processor next (that's W2). Just confirm core compiles. Commit (`feat(core): @CollectionWrapper targets a class; @KMapperConfig.wrappers; drop CollectionWrapperDescriptor`, trailer).

## W2 — processor

**Files:** `processor/.../analyzer/CollectionWrapperSupport.kt` (rewrite), `MappingProcessor.kt`, `analyzer/TypeMatcher.kt`, `generator/MappingCodeGenerator.kt`; tests `CollectionWrapperTest.kt`.

- [ ] **Step 1:** Rewrite `CollectionWrapperSupport`: a function that, given the resolver, reads all `@KMapperConfig` (`getSymbolsWithAnnotation`, in-module) → their `wrappers` arg (`List<KSType>`) → for each wrapper `KSClassDeclaration`, read its `@CollectionWrapper.forType` FQN + capture the wrapper object's FQN → return `Map<forTypeFqn, wrapperObjectFqn>`. Remove descriptor generation, `getDeclarationsFromPackage`, `inMemoryWrappers`, Source-2b.
- [ ] **Step 2:** `TypeMatcher`: when target field is a collection whose element-mapped form needs wrapping and its FQN is in the wrapper map → `WrappedCollection(elementStrategy, wrapperObjectFqn)` (store the wrapper OBJECT fqn now, not a wrap-fn fqn). Conflict (same forType listed twice) → `logger.error`.
- [ ] **Step 3:** `MappingCodeGenerator`: emit `%T.wrap(%L)` where `%T` = `ClassName.bestGuess(wrapperObjectFqn)` and `%L` = `source.map { it.toX() }` (non-null) / wrap a nullable as `source?.map { … }?.let·{ %T.wrap(it) }`.
- [ ] **Step 4:** Update `CollectionWrapperTest`: define an inline `@CollectionWrapper(forType=PersistentList::class) object W { fun <T> wrap(i: List<T>) = i.toPersistentList() }` + a `@KMapperConfig(wrappers=[W::class]) object Cfg` + a `@MapTo` model with a `PersistentList` target → assert generated contains `W.wrap(`. Add a **runtime-exec** test (classload + invoke) asserting the wrapped value is correct. Keep the "two wrappers same forType → COMPILATION_ERROR" test (now via two `@KMapperConfig` wrapper entries).
- [ ] **Step 5:** `./gradlew :processor:test -q` → all green. Commit, push.

## W3 — add-ons (functions → annotated objects)

- [ ] **Step 1 — `converters-immutable`:** rewrite `ImmutableConverters.kt` from extension funs to annotated objects:
```kotlin
@CollectionWrapper(forType = PersistentList::class)
object PersistentListWrapper { fun <T> wrap(items: List<T>): PersistentList<T> = items.toPersistentList() }
@CollectionWrapper(forType = ImmutableList::class)
object ImmutableListWrapper { fun <T> wrap(items: List<T>): ImmutableList<T> = items.toImmutableList() }
@CollectionWrapper(forType = PersistentSet::class)
object PersistentSetWrapper  { fun <T> wrap(items: List<T>): PersistentSet<T> = items.toPersistentSet() }
@CollectionWrapper(forType = ImmutableSet::class)
object ImmutableSetWrapper   { fun <T> wrap(items: List<T>): ImmutableSet<T> = items.toImmutableSet() }
```
Delete any static `…/generated/*Descriptor*.kt` files added in P4. Update `ImmutableConvertersTest.kt` to call `PersistentListWrapper.wrap(listOf(...))` etc.
- [ ] **Step 2 — `converters-arrow`:** rewrite to:
```kotlin
@CollectionWrapper(forType = NonEmptyList::class)
object NonEmptyListWrapper {
    fun <T> wrap(items: List<T>): NonEmptyList<T> =
        items.toNonEmptyListOrNull() ?: throw MappingException.EmptyCollection("NonEmptyList source was empty")
}
```
Delete its static descriptor file. Update `NonEmptyListWrapperTest.kt` to `NonEmptyListWrapper.wrap(...)`.
- [ ] **Step 3:** `./gradlew :converters-immutable:build :converters-arrow:build :converters-immutable:iosSimulatorArm64Test :converters-arrow:iosSimulatorArm64Test -q` → green. Commit, push.

## W4 — consumers (explicit `@KMapperConfig(wrappers=…)`) + restore integration-test iOS

- [ ] **Step 1 — `:sample`:** add (sample maps to `PersistentSet` + `NonEmptyList`):
```kotlin
import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.immutable.PersistentSetWrapper
import com.sahsenvar.kmapper.arrow.NonEmptyListWrapper
@KMapperConfig(wrappers = [PersistentSetWrapper::class, NonEmptyListWrapper::class]) object SampleConfig
```
Build `:sample` (JVM) → generated `CartRMappers.kt`/`TeamRMappers.kt` now emit `PersistentSetWrapper.wrap(...)` / `NonEmptyListWrapper.wrap(...)`. Verify.
- [ ] **Step 2 — `:integration-test`:** revert the JVM-only green-fix — restore the `android {}` block + `iosArm64()`/`iosSimulatorArm64()` + the `android.kotlin.multiplatform.library` plugin + `add("kspCommonMainMetadata", project(":processor"))` + the `dependsOn("kspCommonMainKotlinMetadata")` wiring (mirror `converters-arrow`'s module shape). In `Models.kt` `ItestMapperConfig`, add `wrappers = [PersistentListWrapper::class, NonEmptyListWrapper::class]` (keep `converters = [StringLocalDateConverter::class]`).
- [ ] **Step 3 — the payoff:** `./gradlew :integration-test:jvmTest -q` → 5/5 PASS, then `./gradlew :integration-test:iosSimulatorArm64Test -q` → **PASS** (the previously-BLOCKED iOS end-to-end now works: wrappers + scalar + enum + null-safety on Native). If iOS still fails to find a wrapper, the `@KMapperConfig.wrappers` resolution in the consumer's common KSP run is the bug — fix it in W2 (do NOT revert to JVM-only). Commit, push.

## W5 — docs + full verification

- [ ] **Step 1:** Update docs (TR `docs/guide/tip-donusumu/{immutable,arrow}.md` + `ileri/cok-modullu.md`; EN mirrors): wrappers are now **listed in `@KMapperConfig(wrappers=[...])`** (not auto-discovered by adding the dependency). Rewrite the multi-module page's cross-module-discovery section to the explicit-listing model. Update README if it claims auto-discovery.
- [ ] **Step 2:** `./gradlew build -q` → 0; `./gradlew iosSimulatorArm64Test -q` → 0 (now includes `:integration-test` Native end-to-end). Commit, push.

---

## Self-Review

**Coverage:** auto-discovery removed (W1 desc-annotation delete, W2 support rewrite, W3 static-file delete) ✓; explicit listing added (`@KMapperConfig.wrappers` W1, processor read W2, consumers W4) ✓; emit `Wrapper.wrap(...)` W2/W3 ✓; KMP/iOS proven by W4 Step 3 (`:integration-test:iosSimulatorArm64Test`) ✓; add-ons converted W3 ✓; docs W5 ✓; main stays green each phase ✓.

**Placeholder scan:** wrapper object code given in full; W2 steps name the exact resolver calls + emit shape; no "implement later". The W4-Step-3 contingency points back to W2 (concrete), not hand-waving.

**Type consistency:** wrapper objects all expose `fun <T> wrap(items: List<T>): <Coll><T>`; processor stores/emits the **wrapper object FQN** (not a function FQN) + the fixed `wrap` method; `@KMapperConfig(converters=[…], wrappers=[…])`; `@CollectionWrapper(forType=…)` now `@Target(CLASS)`. Consistent across W1–W4.

---

## Execution Handoff
Subagent-driven, sequential. W1+W2 (core+processor) first, then W3 (add-ons), W4 (consumers + iOS payoff), W5 (docs + full verify).

