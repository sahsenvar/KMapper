# FAQ

## Why does the generated function throw instead of returning `Result`?

Because a plain, throwing function is the shape every Kotlin caller already knows, and nested
mappings compose more simply when they don't have to unwrap a `Result` at every level — a
nested mapper just calls the inner `toX()` and lets a failure propagate. If you want failure
as part of the signature — `Result`, `Flow`, or your own wrapper type — that's one annotation
away and never a replacement for the plain function: see
[Return Wrappers](../basic-usage/return-wrappers.md) and
[Error Handling](../error-handling/mapping-exception.md).

## I upgraded to 3.0.0 and `toXResult()` is gone. What do I do?

Pick one: switch call sites to `dto.toUser()` (+ `runCatching { }` where you still want a
`Result` locally), or keep the old shape everywhere with one module-wide setting —
`KMapper { wrapper = KMapperWrapper.KtResult }` (Gradle plugin) or
`ksp { arg("kmapper.wrapper", "KtResult") }`. See
[Return Wrappers → Migrating from 2.x](../basic-usage/return-wrappers.md#migrating-from-2x).

## Why didn't my broken value become an error?

Probably the target field is nullable or has a default — a declared escape on the
[fallback ladder](../basic-usage/null-safety.md). The absorption was reported to the
[degradation sink](../observability/listener.md); register a listener and you'll see it.
Want hardness for that field? `@ConvertWith(onFail = OnFail.Throw)`.

## Why is there no `@MapDefaultValue`?

Kotlin already has default values — in the constructor. KMapper uses them by *omitting the
argument*, so the default lives in exactly one place and behaves identically for mapping and
hand construction. If a default should *not* act as a wire fallback, that's
[`@IgnoreDefaultValue`](../basic-usage/field-mapping.md).

## Why does `Long → Int` fail my build? Other mappers just convert it.

They convert it *until the value doesn't fit*, then silently give you a wrong number. KMapper
[refuses lossy directions at compile time](../type-conversion/built-in.md#refused-directions-are-a-feature)
with a reasoned message; if your domain guarantees the range, a three-line custom converter
makes that guarantee explicit and owned.

## Why doesn't KMapper map enums by name automatically?

`name`/`ordinal` mapping breaks silently on rename/reorder — the exact failure class KMapper
exists to eliminate. [`MappableEnum`](../enum/mappable-enum.md) costs one `wireValue` per
constant and is rename-proof.

## My `@ConvertWith` annotation seems ignored. Why?

Field directives are read from the **source side of the generated direction** — with
`@MapTo` on the wire model, annotate the wire field, not the domain field.
[The placement rule](../type-conversion/convert-with.md#the-placement-rule-worth-memorizing).

## Can I use KMapper without code generation?

Yes — `kmapper-core` is a standalone artifact. Hand-written mappers use the same public
seams, ladder semantics, converters, and error types as generated code (that's the
[parity principle](../getting-started/mental-model.md#the-parity-principle)). See the
`CoreOnlyMapping` [example](../getting-started/examples.md).

## How do I see what was generated?

`build/generated/ksp/<target>/kotlin/…` — plain Kotlin, breakpointable.
[Architecture](../advanced/architecture.md).

## Does it work with R8/ProGuard?

Yes. No reflection, and error paths are compile-time string literals — your release-build
stack traces still say `customer.address.zipCode`.

## Where are complete runnable examples?

The [sample gallery](../getting-started/examples.md): 25 files, every feature, basic →
advanced, each with documented output.
