# Converter Add-ons (uuid/okio/uri) + Arrow Option — Implementation Plan
> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use checkbox (- [ ]) syntax.

**Goal:** Add three scalar converter modules (`converters-uuid`, `converters-okio`, `converters-uri`)
and one processor rule (Arrow `Option<T>` wrap/unwrap) to the kmap library at
`/Users/sahansenvar/StudioProjects/kmap`. All modules stay at version `0.2.0-SNAPSHOT`. No publish.

**Architecture:** Scalar modules clone `converters-datetime` exactly (no KSP, commonMain + optional
`jvmAndroidMain`, `mavenPublishing` block). `converters-uri` is platform-split (no commonMain
converters; jvmMain / androidMain / shared iosMain). Arrow Option is a processor rule in
`:processor` matching target-type FQN `"arrow.core.Option"` — no arrow Gradle dependency in
processor or core.

**Tech Stack:** Kotlin 2.3.10, KSP2 2.3.6, kotest 6.1.11, okio 3.9.1

---

## Prerequisites

- Repo path: `/Users/sahansenvar/StudioProjects/kmap`
- All commands run via `mcp__plugin_context-mode_context-mode__ctx_execute` (never Bash for
  build/test output). Git commits use Bash.
- Build commands use `--console=plain -q` and redirect to `/tmp/` log files.
- Commit after each module's final green build using conventional commits.
- Do NOT bump version, do NOT run publish tasks.

---

## Group A — `converters-uuid`

### A.1 — Scaffold module

- [ ] **Step 1:** Add `:converters-uuid` to `settings.gradle.kts` include list.

  File: `/Users/sahansenvar/StudioProjects/kmap/settings.gradle.kts`
  Change the `include(…)` line to add `":converters-uuid"` after `":converters-bignumber"`.

- [ ] **Step 2:** Create `converters-uuid/build.gradle.kts`:

  ```kotlin
  // /Users/sahansenvar/StudioProjects/kmap/converters-uuid/build.gradle.kts
  plugins {
      alias(libs.plugins.kotlin.multiplatform)
      alias(libs.plugins.android.kotlin.multiplatform.library)
      alias(libs.plugins.vanniktech.publish)
  }

  kotlin {
      android {
          namespace = "com.sahsenvar.kmapper.uuid"
          compileSdk = 36
          minSdk = 30
      }
      jvm()
      iosArm64()
      iosSimulatorArm64()

      sourceSets {
          commonMain.dependencies {
              api(project(":core"))
          }
          // Shared source set: java.util.UUID converters written once for jvm + android.
          val jvmAndroidMain by creating {
              dependsOn(commonMain.get())
          }
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
      publishToMavenCentral()
      signAllPublications()
      coordinates("io.github.sahsenvar", "kmapper-converters-uuid", version.toString())
      pom {
          name.set("kmap converters-uuid")
          description.set("KMP-friendly compile-time object mapper (KSP). Converters-uuid module: kotlin.uuid.Uuid (common) and java.util.UUID (jvm/android) scalar converters + kotlin↔java UUID bridges.")
          inceptionYear.set("2026")
          url.set("https://github.com/sahsenvar/kmap")
          licenses {
              license {
                  name.set("The Apache License, Version 2.0")
                  url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
              }
          }
          developers {
              developer {
                  id.set("sahsenvar")
                  name.set("Şahan Şenvar")
                  url.set("https://github.com/sahsenvar")
              }
          }
          scm { url.set("https://github.com/sahsenvar/kmap") }
      }
  }
  ```

  > No new `libs.versions.toml` entry needed: `kotlin.uuid.Uuid` is part of the stdlib already in
  > the catalog; `java.util.UUID` is a JDK type. State this in the commit message.

### A.2 — commonMain converter (TDD: test first)

- [ ] **Step 1:** Create the test file first.

  File: `converters-uuid/src/commonTest/kotlin/com/sahsenvar/kmapper/uuid/StringUuidConverterTest.kt`

  ```kotlin
  package com.sahsenvar.kmapper.uuid

  import io.kotest.assertions.throwables.shouldThrow
  import kotlin.test.Test
  import kotlin.test.assertEquals

  class StringUuidConverterTest {

      private val sample = "550e8400-e29b-41d4-a716-446655440000"

      @Test fun `round-trip valid UUID string`() {
          val uuid = StringUuidConverter.convertToNonNull(sample)
          assertEquals(sample, StringUuidConverter.convertFromNonNull(uuid))
      }

      @Test fun `convertTo on invalid string throws`() {
          shouldThrow<Exception> {
              StringUuidConverter.convertToNonNull("not-a-uuid")
          }
      }
  }
  ```

- [ ] **Step 2:** Create the commonMain converter.

  File: `converters-uuid/src/commonMain/kotlin/com/sahsenvar/kmapper/uuid/UuidConverters.kt`

  ```kotlin
  package com.sahsenvar.kmapper.uuid

  import com.sahsenvar.kmapper.converter.MapTypeConverter
  import kotlin.uuid.Uuid

  // NOTE: kotlin.uuid.Uuid is stable in Kotlin 2.1+. No @OptIn required on 2.3.10.
  // If the compiler reports ExperimentalUuidApi, add @file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
  // to this file and remove it once confirmed stable.

  /** [String] ↔ [kotlin.uuid.Uuid]. Parses RFC-4122 UUID strings in both directions. */
  object StringUuidConverter : MapTypeConverter<String, Uuid>(String::class, Uuid::class) {
      override fun convertToNonNull(value: String): Uuid = Uuid.parse(value)
      override fun convertFromNonNull(value: Uuid): String = value.toString()
  }
  ```

