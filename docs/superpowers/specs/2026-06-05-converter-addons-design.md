# Converter Add-ons R2 — Design Spec
> uuid / okio / uri scalar modules + Arrow `Option<T>` processor rule

**Date:** 2026-06-05
**Status:** Approved for implementation
**Version:** `0.2.0-SNAPSHOT` (no version bump or publish in this work item)

---

## 1. Scope

Four additions to the KMapper library:

| # | Work Item | Module(s) Touched | Kind |
|---|-----------|-------------------|------|
| 1 | `converters-uuid` | new module | scalar add-on |
| 2 | `converters-okio` | new module | scalar add-on |
| 3 | `converters-uri` | new module | platform-split scalar add-on |
| 4 | Arrow `Option<T>` support | `:processor` + `:converters-arrow` doc | processor rule |

---

## 2. Module Structure Template (scalar add-on)

Every new scalar add-on clones `converters-datetime` exactly:

```
converters-{name}/
  build.gradle.kts            ← plugins: kmp, agp-kmp-library, vanniktech-publish (NO ksp)
  src/
    commonMain/kotlin/com/sahsenvar/kmapper/{name}/
      {Name}Converters.kt     ← converter objects
    jvmAndroidMain/kotlin/…/  ← (only if JVM/Android platform types are exposed)
      Java{Name}Converters.kt
    commonTest/kotlin/…/
      {Name}ConverterTest.kt
    jvmTest/kotlin/…/
      Java{Name}ConverterTest.kt  ← (only if jvmAndroidMain exists)
```

**build.gradle.kts shape** (scalar modules have NO `:processor` KSP dependency):

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.publish)
}
kotlin {
    android { namespace = "…"; compileSdk = 36; minSdk = 30 }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies { api(project(":core")); /* optional runtime dep */ }
        val jvmAndroidMain by creating { dependsOn(commonMain.get()) }
        jvmMain.get().dependsOn(jvmAndroidMain)
        androidMain.get().dependsOn(jvmAndroidMain)
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotest.assertions)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotest.assertions)
        }
    }
}
mavenPublishing {
    publishToMavenCentral(); signAllPublications()
    coordinates("io.github.sahsenvar", "kmapper-converters-{name}", version.toString())
    pom {
        name.set("KMapper converters-{name}")
        description.set("…")
        inceptionYear.set("2026")
        url.set("https://github.com/sahsenvar/KMapper")
        licenses { license { name.set("The Apache License, Version 2.0"); url.set("https://www.apache.org/licenses/LICENSE-2.0.txt") } }
        developers { developer { id.set("sahsenvar"); name.set("Şahan Şenvar"); url.set("https://github.com/sahsenvar") } }
        scm { url.set("https://github.com/sahsenvar/KMapper") }
    }
}
```

---

## 3. `converters-uuid`

### 3.1 Rationale

`kotlin.uuid.Uuid` (stdlib since 2.1) and `java.util.UUID` (JDK) are ubiquitous in domain models.
String→UUID and cross-platform UUID bridge converters are high-frequency boilerplate.

### 3.2 Converters

**commonMain** (`com.sahsenvar.kmapper.uuid`):

| Object | S | T | `convertToNonNull` | `convertFromNonNull` |
|--------|---|---|--------------------|----------------------|
| `StringUuidConverter` | `String` | `kotlin.uuid.Uuid` | `Uuid.parse(value)` | `value.toString()` |

**jvmAndroidMain** (mirrors datetime's `JavaTimeConverters`):

| Object | S | T | `convertToNonNull` | `convertFromNonNull` |
|--------|---|---|--------------------|----------------------|
| `JavaStringUuidConverter` | `String` | `java.util.UUID` | `UUID.fromString(value)` | `value.toString()` |
| `KotlinJavaUuidConverter` | `kotlin.uuid.Uuid` | `java.util.UUID` | `value.toJavaUuid()` | `value.toKotlinUuid()` |

### 3.3 Opt-in Note

`kotlin.uuid.Uuid` was experimental in Kotlin 2.0; in Kotlin 2.1+ it is stable.
Since this project uses Kotlin 2.3.10, **no `@OptIn` is required** at compilation time.
However, the implementation plan instructs verifying this at build time: if the compiler emits
`ExperimentalUuidApi` warnings or errors, add `@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)`
to the source files. Do not add the opt-in pre-emptively.

### 3.4 Catalog Changes

None. `kotlin.uuid.Uuid` is part of the Kotlin stdlib already in the catalog.
`java.util.UUID` is a JDK type — no dependency.

### 3.5 Tests

**commonTest** (`StringUuidConverterTest`):
- Round-trip with fixed string `"550e8400-e29b-41d4-a716-446655440000"`.
- `convertToNonNull` on invalid string → throws (any exception — behavior of `Uuid.parse`).

**jvmTest** (`JavaUuidConverterTest`):
- `JavaStringUuidConverter` round-trip with same fixed string.
- `KotlinJavaUuidConverter` round-trip: create `Uuid.parse(s)`, convert to `java.util.UUID`, convert back, assert equal.
- `KotlinJavaUuidConverter` cross-check: converted `java.util.UUID` `.toString()` equals original string.

---

## 4. `converters-okio`

### 4.1 Rationale

Okio is a KMP-native I/O library. `ByteString` and `Path` appear in data-layer models; converters
eliminate per-project boilerplate.

### 4.2 Catalog Addition

```toml
[versions]
okio = "3.9.1"

