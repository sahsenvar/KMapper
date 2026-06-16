# Serializable-enum mapping — design

**Goal:** let an enum participate in mapping via its **kotlinx.serialization `@SerialName`**
annotations as an *alternative* to implementing `MappableEnum<W>`, so projects that already
use kotlinx-serialization don't have to declare wire values twice.

## Decisions (locked)

1. **Both mechanisms supported.** `MappableEnum` stays; `@Serializable` is an additional,
   independent way to opt an enum into mapping. Not everyone uses kotlinx-serialization.
2. **`MappableEnum` wins.** If an enum has *both* `MappableEnum` and `@Serializable`, the
   `MappableEnum.wireValue` path is used (silently — no warning; documented).
3. **No runtime kotlinx-serialization dependency.** The processor reads `@Serializable` /
   `@SerialName` **by FQN string** from the KSP model (like the optional arrow `Option`
   support). Nothing is added to `kmapper-core` / `kmapper-compiler` runtime deps.

## Wire-value rule (matches kotlinx-serialization's own JSON behavior)

For a `@Serializable` enum, each entry's wire value is:
- its `@SerialName("…")` argument if present, **else** the entry's declared name.

Wire type is therefore **always `String`** (kotlinx-serialization serializes enums as their
serial-name strings; there is no `Int`-coded form). If the other side of the mapping is not
`String`, that's the existing `enum wire type mismatch` compile error (`expected kotlin.String`).

## Resolution precedence (TypeMatcher.determineEnumStrategy)

For an enum on either side:
1. Implements `MappableEnum<W>` → existing `EnumFromWire(enumFqn)` / `EnumToWire`
   (reads `it.wireValue`). **Wins over @Serializable.**
2. Else annotated `@kotlinx.serialization.Serializable` → new
   `SerializableEnumFromWire` / `SerializableEnumToWire` (String wire, `when`-based).
3. Else → compile error: *"enum '…' must implement MappableEnum<…>, be annotated
   @Serializable, or use @ConvertWith"*.

## Codegen — compile-time `when`, no runtime serializer

The processor resolves the full `entry → wireValue` list at compile time and carries it in the
strategy. Generated code is a plain `when` over **string literals** (R8-safe, fast, no
serializer lookup):

- **SerializableEnumToWire** (enum → String) — total, cannot fail, no seam:
  ```kotlin
  // non-null:  source mapped through:
  when (source) { Status.ACTIVE -> "active"; Status.BANNED -> "banned" }
  // nullable:  source?.let { when (it) { Status.ACTIVE -> "active"; … } }
  ```
- **SerializableEnumFromWire** (String → enum) — rides the SAME conversion seam as
  `EnumFromWire`, so an unknown wire value follows the fallback ladder (nullable/defaulted
  target → null/default + degradation report; hard otherwise). The convert lambda:
  ```kotlin
  { wire -> when (wire) {
      "active" -> Status.ACTIVE
      "banned" -> Status.BANNED
      else -> throw MappingException.UnknownEnumValue("", "Status", wire)
  } }
  ```
  (empty path; the seam prefixes the target field name — identical to the MappableEnum lambda.)

Both new strategies are also wired into the **collection element** path
(`elementConvertLambda`), so `List<String> → List<SerializableEnum>` etc. work like the
MappableEnum ones.

## Edge cases

- **Duplicate serial names** (two entries resolve to the same wire string) → **compile error**
  (`@Serializable enum '…' has duplicate wire value "…" on entries X and Y`). Without this the
  generated `when` would have a duplicate `fromWire` branch anyway; we catch it with a clear
  message first.
- **`@SerialName` on the enum class itself** (vs. entries) sets the *type's* serial name and is
  irrelevant to per-entry wire values — only **entry-level** `@SerialName` is read.
- **Empty enum** (no entries) — degenerate; the `when` over `wire` is just `else -> throw`,
  toWire `when` is exhaustive vacuously. Not special-cased.

## Test plan (kctfork BehaviorSpec, golden + runtime — per CLAUDE.md)

- golden: `@Serializable` enum with mixed `@SerialName`/bare entries emits the expected `when`
  (both directions); wire literals come from `@SerialName` else entry name.
- runtime: round-trip a value; an unknown wire value → `UnknownEnumValue` (hard target) /
  null + report (nullable target).
- precedence: enum with BOTH `MappableEnum` and `@Serializable` uses `wireValue` (golden shows
  `it.wireValue`, not the `when`).
- mismatch: `@Serializable` enum mapped to `Int` → `expected kotlin.String` error.
- duplicate serial names → compile error.
- collection: `List<String> → List<SerializableEnum>` rides the element seam.
- not-mappable enum (neither MappableEnum nor @Serializable) → updated error message mentions
  @Serializable.

## Touch points

- `processor/.../model/MappingStrategy.kt` — add `SerializableEnumFromWire(enumFqn, entries)` +
  `SerializableEnumToWire(enumFqn, entries)` (entries = `List<Pair<entrySimpleName, wireValue>>`).
- `processor/.../analyzer/TypeMatcher.kt` — `determineEnumStrategy` precedence; new
  `resolveSerializableEnumEntries(enumDecl)`; the never-null warn-list (`EnumFromWire` analog).
- `processor/.../generator/MappingCodeGenerator.kt` — `generateSerializableEnumFromWireMapping`
  (seam + `when` lambda) / `generateSerializableEnumToWireMapping` (`when`), and the two
  `elementConvertLambda` branches.
- docs: enum page (en+tr), AGENTS.md sharp-edges, CHANGELOG; a sample in the gallery.
