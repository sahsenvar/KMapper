# FAQ

## Does kmap use reflection at runtime?

No. kmap runs entirely at compile time. The KSP processor analyzes annotations and generates plain Kotlin extension functions. The generated code does not use reflection APIs such as `KClass`, `::class.members`, or `getDeclaredField`. For this reason kmap works without any restrictions on all KMP targets, including iOS/Kotlin Native.

## Does it work on iOS and Kotlin/Native?

Yes. The `core` artifact is KMP; the generated extension functions are standard Kotlin and compile for all targets (Android, iOS/Native, JVM). `processor` is JVM-only but is executed only by the build toolchain; it is not included in the distributed code.

## Why aren't `ordinal` or `name` used?

`ordinal` depends on the order of constants. When the enum order changes or a new constant is inserted in the middle, `ordinal` silently maps to the wrong value — impossible to notice without a compile error or a test. `name` is equally fragile against renaming. `wireValue` is bound directly to the constant; you can reorder the enum or rename the constant and the mapping does not change.

## What should I do if the source and target field names are different?

Use `@FieldMap(fieldName = "targetFieldName")`:

```kotlin
@MapTo(UserDomain::class)
data class UserRemote(
    @FieldMap(fieldName = "id")
    val userId: String,
) : RemoteModel
```

If there are multiple targets, add the `targetClass` parameter:

```kotlin
@FieldMap(fieldName = "id",     targetClass = UserDomain::class)
@FieldMap(fieldName = "userId", targetClass = UserUiModel::class)
val userId: String,
```

See [Field Mapping](../basic-usage/field-mapping.md).

## How do I add a custom converter?

Extend `MapTypeConverter<S, T>` and add it to `@KMapperConfig`:

```kotlin
object IsoStringToInstantConverter : MapTypeConverter<String, Instant>(String::class, Instant::class) {
    override fun convertToNonNull(value: String): Instant =
        Instant.parse(value)
    override fun convertFromNonNull(value: Instant): String =
        value.toString()
}

@KMapperConfig(converters = [IsoStringToInstantConverter::class])
object AppMapperConfig
```

If a specific field needs a different conversion, use `@UseMapTypeConverter` for a per-field override. See [@KMapperConfig and @UseMapTypeConverter](../type-conversion/kmapperconfig.md).

## Does every module in a multi-module project need its own `@KMapperConfig`?

Yes. KSP compiles each module independently; one module's `@KMapperConfig` is not visible to another module's processor. Every module that generates mappers must define its own `@KMapperConfig` object.

See [Multi-Module Projects](../advanced/multi-module.md).

## Are marker interfaces (RemoteModel, DomainModel, etc.) required?

No. You can apply the `@MapTo` or `@MapFrom` annotation to any class; you do not need to implement a specific marker interface. Marker interfaces are a useful convention for categorizing classes by layer in large projects; they are not enforced by kmap.

## What happens to the target constructor if I skip a field with `@Ignore`?

The corresponding field in the target class constructor must either be absent or have a default value. If there is no default value and the field is present in the target, you will get a compile error — the generated code skips that field, so the target constructor call fails with a missing argument.

```kotlin
data class UserDomain(
    val id: String,
    val role: String = "USER",  // has a default value → id can be mapped with @Ignore
)
```

## What is `convertOrFail`?

`convertOrFail` is a helper function defined in the `core` artifact. It wraps a converter call in `try/catch` and converts any exception the converter throws into `MappingException.TypeConversionFailed`. You do not need to call it directly; it is used by the generated code. See [Error Handling](../error-handling/mapping-exception.md).
