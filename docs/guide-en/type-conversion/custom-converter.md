# Writing a Custom Converter

When you need to convert a type pair that is not in the built-in table, extend the `MapTypeConverter<S, T>` abstract class to write your own converter.

---

## The MapTypeConverter Interface

```kotlin
abstract class MapTypeConverter<S : Any, T : Any>(
    val sourceType: KClass<S>,
    val targetType: KClass<T>,
) {
    abstract fun convertToNonNull(value: S): T
    abstract fun convertFromNonNull(value: T): S

    // Null-safe helpers (no need to override):
    fun convertTo(value: S?): T? = value?.let { convertToNonNull(it) }
    fun convertFrom(value: T?): S? = value?.let { convertFromNonNull(it) }
}
```

A single converter object covers **both directions**:

- `convertToNonNull(S): T` → `S → T` conversion (forward)
- `convertFromNonNull(T): S` → `T → S` conversion (reverse)
- `convertTo` / `convertFrom` → null-safe wrappers; you do not need to re-implement them

The processor analyzes which direction is needed and calls the right method. You only need to implement both once.

---

## Example: UUID ↔ String

```kotlin
import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuidFrom
import com.sahsenvar.kmapper.converter.MapTypeConverter

object UuidStringConverter : MapTypeConverter<Uuid, String>(Uuid::class, String::class) {
    override fun convertToNonNull(value: Uuid): String = value.toString()
    override fun convertFromNonNull(value: String): Uuid = uuidFrom(value)
}
```

---

## Example: Enum Wire Value ↔ Domain Enum

```kotlin
enum class StatusRemote { ACTIVE, INACTIVE, UNKNOWN }
enum class StatusDomain { Active, Inactive }

object StatusConverter : MapTypeConverter<StatusRemote, StatusDomain>(
    StatusRemote::class, StatusDomain::class
) {
    override fun convertToNonNull(value: StatusRemote): StatusDomain = when (value) {
        StatusRemote.ACTIVE   -> StatusDomain.Active
        StatusRemote.INACTIVE -> StatusDomain.Inactive
        StatusRemote.UNKNOWN  -> throw IllegalArgumentException("Unknown status: $value")
    }

    override fun convertFromNonNull(value: StatusDomain): StatusRemote = when (value) {
        StatusDomain.Active   -> StatusRemote.ACTIVE
        StatusDomain.Inactive -> StatusRemote.INACTIVE
    }
}
```

> KMapper also provides the `MappableEnum<W>` interface for enum mapping. For details, see [MappableEnum](../enum/mappable-enum.md).

---

## Error Handling

Any exception thrown inside `convertToNonNull` or `convertFromNonNull` is wrapped by the generated code into `MappingException.TypeConversionFailed` (via the `convertOrFail` mechanism):

```kotlin
// Generated wrapping:
field = convertOrFail("Uuid", "String") { UuidStringConverter.convertToNonNull(rawId) }
```

If any `Throwable` is thrown during conversion, the caller always receives `MappingException.TypeConversionFailed`. The original exception is available in the `cause` field.

---

## Registering the Converter

You must add your converter to the `@KMapperConfig` list; otherwise the processor cannot see it and will report a compile error:

```kotlin
@KMapperConfig(converters = [UuidStringConverter::class, StatusConverter::class])
object AppMapperConfig
```

If you need to use a different converter than the one in the global list for a specific field, you can apply `@UseMapTypeConverter` to that field — see [@KMapperConfig and @UseMapTypeConverter](kmapperconfig.md).

---

Next: [@KMapperConfig and @UseMapTypeConverter](kmapperconfig.md)