- [ ] **Step 3:** Run commonTest on JVM:

  ```
  ./gradlew :converters-uuid:jvmTest --console=plain -q > /tmp/uuid-common.log 2>&1; echo exit=$?; grep -E '^e:|error:|FAILED|BUILD FAILED' /tmp/uuid-common.log | head -20
  ```

  Expect: exit=0, no errors.

### A.3 — jvmAndroidMain converters (TDD: test first)

- [ ] **Step 1:** Create the JVM test file first.

  File: `converters-uuid/src/jvmTest/kotlin/com/sahsenvar/kmapper/uuid/JavaUuidConverterTest.kt`

  ```kotlin
  package com.sahsenvar.kmapper.uuid

  import io.kotest.matchers.shouldBe
  import kotlin.test.Test
  import kotlin.uuid.Uuid

  class JavaUuidConverterTest {

      private val sample = "550e8400-e29b-41d4-a716-446655440000"

      @Test fun `JavaStringUuidConverter round-trip`() {
          val javaUuid = JavaStringUuidConverter.convertToNonNull(sample)
          JavaStringUuidConverter.convertFromNonNull(javaUuid) shouldBe sample
      }

      @Test fun `KotlinJavaUuidConverter round-trip`() {
          val kotlinUuid = Uuid.parse(sample)
          val javaUuid = KotlinJavaUuidConverter.convertToNonNull(kotlinUuid)
          KotlinJavaUuidConverter.convertFromNonNull(javaUuid) shouldBe kotlinUuid
      }

      @Test fun `KotlinJavaUuidConverter toString consistency`() {
          val kotlinUuid = Uuid.parse(sample)
          val javaUuid = KotlinJavaUuidConverter.convertToNonNull(kotlinUuid)
          javaUuid.toString().lowercase() shouldBe sample
      }
  }
  ```

- [ ] **Step 2:** Create the jvmAndroidMain converters.

  File: `converters-uuid/src/jvmAndroidMain/kotlin/com/sahsenvar/kmapper/uuid/JavaUuidConverters.kt`

  ```kotlin
  package com.sahsenvar.kmapper.uuid

  import com.sahsenvar.kmapper.converter.MapTypeConverter
  import kotlin.uuid.Uuid
  import kotlin.uuid.toJavaUuid
  import kotlin.uuid.toKotlinUuid
  import java.util.UUID

  // NOTE: kotlin.uuid.Uuid is stable in Kotlin 2.1+. No @OptIn required on 2.3.10.
  // If the compiler reports ExperimentalUuidApi, add @file:OptIn(kotlin.uuid.ExperimentalUuidApi::class).

  /** [String] ↔ [java.util.UUID]. Uses UUID.fromString for parsing. */
  object JavaStringUuidConverter : MapTypeConverter<String, UUID>(String::class, UUID::class) {
      override fun convertToNonNull(value: String): UUID = UUID.fromString(value)
      override fun convertFromNonNull(value: UUID): String = value.toString()
  }

  /** [kotlin.uuid.Uuid] ↔ [java.util.UUID]. Uses kotlin.uuid stdlib bridge functions. */
  object KotlinJavaUuidConverter : MapTypeConverter<Uuid, UUID>(Uuid::class, UUID::class) {
      override fun convertToNonNull(value: Uuid): UUID = value.toJavaUuid()
      override fun convertFromNonNull(value: UUID): Uuid = value.toKotlinUuid()
  }
  ```

- [ ] **Step 3:** Run jvmTest:

  ```
  ./gradlew :converters-uuid:jvmTest --console=plain -q > /tmp/uuid-jvm.log 2>&1; echo exit=$?; grep -E '^e:|error:|FAILED|BUILD FAILED' /tmp/uuid-jvm.log | head -20
  ```

  Expect: exit=0.

### A.4 — iOS build verification

- [ ] **Step 1:** Compile for iosSimulatorArm64:

  ```
  ./gradlew :converters-uuid:iosSimulatorArm64Test --console=plain -q > /tmp/uuid-ios.log 2>&1; echo exit=$?; grep -E '^e:|error:|FAILED|BUILD FAILED' /tmp/uuid-ios.log | head -20
  ```

  Expect: exit=0. (No iOS-specific test code; this verifies the commonMain compiles for the iOS target.)

### A.5 — Full build + commit

- [ ] **Step 1:** Full module build:

  ```
  ./gradlew :converters-uuid:build --console=plain -q > /tmp/uuid-build.log 2>&1; echo exit=$?; grep -E '^e:|error:|FAILED|BUILD FAILED' /tmp/uuid-build.log | head -20
  ```

  Expect: exit=0.

