# Big Numbers — converters-bignumber

Converters for arbitrary-precision numbers:
[ionspin kotlin-multiplatform-bignum](https://github.com/ionspin/kotlin-multiplatform-bignum)
(`commonMain`, all targets) and `java.math` (JVM/Android).

## Setup

```kotlin
commonMain.dependencies {
    implementation("io.github.sahsenvar:kmapper-converters-bignumber:2.0.1")
}
```

```kotlin
@KMapperConfig(converters = [StringBigDecimalConverter::class])
object AppMapperConfig
```

## Converters (`com.sahsenvar.kmapper.bignumber`)

ionspin (KMP):

| Object | Pair | Notes |
|--------|------|-------|
| `StringBigDecimalConverter` | `String ↔ BigDecimal` | the recommended money/precision wire format |
| `StringBigIntegerConverter` | `String ↔ BigInteger` | |
| `DoubleBigDecimalConverter` | `Double → BigDecimal` | reverse direction **refused** (see below) |
| `LongBigIntegerConverter` | `Long → BigInteger` | reverse **refused** |
| `IntBigIntegerConverter` | `Int → BigInteger` | reverse **refused** |
| `BigIntegerBigDecimalConverter` | `BigInteger → BigDecimal` | reverse **refused** |

`java.math` (JVM/Android): `StringJavaBigDecimalConverter` (plain string, never scientific
notation), `StringJavaBigIntegerConverter`, `DoubleJavaBigDecimalConverter`,
`LongJavaBigIntegerConverter`, `JavaBigIntegerBigDecimalConverter` — same pairs, same refusal
policy.

## Why some directions are refused

`BigDecimal → Double` loses precision; `BigInteger → Long`/`Int` overflows; `BigDecimal →
BigInteger` drops the fraction. Earlier versions truncated these **silently** — exactly the
"plausible wrong value" failure mode KMapper exists to kill. They are now
[`@UnsupportedDirection`](custom-converter.md#refusing-a-direction): a mapping that needs one
fails **at compile time** with the reason and the options.

If your domain genuinely guarantees the range, own that decision in a three-line custom
converter:

```kotlin
object CentsBigIntegerConverter : MapTypeConverter<Long, BigInteger>(Long::class, BigInteger::class) {
    override fun convertTo(source: Long): BigInteger = BigInteger.fromLong(source)
    override fun convertFrom(target: BigInteger): Long = target.longValue(exactRequired = true) // throws on overflow
}
```

The string pairs round-trip exactly — for money on the wire, prefer
`String ↔ BigDecimal` over anything passing through `Double`.

> Next: **[UUID →](uuid.md)**
