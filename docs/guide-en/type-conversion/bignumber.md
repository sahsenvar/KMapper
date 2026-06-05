# Big Number Converters — converters-bignumber

The `converters-bignumber` module provides **scalar converters** for mapping between `BigDecimal`/`BigInteger` types and `String`, `Double`, `Long`, or `Int`. You must list the converters you need in `@KMapperConfig(converters = [...])` — they are not auto-discovered.

---

## Setup

```kotlin
// build.gradle.kts (consuming module)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.sahsenvar:kmapper-core:1.0.0")
            implementation("io.github.sahsenvar:kmapper-converters-bignumber:1.0.0")
        }
    }
}
```

---

## Platform Support

| Converter group | Source set | Runs on |
|-----------------|------------|---------|
| ionspin converters | `commonMain` | All platforms (JVM, Android, iOS, JS, WASM) |
| java.math converters | `jvmAndroidMain` | JVM and Android only |

---

## ionspin Converters (commonMain)

Uses `com.ionspin.kotlin.bignum` — available on all platforms.

| Converter | Source | Target |
|-----------|--------|--------|
| `StringBigDecimalConverter` | `String` | `com.ionspin.kotlin.bignum.decimal.BigDecimal` |
| `StringBigIntegerConverter` | `String` | `com.ionspin.kotlin.bignum.integer.BigInteger` |
| `DoubleBigDecimalConverter` | `Double` | `com.ionspin.kotlin.bignum.decimal.BigDecimal` |
| `LongBigIntegerConverter` | `Long` | `com.ionspin.kotlin.bignum.integer.BigInteger` |
| `IntBigIntegerConverter` | `Int` | `com.ionspin.kotlin.bignum.integer.BigInteger` |
| `BigIntegerBigDecimalConverter` | `BigInteger` (ionspin) | `com.ionspin.kotlin.bignum.decimal.BigDecimal` |

> **Note:** `StringBigDecimalConverter.convertFromNonNull` uses expanded notation (`toStringExpanded()`) — it does not produce scientific notation such as `1.23E+5`.

---

## java.math Converters (jvmAndroidMain)

Uses `java.math` — available on JVM and Android only. Names use `Java` prefix or contain `Java` to distinguish them from their ionspin counterparts.

| Converter | Source | Target |
|-----------|--------|--------|
| `StringJavaBigDecimalConverter` | `String` | `java.math.BigDecimal` |
| `StringJavaBigIntegerConverter` | `String` | `java.math.BigInteger` |
| `DoubleJavaBigDecimalConverter` | `Double` | `java.math.BigDecimal` |
| `LongJavaBigIntegerConverter` | `Long` | `java.math.BigInteger` |
| `JavaBigIntegerBigDecimalConverter` | `java.math.BigInteger` | `java.math.BigDecimal` |

---

## Usage

List the converters in `@KMapperConfig(converters = [...])`:

```kotlin
import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.bignumber.StringBigDecimalConverter
import com.sahsenvar.kmapper.bignumber.StringBigIntegerConverter

@KMapperConfig(converters = [StringBigDecimalConverter::class, StringBigIntegerConverter::class])
object MyMappers
```

Then use those types in your models:

```kotlin
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger

@MapTo(InvoiceDomain::class)
data class InvoiceRemote(
    val id: String,
    val totalAmount: String,    // "12345.67"
    val quantity: String,       // "9999999999999999"
)

data class InvoiceDomain(
    val id: String,
    val totalAmount: BigDecimal,
    val quantity: BigInteger,
)
```

Generated mapping:

```kotlin
public fun InvoiceRemote.toInvoiceDomain(): InvoiceDomain = InvoiceDomain(
    id          = id,
    totalAmount = StringBigDecimalConverter.convertToNonNull(totalAmount),
    quantity    = StringBigIntegerConverter.convertToNonNull(quantity),
)
```

---

## Which Converter Should You Use?

| Model type | Recommended converters |
|------------|------------------------|
| `com.ionspin.kotlin.bignum.decimal.BigDecimal` | `StringBigDecimalConverter`, `DoubleBigDecimalConverter` |
| `com.ionspin.kotlin.bignum.integer.BigInteger` | `StringBigIntegerConverter`, `LongBigIntegerConverter`, `IntBigIntegerConverter` |
| `java.math.BigDecimal` (JVM/Android only) | `StringJavaBigDecimalConverter`, `DoubleJavaBigDecimalConverter` |
| `java.math.BigInteger` (JVM/Android only) | `StringJavaBigIntegerConverter`, `LongJavaBigIntegerConverter` |

If your project targets iOS or other non-JVM platforms, use only the ionspin converters.

---

Next: [@KMapperConfig and @UseMapTypeConverter →](kmapperconfig.md) | See also: [Writing a Custom Converter](custom-converter.md)