- [ ] **Step 2:** Commit:

  ```bash
  cd /Users/sahansenvar/StudioProjects/kmap
  git add settings.gradle.kts converters-uuid
  git commit -m "feat(converters-uuid): add kotlin.uuid.Uuid + java.util.UUID scalar converters

  - StringUuidConverter (common): String ↔ kotlin.uuid.Uuid
  - JavaStringUuidConverter (jvm/android): String ↔ java.util.UUID
  - KotlinJavaUuidConverter (jvm/android): kotlin.uuid.Uuid ↔ java.util.UUID
  - No new catalog entries (stdlib + JDK types)

  Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
  ```

---

## Group B — `converters-okio`

### B.1 — Catalog + scaffold

- [ ] **Step 1:** Add okio to `gradle/libs.versions.toml`:

  Under `[versions]`:
  ```toml
  okio = "3.9.1"
  ```
  Under `[libraries]`:
  ```toml
  okio = { module = "com.squareup.okio:okio", version.ref = "okio" }
  ```

- [ ] **Step 2:** Add `:converters-okio` to `settings.gradle.kts` include list (append after `:converters-uuid`).

- [ ] **Step 3:** Create `converters-okio/build.gradle.kts`:

  ```kotlin
  // /Users/sahansenvar/StudioProjects/kmap/converters-okio/build.gradle.kts
  plugins {
      alias(libs.plugins.kotlin.multiplatform)
      alias(libs.plugins.android.kotlin.multiplatform.library)
      alias(libs.plugins.vanniktech.publish)
  }

  kotlin {
      android {
          namespace = "com.sahsenvar.kmapper.okio"
          compileSdk = 36
          minSdk = 30
      }
      jvm()
      iosArm64()
      iosSimulatorArm64()

      sourceSets {
          commonMain.dependencies {
              api(project(":core"))
              implementation(libs.okio)
          }

          commonTest.dependencies {
              implementation(kotlin("test"))
              implementation(libs.kotest.assertions)
          }
      }
  }

  mavenPublishing {
      publishToMavenCentral()
      signAllPublications()
      coordinates("io.github.sahsenvar", "kmapper-converters-okio", version.toString())
      pom {
          name.set("kmap converters-okio")
          description.set("KMP-friendly compile-time object mapper (KSP). Converters-okio module: okio ByteString and Path scalar converters.")
          inceptionYear.set("2026")
          url.set("https://github.com/sahsenvar/kmap")
          licenses {
              license {
                  name.set("The Apache License, Version 2.0")
                  url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
              }
          }
          developers {
              developer {
                  id.set("sahsenvar")
                  name.set("Şahan Şenvar")
                  url.set("https://github.com/sahsenvar")
              }
          }
          scm { url.set("https://github.com/sahsenvar/kmap") }
      }
  }
  ```

### B.2 — commonMain converters (TDD: test first)

- [ ] **Step 1:** Create the test file first.

  File: `converters-okio/src/commonTest/kotlin/com/sahsenvar/kmapper/okio/OkioConverterTest.kt`

  ```kotlin
  package com.sahsenvar.kmapper.okio

  import io.kotest.matchers.shouldBe
  import kotlin.test.Test
  import kotlin.test.assertTrue

  class OkioConverterTest {

      // StringByteStringConverter

      @Test fun `StringByteStringConverter round-trip non-empty`() {
          val original = "hello kmap"
          StringByteStringConverter.convertFromNonNull(
              StringByteStringConverter.convertToNonNull(original)
          ) shouldBe original
      }

      @Test fun `StringByteStringConverter round-trip empty string`() {
          val original = ""
          StringByteStringConverter.convertFromNonNull(
              StringByteStringConverter.convertToNonNull(original)
          ) shouldBe original
      }

      // ByteArrayByteStringConverter — compare contents, NOT reference

      @Test fun `ByteArrayByteStringConverter round-trip`() {
          val original = byteArrayOf(1, 2, 3, 4, 127, -1)
          val roundTripped = ByteArrayByteStringConverter.convertFromNonNull(
              ByteArrayByteStringConverter.convertToNonNull(original)
          )
          assertTrue(roundTripped.contentEquals(original), "ByteArray contents must be equal")
      }

      @Test fun `ByteArrayByteStringConverter round-trip empty`() {
          val original = byteArrayOf()
          val roundTripped = ByteArrayByteStringConverter.convertFromNonNull(
              ByteArrayByteStringConverter.convertToNonNull(original)
          )
          assertTrue(roundTripped.contentEquals(original))
      }

      // StringPathConverter

      @Test fun `StringPathConverter round-trip unix path`() {
          val original = "/tmp/test"
          StringPathConverter.convertFromNonNull(
              StringPathConverter.convertToNonNull(original)
          ) shouldBe original
      }
  }
  ```

