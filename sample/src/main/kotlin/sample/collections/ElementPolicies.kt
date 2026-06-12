package sample.collections

import com.sahsenvar.kmapper.annotations.ConvertWith
import com.sahsenvar.kmapper.annotations.MapTo
import com.sahsenvar.kmapper.annotations.OnFail

/**
 * COLLECTIONS 3 — choosing the element policy per field.
 *
 * | you want                                   | you write                          |
 * |--------------------------------------------|------------------------------------|
 * | salvage (default): drop broken, keep rest  | nothing — `List<T>`                |
 * | preserve length & positions                | target element nullable `List<T?>` |
 * | all-or-nothing: one bad element fails all  | `@ConvertWith(onFail = Throw)`     |
 * | compact even a nullable-element list       | `@ConvertWith(onFail = Skip)`      |
 *
 * Pick by what the list MEANS: accounting lines are positional and contractual (Throw);
 * sensor readings tolerate gaps but positions matter (`T?`); a feed just wants survivors.
 */
data class Measurements(
    val invoiceLines: List<Long>, //   contractual: a single bad line invalidates the document
    val readings: List<Double?>, //    positional: broken reading -> null IN PLACE, length kept
    val tagIds: List<Long?>, //        Skip beats null-in-place: broken/null compacted away
)

// `onFail` directives live on the GENERATING side's fields — here the wire model (@MapTo source).
@MapTo(Measurements::class)
data class MeasurementsResponse(
    @ConvertWith(onFail = OnFail.Throw)
    val invoiceLines: List<String>,
    val readings: List<String?>,
    @ConvertWith(onFail = OnFail.Skip)
    val tagIds: List<String?>,
)

fun main() = runElementPoliciesDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runElementPoliciesDemo() {
    val clean = MeasurementsResponse(
        invoiceLines = listOf("100", "250"),
        readings = listOf("21.5", null, "abc", "23.0"), // null passes through; "abc" -> null (reported)
        tagIds = listOf("1", null, "x", "4"),
    ).toMeasurementsResult().getOrThrow()
    println("readings keep alignment -> ${clean.readings}") // [21.5, null, null, 23.0]
    println("tagIds get compacted    -> ${clean.tagIds}") //    [1, 4]

    // One bad invoice line under Throw -> the WHOLE mapping fails, with the indexed path.
    val outcome = MeasurementsResponse(
        invoiceLines = listOf("100", "n/a"),
        readings = emptyList(),
        tagIds = emptyList(),
    ).toMeasurementsResult()
    println("all-or-nothing          -> ${outcome.exceptionOrNull()?.message}")
    //  Cannot convert invoiceLines[1]: kotlin.String -> kotlin.Long
}
