# Writing a Custom Converter

A converter is an `object` extending `MapTypeConverter<S, T>`. Yours runs on **exactly the
same rails** as the built-ins: same discovery, same overrides, same compile-time checks, same
error taxonomy. This page is the complete contract.

## The shape

```kotlin
import com.sahsenvar.kmapper.converter.MapTypeConverter

object MoneyStringConverter : MapTypeConverter<Money, String>(Money::class, String::class) {
    override fun convertTo(source: Money): String = source.format()        // Money  -> String
    override fun convertFrom(target: String): Money = Money.parse(target)  // String -> Money
}
```

Conventions:

- **`object`, not class** — generated code calls `MoneyStringConverter.convertFrom(x)` as a
  direct FQN reference; no instantiation, no reflection.
- **Richer type first** (`<Money, String>`): `convertTo` goes *toward* the second type,
  `convertFrom` comes back from it.
- **Throw on bad input.** `convertFrom("garbage")` should throw (an
  `IllegalArgumentException` is fine) — the [ladder](../basic-usage/null-safety.md) and
  `Result` boundary turn it into a typed, path-carrying failure. Never return a guessed value.

Register it once in [@KMapperConfig](kmapperconfig.md) — after that, every
`Money`/`String` field pair in the module resolves to it automatically.

## The two optional methods: sanctioned null

`convertToOrNull` / `convertFromOrNull` exist for conversions where **null is a valid
answer, not a failure** — e.g. a lookup that may legitimately find nothing. By default they
delegate to the total methods; override when "no result" is meaningful:

```kotlin
object CountryCodeConverter : MapTypeConverter<Country, String>(Country::class, String::class) {
    override fun convertTo(source: Country): String = source.isoCode
    override fun convertFrom(target: String): Country =
        Country.byIso(target) ?: throw IllegalArgumentException("Unknown ISO code: $target")

    // sanctioned null: unknown code is an expected outcome here, not an error
    override fun convertFromOrNull(target: String): Country? = Country.byIso(target)
}
```

Generated code calls the `OrNull` variant when the target field is nullable — so an unknown
country flows as `null` *without* a degradation report (it's sanctioned, not broken).

## Refusing a direction

When one direction can't be implemented honestly, **refuse it loudly** instead of
approximating — the same mechanism built-ins use:

```kotlin
import com.sahsenvar.kmapper.converter.UnsupportedDirection

object SearchQueryConverter : MapTypeConverter<SearchQuery, String>(SearchQuery::class, String::class) {
    override fun convertTo(source: SearchQuery): String = source.serialize()

    @UnsupportedDirection("parsing a raw query string is lossy; build SearchQuery via the DSL instead")
    override fun convertFrom(target: String): SearchQuery = unsupported()
}
```

If a mapping ever *needs* the refused direction, the **build fails** with your reason in the
message. (Detection is by the annotation; `unsupported()` provides the consistent runtime
backstop. Mark the total method, not the `OrNull` one — the compiler guides you if you get it
backwards.)

## Parameterized converters

`@KMapperConfig`/`@ConvertWith` reference objects, so parameterization happens through an
**abstract base class with constructor parameters**, subclassed as one-line objects — define
the rule once, stamp named variants:

```kotlin
abstract class FormattedDoubleStringConverter(
    private val decimalDigits: Int,
    private val suffix: String = "",
) : MapTypeConverter<Double, String>(Double::class, String::class) {
    override fun convertTo(source: Double): String = source.format(decimalDigits) + suffix
    override fun convertFrom(target: String): Double = target.removeSuffix(suffix).trim().toDouble()
}

/** 12.345 -> "12.35"  */ object PriceFormatConverter : FormattedDoubleStringConverter(decimalDigits = 2)
/** 12.345 -> "12.3%" */ object PercentFormatConverter : FormattedDoubleStringConverter(decimalDigits = 1, suffix = "%")
```

Each variant is a named, testable thing, selected per field with
`@ConvertWith(use = PriceFormatConverter::class)` — no annotation-argument magic. (Full
runnable version: `ParameterizedConverters.kt` in the
[gallery](../getting-started/examples.md).) The
[validator library](../validation/validators.md) uses the identical recipe
(`RegexValidator`, `IntRangeValidator`, …).

## Collection wrappers

To map `List<T>` into your own container type, declare a wrapper with `@CollectionWrapper`:

```kotlin
@CollectionWrapper(forType = PersistentList::class)
object PersistentListWrapper {
    fun <T> wrap(source: List<T>): PersistentList<T> = source.toPersistentList()
    fun <T> unwrap(source: PersistentList<T>): List<T> = source.toList()
}
```

Both directions are required and **compile-checked** (signature convention validated by the
processor). Register in `@KMapperConfig(wrappers = [...])`. Element conversion still rides
the normal element ladder — wrappers only change the container.

> Next: **[@KMapperConfig →](kmapperconfig.md)**