[libraries]
okio = { module = "com.squareup.okio:okio", version.ref = "okio" }
```

Okio 3.9.1 is a known-stable KMP release. It ships okio targets for JVM, Android, iosArm64,
iosSimulatorArm64 — matching this project's target set exactly.

### 4.3 Converters

**commonMain** (`com.sahsenvar.kmapper.okio`):

| Object | S | T | `convertToNonNull` | `convertFromNonNull` | Round-trip note |
|--------|---|---|--------------------|----------------------|-----------------|
| `StringByteStringConverter` | `String` | `okio.ByteString` | `value.encodeUtf8()` | `value.utf8()` | exact |
| `ByteArrayByteStringConverter` | `ByteArray` | `okio.ByteString` | `value.toByteString()` | `value.toByteArray()` | compare with `contentEquals` |
| `StringPathConverter` | `String` | `okio.Path` | `value.toPath()` | `value.toString()` | exact on normalized strings |

`toPath()` import: `import okio.Path.Companion.toPath`.

### 4.4 Dependencies

```kotlin
commonMain.dependencies {
    api(project(":core"))
    implementation(libs.okio)
}
```

No `jvmAndroidMain` needed — all three converters work on all platforms.

### 4.5 Tests

**commonTest** (`OkioConverterTest`):
- `StringByteStringConverter`: round-trip `"hello"`, empty string.
- `ByteArrayByteStringConverter`: round-trip with `byteArrayOf(1,2,3,4)` — use `assertTrue(result.contentEquals(original))` because `ByteArray` equality is by reference.
- `StringPathConverter`: round-trip `"/tmp/test"` (unix-style; valid on all platforms in okio).

---

## 5. `converters-uri`

### 5.1 Rationale

URI/URL conversion is platform-specific: there is no KMP-common URI type. Each platform has its own:
`java.net.URI` (JVM), `android.net.Uri` (Android), `platform.Foundation.NSURL` (iOS/macOS).

### 5.2 Module Structure (platform-split, no commonMain converters)

```
converters-uri/
  src/
    commonMain/            ← empty (doc comment only — no converters)
    jvmMain/               ← java.net.URI
    androidMain/           ← android.net.Uri
    iosMain/               ← NSURL (shared source set for iosArm64 + iosSimulatorArm64)
    jvmTest/               ← jvmMain tests
    iosTest/               ← NSURL tests (iosSimulatorArm64 target)
