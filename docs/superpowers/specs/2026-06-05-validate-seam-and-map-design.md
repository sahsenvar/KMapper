# Design Spec: @Validate Seam + Map<K,V> Mapping
> **Date:** 2026-06-05  
> **Status:** Approved for implementation  
> **Version:** 0.2.0-SNAPSHOT (no version bump)

---

## 1. Scope

Three additive features, no breaking changes:

| # | Feature | Module(s) touched |
|---|---------|-------------------|
| 1 | `@ValidateFrom` / `@ValidateTo` field-level validation seam | `core`, `processor` |
| 2 | `validators` add-on module | new `:validators` module |
| 3 | `Map<K,V>` value mapping | `processor` |

---

## 2. Feature 1 — @Validate Seam

### 2.1 New annotations (`core`, package `com.sahsenvar.kmapper.annotations`)

```kotlin
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ValidateFrom(vararg val validators: KClass<*>)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ValidateTo(vararg val validators: KClass<*>)
```

Both annotations:
- Declared on the **source** property (same convention as `@UseMapTypeConverter`, `@MapDefaultValue`).
- Use `vararg` — list multiple validators in one annotation, not repeatable.
- `@Target(PROPERTY)`, `@Retention(SOURCE)` — exact same shape as existing field annotations.

### 2.2 `Validator<T>` base class (`core`, new package `com.sahsenvar.kmapper.validation`)

```kotlin
abstract class Validator<T : Any>(val targetType: KClass<T>) {
    /**
     * Returns null if [value] is valid; returns a human-readable reason string if invalid.
     * Receives a NON-NULL value — null handling is owned by the existing nullability machinery.
     */
    abstract fun validate(value: T): String?
}
```

Design decisions:
- **Non-null contract**: validators never receive null. Null handling on source/target fields is already handled by `applyNullableHandling`; validation wraps around it.
- **Single type param**: intentionally mirrors `MapTypeConverter<S,T>` (bilateral) but simpler (one type). No `abstract fun validateFrom/validateTo` split — one `validate` method; placement (`@ValidateFrom` vs `@ValidateTo`) determines when it fires.
- **Object singletons**: validators MUST be `object` declarations. The processor emits direct calls like `NotBlankValidator.validate(x)` by FQN — no reflection, no factory, fully KMP-safe.

### 2.3 Built-in validators (`core`, package `com.sahsenvar.kmapper.validation.builtin`)

| Object | Type param | Invalid condition | Message |
|--------|-----------|-------------------|---------|
| `NotBlankValidator` | `String` | `value.isBlank()` | `"must not be blank"` |
| `NotEmptyStringValidator` | `String` | `value.isEmpty()` | `"must not be empty"` |
| `NotEmptyCollectionValidator` | `Collection<*>` | `value.isEmpty()` | `"must not be empty"` |

All declared in `core/src/commonMain/kotlin/com/sahsenvar/kmapper/validation/builtin/`.

### 2.4 `MappingException.ValidationFailed` (`core`, file `MappingException.kt`)

New subclass added to the existing `sealed class MappingException`:

```kotlin
class ValidationFailed(val field: String, val reason: String)
    : MappingException("Validation failed for '$field': $reason")
```

`field` carries the **target** field name (consistent with `RequiredFieldMissing`). `reason` is the string returned by `Validator.validate(value)`.

### 2.5 `FieldInfo` changes (`processor/model/FieldInfo.kt`)

Add two new fields:

```kotlin
data class FieldInfo(
    // ... existing fields unchanged ...
    val validateFrom: List<String> = emptyList(),  // validator object FQNs, ValidateFrom
    val validateTo: List<String> = emptyList(),    // validator object FQNs, ValidateTo
)
```

Default `emptyList()` ensures backward compatibility with all existing construction sites.

### 2.6 `FieldAnalyzer` changes (`processor/analyzer/FieldAnalyzer.kt`)

Two new `extractXxx` private methods, added alongside `extractUseConverter`:

