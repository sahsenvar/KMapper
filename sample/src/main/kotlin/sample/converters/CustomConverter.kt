package sample.converters

import com.sahsenvar.kmapper.annotations.MapTo
import com.sahsenvar.kmapper.converter.MapTypeConverter

/**
 * CONVERTERS 2 — writing your own converter.
 *
 * A converter is a tiny `object` extending [MapTypeConverter] and overriding the directions it
 * supports — EXACTLY the same base class, discovery and rails the built-ins use (user–author
 * parity: nothing in KMapper is author-privileged).
 *
 * This one is registered module-wide in `sample.config.MappingConfig`, so any
 * `Money <-> String` field in this module converts automatically — see [PricedItem] below.
 */
data class Money(
    val cents: Long,
    val currency: String,
) {
    override fun toString(): String = "${cents / 100}.${(cents % 100).toString().padStart(2, '0')} $currency"
}

object MoneyStringConverter : MapTypeConverter<Money, String>(Money::class, String::class) {
    /** Money -> String, e.g. Money(129900, "USD") -> "1299.00 USD" — total, never fails. */
    override fun convertTo(source: Money): String = source.toString()

    /** String -> Money, e.g. "1299.00 USD" -> Money(129900, "USD") — throws on malformed input. */
    override fun convertFrom(target: String): Money {
        val (amount, currency) = target.split(' ', limit = 2)
        val (whole, fraction) = amount.split('.', limit = 2)
        return Money(cents = whole.toLong() * 100 + fraction.padEnd(2, '0').take(2).toLong(), currency = currency)
    }
}

data class PricedItem(
    val name: String,
    val price: Money, // String on the wire -> Money in the domain, via the registered converter
)

@MapTo(PricedItem::class)
data class PricedItemResponse(
    val name: String,
    val price: String,
)

fun main() = runCustomConverterDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runCustomConverterDemo() {
    val item = PricedItemResponse(name = "Split Keyboard", price = "189.00 EUR")
        .toPricedItemResult()
        .getOrThrow()
    println("custom-converted -> $item")

    // A malformed price is handled exactly like a broken built-in conversion:
    val broken = PricedItemResponse(name = "?", price = "free!!").toPricedItemResult()
    println("malformed price  -> ${broken.exceptionOrNull()?.message}")
}
