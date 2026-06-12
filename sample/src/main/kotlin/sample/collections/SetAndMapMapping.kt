package sample.collections

import com.sahsenvar.kmapper.annotations.MapTo

/**
 * COLLECTIONS 2 — Sets and Maps have their own honest semantics.
 *
 * - `Set` elements always SKIP when unproducible; if two distinct source elements CONVERGE to
 *   the same target element after conversion ("7" and "07" both become 7), the set shrinks —
 *   and that convergence is reported (it is exactly the kind of contract drift you want to see).
 * - `Map` keys and values convert independently; an unproducible side drops the ENTRY
 *   (reported, path like `stock["kb-2026"]`). If two source keys collide after conversion,
 *   the last one wins — with a DuplicateKey report.
 */
data class Inventory(
    val warehouseIds: Set<Long>,
    val stock: Map<String, Long>, // values parse from String
)

@MapTo(Inventory::class)
data class InventoryResponse(
    val warehouseIds: Set<String>,
    val stock: Map<String, String>,
)

fun main() = runSetAndMapMappingDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runSetAndMapMappingDemo() {
    val inventory = InventoryResponse(
        warehouseIds = setOf("7", "07", "12"), // "7" and "07" converge to 7L -> set of 2, reported
        stock = mapOf(
            "kb-2026" to "120",
            "mouse-x" to "not-a-number", // broken value -> entry dropped, reported
            "desk-9" to "4",
        ),
    ).toInventoryResult().getOrThrow()

    println("warehouses (converged) -> ${inventory.warehouseIds}")
    println("stock (salvaged)       -> ${inventory.stock}")
}
