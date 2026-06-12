package sample.converters

import com.sahsenvar.kmapper.annotations.ConvertWith
import com.sahsenvar.kmapper.annotations.MapTo
import com.sahsenvar.kmapper.converter.MapTypeConverter
import kotlin.math.roundToLong

/**
 * CONVERTERS 6 — format variants WITHOUT writing a converter per field.
 *
 * The official recipe: ONE abstract base with constructor parameters + a one-line configured
 * `object` per variant + `@ConvertWith(use = ...)` per field. No annotation-argument magic,
 * fully type-safe, and each variant is a named, testable thing.
 */
abstract class FormattedDoubleStringConverter(
    private val decimalDigits: Int,
    private val suffix: String = "",
) : MapTypeConverter<Double, String>(Double::class, String::class) {

    override fun convertTo(source: Double): String {
        var scale = 1L
        repeat(decimalDigits) { scale *= 10 }
        val scaled = (source * scale).roundToLong()
        val whole = scaled / scale
        val fraction = (scaled % scale).toString().padStart(decimalDigits, '0')
        return if (decimalDigits == 0) "$whole$suffix" else "$whole.$fraction$suffix"
    }

    override fun convertFrom(target: String): Double = target.removeSuffix(suffix).trim().toDouble()
}

/** Two decimal places: 12.345 -> "12.35" */
object PriceFormatConverter : FormattedDoubleStringConverter(decimalDigits = 2)

/** One decimal place with a percent sign: 12.345 -> "12.3%" */
object PercentFormatConverter : FormattedDoubleStringConverter(decimalDigits = 1, suffix = "%")

data class ProductCard(
    val price: String,
    val discountRate: String,
)

@MapTo(ProductCard::class)
data class ProductPricing(
    @ConvertWith(use = PriceFormatConverter::class)
    val price: Double,
    @ConvertWith(use = PercentFormatConverter::class)
    val discountRate: Double,
)

fun main() = runParameterizedConvertersDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runParameterizedConvertersDemo() {
    val card = ProductPricing(price = 189.005, discountRate = 12.34).toProductCardResult().getOrThrow()
    println("formatted -> $card")
    //  ProductCard(price=189.01, discountRate=12.3%)
}
