# Validate Seam + Map<K,V> Mapping — Implementation Plan
> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use checkbox (- [ ]) syntax.

**Goal:** Implement three features in the `KMapper` KMP KSP object-mapper library at `/Users/sahansenvar/StudioProjects/KMapper`:
1. `@ValidateFrom` / `@ValidateTo` field-level validation seam with pluggable `Validator<T>` objects.
2. `:validators` add-on module with `EmailValidator` and `UrlValidator`.
3. `Map<K,V>` core mapping via a new `MappingStrategy.MapValues`.

**Architecture:** See design spec at `docs/superpowers/specs/2026-06-05-validate-seam-and-map-design.md`. Key facts: validators are per-field direct `KClass<*>` references (same in-module resolution path as `@UseMapTypeConverter`, no `@KMapperConfig` listing needed). `wrapWithValidation()` wraps the output of `applyNullableHandling`. Map<K,V> mirrors the Collection strategy pattern. No breaking changes; no version bump; no publish.

**Tech Stack:** Kotlin 2.3.10, KSP2 2.3.6, KotlinPoet 2.2.0, kctfork 0.12.1, kotest 6.1.11

**Build tool:** ALL build/test commands MUST be run via the context-mode MCP tool
`mcp__plugin_context-mode_context-mode__ctx_execute` (language: `bash`) from repo root
`/Users/sahansenvar/StudioProjects/KMapper` — NOT the Bash tool — per project rules.
- JVM tests: `./gradlew :<module>:jvmTest --console=plain -q > /tmp/t.log 2>&1; echo exit=$?`
- Processor tests: `./gradlew :processor:test --tests "*<TestClass>*" --console=plain -q > /tmp/t.log 2>&1; echo exit=$?`
- iOS: `./gradlew :integration-test:iosSimulatorArm64Test --console=plain -q > /tmp/it-ios.log 2>&1; echo exit=$?`
- Parse errors: `grep -nE '^e: |error:|> Task .* FAILED|^BUILD FAILED|^FAILURE:' /tmp/t.log | head -30`

**Commits:** Conventional commits after each green task. Keep `0.2.0-SNAPSHOT`; do not bump or publish.

---

## Task 1 — `MappingException.ValidationFailed` + `Validator<T>` + built-ins + core unit tests

### Task 1.1: Add `MappingException.ValidationFailed`

**File:** `core/src/commonMain/kotlin/com/sahsenvar/kmapper/MappingException.kt`

- [ ] **Step 1: Write the failing unit test**

  Create `core/src/commonTest/kotlin/com/sahsenvar/kmapper/ValidationFailedExceptionTest.kt`:
  ```kotlin
  package com.sahsenvar.kmapper
  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertIs

  class ValidationFailedExceptionTest {
      @Test fun `ValidationFailed carries field and reason`() {
          val ex = MappingException.ValidationFailed("email", "must be a valid email")
          assertEquals("email", ex.field)
          assertEquals("must be a valid email", ex.reason)
          assertIs<MappingException>(ex)
      }
      @Test fun `ValidationFailed message format`() {
          val ex = MappingException.ValidationFailed("name", "must not be blank")
          assertEquals("Validation failed for 'name': must not be blank", ex.message)
      }
  }
  ```

- [ ] **Step 2: Run — expect FAIL**
  `./gradlew :core:jvmTest --tests "*ValidationFailedExceptionTest*" --console=plain -q > /tmp/t.log 2>&1; echo exit=$?`

- [ ] **Step 3: Add subclass to `MappingException.kt`**

  In `core/src/commonMain/kotlin/com/sahsenvar/kmapper/MappingException.kt`, add after the last existing subclass:
  ```kotlin
  class ValidationFailed(val field: String, val reason: String)
      : MappingException("Validation failed for '$field': $reason")
  ```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**
  `git commit -m "feat(core): add MappingException.ValidationFailed"`

---

### Task 1.2: Add `Validator<T>` abstract base class

**New file:** `core/src/commonMain/kotlin/com/sahsenvar/kmapper/validation/Validator.kt`

- [ ] **Step 1: Write the failing test**

  Create `core/src/commonTest/kotlin/com/sahsenvar/kmapper/validation/ValidatorContractTest.kt`:
  ```kotlin
  package com.sahsenvar.kmapper.validation
  import kotlin.test.Test
  import kotlin.test.assertNull
  import kotlin.test.assertNotNull

  private object AlwaysValidStringValidator : Validator<String>(String::class) {
      override fun validate(value: String): String? = null
  }
  private object AlwaysInvalidStringValidator : Validator<String>(String::class) {
      override fun validate(value: String): String? = "always invalid"
  }

  class ValidatorContractTest {
      @Test fun `returning null means valid`() {
          assertNull(AlwaysValidStringValidator.validate("hello"))
      }
      @Test fun `returning non-null means invalid with reason`() {
          assertNotNull(AlwaysInvalidStringValidator.validate("hello"))
          assertNotNull(AlwaysInvalidStringValidator.validate("")) // null is never passed
      }
  }
  ```

- [ ] **Step 2: Run — expect FAIL**
  `./gradlew :core:jvmTest --tests "*ValidatorContractTest*" --console=plain -q > /tmp/t.log 2>&1; echo exit=$?`

- [ ] **Step 3: Create `Validator.kt`**
  ```kotlin
  package com.sahsenvar.kmapper.validation

  import kotlin.reflect.KClass

  /**
   * Base class for field value validators used with [@ValidateFrom] and [@ValidateTo].
   *
   * Implementations MUST be `object` singletons — the processor emits direct FQN calls
   * (e.g. `NotBlankValidator.validate(x)`) with no reflection.
   *
   * The [validate] method receives a NON-NULL value. Null handling is owned by the existing
   * nullability machinery in MappingCodeGenerator.applyNullableHandling; validators only fire
   * when a non-null value is present.
   *
   * @param T the type of value this validator accepts
   * @param targetType the KClass of T, used for documentation/introspection
   */
  abstract class Validator<T : Any>(val targetType: KClass<T>) {
      /**
       * Returns `null` if [value] is valid, or a human-readable reason string if invalid.
       */
      abstract fun validate(value: T): String?
  }
  ```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**
  `git commit -m "feat(core): add Validator<T> abstract base class"`

---

### Task 1.3: Add built-in validators

**New files in** `core/src/commonMain/kotlin/com/sahsenvar/kmapper/validation/builtin/`:

- [ ] **Step 1: Write the failing tests**

  Create `core/src/commonTest/kotlin/com/sahsenvar/kmapper/validation/builtin/BuiltInValidatorsTest.kt`:
  ```kotlin
  package com.sahsenvar.kmapper.validation.builtin
  import kotlin.test.Test
  import kotlin.test.assertNull
  import kotlin.test.assertNotNull
  import kotlin.test.assertEquals

  class BuiltInValidatorsTest {
      // NotBlankValidator
      @Test fun `NotBlankValidator - valid non-blank string`() = assertNull(NotBlankValidator.validate("hello"))
      @Test fun `NotBlankValidator - blank string is invalid`() = assertNotNull(NotBlankValidator.validate("   "))
      @Test fun `NotBlankValidator - empty string is invalid`() = assertNotNull(NotBlankValidator.validate(""))
      @Test fun `NotBlankValidator - reason message`() {
          assertEquals("must not be blank", NotBlankValidator.validate("  "))
      }

      // NotEmptyStringValidator
      @Test fun `NotEmptyStringValidator - valid non-empty`() = assertNull(NotEmptyStringValidator.validate("x"))
      @Test fun `NotEmptyStringValidator - empty string invalid`() = assertNotNull(NotEmptyStringValidator.validate(""))
      @Test fun `NotEmptyStringValidator - blank is valid (only checks empty)`() = assertNull(NotEmptyStringValidator.validate("  "))
      @Test fun `NotEmptyStringValidator - reason message`() {
          assertEquals("must not be empty", NotEmptyStringValidator.validate(""))
      }

      // NotEmptyCollectionValidator
      @Test fun `NotEmptyCollectionValidator - valid list`() = assertNull(NotEmptyCollectionValidator.validate(listOf(1)))
      @Test fun `NotEmptyCollectionValidator - empty list invalid`() = assertNotNull(NotEmptyCollectionValidator.validate(emptyList<Int>()))
      @Test fun `NotEmptyCollectionValidator - empty set invalid`() = assertNotNull(NotEmptyCollectionValidator.validate(emptySet<String>()))
      @Test fun `NotEmptyCollectionValidator - reason message`() {
          assertEquals("must not be empty", NotEmptyCollectionValidator.validate(emptyList<Any>()))
      }
  }
  ```

- [ ] **Step 2: Run — expect FAIL**
  `./gradlew :core:jvmTest --tests "*BuiltInValidatorsTest*" --console=plain -q > /tmp/t.log 2>&1; echo exit=$?`

- [ ] **Step 3: Create the three validator files**

  `NotBlankValidator.kt`:
  ```kotlin
  package com.sahsenvar.kmapper.validation.builtin
  import com.sahsenvar.kmapper.validation.Validator

  object NotBlankValidator : Validator<String>(String::class) {
      override fun validate(value: String): String? =
          if (value.isBlank()) "must not be blank" else null
  }
  ```

  `NotEmptyStringValidator.kt`:
  ```kotlin
  package com.sahsenvar.kmapper.validation.builtin
  import com.sahsenvar.kmapper.validation.Validator

  object NotEmptyStringValidator : Validator<String>(String::class) {
      override fun validate(value: String): String? =
          if (value.isEmpty()) "must not be empty" else null
  }
  ```

  `NotEmptyCollectionValidator.kt`:
  ```kotlin
  package com.sahsenvar.kmapper.validation.builtin
  import com.sahsenvar.kmapper.validation.Validator

  object NotEmptyCollectionValidator : Validator<Collection<*>>(Collection::class) {
      override fun validate(value: Collection<*>): String? =
          if (value.isEmpty()) "must not be empty" else null
  }
  ```

- [ ] **Step 4: Run — expect PASS**
  Also run full core suite: `./gradlew :core:jvmTest --console=plain -q > /tmp/t.log 2>&1; echo exit=$?`

- [ ] **Step 5: Commit**
  `git commit -m "feat(core): add NotBlankValidator, NotEmptyStringValidator, NotEmptyCollectionValidator"`

---

## Task 2 — `@ValidateFrom` and `@ValidateTo` annotations

**New files in** `core/src/commonMain/kotlin/com/sahsenvar/kmapper/annotations/`:

- [ ] **Step 1: Write annotation existence tests**

  Create `core/src/commonTest/kotlin/com/sahsenvar/kmapper/annotations/ValidateAnnotationsTest.kt`:
  ```kotlin
  package com.sahsenvar.kmapper.annotations
  import com.sahsenvar.kmapper.validation.builtin.NotBlankValidator
  import kotlin.test.Test

  // Compile-time test: if these compile, the annotations exist with the right shape.
  @ValidateFrom(NotBlankValidator::class)
  @ValidateTo(NotBlankValidator::class)
  private data class AnnotationCompileCheck(val name: String)

  class ValidateAnnotationsTest {
      @Test fun `ValidateFrom annotation is usable on a property`() {
          // If this file compiled, the annotations work.
      }
  }
  ```

- [ ] **Step 2: Run — expect FAIL (compile error)**
  `./gradlew :core:jvmTest --tests "*ValidateAnnotationsTest*" --console=plain -q > /tmp/t.log 2>&1; echo exit=$?`

- [ ] **Step 3: Create the annotation files**

  `ValidateFrom.kt`:
  ```kotlin
  package com.sahsenvar.kmapper.annotations

  import kotlin.reflect.KClass

  /**
   * Validates the SOURCE field value BEFORE type conversion and null handling.
   * All listed [validators] are checked in order (fail-fast).
   * Each validator must be an `object` singleton subclassing [com.sahsenvar.kmapper.validation.Validator].
   * Null source values are never passed to validators — null handling is separate.
   */
  @Target(AnnotationTarget.PROPERTY)
  @Retention(AnnotationRetention.SOURCE)
  annotation class ValidateFrom(vararg val validators: KClass<*>)
  ```

  `ValidateTo.kt`:
  ```kotlin
  package com.sahsenvar.kmapper.annotations

  import kotlin.reflect.KClass

  /**
   * Validates the FINAL produced field value AFTER type conversion and null/default resolution,
   * immediately before assignment to the target constructor.
   * All listed [validators] are checked in order (fail-fast).
   * Each validator must be an `object` singleton subclassing [com.sahsenvar.kmapper.validation.Validator].
   * Null result values are never passed to validators.
   */
  @Target(AnnotationTarget.PROPERTY)
  @Retention(AnnotationRetention.SOURCE)
  annotation class ValidateTo(vararg val validators: KClass<*>)
  ```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**
  `git commit -m "feat(core): add @ValidateFrom and @ValidateTo field annotations"`

---