```kotlin
private fun extractValidateFrom(annotated: KSAnnotated): List<String> =
    extractValidatorFqns(annotated, "ValidateFrom", "com.sahsenvar.kmapper.annotations.ValidateFrom")

private fun extractValidateTo(annotated: KSAnnotated): List<String> =
    extractValidatorFqns(annotated, "ValidateTo", "com.sahsenvar.kmapper.annotations.ValidateTo")

private fun extractValidatorFqns(annotated: KSAnnotated, shortName: String, fqn: String): List<String> {
    val annotation = annotated.annotations.firstOrNull {
        it.shortName.asString() == shortName ||
            it.annotationType.resolve().declaration.qualifiedName?.asString() == fqn
    } ?: return emptyList()
    // vararg validators: Array<KClass<*>> — annotation.arguments[0].value is List<KSType>
    @Suppress("UNCHECKED_CAST")
    val validators = annotation.arguments.firstOrNull()?.value as? List<KSType> ?: return emptyList()
    return validators.mapNotNull { it.declaration.qualifiedName?.asString() }
}
```

Called in `analyzeConstructor` for both `param` and `property` (same merge pattern as `extractUseConverter`):

```kotlin
val validateFrom = extractValidateFrom(param).ifEmpty { property?.let { extractValidateFrom(it) } ?: emptyList() }
val validateTo   = extractValidateTo(param).ifEmpty  { property?.let { extractValidateTo(it)   } ?: emptyList() }
```

### 2.7 Code generation — `wrapWithValidation` (`processor/generator/MappingCodeGenerator.kt`)

A new private method called at the **end** of `generateFieldMapping`, after `applyNullableHandling`:

```kotlin
fun generateFieldMapping(...): CodeBlock {
    // ... existing strategy dispatch + applyNullableHandling ...
    val nullableHandled = applyNullableHandling(sourceField, targetField, baseMapping)
    return wrapWithValidation(sourceField, targetField, nullableHandled)
}
```

#### Emission semantics (exact KotlinPoet output)

When `validateFrom` and `validateTo` are both empty → return `expr` unchanged (zero-cost).

Otherwise emit:

```kotlin
run {
    // ValidateFrom checks — each on the source field value, before transform
    // non-null source:
    NotBlankValidator.validate(<srcName>)?.let { throw MappingException.ValidationFailed("<targetName>", it) }
    // nullable source:
    <srcName>?.let { __s -> NotBlankValidator.validate(__s)?.let { m -> throw MappingException.ValidationFailed("<targetName>", m) } }

    val __result = <expr>   // the fully null-handled expression produced by applyNullableHandling

    // ValidateTo checks — each on __result
    // non-null result (target field is non-null):
    EmailValidator.validate(__result)?.let { throw MappingException.ValidationFailed("<targetName>", it) }
    // nullable result (target field is nullable):
    __result?.let { __r -> EmailValidator.validate(__r)?.let { m -> throw MappingException.ValidationFailed("<targetName>", m) } }

    __result
}
```

Rules:
- Multiple validators → sequential `.let` chains (fail-fast: first failing validator throws).
- ValidateFrom fires first, then `val __result = <expr>`, then ValidateTo.
- If only `validateFrom` is present: validate source, then `val __result = <expr>`, then yield `__result`.
- If only `validateTo` is present: `val __result = <expr>`, then validate, then yield `__result`.
- The target field name (not source) appears in `ValidationFailed` for both `@ValidateFrom` and `@ValidateTo`.
- Whether to use the nullable form for ValidateFrom is determined by `sourceField.isNullable`.
- Whether to use the nullable form for ValidateTo is determined by `targetField.isNullable`.

#### No `@KMapperConfig` registration

