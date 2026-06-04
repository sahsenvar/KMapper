# Built-in Converters

kmap supports the most common type conversions out of the box. These converters live in the `com.sahsenvar.kmapper.converter.builtin` package and are recognized by the processor automatically — no `@KMapperConfig` registration is needed.

---

## Built-in Converter Table

| Converter | `S → T` (forward) | `T → S` (reverse) |
|-----------|-------------------|-------------------|
| `StringIntConverter` | `String → Int` (`toInt()`) | `Int → String` (`toString()`) |
| `StringLongConverter` | `String → Long` (`toLong()`) | `Long → String` |
| `StringDoubleConverter` | `String → Double` (`toDouble()`) | `Double → String` |
| `StringFloatConverter` | `String → Float` (`toFloat()`) | `Float → String` |
| `StringBooleanConverter` | `String → Boolean` (`toBoolean()`) | `Boolean → String` |
| `IntLongConverter` | `Int → Long` | `Long → Int` ¹ |
| `StringInstantConverter` | `String → Instant` (ISO-8601) | `Instant → String` |
| `LongInstantConverter` | `Long → Instant` (epoch ms) | `Instant → Long` |

¹ The reverse direction (`Long → Int`) throws `TypeConversionFailed` if the value is outside the `Int` range.

`Instant` refers to `kotlinx.datetime.Instant` (requires the `kotlinx-datetime` dependency).

---

## The Bilateral Converter Concept

Each converter object handles **both directions** in a single class:

```kotlin
object StringIntConverter : MapTypeConverter<String, Int>(String::class, Int::class) {
    override fun convertToNonNull(value: String): Int = value.toInt()
    override fun convertFromNonNull(value: Int): String = value.toString()
}
```

The processor analyzes which direction is needed and calls the correct method. For a `String → Int` mapping it uses `convertToNonNull`; for `Int → String` it uses `convertFromNonNull`.

---

## The convertOrFail Wrapper

All converter calls — including built-in ones — are wrapped with `convertOrFail` in the generated code. This prevents raw platform exceptions (e.g. `NumberFormatException`) from leaking out:

```kotlin
// What the generated code looks like:
count = convertOrFail("String", "Int") { StringIntConverter.convertToNonNull(count) }
```

> **Note:** The type names passed to `convertOrFail` in the generated code are fully qualified — e.g. `"kotlin.String"`, `"kotlin.Int"`, `"kotlinx.datetime.Instant"`. Short forms are used in the examples here.

If conversion fails, `MappingException.TypeConversionFailed` is thrown; the original exception is available in the `cause` field.

---

## Usage Example

```kotlin
@MapTo(ProductDomain::class)
data class ProductRemote(
    val id: String,
    val price: String,     // comes from the API as String, needs Double in domain
    val stock: String,     // comes from the API as String, needs Int in domain
)

data class ProductDomain(
    val id: String,
    val price: Double,
    val stock: Int,
)
```

`String → Double` and `String → Int` are both built-in, so no extra registration is needed. Generated:

```kotlin
public fun ProductRemote.toProductDomain(): ProductDomain = ProductDomain(
    id    = id,
    price = convertOrFail("String", "Double") { StringDoubleConverter.convertToNonNull(price) },
    stock = convertOrFail("String", "Int")    { StringIntConverter.convertToNonNull(stock) },
)
```

---

## Unregistered Type Pair → Compile Error

If a type pair is not in the built-in table and has not been added to `@KMapperConfig`, the processor reports a **compile error**:

```
no converter for MyCustomType -> MyTargetType; add it to @KMapperConfig(converters=[...]) or annotate the field with @UseMapTypeConverter
```

To write your own converter, see [Writing a Custom Converter](custom-converter.md).

---

Next: [Writing a Custom Converter](custom-converter.md)
