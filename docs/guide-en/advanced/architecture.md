# Architecture: How It Works

A look under the hood — useful for debugging builds, reviewing generated code, and trusting
what runs in production.

## The pipeline

```
your annotated models
   └─ KSP2 (kmapper-compiler)
        ├─ analyze: match fields, resolve converters/wrappers, check directives
        ├─ refuse:  MissingConverter / UnsupportedConversion / structural errors -> build fails
        └─ generate: toXResult() extension functions (plain Kotlin, KotlinPoet)
              └─ compiled like hand-written code; calls kmapper-core seams at runtime
```

Everything type-related is decided **at compile time**: which converter handles each field,
which ladder rung each escape provides, which validators fire. There is no runtime registry
lookup on the hot path and no reflection anywhere.

## What generated code looks like

```kotlin
public fun UserResponse.toUserResult(): Result<User> = runCatching {
    if (KMapper.hasListeners) KMapper.dispatch { onMapStart(this@toUserResult, User::class) }
    val result = User(
        id = id,
        joined = joined.convertOrFail("joined", "kotlin.String", "kotlinx.datetime.LocalDate") {
            LocalDateStringConverter.convertFrom(it)
        },
    )
    if (KMapper.hasListeners) KMapper.dispatch { onMapComplete(this@toUserResult, result) }
    result
}
```

Worth noticing:

- **Converters are called as objects** (`LocalDateStringConverter.convertFrom(...)`), never
  inlined as ad-hoc casts — user and built-in converters run on identical rails.
- **Seams** (`convertOrFail`, `convertOrNull`, `convertEachOrSkip`, …) are public
  `kmapper-core` functions implementing the [ladder](../basic-usage/null-safety.md) — the
  same functions available to [hand-written
  mappers](../getting-started/examples.md).
- **Paths are string literals** — R8/ProGuard-safe error messages.
- The observability hooks vanish behind a single `hasListeners` check when unused.

## Inspecting generated code

```
build/generated/ksp/<target>/kotlin/…
```

Generated files are ordinary Kotlin — readable, debuggable, breakpointable. When mapping
behavior surprises you, read the generated function first; it usually answers the question.

## Design invariants the generator enforces

- A field either maps cleanly, or the build names the problem — no silent skips.
- Lossy conversions don't exist unless *you* wrote the converter
  ([refusal policy](../type-conversion/built-in.md#refused-directions-are-a-feature)).
- Every absorbed error has a sink event; every hard error has a path.
- `CancellationException` is always rethrown — mappings never swallow coroutine
  cancellation.

> Next: **[Annotation Reference →](../reference/annotations.md)**