```

**build.gradle.kts**: Unlike the scalar template, this module requires an explicit `iosMain` shared
source set for the two iOS targets:

```kotlin
val iosMain by creating { dependsOn(commonMain.get()) }
iosArm64Main.get().dependsOn(iosMain)
iosSimulatorArm64Main.get().dependsOn(iosMain)
val iosTest by creating { dependsOn(commonTest.get()) }
iosArm64Test.get().dependsOn(iosTest)
iosSimulatorArm64Test.get().dependsOn(iosTest)
```

### 5.3 Converters

**jvmMain** (`com.sahsenvar.kmapper.uri`):

| Object | S | T | `convertToNonNull` | `convertFromNonNull` |
|--------|---|---|--------------------|----------------------|
| `JavaStringUriConverter` | `String` | `java.net.URI` | `URI.create(value)` | `value.toString()` |

**androidMain** (`com.sahsenvar.kmapper.uri`):

| Object | S | T | `convertToNonNull` | `convertFromNonNull` |
|--------|---|---|--------------------|----------------------|
| `AndroidStringUriConverter` | `String` | `android.net.Uri` | `Uri.parse(value)` | `value.toString()` |

**iosMain** (`com.sahsenvar.kmapper.uri`):

| Object | S | T | `convertToNonNull` | `convertFromNonNull` |
|--------|---|---|--------------------|----------------------|
| `NsUrlStringConverter` | `String` | `platform.Foundation.NSURL` | `NSURL.URLWithString(value) ?: throw MappingException.TypeConversionFailed(…)` | `value.absoluteString ?: value.path ?: ""` |

`NSURL.URLWithString()` returns nullable (`NSURL?`) for malformed input; the converter throws
`MappingException.TypeConversionFailed("String", "NSURL", cause)` on null.

### 5.4 Round-trip Caveat for NSURL

`NSURL` normalizes URLs (e.g. trailing slash canonicalization). Round-trip tests must use
already-normalized URLs. Recommended test URL: `"https://example.com/"` (trailing slash present).
Do not use `"https://example.com"` (without slash) — NSURL may normalize it differently.

### 5.5 Catalog Changes

None. All types are platform SDKs.

### 5.6 Tests

**jvmTest** (`JavaUriConverterTest`): round-trip `"https://example.com/"` and `"ftp://files.example.org/pub"`.
**iosTest** (`NsUrlConverterTest`, runs on iosSimulatorArm64): round-trip `"https://example.com/"`.
- Android test: not included in this plan (Android instrumented tests require an emulator; the
  jvm path covers the identical `toString()` symmetry).

---

## 6. Arrow `Option<T>` — Processor Rule

### 6.1 Problem

`arrow.core.Option<T>` is generic — `MapTypeConverter<S, T>` requires concrete `KClass` instances,
so a `MapTypeConverter<T?, Option<T>>` cannot be written for all `T`. The `@CollectionWrapper`
mechanism is also inapplicable (it wraps `List<T>`, not a nullable `T?`).

The only clean path is a **dedicated processor rule** that matches target fields by FQN string
`"arrow.core.Option"` and emits `Option.fromNullable(…)` / `getOrNull()` directly in generated
code — keeping `:core` and `:processor` free of any Arrow Gradle dependency.

### 6.2 Arrow-free Constraint

The `:processor` module **must not** add `arrow-core` as a dependency. The processor detects
`Option<T>` by comparing the target field's qualified name string against `"arrow.core.Option"`.
No `KClass`, no import, no reflection against arrow types. The generated code contains the FQN
`arrow.core.Option.fromNullable(…)` as a string literal rendered into a `CodeBlock` via KotlinPoet.
Arrow ends up on the consumer classpath via `:converters-arrow` which declares
`implementation(libs.arrow.core)`.

### 6.3 New `MappingStrategy` Variants

```kotlin
// In processor/src/commonMain/kotlin/…/model/MappingStrategy.kt

/** Source field is `Inner` or `Inner?`; target is `Option<Inner>`. */
data class OptionWrap(
    /** Non-null if the inner type itself requires a mapper call (Nested case). */
    val innerMapperFn: String? = null
) : MappingStrategy()

/** Source field is `Option<Inner>`; target is `Inner?` (or `Inner` — handled by null-guard). */
data class OptionUnwrap(
    val innerMapperFn: String? = null
) : MappingStrategy()
```

### 6.4 TypeMatcher — Detection Rule

In `TypeMatcher.determineMappingStrategy`, **after** collection checks and **before** custom
converters (step 3c, or wherever the ordering fits cleanly — between step 3b and step 4):

```kotlin
// 3c. Check Option<T> wrap/unwrap
val targetFqn = targetField.type.declaration.qualifiedName?.asString()
val sourceFqn = sourceField.type.declaration.qualifiedName?.asString()

