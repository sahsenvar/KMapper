# Architecture: How It Works

A look under the hood — useful for debugging builds, reviewing generated code, and trusting
what runs in production.

## The pipeline

```
your annotated models
   └─ KSP2 (kmapper-compiler)
        ├─ analyze: match fields, resolve converters/wrappers, check directives
        ├─ refuse:  MissingConverter / UnsupportedConversion / structural errors -> build fails
        └─ generate: toX() extension function (plain Kotlin, KotlinPoet), plus one more
              extension per wrap() overload of the resolved wrapper (toXResult(), toXFlow(), …)
              └─ compiled like hand-written code; calls kmapper-core seams at runtime
```

Everything type-related is decided **at compile time**: which converter handles each field,
which ladder rung each escape provides, which validators fire. There is no runtime registry
lookup on the hot path and no reflection anywhere.

## What generated code looks like

```kotlin
public fun UserResponse.toUser(): User {
    if (KMapper.hasListeners) KMapper.dispatch { onMapStart(this@toUser, User::class) }
    val result = User(
        id = id,
        joined = joined.convertOrFail("joined", "kotlin.String", "kotlinx.datetime.LocalDate") {
            LocalDateStringConverter.convertFrom(it)
        },
    )
    if (KMapper.hasListeners) KMapper.dispatch { onMapComplete(this@toUser, result) }
    return result
}

// only generated when a wrapper resolves to KtResult (see @MapTo(wrapper = …) / module setting):
public fun UserResponse.toUserResult(): Result<User> = KMapperWrapper.KtResult.wrap(this) { it.toUser() }
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
- **`toUser()` itself never catches anything** — it throws straight through. A wrapper
  extension calls the plain function and applies its own `wrap`, exactly as a hand-written
  caller would; see [Return Wrappers](../basic-usage/return-wrappers.md).

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