Validators are referenced by `KClass<*>` **directly on the source property** (resolved in the CONSUMER's own KSP compilation run, exactly like `@UseMapTypeConverter`). This is in contrast to `@KMapperConfig(converters=[...])` / `@KMapperConfig(wrappers=[...])`, which require explicit listing because their discovery was broken by KSP2's cross-module isolation. Validators do not suffer from this because the consumer's `@ValidateFrom(MyValidator::class)` is in-module — the FQN is read from the annotation argument at compile time, just like `extractUseConverter` reads the converter FQN. No `@KMapperConfig` changes needed.

#### Type mismatch safety

If a consumer annotates a `String` field with a `Validator<Int>`, the emitted `IntRangeValidator.validate(stringValue)` will fail to compile. This is intentional: type safety is enforced at compile time by Kotlin's type system, not by the processor.

---

## 3. Feature 2 — `validators` Add-on Module

### 3.1 Module coordinates

| Property | Value |
|----------|-------|
| Gradle module | `:validators` |
| Maven coordinates | `io.github.sahsenvar:kmapper-validators` |
| Kotlin package | `com.sahsenvar.kmapper.validators` |
| Android namespace | `com.sahsenvar.kmapper.validators` |

### 3.2 `build.gradle.kts` shape

Clone `:converters-datetime/build.gradle.kts` minus the KSP plugin and `jvmAndroid` source set — this module is **pure commonMain** (no platform-specific code needed). Key deltas:

- `api(project(":core"))` — not `implementation`, so consumers of `:validators` get `Validator<T>` transitively.
- No `ksp` plugin, no `kspCommonMainMetadata` dependency.
- `mavenPublishing { }` block with updated coordinates: `io.github.sahsenvar:kmapper-validators`, name `"KMapper-validators"`, description `"Pre-built Validator<T> implementations for KMapper"`.

### 3.3 Validators provided

All in `com.sahsenvar.kmapper.validators`, all `object` singletons:

| Object | Validates | Invalid condition | Message |
|--------|-----------|-------------------|---------|
| `EmailValidator` | `String` | does not match `^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$` | `"must be a valid email"` |
| `UrlValidator` | `String` | does not match `^https?://[^\s/$.?#].[^\s]*$` | `"must be a valid URL"` |

### 3.4 Extension story for custom validators

Consumers subclass `Validator<T>` from `:core` as an `object` and reference it directly. No module dependency on `:validators` is required for custom validators. Example:

```kotlin
object MinAgeValidator : Validator<Int>(Int::class) {
    override fun validate(value: Int): String? =
        if (value < 18) "must be at least 18" else null
}

@MapTo(UserDomain::class)
data class UserRemote(
    @ValidateTo(MinAgeValidator::class) val age: Int,
    @ValidateFrom(NotBlankValidator::class) @ValidateTo(EmailValidator::class) val email: String,
)
```

---

## 4. Feature 3 — Map<K,V> Core Mapping (R2 Plain Maps)

### 4.1 Scope

Plain `kotlin.collections.Map<K, V1>` (source) → `kotlin.collections.Map<K, V2>` (target) where:
- Key type `K` is the same directly-assignable type on both sides.
- Value type `V1 → V2` is either directly-assignable (same type) OR nested-mappable (`V1` has `toV2()` generated by KMapper).

**Explicitly deferred**: `PersistentMap`, `ImmutableMap`, and other non-stdlib map types. Collection wrappers use a `wrap(List<T>)` signature that cannot accept a `Map` — a new `wrapMap(Map<K,V>)` protocol would be needed. Deferred to a future R3.

### 4.2 New `MappingStrategy.MapValues` (`processor/model/MappingStrategy.kt`)

```kotlin
/**
 * Map<K,V1> → Map<K,V2> mapping by transforming values.
 * Keys are directly assigned (same type K on both sides).
 * @param valueStrategy how to map each value: Direct (same type) or Nested (toV2() call)
 */
data class MapValues(val valueStrategy: MappingStrategy) : MappingStrategy()
```

### 4.3 `TypeMatcher` changes (`processor/analyzer/TypeMatcher.kt`)

Add `isMapType` and `extractMapKeyType`/`extractMapValueType` helpers:

```kotlin
fun isMapType(type: KSType): Boolean {
    val fqn = type.declaration.qualifiedName?.asString() ?: return false
    return fqn == "kotlin.collections.Map" || fqn == "kotlin.collections.MutableMap"
}

fun extractMapKeyType(type: KSType): KSType? = type.arguments.getOrNull(0)?.type?.resolve()
fun extractMapValueType(type: KSType): KSType? = type.arguments.getOrNull(1)?.type?.resolve()
```

Detection added in `determineMappingStrategy`, placed **after** collection detection (step 2), **before** data class nested detection (step 3):

```kotlin
// 2b. Check Map<K,V> types
if (isMapType(sourceField.type) && isMapType(targetField.type)) {
    val srcKeyType  = extractMapKeyType(sourceField.type)
    val tgtKeyType  = extractMapKeyType(targetField.type)
    val srcValType  = extractMapValueType(sourceField.type)
    val tgtValType  = extractMapValueType(targetField.type)
    if (srcKeyType != null && tgtKeyType != null && isSameType(srcKeyType, tgtKeyType)
        && srcValType != null && tgtValType != null) {
        val valueStrategy = if (isSameType(srcValType, tgtValType)) {
            MappingStrategy.Direct
        } else if (isDataClass(srcValType) && isDataClass(tgtValType)) {
            MappingStrategy.Nested("to${tgtValType.declaration.simpleName.asString()}")
        } else {
            MappingStrategy.Direct  // fallback; may emit a type error at Kotlin compile time
        }
        return MappingStrategy.MapValues(valueStrategy)
    }
}
```

Key type mismatch (K1 ≠ K2) → falls through to `Unmappable` (correct — we cannot rekey a map without a converter).

### 4.4 Code generation (`processor/generator/MappingCodeGenerator.kt`)

New branch in the `when (strategy)` dispatch inside `generateFieldMapping`, mirroring `Collection`:

```kotlin
is MappingStrategy.MapValues -> generateMapValuesMapping(sourceField, targetField, strategy)
```

New private method:

```kotlin
private fun generateMapValuesMapping(
    sourceField: FieldInfo,
    @Suppress("UNUSED_PARAMETER") targetField: FieldInfo,
    strategy: MappingStrategy.MapValues
): CodeBlock = when (strategy.valueStrategy) {
    is MappingStrategy.Nested -> {
        val mapperFn = (strategy.valueStrategy as MappingStrategy.Nested).mapperFunctionName
        if (sourceField.isNullable)
            CodeBlock.of("%N?.mapValues·{·(_,·v)·->·v.%N()·}", sourceField.name, mapperFn)
        else
            CodeBlock.of("%N.mapValues·{·(_,·v)·->·v.%N()·}", sourceField.name, mapperFn)
    }
    else ->  // Direct (same value type) — passthrough
        CodeBlock.of("%N", sourceField.name)
}
```

`applyNullableHandling` is already called after `generateFieldMapping` returns the base expression, so nullable source→required target wraps correctly without extra logic.

---

## 5. Cross-Cutting Concerns

### 5.1 Validator vs Converter — discovery contrast

| Mechanism | Discovery | Requires `@KMapperConfig` listing? |
|-----------|-----------|-------------------------------------|
| `@KMapperConfig(converters=[...])` | cross-module, consumer-side listing required (KSP2 isolation) | YES |
| `@KMapperConfig(wrappers=[...])` | cross-module, consumer-side listing required (KSP2 isolation) | YES |
| `@ValidateFrom/To(SomeValidator::class)` | in-module annotation argument (resolved in consumer's KSP run) | NO |

Validators follow the same in-module path as `@UseMapTypeConverter`. The processor reads the `KClass<*>` argument FQN directly from the annotation on the source property — no enumeration of dependency packages is needed.

### 5.2 KMP / iOS Safety

- `Validator<T>` is `commonMain` Kotlin with no platform-specific APIs.
- Built-in validators use only `kotlin.String.isBlank()`/`.isEmpty()` and `kotlin.collections.Collection.isEmpty()` — all KMP-safe.
- `EmailValidator` / `UrlValidator` use `Regex` — KMP-safe (commonMain).
- Generated validation code uses only stdlib: `?.let`, `throw`, `run {}` — no JVM-specific classes.

### 5.3 Version

Remains at `0.2.0-SNAPSHOT`. No publish step in this plan.
