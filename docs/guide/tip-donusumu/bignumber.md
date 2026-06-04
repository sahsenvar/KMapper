# Büyük Sayı Converter'ları — converters-bignumber

`converters-bignumber` modülü, `BigDecimal` ve `BigInteger` tiplerini `String`/`Double`/`Long`/`Int` ile eşlemek için **scalar converter'lar** sağlar. `@KMapperConfig(converters = [...])` listesine eklenmeleri gerekir — otomatik keşfedilmezler.

> **Not:** `converters-bignumber` sürüm **0.2.0** ile gelir; henüz Maven Central'da değildir.
> Yayınlanana kadar `publishToMavenLocal` + `mavenLocal()` ile kullanın.
> `core` ve `processor` hâlâ Maven Central'dan `0.1.0` olarak çekilebilir.

---

## Kurulum

```kotlin
// settings.gradle.kts — pre-release için mavenLocal ekle
dependencyResolutionManagement {
    repositories {
        mavenLocal()        // 0.2.0 add-on'lar için
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts (tüketen modül)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.sahsenvar:kmapper-core:0.1.0")
            implementation("io.github.sahsenvar:kmapper-converters-bignumber:0.2.0")
        }
    }
}
```

---

## Platform Desteği

| Converter grubu | Modül kaynağı | Çalışır |
|-----------------|--------------|---------|
| ionspin converter'ları | `commonMain` | Tüm platformlar (JVM, Android, iOS, JS, WASM) |
| java.math converter'ları | `jvmAndroidMain` | Yalnızca JVM ve Android |

---

## ionspin Converter'ları (commonMain)

`com.ionspin.kotlin.bignum` kütüphanesi — tüm platformlarda kullanılabilir.

| Converter | Kaynak | Hedef |
|-----------|--------|-------|
| `StringBigDecimalConverter` | `String` | `com.ionspin.kotlin.bignum.decimal.BigDecimal` |
| `StringBigIntegerConverter` | `String` | `com.ionspin.kotlin.bignum.integer.BigInteger` |
| `DoubleBigDecimalConverter` | `Double` | `com.ionspin.kotlin.bignum.decimal.BigDecimal` |
| `LongBigIntegerConverter` | `Long` | `com.ionspin.kotlin.bignum.integer.BigInteger` |
| `IntBigIntegerConverter` | `Int` | `com.ionspin.kotlin.bignum.integer.BigInteger` |
| `BigIntegerBigDecimalConverter` | `BigInteger` (ionspin) | `com.ionspin.kotlin.bignum.decimal.BigDecimal` |

> **Not:** `StringBigDecimalConverter.convertFromNonNull` genişletilmiş gösterim (`toStringExpanded()`) kullanır; bilimsel gösterim (`1.23E+5`) üretmez.

---

## java.math Converter'ları (jvmAndroidMain)

`java.math` — yalnızca JVM ve Android'de kullanılabilir. İsimlendirme: `Java`-öneki veya `Java` içeren isimlerle ionspin'den ayrışır.

| Converter | Kaynak | Hedef |
|-----------|--------|-------|
| `StringJavaBigDecimalConverter` | `String` | `java.math.BigDecimal` |
| `StringJavaBigIntegerConverter` | `String` | `java.math.BigInteger` |
| `DoubleJavaBigDecimalConverter` | `Double` | `java.math.BigDecimal` |
| `LongJavaBigIntegerConverter` | `Long` | `java.math.BigInteger` |
| `JavaBigIntegerBigDecimalConverter` | `java.math.BigInteger` | `java.math.BigDecimal` |

---

## Kullanım

Scalar converter'ları `@KMapperConfig(converters = [...])` listesine ekleyin:

```kotlin
import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.bignumber.StringBigDecimalConverter
import com.sahsenvar.kmapper.bignumber.StringBigIntegerConverter

@KMapperConfig(converters = [StringBigDecimalConverter::class, StringBigIntegerConverter::class])
object MyMappers
```

Ardından modellerinizde bu tipleri doğrudan kullanın:

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

Üretilen eşleme:

```kotlin
public fun InvoiceRemote.toInvoiceDomain(): InvoiceDomain = InvoiceDomain(
    id          = id,
    totalAmount = StringBigDecimalConverter.convertToNonNull(totalAmount),
    quantity    = StringBigIntegerConverter.convertToNonNull(quantity),
)
```

---

## Hangi Converter'ı Seçmeli?

| Model tipi | Öneri |
|------------|-------|
| `com.ionspin.kotlin.bignum.decimal.BigDecimal` | `StringBigDecimalConverter`, `DoubleBigDecimalConverter` |
| `com.ionspin.kotlin.bignum.integer.BigInteger` | `StringBigIntegerConverter`, `LongBigIntegerConverter`, `IntBigIntegerConverter` |
| `java.math.BigDecimal` (yalnızca JVM/Android) | `StringJavaBigDecimalConverter`, `DoubleJavaBigDecimalConverter` |
| `java.math.BigInteger` (yalnızca JVM/Android) | `StringJavaBigIntegerConverter`, `LongJavaBigIntegerConverter` |

KMP projesinde iOS'u da desteklemeniz gerekiyorsa yalnızca ionspin converter'larını kullanın.

---

Sonraki adım: [@KMapperConfig ve @UseMapTypeConverter →](kmapperconfig.md) | Diğer kaynaklar: [Kendi Converter'ını Yazmak](ozel-converter.md)