- [ ] **Step 2:** Create the commonMain converters.

  File: `converters-okio/src/commonMain/kotlin/com/sahsenvar/kmapper/okio/OkioConverters.kt`

  ```kotlin
  package com.sahsenvar.kmapper.okio

  import com.sahsenvar.kmapper.converter.MapTypeConverter
  import okio.ByteString
  import okio.ByteString.Companion.encodeUtf8
  import okio.ByteString.Companion.toByteString
  import okio.Path
  import okio.Path.Companion.toPath

  /** [String] ↔ [okio.ByteString] via UTF-8 encoding. */
  object StringByteStringConverter : MapTypeConverter<String, ByteString>(String::class, ByteString::class) {
      override fun convertToNonNull(value: String): ByteString = value.encodeUtf8()
      override fun convertFromNonNull(value: ByteString): String = value.utf8()
  }

  /**
   * [ByteArray] ↔ [okio.ByteString].
   * Round-trip test must compare with [ByteArray.contentEquals], NOT reference equality.
   */
  object ByteArrayByteStringConverter : MapTypeConverter<ByteArray, ByteString>(ByteArray::class, ByteString::class) {
      override fun convertToNonNull(value: ByteArray): ByteString = value.toByteString()
      override fun convertFromNonNull(value: ByteString): ByteArray = value.toByteArray()
  }

  /** [String] ↔ [okio.Path]. */
  object StringPathConverter : MapTypeConverter<String, Path>(String::class, Path::class) {
      override fun convertToNonNull(value: String): Path = value.toPath()
      override fun convertFromNonNull(value: Path): String = value.toString()
  }
  ```

- [ ] **Step 3:** Run tests on JVM:

  ```
  ./gradlew :converters-okio:jvmTest --console=plain -q > /tmp/okio-jvm.log 2>&1; echo exit=$?; grep -E '^e:|error:|FAILED|BUILD FAILED' /tmp/okio-jvm.log | head -20
  ```

  Expect: exit=0.

### B.3 — iOS build verification

- [ ] **Step 1:**

  ```
  ./gradlew :converters-okio:iosSimulatorArm64Test --console=plain -q > /tmp/okio-ios.log 2>&1; echo exit=$?; grep -E '^e:|error:|FAILED|BUILD FAILED' /tmp/okio-ios.log | head -20
  ```

  Expect: exit=0.

### B.4 — Full build + commit

- [ ] **Step 1:**

  ```
  ./gradlew :converters-okio:build --console=plain -q > /tmp/okio-build.log 2>&1; echo exit=$?; grep -E '^e:|error:|FAILED|BUILD FAILED' /tmp/okio-build.log | head -20
  ```

  Expect: exit=0.

- [ ] **Step 2:** Commit:

  ```bash
  cd /Users/sahansenvar/StudioProjects/kmap
  git add gradle/libs.versions.toml settings.gradle.kts converters-okio
  git commit -m "feat(converters-okio): add okio ByteString and Path scalar converters

  - Add okio 3.9.1 to version catalog
  - StringByteStringConverter: String ↔ okio.ByteString (UTF-8)
  - ByteArrayByteStringConverter: ByteArray ↔ okio.ByteString
  - StringPathConverter: String ↔ okio.Path
  - All converters in commonMain; verified on JVM + iOS

  Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
  ```

---

## Group C — `converters-uri`

### C.1 — Scaffold module

- [ ] **Step 1:** Add `:converters-uri` to `settings.gradle.kts` include list (append after `:converters-okio`).

- [ ] **Step 2:** Create `converters-uri/build.gradle.kts`:

  ```kotlin
  // /Users/sahansenvar/StudioProjects/kmap/converters-uri/build.gradle.kts
  plugins {
      alias(libs.plugins.kotlin.multiplatform)
      alias(libs.plugins.android.kotlin.multiplatform.library)
      alias(libs.plugins.vanniktech.publish)
  }

  kotlin {
      android {
          namespace = "com.sahsenvar.kmapper.uri"
          compileSdk = 36
          minSdk = 30
      }
      jvm()
      iosArm64()
      iosSimulatorArm64()

      sourceSets {
          commonMain.dependencies {
              api(project(":core"))
          }

          // Shared iOS source set: NSURL converters written once for both iOS targets.
          val iosMain by creating { dependsOn(commonMain.get()) }
          iosArm64Main.get().dependsOn(iosMain)
          iosSimulatorArm64Main.get().dependsOn(iosMain)
          val iosTest by creating { dependsOn(commonTest.get()) }
          iosArm64Test.get().dependsOn(iosTest)
          iosSimulatorArm64Test.get().dependsOn(iosTest)

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
      publishToMavenCentral()
      signAllPublications()
      coordinates("io.github.sahsenvar", "kmapper-converters-uri", version.toString())
      pom {
          name.set("kmap converters-uri")
          description.set("KMP-friendly compile-time object mapper (KSP). Converters-uri module: platform-specific URI converters (java.net.URI, android.net.Uri, NSURL).")
          inceptionYear.set("2026")
          url.set("https://github.com/sahsenvar/kmap")
          licenses {
              license {
                  name.set("The Apache License, Version 2.0")
                  url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
              }
          }
          developers {
              developer {
                  id.set("sahsenvar")
                  name.set("Şahan Şenvar")
                  url.set("https://github.com/sahsenvar")
              }
          }
          scm { url.set("https://github.com/sahsenvar/kmap") }
      }
  }
  ```

  > No new catalog entries: all types are platform SDKs.
  > commonMain holds no converter objects — only the `api(project(":core"))` dependency so
  > consumers get `MapTypeConverter` on classpath for all targets.