if (targetFqn == "arrow.core.Option") {
    val innerType = targetField.type.arguments.firstOrNull()?.type?.resolve()
    // determine if inner needs a nested mapper call
    val innerMapperFn = if (innerType != null && isDataClass(innerType)) {
        "to${innerType.declaration.simpleName.asString()}"
    } else null
    return MappingStrategy.OptionWrap(innerMapperFn)
}
if (sourceFqn == "arrow.core.Option") {
    val innerType = sourceField.type.arguments.firstOrNull()?.type?.resolve()
    val innerMapperFn = if (innerType != null && isDataClass(innerType)) {
        "to${innerType.declaration.simpleName.asString()}"
    } else null
    return MappingStrategy.OptionUnwrap(innerMapperFn)
}
```

### 6.5 MappingCodeGenerator — Codegen

New private method `generateOptionWrapMapping` and `generateOptionUnwrapMapping` in
`MappingCodeGenerator`. Dispatch added to `generateFieldMapping` alongside existing strategy
branches.

**OptionWrap (source → Option<Inner>):**

```kotlin
// Non-null source, no nested mapper:   arrow.core.Option.fromNullable(source)
// Nullable source, no nested mapper:   arrow.core.Option.fromNullable(source)   (same — fromNullable accepts null)
// Non-null source, nested:   arrow.core.Option.fromNullable(source.toInner())
// Nullable source, nested:   arrow.core.Option.fromNullable(source?.toInner())
```

All four cases unify to: emit `arrow.core.Option.fromNullable(…innerExpr…)` where `innerExpr` is
`source` or `source.toInner()` or `source?.toInner()`.

**OptionUnwrap (Option<Inner> source → target):**

```kotlin
// No nested: source.getOrNull()
// Nested:    source.getOrNull()?.toInner()
```

After `getOrNull()` the value is `Inner?`. The existing nullable→non-null null-guard
(`?: throw MappingException.RequiredFieldMissing(…)`) fires via the standard null-safety path in
`generateNullGuard` — no special handling needed in `OptionUnwrap` codegen.

### 6.6 Reverse Mapping (`@MapFrom`)

When `isReverse = true` and the SOURCE field (in reversed perspective) has FQN `"arrow.core.Option"`:
apply `OptionUnwrap`. When the TARGET field has FQN `"arrow.core.Option"`: apply `OptionWrap`.
The check in step 3c is symmetric for forward and reverse if implemented before the `isReverse`
branch split — the `targetField`/`sourceField` swap in `processMapFromAnnotation` already handles
direction.

### 6.7 `converters-arrow` Module

No new converter objects needed. The module's role is purely to put `arrow-core` on the consumer
classpath. Add a note to its `README.md` documenting that `Option<T>` mapping is provided by the
processor rule, not a converter object.

### 6.8 Integration Test

Add to `:integration-test` `commonMain/Models.kt`:

```kotlin
data class OptionTarget(
    val maybeId: arrow.core.Option<String>,       // String? → Option<String>
    val maybeTag: arrow.core.Option<TagD>,        // TagR? → Option<TagD> (nested)
)

@MapTo(OptionTarget::class)
data class OptionSource(
    val maybeId: String?,
    val maybeTag: TagR?,
)
```

Add to `commonTest/IntegrationTest.kt`:

```kotlin
@Test fun `Option wrap — Some and None`() {
    val some = OptionSource("abc", TagR("tag1")).toOptionTarget()
    assertEquals(Option.Some("abc"), some.maybeId)
    assertEquals(Option.Some(TagD("tag1")), some.maybeTag)

    val none = OptionSource(null, null).toOptionTarget()
    assertEquals(Option.None, none.maybeId)
    assertEquals(Option.None, none.maybeTag)
}
```

### 6.9 Known Blocker Condition

If it turns out that KotlinPoet requires an actual `ClassName` derived from `arrow.core.Option`
that cannot be constructed purely from its FQN string (e.g. because of generic type parameter
erasure in the generated code), a minimal `compileOnly` dependency on `arrow-core` in `:processor`
**may** be acceptable. This would be declared `compileOnly` only, so it does not bleed into
consumers. The plan instructs the agentic worker to attempt the FQN-string approach first and
escalate to `compileOnly` only if the build fails. This is NOT a showstopper.

---

## 7. Cross-cutting Concerns

### 7.1 `settings.gradle.kts` Updates

```
include(":converters-uuid", ":converters-okio", ":converters-uri")
```

Added to the existing include list in the same file.

### 7.2 Version

All new modules use `version.toString()` — which resolves to the root `version` property
(`0.2.0-SNAPSHOT`). No version bump.

### 7.3 iOS Verification

All commonMain-based modules (uuid, okio) must pass `:converters-X:iosSimulatorArm64Test`.
The `converters-uri` iOS test runs via `:converters-uri:iosSimulatorArm64Test`.
The Arrow Option integration test runs via `:integration-test:iosSimulatorArm64Test`.

### 7.4 No Publish

Do not run `publishToMavenLocal` or `publishToMavenCentral` in any task. All `build` commands
are `./gradlew :module:build --console=plain -q`.

---

## 8. Open Questions / Risks

| # | Risk | Mitigation |
|---|------|-----------|
| 1 | `kotlin.uuid.Uuid` may still require `@OptIn` on 2.3.10 | Build and check; add opt-in only if compiler requires |
| 2 | okio 3.9.1 `Path` normalization may make `StringPathConverter` round-trip platform-dependent | Use simple Unix paths in tests; document caveat |
| 3 | `NSURL.URLWithString` normalizes trailing slashes | Use pre-normalized test URLs |
| 4 | Arrow Option FQN-only detection may hit KotlinPoet limitations | Fall back to `compileOnly` in processor if needed |
| 5 | iosMain shared source set name (`iosMain`) vs AGP-KMP convention | Verify against converters-datetime build; adjust if needed |
