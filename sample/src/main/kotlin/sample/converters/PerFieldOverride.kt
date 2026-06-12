package sample.converters

import com.sahsenvar.kmapper.annotations.ConvertWith
import com.sahsenvar.kmapper.annotations.MapTo
import com.sahsenvar.kmapper.converter.MapTypeConverter

/**
 * CONVERTERS 3 — overriding the discovered converter on ONE field.
 *
 * `@ConvertWith(use = ...)` is OVERRIDE-ONLY: you never need it to find a converter (discovery
 * is automatic by type pair); you use it when one field must behave differently from the rest.
 *
 * Below, `Money <-> String` normally goes through the module-wide [MoneyStringConverter]
 * ("1299.00 USD"), but the legacy `previous` field arrives in an archaic "EUR#15900" format —
 * only that field gets the override.
 */
object LegacyMoneyConverter : MapTypeConverter<Money, String>(Money::class, String::class) {
    override fun convertTo(source: Money): String = "${source.currency}#${source.cents}"

    override fun convertFrom(target: String): Money {
        val (currency, cents) = target.split('#', limit = 2)
        return Money(cents = cents.toLong(), currency = currency)
    }
}

data class PriceHistory(
    val current: Money,
    val previous: Money,
)

@MapTo(PriceHistory::class)
data class PriceHistoryResponse(
    val current: String, // "189.00 EUR" -> module-wide MoneyStringConverter (auto-discovered)
    @ConvertWith(use = LegacyMoneyConverter::class)
    val previous: String, // "EUR#15900" -> per-field override, this field only
)

fun main() = runPerFieldOverrideDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runPerFieldOverrideDemo() {
    val history = PriceHistoryResponse(current = "189.00 EUR", previous = "EUR#15900")
        .toPriceHistoryResult()
        .getOrThrow()
    println("mixed converters on one class -> $history")
}