### C.2 — jvmMain converter (TDD: test first)

- [ ] **Step 1:** Create jvmTest file:

  File: `converters-uri/src/jvmTest/kotlin/com/sahsenvar/kmapper/uri/JavaUriConverterTest.kt`

  ```kotlin
  package com.sahsenvar.kmapper.uri

  import io.kotest.matchers.shouldBe
  import kotlin.test.Test

  class JavaUriConverterTest {

      @Test fun `JavaStringUriConverter round-trip https`() {
          val original = "https://example.com/"
          JavaStringUriConverter.convertFromNonNull(
              JavaStringUriConverter.convertToNonNull(original)
          ) shouldBe original
      }

      @Test fun `JavaStringUriConverter round-trip ftp`() {
          val original = "ftp://files.example.org/pub"
          JavaStringUriConverter.convertFromNonNull(
              JavaStringUriConverter.convertToNonNull(original)
          ) shouldBe original
      }
  }
  ```

- [ ] **Step 2:** Create jvmMain converter:

  File: `converters-uri/src/jvmMain/kotlin/com/sahsenvar/kmapper/uri/JavaUriConverter.kt`

  ```kotlin
  package com.sahsenvar.kmapper.uri

  import com.sahsenvar.kmapper.converter.MapTypeConverter
  import java.net.URI

  /** [String] ↔ [java.net.URI]. Uses [URI.create] for parsing. */
  object JavaStringUriConverter : MapTypeConverter<String, URI>(String::class, URI::class) {
      override fun convertToNonNull(value: String): URI = URI.create(value)
      override fun convertFromNonNull(value: URI): String = value.toString()
  }
  ```

- [ ] **Step 3:** Run jvmTest:

  ```
  ./gradlew :converters-uri:jvmTest --console=plain -q > /tmp/uri-jvm.log 2>&1; echo exit=$?; grep -E '^e:|error:|FAILED|BUILD FAILED' /tmp/uri-jvm.log | head -20
  ```

  Expect: exit=0.

### C.3 — androidMain converter

- [ ] **Step 1:** Create androidMain converter:

  File: `converters-uri/src/androidMain/kotlin/com/sahsenvar/kmapper/uri/AndroidUriConverter.kt`

  ```kotlin
  package com.sahsenvar.kmapper.uri

  import android.net.Uri
  import com.sahsenvar.kmapper.converter.MapTypeConverter

  /** [String] ↔ [android.net.Uri]. Uses [Uri.parse] for parsing. */
  object AndroidStringUriConverter : MapTypeConverter<String, Uri>(String::class, Uri::class) {
      override fun convertToNonNull(value: String): Uri = Uri.parse(value)
      override fun convertFromNonNull(value: Uri): String = value.toString()
  }
  ```

  > Android instrumented tests are NOT included in this plan (require an emulator). The converter
  > compiles against the Android SDK stub. Its symmetry mirrors `JavaStringUriConverter` exactly.

### C.4 — iosMain converter (TDD: test first)

- [ ] **Step 1:** Create iosTest file:

  File: `converters-uri/src/iosTest/kotlin/com/sahsenvar/kmapper/uri/NsUrlConverterTest.kt`

  ```kotlin
  package com.sahsenvar.kmapper.uri

  import io.kotest.matchers.shouldBe
  import kotlin.test.Test
  import kotlin.test.assertFailsWith

  class NsUrlConverterTest {

      // Use a pre-normalized URL: NSURL adds trailing slash to bare host.
      private val normalizedUrl = "https://example.com/"

      @Test fun `NsUrlStringConverter round-trip normalized URL`() {
          val nsUrl = NsUrlStringConverter.convertToNonNull(normalizedUrl)
          NsUrlStringConverter.convertFromNonNull(nsUrl) shouldBe normalizedUrl
      }

      @Test fun `NsUrlStringConverter throws on empty string`() {
          // NSURL.URLWithString("") returns null on empty input
          assertFailsWith<Exception> {
              NsUrlStringConverter.convertToNonNull("")
          }
      }
  }
  ```

- [ ] **Step 2:** Create iosMain converter:

  File: `converters-uri/src/iosMain/kotlin/com/sahsenvar/kmapper/uri/NsUrlConverter.kt`

  ```kotlin
  package com.sahsenvar.kmapper.uri

  import com.sahsenvar.kmapper.MappingException
  import com.sahsenvar.kmapper.converter.MapTypeConverter
  import platform.Foundation.NSURL

  /**
   * [String] ↔ [NSURL].
   *
   * [NSURL.URLWithString] returns null for malformed URLs; in that case
   * [convertToNonNull] throws [MappingException.TypeConversionFailed].
   *
   * Round-trip note: NSURL normalizes URLs (e.g. adds trailing slash to bare host).
   * Use pre-normalized URLs in tests (e.g. "https://example.com/" with trailing slash).
   */
  object NsUrlStringConverter : MapTypeConverter<String, NSURL>(String::class, NSURL::class) {
      override fun convertToNonNull(value: String): NSURL =
          NSURL.URLWithString(value)
              ?: throw MappingException.TypeConversionFailed(
                  from = "String",
                  to = "NSURL",
                  cause = IllegalArgumentException("NSURL.URLWithString returned null for: $value")
              )

      override fun convertFromNonNull(value: NSURL): String =
          value.absoluteString ?: value.path ?: ""
  }
  ```

