# Date and Time — converters-datetime

> **kotlinx-datetime needs no add-on.** `Instant`, `LocalDate`, `LocalDateTime`, `LocalTime`
> (↔ `String`/`Long`) and `kotlin.time.Duration` are [core built-ins](built-in.md) —
> auto-resolved, zero registration. This add-on exists for **`java.time`** models and for
> bridging the two worlds on JVM/Android.

## Setup

```kotlin
dependencies {
    implementation("io.github.sahsenvar:kmapper-converters-datetime:2.1.0")
}
```

Register what you use in [`@KMapperConfig`](kmapperconfig.md) (add-on converters are not
auto-discovered):

```kotlin
@KMapperConfig(converters = [StringJavaInstantConverter::class, KotlinJavaDurationConverter::class])
object AppMapperConfig
```

## `java.time` scalar converters (JVM/Android)

Package `com.sahsenvar.kmapper.datetime`; all parse/format ISO-8601 via the type's own
`parse`/`toString`:

| Object | Pair |
|--------|------|
| `StringJavaInstantConverter` | `String ↔ java.time.Instant` |
| `LongJavaInstantConverter` | `Long` (epoch ms) `↔ java.time.Instant` |
| `StringJavaLocalDateConverter` | `String ↔ java.time.LocalDate` |
| `StringJavaLocalDateTimeConverter` | `String ↔ java.time.LocalDateTime` |
| `StringJavaLocalTimeConverter` | `String ↔ java.time.LocalTime` |
| `StringJavaZonedDateTimeConverter` | `String ↔ java.time.ZonedDateTime` |
| `StringJavaOffsetDateTimeConverter` | `String ↔ java.time.OffsetDateTime` |
| `StringJavaDurationConverter` | `String` (`"PT1H30M"`) `↔ java.time.Duration` |

## Bridges: kotlinx ↔ java

For codebases straddling both libraries (a kotlinx-datetime KMP domain, a java.time
persistence layer):

| Object | Pair | Notes |
|--------|------|-------|
| `KotlinJavaInstantConverter` | `kotlinx Instant ↔ java Instant` | exact |
| `KotlinJavaLocalDateConverter` | `kotlinx LocalDate ↔ java LocalDate` | exact |
| `KotlinJavaDurationConverter` | `kotlin.time.Duration ↔ java.time.Duration` | exact for finite values within ±146 years at ns precision |

## Choosing a wire format per field

`String ↔ Instant` (ISO) and `Long ↔ Instant` (epoch millis) are different pairs, so both can
coexist via discovery. Two *formats of the same pair* (say ISO string and epoch-as-string)
are a per-field decision — see
[parameterized converters](custom-converter.md#parameterized-converters) and
[@ConvertWith](convert-with.md).

> Next: **[Big Numbers →](bignumber.md)**