## Task 3 — `FieldInfo` extension + `FieldAnalyzer` reads `@ValidateFrom`/`@ValidateTo`

### Task 3.1: Add `validateFrom` / `validateTo` to `FieldInfo`

**File:** `processor/src/main/kotlin/com/sahsenvar/kmapper/processor/model/FieldInfo.kt`

- [ ] **Step 1: Write unit test for FieldInfo construction**

  Create `processor/src/test/kotlin/com/sahsenvar/kmapper/processor/model/FieldInfoValidationFieldsTest.kt`:
  ```kotlin
  package com.sahsenvar.kmapper.processor.model
  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertTrue
  import io.mockk.mockk
  import com.google.devtools.ksp.symbol.KSType

  class FieldInfoValidationFieldsTest {
      private fun makeFieldInfo(
          validateFrom: List<String> = emptyList(),
          validateTo: List<String> = emptyList()
      ) = FieldInfo(
          name = "field", type = mockk<KSType>(relaxed = true), isNullable = false,
          hasDefault = false, defaultValue = null, isComputed = false,
          fieldMapTargets = emptyMap(), useConverter = null, isIgnored = false,
          validateFrom = validateFrom, validateTo = validateTo
      )

      @Test fun `default FieldInfo has empty validation lists`() {
          val fi = makeFieldInfo()
          assertTrue(fi.validateFrom.isEmpty())
          assertTrue(fi.validateTo.isEmpty())
      }
      @Test fun `validateFrom stores FQNs`() {
          val fi = makeFieldInfo(validateFrom = listOf("com.example.NotBlankValidator"))
          assertEquals(listOf("com.example.NotBlankValidator"), fi.validateFrom)
      }
  }
  ```

  NOTE: if mockk is not in the processor test classpath, use a real but minimal `KSType` alternative or skip the mock approach — adapt to whatever the test uses (`compile()` helper produces real KSTypes). In that case, test via a compile-based test in Task 4.1 instead. Skip this unit test and move straight to Step 3.

