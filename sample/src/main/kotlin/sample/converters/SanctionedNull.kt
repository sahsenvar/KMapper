package sample.converters

import com.sahsenvar.kmapper.annotations.ConvertWith
import com.sahsenvar.kmapper.annotations.MapTo
import com.sahsenvar.kmapper.annotations.OnFail
import com.sahsenvar.kmapper.converter.MapTypeConverter

/**
 * CONVERTERS 5 — sanctioned null: the converter AUTHOR declares "this input has no counterpart".
 *
 * Override `convertToOrNull`/`convertFromOrNull` IN ADDITION to the total method to say which
 * inputs legitimately map to nothing (here: a blank discount code). Two crucial differences
 * from a thrown error:
 *
 * 1. A sanctioned null is SILENT — it is legitimate flow, not a degradation, so nothing is
 *    reported to the sink.
 * 2. It SURVIVES `OnFail.Throw` — strictness hardens failures, and a sanctioned null is not
 *    a failure. (`"WELCOME10"` broken stays loud; `""` stays quietly null.)
 */
data class DiscountCode(
    val value: String,
)

object DiscountCodeConverter : MapTypeConverter<DiscountCode, String>(DiscountCode::class, String::class) {
    override fun convertTo(source: DiscountCode): String = source.value

    override fun convertFrom(target: String): DiscountCode {
        require(target.matches(Regex("[A-Z0-9]{4,12}"))) { "not a discount code: '$target'" }
        return DiscountCode(target)
    }

    /** Blank string = "no code entered" — a legitimate nothing, not an error. */
    override fun convertFromOrNull(target: String): DiscountCode? = if (target.isBlank()) null else convertFrom(target)
}

data class Checkout(
    val totalCents: Long,
    val discount: DiscountCode?,
)

@MapTo(Checkout::class)
data class CheckoutRequest(
    val totalCents: Long,
    @ConvertWith(use = DiscountCodeConverter::class, onFail = OnFail.Throw)
    val discount: String?,
)

fun main() = runSanctionedNullDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runSanctionedNullDemo() {
    // Sanctioned null: blank -> null, silently — EVEN under OnFail.Throw.
    println(CheckoutRequest(9900, discount = "").toCheckoutResult().getOrThrow())
    //  Checkout(totalCents=9900, discount=null)

    // A real code converts.
    println(CheckoutRequest(9900, discount = "SUMMER26").toCheckoutResult().getOrThrow())

    // Garbage is a FAILURE (Throw hardens it) — sanctioned null does not cover "broken".
    val outcome = CheckoutRequest(9900, discount = "drop table").toCheckoutResult()
    println("broken code -> isFailure=${outcome.isFailure}")
}