- [ ] **Step 3:** Build iOS to verify compilation:

  ```
  ./gradlew :converters-uri:iosSimulatorArm64Test --console=plain -q > /tmp/uri-ios.log 2>&1; echo exit=$?; grep -E '^e:|error:|FAILED|BUILD FAILED' /tmp/uri-ios.log | head -20
  ```

  Expect: exit=0.

### C.5 — Full build + commit

- [ ] **Step 1:**

  ```
  ./gradlew :converters-uri:build --console=plain -q > /tmp/uri-build.log 2>&1; echo exit=$?; grep -E '^e:|error:|FAILED|BUILD FAILED' /tmp/uri-build.log | head -20
  ```

  Expect: exit=0.

- [ ] **Step 2:** Commit:

  ```bash
  cd /Users/sahansenvar/StudioProjects/kmap
  git add settings.gradle.kts converters-uri
  git commit -m "feat(converters-uri): add platform-specific URI scalar converters

  - JavaStringUriConverter (jvm): String ↔ java.net.URI
  - AndroidStringUriConverter (android): String ↔ android.net.Uri
  - NsUrlStringConverter (ios): String ↔ platform.Foundation.NSURL (throws TypeConversionFailed on null)
  - No new catalog entries (platform SDK types)
  - Round-trip caveat: NSURL normalizes URLs; tests use pre-normalized URLs

  Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
  ```

---

## Group D — Arrow `Option<T>` Processor Rule

> This is the riskiest group. Implement last. Read `:processor/src/` carefully before touching
> any file. The constraint is absolute: `:processor` and `:core` must NOT gain an `arrow-core`
> Gradle dependency.

### D.1 — Add `MappingStrategy.OptionWrap` and `MappingStrategy.OptionUnwrap`

- [ ] **Step 1:** Open the file first.

  File: `processor/src/commonMain/kotlin/com/sahsenvar/kmapper/processor/model/MappingStrategy.kt`

  Read it. Append two new data classes to the sealed class **after** the last existing variant
  (before the closing brace):

  ```kotlin
  /**
   * Target field type is `arrow.core.Option<Inner>`.
   * Source field is `Inner` (non-null) or `Inner?` (nullable).
   * Detection: matched by target-type FQN string "arrow.core.Option" — no arrow Gradle dep needed.
   *
   * @param innerMapperFn non-null when the inner type requires a nested mapper call (data class).
   */
  data class OptionWrap(val innerMapperFn: String? = null) : MappingStrategy()

  /**
   * Source field type is `arrow.core.Option<Inner>`.
   * Target field is `Inner?` or `Inner` (non-null guarded by existing RequiredFieldMissing path).
   * Detection: matched by source-type FQN string "arrow.core.Option".
   *
   * @param innerMapperFn non-null when the inner type requires a nested mapper call (data class).
   */
  data class OptionUnwrap(val innerMapperFn: String? = null) : MappingStrategy()
  ```

- [ ] **Step 2:** Compile the processor module only to confirm no syntax errors:

  ```
  ./gradlew :processor:compileKotlinJvm --console=plain -q > /tmp/opt-strategy.log 2>&1; echo exit=$?; grep -E '^e:|error:|FAILED|BUILD FAILED' /tmp/opt-strategy.log | head -20
  ```

  Expect: exit=0.

### D.2 — TypeMatcher: add Option detection rule

- [ ] **Step 1:** Read `TypeMatcher.kt` first.

  File: `processor/src/commonMain/kotlin/com/sahsenvar/kmapper/processor/analyzer/TypeMatcher.kt`

  Locate the block numbered comment `// 3b. Check same type (non-collection)` (the non-collection
  direct check). Insert the new Option block **between step 3 (collection checks) and step 3b**,
  or after step 3b and before step 4 (nested object check) — wherever it fits cleanly without
  disturbing collection or direct logic. Add as step 3c:

  ```kotlin
  // 3c. Check arrow.core.Option<T> wrap/unwrap (matched by FQN string — no arrow Gradle dep)
  val targetTypeFqn = targetField.type.declaration.qualifiedName?.asString()
  val sourceTypeFqn = sourceField.type.declaration.qualifiedName?.asString()

  if (targetTypeFqn == "arrow.core.Option") {
      val innerType = targetField.type.arguments.firstOrNull()?.type?.resolve()
      val innerMapperFn = if (innerType != null && isDataClass(innerType)) {
          "to${innerType.declaration.simpleName.asString()}"
      } else null
      return MappingStrategy.OptionWrap(innerMapperFn)
  }
  if (sourceTypeFqn == "arrow.core.Option") {
      val innerType = sourceField.type.arguments.firstOrNull()?.type?.resolve()
      val innerMapperFn = if (innerType != null && isDataClass(innerType)) {
          "to${innerType.declaration.simpleName.asString()}"
      } else null
      return MappingStrategy.OptionUnwrap(innerMapperFn)
  }
  ```