- [ ] **Step 2: Run — expect FAIL** (field doesn't exist yet)

- [ ] **Step 3: Add fields to `FieldInfo.kt`**

  Add two fields with defaults after `isIgnored`:
  ```kotlin
  val validateFrom: List<String> = emptyList(),
  val validateTo: List<String> = emptyList(),
  ```

- [ ] **Step 4: Run — expect PASS** (all existing FieldInfo construction sites use named parameters + have defaults)

- [ ] **Step 5: Commit**
  `git commit -m "feat(processor): add validateFrom/validateTo fields to FieldInfo"`

---

### Task 3.2: `FieldAnalyzer` reads annotation FQNs

**File:** `processor/src/main/kotlin/com/sahsenvar/kmapper/processor/analyzer/FieldAnalyzer.kt`

- [ ] **Step 1: Write a compile-test that checks FieldInfo is populated**

  Create `processor/src/test/kotlin/com/sahsenvar/kmapper/processor/ValidateAnnotationReadTest.kt`:
  ```kotlin
  package com.sahsenvar.kmapper.processor

  import com.tschuchort.compiletesting.KotlinCompilation
  import com.tschuchort.compiletesting.SourceFile
  import kotlin.test.Test
  import kotlin.test.assertEquals

  class ValidateAnnotationReadTest {
      @Test fun `@ValidateFrom FQNs appear in generated code`() {
          val src = SourceFile.kotlin("V.kt", """
              import com.sahsenvar.kmapper.annotations.MapTo
              import com.sahsenvar.kmapper.annotations.ValidateFrom
              import com.sahsenvar.kmapper.validation.builtin.NotBlankValidator

              data class NameDomain(val name: String)

              @MapTo(NameDomain::class)
              data class NameRemote(
                  @ValidateFrom(NotBlankValidator::class) val name: String
              )
          """.trimIndent())
          val (result, compilation) = compile(src)
          assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
          val gen = compilation.generatedFile("NameRemoteMappers.kt")
          assert(gen.contains("NotBlankValidator")) { "Expected NotBlankValidator in:\n$gen" }
          assert(gen.contains("ValidationFailed")) { "Expected ValidationFailed in:\n$gen" }
      }

      @Test fun `@ValidateTo FQNs appear in generated code`() {
          val src = SourceFile.kotlin("V2.kt", """
              import com.sahsenvar.kmapper.annotations.MapTo
              import com.sahsenvar.kmapper.annotations.ValidateTo
              import com.sahsenvar.kmapper.validation.builtin.NotBlankValidator

              data class EmailDomain(val email: String)

              @MapTo(EmailDomain::class)
              data class EmailRemote(
                  @ValidateTo(NotBlankValidator::class) val email: String
              )
          """.trimIndent())
          val (result, compilation) = compile(src)
          assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
          val gen = compilation.generatedFile("EmailRemoteMappers.kt")
          assert(gen.contains("NotBlankValidator")) { "Expected NotBlankValidator in:\n$gen" }
      }
  }
  ```

- [ ] **Step 2: Run — expect FAIL** (annotations not read yet; generated code has no validator calls)
  `./gradlew :processor:test --tests "*ValidateAnnotationReadTest*" --console=plain -q > /tmp/t.log 2>&1; echo exit=$?`

- [ ] **Step 3: Add `extractValidatorFqns`, `extractValidateFrom`, `extractValidateTo` to `FieldAnalyzer`**

  In `FieldAnalyzer.kt`, alongside `extractUseConverter`:

  ```kotlin
  private fun extractValidatorFqns(annotated: KSAnnotated, shortName: String, fqn: String): List<String> {
      val annotation = annotated.annotations.firstOrNull {
          it.shortName.asString() == shortName ||
              it.annotationType.resolve().declaration.qualifiedName?.asString() == fqn
      } ?: return emptyList()
      @Suppress("UNCHECKED_CAST")
      val validators = annotation.arguments.firstOrNull()?.value as? List<KSType> ?: return emptyList()
      return validators.mapNotNull { it.declaration.qualifiedName?.asString() }
  }

  private fun extractValidateFrom(annotated: KSAnnotated): List<String> =
      extractValidatorFqns(annotated, "ValidateFrom", "com.sahsenvar.kmapper.annotations.ValidateFrom")

  private fun extractValidateTo(annotated: KSAnnotated): List<String> =
      extractValidatorFqns(annotated, "ValidateTo", "com.sahsenvar.kmapper.annotations.ValidateTo")
  ```

  In `analyzeConstructor`, inside the `constructor.parameters.forEach` block, after the `useConverter` extraction:
  ```kotlin
  val validateFrom = extractValidateFrom(param).ifEmpty { property?.let { extractValidateFrom(it) } ?: emptyList() }
  val validateTo   = extractValidateTo(param).ifEmpty   { property?.let { extractValidateTo(it)   } ?: emptyList() }
  ```

  Add `validateFrom = validateFrom, validateTo = validateTo` to both `FieldInfo(...)` construction sites (constructor param and computed property paths). In the computed property path, both lists also use the `property`-only extraction (no `param`).

- [ ] **Step 4: Run — expect PASS**
  Then run all processor tests: `./gradlew :processor:test --console=plain -q > /tmp/t.log 2>&1; echo exit=$?`

- [ ] **Step 5: Commit**
  `git commit -m "feat(processor): FieldAnalyzer reads @ValidateFrom/@ValidateTo KClass FQNs"`

---

## Task 4 — Codegen: `wrapWithValidation` + processor compile+runtime tests

**File:** `processor/src/main/kotlin/com/sahsenvar/kmapper/processor/generator/MappingCodeGenerator.kt`

### Task 4.1: Write the failing compile+runtime tests

Create `processor/src/test/kotlin/com/sahsenvar/kmapper/processor/ValidateCodegenTest.kt`:

```kotlin
package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals

class ValidateCodegenTest {

    // --- Compile-only structural tests ---

    @Test fun `ValidateFrom non-null source emits direct validator call`() {
        val src = SourceFile.kotlin("A.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            import com.sahsenvar.kmapper.annotations.ValidateFrom
            import com.sahsenvar.kmapper.validation.builtin.NotBlankValidator

            data class NameD(val name: String)
            @MapTo(NameD::class)
            data class NameR(@ValidateFrom(NotBlankValidator::class) val name: String)
        """.trimIndent())
        val (result, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val gen = compilation.generatedFile("NameRMappers.kt")
        assert(gen.contains("NotBlankValidator.validate(name)")) { gen }
        assert(gen.contains("ValidationFailed")) { gen }
        // ValidateFrom fires BEFORE the expression — check ordering
        assert(gen.indexOf("NotBlankValidator") < gen.indexOf("val __result")) { gen }
    }

    @Test fun `ValidateFrom nullable source emits safe-call validator`() {
        val src = SourceFile.kotlin("B.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            import com.sahsenvar.kmapper.annotations.ValidateFrom
            import com.sahsenvar.kmapper.validation.builtin.NotBlankValidator

            data class TagD(val name: String)
            @MapTo(TagD::class)
            data class TagR(@ValidateFrom(NotBlankValidator::class) val name: String?)
        """.trimIndent())
        val (result, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val gen = compilation.generatedFile("TagRMappers.kt")
        // Nullable source uses safe-call let pattern
        assert(gen.contains("name?.let")) { gen }
        assert(gen.contains("NotBlankValidator.validate")) { gen }
    }

    @Test fun `ValidateTo non-null result emits validator on __result`() {
        val src = SourceFile.kotlin("C.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            import com.sahsenvar.kmapper.annotations.ValidateTo
            import com.sahsenvar.kmapper.validation.builtin.NotBlankValidator

            data class EmailD(val email: String)
            @MapTo(EmailD::class)
            data class EmailR(@ValidateTo(NotBlankValidator::class) val email: String)
        """.trimIndent())
        val (result, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val gen = compilation.generatedFile("EmailRMappers.kt")
        assert(gen.contains("val __result")) { gen }
        assert(gen.contains("NotBlankValidator.validate(__result)")) { gen }
        // ValidateTo fires AFTER val __result
        assert(gen.indexOf("val __result") < gen.indexOf("NotBlankValidator.validate")) { gen }
    }

    @Test fun `multiple validators are emitted sequentially`() {
        val src = SourceFile.kotlin("D.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            import com.sahsenvar.kmapper.annotations.ValidateTo
            import com.sahsenvar.kmapper.validation.builtin.NotBlankValidator
            import com.sahsenvar.kmapper.validation.builtin.NotEmptyStringValidator

            data class ND(val s: String)
            @MapTo(ND::class)
            data class NR(@ValidateTo(NotBlankValidator::class, NotEmptyStringValidator::class) val s: String)
        """.trimIndent())
        val (result, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val gen = compilation.generatedFile("NRMappers.kt")
        assert(gen.contains("NotBlankValidator.validate")) { gen }
        assert(gen.contains("NotEmptyStringValidator.validate")) { gen }
    }

    @Test fun `field without validators emits no run block`() {
        val src = SourceFile.kotlin("E.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            data class PlainD(val x: String)
            @MapTo(PlainD::class)
            data class PlainR(val x: String)
        """.trimIndent())
        val (result, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val gen = compilation.generatedFile("PlainRMappers.kt")
        assert(!gen.contains("run {")) { "Expected no run block in:\n$gen" }
        assert(!gen.contains("ValidationFailed")) { gen }
    }
}
```

- [ ] **Step 1: Run tests — expect FAIL**
  `./gradlew :processor:test --tests "*ValidateCodegenTest*" --console=plain -q > /tmp/t.log 2>&1; echo exit=$?`

### Task 4.2: Implement `wrapWithValidation` in `MappingCodeGenerator`

- [ ] **Step 2: Add `wrapWithValidation` and wire it into `generateFieldMapping`**

  At the end of `generateFieldMapping`, replace:
  ```kotlin
  return applyNullableHandling(sourceField, targetField, baseMapping)
  ```
  with:
  ```kotlin
  val nullableHandled = applyNullableHandling(sourceField, targetField, baseMapping)
  return wrapWithValidation(sourceField, targetField, nullableHandled)
  ```

  Add the new private method:

  ```kotlin
  private fun wrapWithValidation(
      sourceField: FieldInfo,
      targetField: FieldInfo,
      expr: CodeBlock
  ): CodeBlock {
      val fromValidators = sourceField.validateFrom
      val toValidators   = sourceField.validateTo
      if (fromValidators.isEmpty() && toValidators.isEmpty()) return expr

      val mappingExceptionClass = ClassName("com.sahsenvar.kmapper", "MappingException")
      val targetName = targetField.name
      val builder = CodeBlock.builder()
      builder.beginControlFlow("run")

      // ValidateFrom: validate source value before transform
      for (validatorFqn in fromValidators) {
          val validatorClass = ClassName.bestGuess(validatorFqn)
          if (sourceField.isNullable) {
              // src?.let { __s -> Validator.validate(__s)?.let { m -> throw ValidationFailed(target, m) } }
              builder.addStatement(
                  "%N?.let·{·__s·->·%T.validate(__s)?.let·{·m·->·throw·%T.ValidationFailed(%S,·m)·}·}",
                  sourceField.name, validatorClass, mappingExceptionClass, targetName
              )
          } else {
              // Validator.validate(src)?.let { throw ValidationFailed(target, it) }
              builder.addStatement(
                  "%T.validate(%N)?.let·{·throw·%T.ValidationFailed(%S,·it)·}",
                  validatorClass, sourceField.name, mappingExceptionClass, targetName
              )
          }
      }

      // Bind result
      builder.addStatement("val __result = %L", expr)

      // ValidateTo: validate final produced value
      for (validatorFqn in toValidators) {
          val validatorClass = ClassName.bestGuess(validatorFqn)
          if (targetField.isNullable) {
              // __result?.let { __r -> Validator.validate(__r)?.let { m -> throw ValidationFailed(target, m) } }
              builder.addStatement(
                  "__result?.let·{·__r·->·%T.validate(__r)?.let·{·m·->·throw·%T.ValidationFailed(%S,·m)·}·}",
                  validatorClass, mappingExceptionClass, targetName
              )
          } else {
              // Validator.validate(__result)?.let { throw ValidationFailed(target, it) }
              builder.addStatement(
                  "%T.validate(__result)?.let·{·throw·%T.ValidationFailed(%S,·it)·}",
                  validatorClass, mappingExceptionClass, targetName
              )
          }
      }

      builder.addStatement("__result")
      builder.endControlFlow()
      return builder.build()
  }
  ```

- [ ] **Step 3: Run compile tests — expect PASS**
  `./gradlew :processor:test --tests "*ValidateCodegenTest*" --console=plain -q > /tmp/t.log 2>&1; echo exit=$?`

### Task 4.3: Write runtime-execution tests (classload generated mapper + assert throws)

Create `processor/src/test/kotlin/com/sahsenvar/kmapper/processor/ValidateRuntimeTest.kt`:

```kotlin
package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.sahsenvar.kmapper.MappingException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ValidateRuntimeTest {

    private val validationSrc = SourceFile.kotlin("Val.kt", """
        import com.sahsenvar.kmapper.annotations.MapTo
        import com.sahsenvar.kmapper.annotations.ValidateFrom
        import com.sahsenvar.kmapper.annotations.ValidateTo
        import com.sahsenvar.kmapper.validation.builtin.NotBlankValidator

        data class PersonD(val name: String, val email: String)

        @MapTo(PersonD::class)
        data class PersonR(
            @ValidateFrom(NotBlankValidator::class) val name: String,
            @ValidateTo(NotBlankValidator::class) val email: String
        )
    """.trimIndent())

    @Test fun `valid values map without throwing`() {
        val (result, compilation) = compile(validationSrc)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val cl = result.classLoader
        val personRClass = cl.loadClass("PersonR")
        val mapFn = cl.loadClass("ValMappers").getDeclaredMethod("toPersonD", personRClass)
        // mapFn is an extension — invoke via static method (KotlinPoet extension functions on JVM)
        val instance = personRClass.getDeclaredConstructor(String::class.java, String::class.java)
            .newInstance("Alice", "alice@example.com")
        val domain = mapFn.invoke(null, instance)
        val nameField = domain!!.javaClass.getDeclaredField("name").also { it.isAccessible = true }
        assertEquals("Alice", nameField.get(domain))
    }

    @Test fun `blank name throws ValidationFailed from ValidateFrom`() {
        val (result, compilation) = compile(validationSrc)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val cl = result.classLoader
        val personRClass = cl.loadClass("PersonR")
        val mapFn = cl.loadClass("ValMappers").getDeclaredMethod("toPersonD", personRClass)
        val instance = personRClass.getDeclaredConstructor(String::class.java, String::class.java)
            .newInstance("   ", "alice@example.com")
        val ex = assertFailsWith<java.lang.reflect.InvocationTargetException> { mapFn.invoke(null, instance) }
        val cause = ex.cause
        assert(cause is MappingException.ValidationFailed) { "Expected ValidationFailed, got: $cause" }
        assertEquals("name", (cause as MappingException.ValidationFailed).field)
    }

    @Test fun `blank email throws ValidationFailed from ValidateTo`() {
        val (result, compilation) = compile(validationSrc)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val cl = result.classLoader
        val personRClass = cl.loadClass("PersonR")
        val mapFn = cl.loadClass("ValMappers").getDeclaredMethod("toPersonD", personRClass)
        val instance = personRClass.getDeclaredConstructor(String::class.java, String::class.java)
            .newInstance("Alice", "   ")
        val ex = assertFailsWith<java.lang.reflect.InvocationTargetException> { mapFn.invoke(null, instance) }
        val cause = ex.cause
        assert(cause is MappingException.ValidationFailed) { "Expected ValidationFailed, got: $cause" }
        assertEquals("email", (cause as MappingException.ValidationFailed).field)
    }

    @Test fun `nullable source with ValidateFrom skips null silently`() {
        val src = SourceFile.kotlin("Null.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            import com.sahsenvar.kmapper.annotations.ValidateFrom
            import com.sahsenvar.kmapper.annotations.MapDefaultValue
            import com.sahsenvar.kmapper.validation.builtin.NotBlankValidator

            data class D(val tag: String)
            @MapTo(D::class)
            data class R(@ValidateFrom(NotBlankValidator::class) @MapDefaultValue("\"default\"") val tag: String?)
        """.trimIndent())
        val (result, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val cl = result.classLoader
        val rClass = cl.loadClass("R")
        val mapFn = cl.loadClass("NullMappers").getDeclaredMethod("toD", rClass)
        // null tag → should use default "default", not throw
        val instance = rClass.getDeclaredConstructor(String::class.java).newInstance(null as String?)
        val domain = mapFn.invoke(null, instance)
        val tagField = domain!!.javaClass.getDeclaredField("tag").also { it.isAccessible = true }
        assertEquals("default", tagField.get(domain))
    }
}
```

- [ ] **Step 4: Run — expect PASS**
  `./gradlew :processor:test --tests "*ValidateRuntimeTest*" --console=plain -q > /tmp/t.log 2>&1; echo exit=$?`

- [ ] **Step 5: Run all processor tests (regression guard)**
  `./gradlew :processor:test --console=plain -q > /tmp/t.log 2>&1; echo exit=$?`

- [ ] **Step 6: Commit**
  `git commit -m "feat(processor): implement wrapWithValidation codegen for @ValidateFrom/@ValidateTo"`

---

## Task 5 — `:validators` add-on module

### Task 5.1: Create the module scaffold

- [ ] **Step 1: Add `:validators` to `settings.gradle.kts`**

  In `/Users/sahansenvar/StudioProjects/KMapper/settings.gradle.kts`, add:
  ```
  include(":validators")
  ```

- [ ] **Step 2: Create `validators/build.gradle.kts`**

  Clone `converters-datetime/build.gradle.kts`, strip out the ksp plugin and `jvmAndroid` source set. Key shape:
  ```kotlin
  plugins {
      alias(libs.plugins.kotlin.multiplatform)
      alias(libs.plugins.android.kotlin.multiplatform.library)
      alias(libs.plugins.vanniktech.publish)
  }

  kotlin {
      android {
          namespace = "com.sahsenvar.kmapper.validators"
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
          commonTest.dependencies {
              implementation(kotlin("test"))
              implementation(libs.kotest.assertions)
          }
      }
  }

  mavenPublishing {
      coordinates("io.github.sahsenvar", "kmapper-validators", version.toString())
      pom {
          name.set("KMapper-validators")
          description.set("Pre-built Validator<T> implementations for KMapper")
          // copy url/licenses/developers/scm from converters-datetime's pom block
      }
  }
  ```

  Read `converters-datetime/build.gradle.kts` for the exact `pom { }` block shape and copy it.

- [ ] **Step 3: Write failing tests**

  Create `validators/src/commonTest/kotlin/com/sahsenvar/kmapper/validators/ValidatorsTest.kt`:
  ```kotlin
  package com.sahsenvar.kmapper.validators
  import kotlin.test.Test
  import kotlin.test.assertNull
  import kotlin.test.assertNotNull
  import kotlin.test.assertEquals

  class ValidatorsTest {
      // EmailValidator
      @Test fun `EmailValidator - valid email`() = assertNull(EmailValidator.validate("user@example.com"))
      @Test fun `EmailValidator - missing at-sign`() = assertNotNull(EmailValidator.validate("userexample.com"))
      @Test fun `EmailValidator - missing domain`() = assertNotNull(EmailValidator.validate("user@"))
      @Test fun `EmailValidator - missing tld`() = assertNotNull(EmailValidator.validate("user@example"))
      @Test fun `EmailValidator - complex valid email`() = assertNull(EmailValidator.validate("user.name+tag@sub.example.co.uk"))
      @Test fun `EmailValidator - reason message`() {
          assertEquals("must be a valid email", EmailValidator.validate("bad"))
      }

      // UrlValidator
      @Test fun `UrlValidator - valid http url`() = assertNull(UrlValidator.validate("http://example.com"))
      @Test fun `UrlValidator - valid https url`() = assertNull(UrlValidator.validate("https://www.example.com/path?q=1"))
      @Test fun `UrlValidator - missing scheme`() = assertNotNull(UrlValidator.validate("example.com"))
      @Test fun `UrlValidator - ftp scheme invalid`() = assertNotNull(UrlValidator.validate("ftp://example.com"))
      @Test fun `UrlValidator - reason message`() {
          assertEquals("must be a valid URL", UrlValidator.validate("not-a-url"))
      }
  }
  ```

- [ ] **Step 4: Run — expect FAIL (module doesn't exist yet)**
  `./gradlew :validators:jvmTest --console=plain -q > /tmp/t.log 2>&1; echo exit=$?`

### Task 5.2: Implement `EmailValidator` and `UrlValidator`

- [ ] **Step 5: Create the source files**

  Create `validators/src/commonMain/kotlin/com/sahsenvar/kmapper/validators/EmailValidator.kt`:
  ```kotlin
  package com.sahsenvar.kmapper.validators

  import com.sahsenvar.kmapper.validation.Validator

  private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

  object EmailValidator : Validator<String>(String::class) {
      override fun validate(value: String): String? =
          if (EMAIL_REGEX.matches(value)) null else "must be a valid email"
  }
  ```

  Create `validators/src/commonMain/kotlin/com/sahsenvar/kmapper/validators/UrlValidator.kt`:
  ```kotlin
  package com.sahsenvar.kmapper.validators

  import com.sahsenvar.kmapper.validation.Validator

  private val URL_REGEX = Regex("^https?://[^\\s/\$.?#].[^\\s]*$")

  object UrlValidator : Validator<String>(String::class) {
      override fun validate(value: String): String? =
          if (URL_REGEX.matches(value)) null else "must be a valid URL"
  }
  ```

- [ ] **Step 6: Run — expect PASS**
  `./gradlew :validators:jvmTest --console=plain -q > /tmp/t.log 2>&1; echo exit=$?`

- [ ] **Step 7: Commit**
  `git commit -m "feat(validators): add EmailValidator and UrlValidator add-on module"`

---

## Task 6 — `Map<K,V>` strategy + analyzer + codegen + tests

### Task 6.1: Add `MappingStrategy.MapValues`

**File:** `processor/src/main/kotlin/com/sahsenvar/kmapper/processor/model/MappingStrategy.kt`

- [ ] **Step 1: Write the failing compile test**

  Create `processor/src/test/kotlin/com/sahsenvar/kmapper/processor/MapValuesCodegenTest.kt`:
  ```kotlin
  package com.sahsenvar.kmapper.processor

  import com.tschuchort.compiletesting.KotlinCompilation
  import com.tschuchort.compiletesting.SourceFile
  import kotlin.test.Test
  import kotlin.test.assertEquals

  class MapValuesCodegenTest {
      @Test fun `Map with nested value type emits mapValues`() {
          val src = SourceFile.kotlin("M.kt", """
              import com.sahsenvar.kmapper.annotations.MapTo

              data class ItemD(val name: String)
              @MapTo(ItemD::class)
              data class ItemR(val name: String)

              data class CatalogD(val items: Map<String, ItemD>)

              @MapTo(CatalogD::class)
              data class CatalogR(val items: Map<String, ItemR>)
          """.trimIndent())
          val (result, compilation) = compile(src)
          assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
          val gen = compilation.generatedFile("CatalogRMappers.kt")
          assert(gen.contains("mapValues")) { "Expected mapValues in:\n$gen" }
          assert(gen.contains("toItemD")) { "Expected toItemD call in:\n$gen" }
      }

      @Test fun `Map with same value type emits direct assignment`() {
          val src = SourceFile.kotlin("M2.kt", """
              import com.sahsenvar.kmapper.annotations.MapTo

              data class ConfigD(val props: Map<String, String>)
              @MapTo(ConfigD::class)
              data class ConfigR(val props: Map<String, String>)
          """.trimIndent())
          val (result, compilation) = compile(src)
          assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
          val gen = compilation.generatedFile("ConfigRMappers.kt")
          // Direct assignment — no mapValues
          assert(!gen.contains("mapValues")) { "Expected no mapValues for same-type map:\n$gen" }
      }

      @Test fun `nullable Map source emits safe-call mapValues`() {
          val src = SourceFile.kotlin("M3.kt", """
              import com.sahsenvar.kmapper.annotations.MapTo

              data class ItemD(val name: String)
              @MapTo(ItemD::class)
              data class ItemR(val name: String)

              data class NullCatD(val items: Map<String, ItemD>?)
              @MapTo(NullCatD::class)
              data class NullCatR(val items: Map<String, ItemR>?)
          """.trimIndent())
          val (result, compilation) = compile(src)
          assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
          val gen = compilation.generatedFile("NullCatRMappers.kt")
          assert(gen.contains("?.mapValues")) { "Expected ?.mapValues for nullable map:\n$gen" }
      }
  }
  ```

- [ ] **Step 2: Run — expect FAIL**
  `./gradlew :processor:test --tests "*MapValuesCodegenTest*" --console=plain -q > /tmp/t.log 2>&1; echo exit=$?`

### Task 6.2: Implement the three pieces

- [ ] **Step 3: Add `MappingStrategy.MapValues` to `MappingStrategy.kt`**

  After the `WrappedCollection` data class, add:
  ```kotlin
  /**
   * Map<K,V1> → Map<K,V2> mapping by transforming values; keys are directly assigned.
   * Emits: source.mapValues { (_, v) -> v.toV2() }    (non-null source, nested values)
   *        source?.mapValues { (_, v) -> v.toV2() }   (nullable source, nested values)
   *        source                                      (direct value — same type K, same type V)
   * Keys must be the same type on both sides. Different key types → Unmappable.
   * Plain kotlin.collections.Map only; PersistentMap/ImmutableMap wrappers are deferred.
   */
  data class MapValues(val valueStrategy: MappingStrategy) : MappingStrategy()
  ```

- [ ] **Step 4: Add `isMapType` + key/value extractors to `TypeMatcher.kt`**

  After `isCollectionType`:
  ```kotlin
  fun isMapType(type: KSType): Boolean {
      val fqn = type.declaration.qualifiedName?.asString() ?: return false
      return fqn == "kotlin.collections.Map" || fqn == "kotlin.collections.MutableMap"
  }

  fun extractMapKeyType(type: KSType): KSType? = type.arguments.getOrNull(0)?.type?.resolve()
  fun extractMapValueType(type: KSType): KSType? = type.arguments.getOrNull(1)?.type?.resolve()
  ```

  Add detection in `determineMappingStrategy` after the `WrappedCollection` block (step ~2b, before the plain `isCollectionType` check):
  ```kotlin
  // 2b. Map<K,V> detection — must come before data-class nested check
  if (isMapType(sourceField.type) && isMapType(targetField.type)) {
      val srcKey = extractMapKeyType(sourceField.type)
      val tgtKey = extractMapKeyType(targetField.type)
      val srcVal = extractMapValueType(sourceField.type)
      val tgtVal = extractMapValueType(targetField.type)
      if (srcKey != null && tgtKey != null && isSameType(srcKey, tgtKey)
          && srcVal != null && tgtVal != null) {
          val valStrategy = if (isSameType(srcVal, tgtVal)) {
              MappingStrategy.Direct
          } else if (isDataClass(srcVal) && isDataClass(tgtVal)) {
              MappingStrategy.Nested("to${tgtVal.declaration.simpleName.asString()}")
          } else {
              MappingStrategy.Direct
          }
          return MappingStrategy.MapValues(valStrategy)
      }
      // Key type mismatch → fall through to Unmappable
  }
  ```

- [ ] **Step 5: Add `generateMapValuesMapping` + wire it into `generateFieldMapping` in `MappingCodeGenerator.kt`**

  In the `when (strategy)` dispatch block, add:
  ```kotlin
  is MappingStrategy.MapValues -> generateMapValuesMapping(sourceField, strategy)
  ```

  New private method:
  ```kotlin
  private fun generateMapValuesMapping(
      sourceField: FieldInfo,
      strategy: MappingStrategy.MapValues
  ): CodeBlock = when (strategy.valueStrategy) {
      is MappingStrategy.Nested -> {
          val mapperFn = (strategy.valueStrategy as MappingStrategy.Nested).mapperFunctionName
          if (sourceField.isNullable)
              CodeBlock.of("%N?.mapValues·{·(_,·v)·->·v.%N()·}", sourceField.name, mapperFn)
          else
              CodeBlock.of("%N.mapValues·{·(_,·v)·->·v.%N()·}", sourceField.name, mapperFn)
      }
      else ->  // Direct (same value type)
          CodeBlock.of("%N", sourceField.name)
  }
  ```

  Also update the exhaustiveness `when` in `generateFieldMapping` to include `MappingStrategy.MapValues`:
  ```kotlin
  is MappingStrategy.MapValues -> generateMapValuesMapping(sourceField, strategy)
  ```

- [ ] **Step 6: Run MapValues tests — expect PASS**
  `./gradlew :processor:test --tests "*MapValuesCodegenTest*" --console=plain -q > /tmp/t.log 2>&1; echo exit=$?`

- [ ] **Step 7: Run all processor tests**
  `./gradlew :processor:test --console=plain -q > /tmp/t.log 2>&1; echo exit=$?`

- [ ] **Step 8: Commit**
  `git commit -m "feat(processor): add Map<K,V> value mapping via MappingStrategy.MapValues"`

---

## Task 7 — Integration tests (JVM + iOS)

**Files:** add to `integration-test/src/commonMain/kotlin/com/sahsenvar/kmapper/itest/` and `integration-test/src/commonTest/kotlin/com/sahsenvar/kmapper/itest/`

### Task 7.1: Add integration-test model declarations and config

- [ ] **Step 1: Create `ValidationSample.kt` in `commonMain`**

  `integration-test/src/commonMain/kotlin/com/sahsenvar/kmapper/itest/ValidationSample.kt`:
  ```kotlin
  package com.sahsenvar.kmapper.itest

  import com.sahsenvar.kmapper.annotations.KMapperConfig
  import com.sahsenvar.kmapper.annotations.MapTo
  import com.sahsenvar.kmapper.annotations.ValidateFrom
  import com.sahsenvar.kmapper.annotations.ValidateTo
  import com.sahsenvar.kmapper.validation.builtin.NotBlankValidator

  data class ContactD(val name: String, val tag: String?)

  @MapTo(ContactD::class)
  data class ContactR(
      @ValidateFrom(NotBlankValidator::class) val name: String,
      @ValidateTo(NotBlankValidator::class) val tag: String?
  )
  ```

  Also add `MapSample.kt` in `commonMain`:
  ```kotlin
  package com.sahsenvar.kmapper.itest

  import com.sahsenvar.kmapper.annotations.MapTo

  data class AttrD(val value: String)
  @MapTo(AttrD::class)
  data class AttrR(val value: String)

  data class CatalogD(val attrs: Map<String, AttrD>)
  @MapTo(CatalogD::class)
  data class CatalogR(val attrs: Map<String, AttrR>)

  data class PropMapD(val props: Map<String, String>)
  @MapTo(PropMapD::class)
  data class PropMapR(val props: Map<String, String>)
  ```

- [ ] **Step 2: Update `integration-test/build.gradle.kts` — add `:validators` dependency**

  In `commonMain.dependencies`:
  ```kotlin
  implementation(project(":validators"))
  ```

### Task 7.2: Write the integration tests (commonTest — JVM + iOS)

- [ ] **Step 3: Create `ValidationIntegrationTest.kt`**

  `integration-test/src/commonTest/kotlin/com/sahsenvar/kmapper/itest/ValidationIntegrationTest.kt`:
  ```kotlin
  package com.sahsenvar.kmapper.itest

  import com.sahsenvar.kmapper.MappingException
  import io.kotest.matchers.shouldBe
  import kotlin.test.Test
  import kotlin.test.assertFailsWith

  class ValidationIntegrationTest {
      @Test fun `valid ContactR maps without throwing`() {
          val d = ContactR(name = "Alice", tag = "engineer").toContactD()
          d.name shouldBe "Alice"
          d.tag shouldBe "engineer"
      }

      @Test fun `blank name throws ValidationFailed (ValidateFrom)`() {
          assertFailsWith<MappingException.ValidationFailed> {
              ContactR(name = "   ", tag = null).toContactD()
          }.field shouldBe "name"
      }

      @Test fun `null tag bypasses ValidateTo silently`() {
          // tag is nullable — null should pass through without calling NotBlankValidator
          val d = ContactR(name = "Bob", tag = null).toContactD()
          d.tag shouldBe null
      }

      @Test fun `blank tag throws ValidationFailed (ValidateTo)`() {
          assertFailsWith<MappingException.ValidationFailed> {
              ContactR(name = "Bob", tag = "   ").toContactD()
          }.field shouldBe "tag"
      }
  }
  ```

  Create `MapValuesIntegrationTest.kt`:
  ```kotlin
  package com.sahsenvar.kmapper.itest

  import io.kotest.matchers.shouldBe
  import kotlin.test.Test

  class MapValuesIntegrationTest {
      @Test fun `nested value map is transformed correctly`() {
          val r = CatalogR(attrs = mapOf("k1" to AttrR("v1"), "k2" to AttrR("v2")))
          val d = r.toCatalogD()
          d.attrs["k1"]!!.value shouldBe "v1"
          d.attrs["k2"]!!.value shouldBe "v2"
          d.attrs.size shouldBe 2
      }

      @Test fun `direct value map passes through unchanged`() {
          val r = PropMapR(props = mapOf("a" to "1", "b" to "2"))
          val d = r.toPropMapD()
          d.props["a"] shouldBe "1"
          d.props["b"] shouldBe "2"
      }
  }
  ```

- [ ] **Step 4: Run on JVM — expect PASS**
  `./gradlew :integration-test:jvmTest --console=plain -q > /tmp/it.log 2>&1; echo exit=$?`
  On failure: `grep -nE '^e: |error:|> Task .* FAILED|FAILURE:' /tmp/it.log | head -30`

- [ ] **Step 5: Run on iOS Simulator — expect PASS**
  `./gradlew :integration-test:iosSimulatorArm64Test --console=plain -q > /tmp/it-ios.log 2>&1; echo exit=$?`
  On failure: `grep -nE 'error:|FAILED|FAILURE:' /tmp/it-ios.log | head -30`

- [ ] **Step 6: Commit**
  `git commit -m "test(integration-test): add validation + Map<K,V> end-to-end tests (JVM + iOS)"`

---

## Task 8 — Docs note

- [ ] **Step 1: Verify all modules build cleanly**
  `./gradlew :core:jvmTest :processor:test :validators:jvmTest :integration-test:jvmTest --console=plain -q > /tmp/final.log 2>&1; echo exit=$?`

- [ ] **Step 2: Add a one-line note to `docs/guide-en/reference/limitations.md`**

  Append to the limitations file: a note that `PersistentMap`/`ImmutableMap` wrappers are out of scope for Map<K,V> mapping (R2 = plain stdlib maps only; wrapper support deferred to R3 pending a `wrapMap(Map<K,V>)` protocol on `@CollectionWrapper`).

- [ ] **Step 3: Final commit**
  `git commit -m "docs: note Map<K,V> scope limitation (plain stdlib maps only in R2)"`

---

## Self-review checklist

- [x] All task steps use `- [ ]` checkbox syntax.
- [x] No placeholder validator FQNs — all FQNs are fully resolved (e.g. `com.sahsenvar.kmapper.validation.builtin.NotBlankValidator`).
- [x] Build commands use `ctx_execute` instruction stated in intro; no Bash tool for builds.
- [x] `FieldInfo` new fields have `= emptyList()` defaults — zero impact on existing construction sites.
- [x] `wrapWithValidation` is called after `applyNullableHandling` in `generateFieldMapping` — ordering preserved.
- [x] `MappingStrategy.MapValues` added to `when` exhaustiveness in `generateFieldMapping`.
- [x] `ValidateFrom`/`ValidateTo` annotations have `@Target(PROPERTY)` and `@Retention(SOURCE)` — consistent with `@UseMapTypeConverter`, `@MapDefaultValue`.
- [x] `ValidateFrom` annotation `vararg val validators: KClass<*>` — consistent with spec (vararg not repeatable).
- [x] Validator discovery contrast vs `@KMapperConfig` documented in spec section 5.1.
- [x] No version bump, no publish step.
- [x] Integration tests cover JVM + iOS (iosSimulatorArm64Test).
- [x] `validators` module uses `api(project(":core"))` so consumers get `Validator<T>` transitively.
- [x] Task count: 8 tasks, 28 sub-steps with failing-test→implement→pass→commit TDD structure.
