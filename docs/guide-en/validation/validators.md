# The Validator Library

Two tiers, one split rule: **structural, dependency-free checks live in core**
(`com.sahsenvar.kmapper.validation.builtin`, no extra artifact); **opinionated format
knowledge lives in the `kmapper-validators` add-on** (`com.sahsenvar.kmapper.validators`).

## Core built-ins — ready objects

| Validator | Type | Rejects |
|-----------|------|---------|
| `NotBlankValidator` | `String` | blank/whitespace-only |
| `NotEmptyStringValidator` | `String` | `""` |
| `NotEmptyCollectionValidator` | `Collection<*>` | empty collections |
| `PositiveIntValidator` / `PositiveLongValidator` / `PositiveDoubleValidator` | numeric | `<= 0` (and `NaN`) |
| `NonNegativeIntValidator` / `NonNegativeLongValidator` / `NonNegativeDoubleValidator` | numeric | `< 0` (and `NaN`) |
| `FiniteDoubleValidator` | `Double` | `NaN`, `±Infinity` |

## Core built-ins — parameterized bases

Open classes with constructor parameters; subclass as an `object` with **your** bounds (the
same recipe as [parameterized
converters](../type-conversion/custom-converter.md#parameterized-converters)):

| Base | Rule |
|------|------|
| `RegexValidator(pattern, reason)` | value must match the whole pattern |
| `StringLengthValidator(minLength, maxLength)` | length in range |
| `IntRangeValidator(range)` / `LongRangeValidator(range)` | value in range |
| `DoubleRangeValidator(min, max)` | value in range (NaN always rejected) |
| `CollectionSizeValidator(minSize, maxSize)` | size in range |

```kotlin
object UsernameLengthValidator : StringLengthValidator(minLength = 3, maxLength = 20)
object QuantityValidator : IntRangeValidator(1..999)
object SkuValidator : RegexValidator(Regex("[A-Z]{3}-\\d{4}"), "must be a SKU like ABC-1234")

data class Product(
    @Validate(SkuValidator::class) val sku: String,
    @Validate(QuantityValidator::class) val quantity: Int,
)
```

Misconfigured bounds (`min > max`, negative length) fail **at construction** — a broken
validator can't sit silently in a model.

## The kmapper-validators add-on

```kotlin
commonMain.dependencies {
    implementation("io.github.sahsenvar:kmapper-validators:2.2.1")
}
```

All operate on `String` unless noted:

| Validator | Accepts |
|-----------|---------|
| `EmailValidator` | pragmatic RFC-style emails |
| `UrlValidator` | `http(s)://…` URLs |
| `PhoneE164Validator` | `+905551112233` — canonical E.164, no separators |
| `Ipv4Validator` | strict dotted decimal, no leading zeros |
| `Ipv6Validator` | full/`::`-compressed groups, embedded IPv4 tail |
| `HostnameValidator` | RFC 1123 hostnames |
| `UuidStringValidator` | canonical 8-4-4-4-12 UUID, either case |
| `SlugValidator` | `lower-case-hyphen-slugs` |
| `Base64Validator` | standard or URL-safe alphabet, padded or unpadded |
| `HexStringValidator` | even-length hex |
| `LatitudeValidator` (`Double`) | `-90.0..90.0` |
| `LongitudeValidator` (`Double`) | `-180.0..180.0` |
| `PortNumberValidator` (`Int`) | `1..65535` |
| `CreditCardNumberValidator` | 12-19 digits passing the Luhn check (spaces/dashes tolerated) |

Validate-vs-convert rule of thumb: if the *domain type* should be richer (a real `Uuid`, a
real `ByteString`), use a [converter](../type-conversion/built-in.md) — conversion already
rejects malformed input. Use a validator when the field **stays a string/number** but must
hold a particular shape.

> Next: **[MappableEnum →](../enum/mappable-enum.md)**