- [ ] **Step 2:** Compile:

  ```
  ./gradlew :processor:compileKotlinJvm --console=plain -q > /tmp/opt-matcher.log 2>&1; echo exit=$?; grep -E '^e:|error:|FAILED|BUILD FAILED' /tmp/opt-matcher.log | head -20
  ```

  Expect: exit=0.

### D.3 — MappingCodeGenerator: emit OptionWrap / OptionUnwrap code

- [ ] **Step 1:** Read `MappingCodeGenerator.kt` first.

  File: `processor/src/commonMain/kotlin/com/sahsenvar/kmapper/processor/MappingCodeGenerator.kt`

  Locate the `when (strategy)` dispatch in `generateFieldMapping` (or the equivalent method that
  dispatches to `generateWrappedCollectionMapping`, `generateCollectionMapping`, etc.).

  Add two branches to the `when` block and two private methods:

  **In the `when` dispatch**, add after the `is MappingStrategy.WrappedCollection` branch:

  ```kotlin
  is MappingStrategy.OptionWrap -> generateOptionWrapMapping(sourceField, strategy)
  is MappingStrategy.OptionUnwrap -> generateOptionUnwrapMapping(sourceField, strategy)
  ```

  **New private method: `generateOptionWrapMapping`**

  ```kotlin
  /**
   * Generates: arrow.core.Option.fromNullable(<innerExpr>)
   *
   * innerExpr is:
   *   no nested, non-null source: source
   *   no nested, nullable source: source            (fromNullable accepts null)
   *   nested, non-null source:   source.toInner()
   *   nested, nullable source:   source?.toInner()
   *
   * fromNullable(null) == Option.None, fromNullable(x) == Option.Some(x).
   */
  private fun generateOptionWrapMapping(
      sourceField: FieldInfo,
      strategy: MappingStrategy.OptionWrap
  ): CodeBlock {
      val innerExpr = when {
          strategy.innerMapperFn == null -> CodeBlock.of("%N", sourceField.name)
          sourceField.isNullable -> CodeBlock.of("%N?.%N()", sourceField.name, strategy.innerMapperFn)
          else -> CodeBlock.of("%N.%N()", sourceField.name, strategy.innerMapperFn)
      }
      // Emit FQN directly — no import needed; KotlinPoet will render it as written.
      return CodeBlock.of("arrow.core.Option.fromNullable(%L)", innerExpr)
  }

  /**
   * Generates: source.getOrNull() [?.toInner()]
   *
   * The result is nullable (Inner?). The standard nullable→non-null null-guard
   * (RequiredFieldMissing) is applied by the caller's generateNullGuard after this returns.
   */
  private fun generateOptionUnwrapMapping(
      sourceField: FieldInfo,
      strategy: MappingStrategy.OptionUnwrap
  ): CodeBlock {
      val getOrNull = CodeBlock.of("%N.getOrNull()", sourceField.name)
      return if (strategy.innerMapperFn != null) {
          CodeBlock.of("%L?.%N()", getOrNull, strategy.innerMapperFn)
      } else {
          getOrNull
      }
  }
  ```

  > **KotlinPoet FQN note:** `CodeBlock.of("arrow.core.Option.fromNullable(%L)", innerExpr)` emits
  > the literal string `arrow.core.Option.fromNullable(…)` into the generated file. KotlinPoet
  > does NOT add an import for it; the consumer's classpath (via `:converters-arrow`) provides the
  > type. If the build fails because KotlinPoet mangles the FQN, switch to:
  > ```kotlin
  > val optionClass = ClassName("arrow.core", "Option")
  > CodeBlock.of("%T.fromNullable(%L)", optionClass, innerExpr)
  > ```
  > `ClassName` construction does NOT require `arrow-core` on the processor classpath — it is a
  > KotlinPoet utility that takes String arguments. This is the approved fallback.

- [ ] **Step 2:** Compile:

  ```
  ./gradlew :processor:compileKotlinJvm --console=plain -q > /tmp/opt-codegen.log 2>&1; echo exit=$?; grep -E '^e:|error:|FAILED|BUILD FAILED' /tmp/opt-codegen.log | head -20
  ```

  Expect: exit=0. If `ClassName` is needed for the FQN, apply the fallback now.

### D.4 — Integration test: add `Option<T>` models + test case

- [ ] **Step 1:** Read the existing `Models.kt` file.

  File: `integration-test/src/commonMain/kotlin/com/sahsenvar/kmapper/itest/Models.kt`

  Append these declarations to the file (do not remove existing content):

  ```kotlin
  // ---- Arrow Option<T> models ----

  data class OptionTargetModel(
      val maybeId: arrow.core.Option<String>,
      val maybeTag: arrow.core.Option<TagD>,
  )

  @MapTo(OptionTargetModel::class)
  data class OptionSourceModel(
      val maybeId: String?,
      val maybeTag: TagR?,
  )
  ```

  > `TagR` and `TagD` are already defined in the file. `arrow.core.Option` is already on the
  > classpath via `converters-arrow` in `commonMain.dependencies`.

