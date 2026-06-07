---
name: Bug report
about: Report a reproducible bug in KMapper
labels: bug
---

## Description

<!-- A clear and concise description of what the bug is. -->

## Minimal Reproduction

<!-- Please provide the smallest possible code that reproduces the issue. -->

**Annotations & models:**

```kotlin
// Source model
@MapTo(TargetModel::class)
data class SourceModel(
    // ...
) : RemoteModel

// Target model
data class TargetModel(
    // ...
) : DomainModel
```

**Generated code (if applicable):**

```kotlin
// Paste the generated toTargetModel() extension from build/generated/ksp/…
```

**How to trigger the bug:**

```kotlin
val source = SourceModel(/* ... */)
val result = source.toTargetModel() // describe what happens here
```

## Expected Behaviour

<!-- What you expected to happen. -->

## Actual Behaviour

<!-- What actually happened. Include the full exception/stacktrace if applicable. -->

## Environment

| | |
|---|---|
| KMapper version | `1.x.x` |
| Kotlin version | `2.x.x` |
| KSP version | `2.x.x` |
| Target platform(s) | JVM / Android / iOS / all |
| Gradle version | `x.x` |
| OS | macOS / Linux / Windows |

## Additional Context

<!-- Any other context, links, or screenshots. -->
