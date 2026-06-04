# Architecture — Modules and the KSP Pipeline

This page explains kmap's internal design at a conceptual level. You do not need to know these details to use the library, but understanding how it works makes troubleshooting and contributing easier.

## Module Split

kmap consists of four separate artifacts:

```
com.sahsenvar.kmapper
├── core              (KMP)
│   ├── Annotations: @MapTo, @MapFrom, @FieldMap, @MapDefaultValue,
│   │                @UseMapTypeConverter, @Ignore, @KMapperConfig, @CollectionWrapper
│   ├── MappableEnum<W>, MappingException (sealed)
│   ├── MapTypeConverter (abstract), TypeConverterRegistry (expect/actual)
│   ├── Built-in primitive converters (str↔int/long/double/float/bool, int↔long, …)
│   └── KMapper, MappingListener, LoggingMappingListener
│
├── processor         (JVM-only, KSP)
│   └── MappingProcessor + FieldAnalyzer → TypeMatcher → MappingCodeGenerator pipeline
│
├── converters-immutable (KMP, optional)
│   └── List/Set → PersistentList/ImmutableList/ImmutableSet wrappers
│       (kotlinx.collections.immutable dependency lives here only)
│
└── converters-arrow  (KMP, optional, empty slot in this release)
    └── Reserved for Nel converters (next round)
```

**Design decisions:**

- Annotations and the runtime are kept together in a single `core` artifact (the MapStruct approach). Separating them later is mechanical; for now, YAGNI.
- `kotlinx.collections.immutable` has been moved out of `core` — it lives only in `converters-immutable`. Projects that use only `core` do not pull in this dependency.
- `processor` is JVM-only: KSP runs only on the JVM. The generated code is KMP.

## KSP Pipeline — Compile Time

kmap never uses runtime reflection. All mapping code is generated during compilation:

```
Source class annotated with @MapTo
        ↓
  FieldAnalyzer
  • Inspects constructor val fields
  • Determines the strategy for each field:
    direct / type-conversion / nested / collection / wrapped-collection / enum
        ↓
  TypeMatcher
  • Resolves the @KMapperConfig converter list
  • Checks built-in primitive conversions
  • Missing converter → compile error
        ↓
  Validator
  • Cycle detection (unconditional cycle → compile error)
  • Enum MappableEnum check
  • W type compatibility check
        ↓
  MappingCodeGenerator (KotlinPoet)
  • Generates the {Source}Mappers.kt extension file
  • Writes null-checks (RequiredFieldMissing)
  • Wraps converter calls with TypeConversionFailed
  • Adds listener guard blocks
        ↓
  build/generated/ksp/…/{Source}Mappers.kt
```

The processor generates one `.kt` file per `@MapTo`/`@MapFrom`-annotated class it analyzes. The files compile as ordinary Kotlin source; there is no reflection.

## Why No Reflection?

The core benefit of the KSP approach is full compile-time safety:

- Missing converter → compile error, not a runtime surprise.
- Type mismatch → compile error.
- Circular dependency → compile error.
- Works on all KMP targets including iOS/Native (no reflection restrictions).

## Platform Compatibility

Because `core` is KMP, the generated extension functions compile for Android, iOS (Kotlin/Native), and JVM targets. `processor` is JVM-only but is executed only by the build toolchain; it is not included in the distributed code.

`TypeConverterRegistry` uses the `expect/actual` mechanism to get a platform-specific implementation per target; the outward-facing API is identical across platforms.

---

Next: [Annotation Reference](../reference/annotations.md)
