package sample.converters

import com.sahsenvar.kmapper.annotations.ConvertWith
import com.sahsenvar.kmapper.annotations.MapTo
import com.sahsenvar.kmapper.converter.MapTypeConverter
import com.sahsenvar.kmapper.converter.UnsupportedDirection

/**
 * CONVERTERS 7 — one-way converters with `@UnsupportedDirection`.
 *
 * Some conversions only make sense in one direction (hashing, redaction, lossy projections).
 * Declare the unsupported direction as an annotated stub:
 *
 * - the stub body is always `= unsupported()`;
 * - the `reason` you write becomes the COMPILE-TIME error any mapping that needs that
 *   direction will show — your own converters get the same guided-diagnostics treatment the
 *   built-ins get (parity).
 */
data class Redacted(
    val masked: String,
)

object RedactingConverter : MapTypeConverter<String, Redacted>(String::class, Redacted::class) {
    /** "4111111111111111" -> Redacted("************1111") */
    override fun convertTo(source: String): Redacted = Redacted("*".repeat((source.length - 4).coerceAtLeast(0)) + source.takeLast(4))

    @UnsupportedDirection("Redaction is one-way: the original value is destroyed by design.")
    override fun convertFrom(target: Redacted): String = unsupported()
}

data class StoredCard(
    val number: Redacted,
)

@MapTo(StoredCard::class)
data class CardForm(
    @ConvertWith(use = RedactingConverter::class)
    val number: String,
)

fun main() = runOneWayConvertersDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runOneWayConvertersDemo() {
    val stored = CardForm(number = "4111111111111111").toStoredCardResult().getOrThrow()
    println("redacted -> $stored")

    // The reverse mapping does not compile. If you add:
    //   @MapTo(CardForm::class) data class StoredCard(...)
    // the build fails with YOUR reason:
    //   Redaction is one-way: the original value is destroyed by design.
}