- [ ] **Step 2:** Read the existing integration test file.

  File: `integration-test/src/commonTest/kotlin/com/sahsenvar/kmapper/itest/IntegrationTest.kt`

  Append a new test function:

  ```kotlin
  @Test fun `Arrow Option wrap — Some and None paths`() {
      // Some path: non-null fields → Option.Some
      val some = OptionSourceModel("abc", TagR("tag1")).toOptionTargetModel()
      assertEquals(arrow.core.Option.Some("abc"), some.maybeId)
      assertEquals(arrow.core.Option.Some(TagD("tag1")), some.maybeTag)

      // None path: null fields → Option.None
      val none = OptionSourceModel(null, null).toOptionTargetModel()
      assertEquals(arrow.core.None, none.maybeId)
      assertEquals(arrow.core.None, none.maybeTag)
  }
  ```

  > `arrow.core.None` is the singleton for `Option.None` in Arrow 2.x. If the Arrow API differs,
  > use `arrow.core.Option.fromNullable(null)` for the comparison.

- [ ] **Step 3:** Run integration tests on JVM:

  ```
  ./gradlew :integration-test:jvmTest --console=plain -q > /tmp/opt-itest-jvm.log 2>&1; echo exit=$?; grep -E '^e:|error:|FAILED|BUILD FAILED' /tmp/opt-itest-jvm.log | head -30
  ```

  Expect: exit=0. If there are compile errors in the generated code, read `/tmp/opt-itest-jvm.log`
  in full to diagnose — then fix `MappingCodeGenerator` accordingly.

### D.5 — iOS integration test verification

- [ ] **Step 1:**

  ```
  ./gradlew :integration-test:iosSimulatorArm64Test --console=plain -q > /tmp/opt-itest-ios.log 2>&1; echo exit=$?; grep -E '^e:|error:|FAILED|BUILD FAILED' /tmp/opt-itest-ios.log | head -30
  ```

  Expect: exit=0.

### D.6 — Processor full build + commit

- [ ] **Step 1:**

  ```
  ./gradlew :processor:build :integration-test:build --console=plain -q > /tmp/opt-final.log 2>&1; echo exit=$?; grep -E '^e:|error:|FAILED|BUILD FAILED' /tmp/opt-final.log | head -20
  ```

  Expect: exit=0.

- [ ] **Step 2:** Commit:

  ```bash
  cd /Users/sahansenvar/StudioProjects/kmap
  git add processor/src integration-test/src
  git commit -m "feat(processor): add Arrow Option<T> wrap/unwrap mapping rule

  - New MappingStrategy.OptionWrap / OptionUnwrap variants
  - TypeMatcher detects arrow.core.Option by FQN string (no arrow Gradle dep in processor)
  - MappingCodeGenerator emits Option.fromNullable(…) / getOrNull() for Option fields
  - Integration test: OptionSourceModel → OptionTargetModel on JVM + iOS
  - converters-arrow remains the sole module providing arrow-core on consumer classpath

  Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
  ```

---

## Final Verification

- [ ] **Full repo build:**

  ```
  ./gradlew build --console=plain -q > /tmp/full-build.log 2>&1; echo exit=$?; grep -E '^e:|error:|FAILED|BUILD FAILED' /tmp/full-build.log | head -30
  ```

  Expect: exit=0.

- [ ] **Confirm no new modules publish accidentally:**

  ```
  grep -r 'publishToMavenCentral\|publishToMavenLocal' /Users/sahansenvar/StudioProjects/kmap/converters-uuid /Users/sahansenvar/StudioProjects/kmap/converters-okio /Users/sahansenvar/StudioProjects/kmap/converters-uri --include='*.kts' | grep -v 'publishToMavenCentral()' | head -5
  ```

  All three modules declare `publishToMavenCentral()` in pom setup (as required by vanniktech) but
  no publish task is run in this plan. Confirm `version.toString()` resolves to `0.2.0-SNAPSHOT`.

---

## Task Count Summary

| Group | Tasks |
|-------|-------|
| A — converters-uuid | A.1 (2 steps), A.2 (3 steps), A.3 (3 steps), A.4 (1 step), A.5 (2 steps) = **11** |
| B — converters-okio | B.1 (3 steps), B.2 (3 steps), B.3 (1 step), B.4 (2 steps) = **9** |
| C — converters-uri | C.1 (2 steps), C.2 (3 steps), C.3 (1 step), C.4 (3 steps), C.5 (2 steps) = **11** |
| D — Arrow Option | D.1 (2 steps), D.2 (2 steps), D.3 (2 steps), D.4 (3 steps), D.5 (1 step), D.6 (2 steps) = **12** |
| Final | 2 steps |
| **Total** | **45 steps across 15 checkboxed tasks** |

---

## Blocker Assessment

**Arrow Option FQN-only feasibility:** CONFIRMED CLEAN.
- `TypeMatcher` already uses `qualifiedName?.asString()` string comparisons for other type checks
  (e.g. collection FQN detection, custom converter lookup by FQN pair).
- `MappingCodeGenerator` already emits FQN strings via `CodeBlock.of` for `MemberName` references.
- `ClassName("arrow.core", "Option")` in KotlinPoet takes pure Strings — no arrow dependency.
- No blocker. The processor can detect and codegen `Option<T>` without any arrow Gradle dep.
