package sample.enums

import com.sahsenvar.kmapper.MappableEnum
import com.sahsenvar.kmapper.annotations.MapTo

/**
 * ENUMS — wire-safe enums with `MappableEnum`.
 *
 * Enums opt into mapping by implementing [MappableEnum] and declaring their WIRE value —
 * mapping never uses `name` or `ordinal`, so renaming a constant or reordering the enum can
 * never silently corrupt data.
 *
 * Unknown wire values follow the ladder like any other brokenness:
 * - non-null target -> hard failure (`UnknownEnumValue`, with the field path);
 * - nullable target -> absorbed to null and REPORTED — the standard forward-compatibility
 *   pattern for "the server may add new statuses before we ship".
 */
enum class OrderStatus(override val wireValue: String) : MappableEnum<String> {
    PENDING("pending"),
    SHIPPED("shipped"),
    DELIVERED("delivered"),
}

data class TrackedOrder(
    val id: Long,
    val status: OrderStatus, //         strict: an unknown status is a contract violation here
)

data class OrderPreview(
    val id: Long,
    val status: OrderStatus?, //        forward-compatible: unknown -> null (reported)
)

@MapTo(TrackedOrder::class)
@MapTo(OrderPreview::class)
data class OrderEvent(
    val id: Long,
    val status: String,
)

fun main() = runEnumMappingDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runEnumMappingDemo() {
    val known = OrderEvent(id = 1, status = "shipped")
    println("strict       -> ${known.toTrackedOrderResult().getOrThrow()}")

    val unknown = OrderEvent(id = 2, status = "teleported") // server got creative
    println("strict fails -> ${unknown.toTrackedOrderResult().exceptionOrNull()?.message}")
    //  Unknown wire value 'teleported' for enum OrderStatus at status

    println("preview absorbs -> ${unknown.toOrderPreviewResult().getOrThrow()}")
    //  OrderPreview(id=2, status=null)   (+ a degradation report)
}
