# MappableEnum — Safe Enum Mapping

Mapping enums by `ordinal` or `name` is a silent trap. If you reorder constants, `ordinal` changes; if you rename a constant, `name` changes. In neither case do you get a compile error — and without tests you may not even get a runtime error; the wrong value is silently mapped.

kmap eliminates this risk entirely: `ordinal` and `name` are **never** used.

## The `MappableEnum<W>` Interface

This interface, found in the `com.sahsenvar.kmapper` package, ties each enum constant to a **wire value**:

```kotlin
interface MappableEnum<W : Any> {
    val wireValue: W
}
```

The `W` type parameter must match the type of the field on the wire side (`String` and `Int` are the most common choices).

### String Wire Value

```kotlin
enum class OrderStatus(override val wireValue: String) : MappableEnum<String> {
    PENDING("PENDING"),
    SHIPPED("in_transit"),   // constant name and wire value can differ
    DELIVERED("DELIVERED"),
}
```

### Int Wire Value

```kotlin
enum class Priority(override val wireValue: Int) : MappableEnum<Int> {
    LOW(10),
    MEDIUM(20),
    HIGH(30),
}
```

## Generated Code

When the processor encounters an enum that implements `MappableEnum<W>`, it generates a forward extension:

```kotlin
// Forward: wire value → enum constant
fun String.toOrderStatus(): OrderStatus =
    OrderStatus.entries.firstOrNull { it.wireValue == this }
        ?: throw MappingException.UnknownEnumValue("OrderStatus", this)
```

**Reverse direction (enum → wire):** The processor does not generate a separate `toWire()` function. The enum-to-wire-value conversion is inlined at the call site directly as `status.wireValue` (or `status?.wireValue` for a nullable field).

## Unknown Wire Value

If the wire source sends a value that is not present in the enum definition, `MappingException.UnknownEnumValue` is thrown. Convert this exception to your own domain error in the feature layer:

```kotlin
fun Throwable.toOrderError(): OrderError = when (this) {
    is MappingException.UnknownEnumValue -> OrderError.InvalidStatus(value.toString())
    // other branches...
    else -> OrderError.Unknown(message, this)
}
```

For details, see [Error Handling](../error-handling/mapping-exception.md).

## Requirement — Compile Error

If a field has an enum type and that enum neither implements `MappableEnum` nor is overridden with `@UseMapTypeConverter`, the processor emits a **compile error**:

```
enum 'PaymentStatus' must implement MappableEnum<...> or use @UseMapTypeConverter
```

This guarantee makes it impossible to leave an enum unmapped.

## Third-Party / Unmodifiable Enums — Escape Hatch

For an enum you do not own (one that comes from a dependency), you cannot add `MappableEnum` to it. In that case, define a per-field converter using `@UseMapTypeConverter`:

```kotlin
// Converter: ThirdPartyStatus from an external library → your own StatusDomain
object ThirdPartyStatusConverter : MapTypeConverter<ThirdPartyStatus, StatusDomain>(ThirdPartyStatus::class, StatusDomain::class) {
    override fun convertToNonNull(value: ThirdPartyStatus): StatusDomain = when (value) {
        ThirdPartyStatus.ACTIVE   -> StatusDomain.ACTIVE
        ThirdPartyStatus.INACTIVE -> StatusDomain.INACTIVE
    }
    override fun convertFromNonNull(value: StatusDomain): ThirdPartyStatus = when (value) {
        StatusDomain.ACTIVE   -> ThirdPartyStatus.ACTIVE
        StatusDomain.INACTIVE -> ThirdPartyStatus.INACTIVE
    }
}

@MapTo(OrderDomain::class)
data class OrderRemote(
    @UseMapTypeConverter(ThirdPartyStatusConverter::class)
    val status: ThirdPartyStatus,
) : RemoteModel
```

## Nullable Enum Fields

Nullable enum fields get null-propagating generation — null is preserved in both the forward and reverse directions:

```kotlin
@MapTo(OrderDomain::class)
data class OrderRemote(
    val status: String?,   // nullable wire value
) : RemoteModel

data class OrderDomain(val status: OrderStatus?)

// generated:
fun OrderRemote.toOrderDomain(): OrderDomain = OrderDomain(
    status = status?.toOrderStatus(),
)
```

## Duplicate `wireValue` Clash — Warning

If two constants share the same `wireValue`, the forward direction (`firstOrNull`) picks the first one in the list; the second becomes **silently unreachable**:

```kotlin
// WRONG — clash
enum class Color(override val wireValue: String) : MappableEnum<String> {
    RED("red"),
    CRIMSON("red"),   // same wire value — CRIMSON is never selected
}
```

Because KSP cannot reliably read the runtime values of constructor arguments, this situation cannot be caught at compile time. Clash detection (`verifyEnums`) is on the roadmap but has not been implemented yet. Make sure every constant in your enum definitions has a unique `wireValue`.

---

Next: [MappingException — Error Handling](../error-handling/mapping-exception.md)
