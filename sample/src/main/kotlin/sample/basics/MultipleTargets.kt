package sample.basics

import com.sahsenvar.kmapper.annotations.FieldMap
import com.sahsenvar.kmapper.annotations.MapTo

/**
 * BASICS 3 — one wire model, many targets.
 *
 * `@MapTo` is repeatable: a single API response often feeds both a full domain object and a
 * lightweight list-row model. With multiple targets, every `@FieldMap` must say WHICH target
 * it talks about (`targetClass`) — otherwise the rename would be ambiguous.
 *
 * Generated here:
 *     fun ProductResponse.toProductResult():        Result<Product>
 *     fun ProductResponse.toProductSummaryResult(): Result<ProductSummary>
 */
data class Product(
    val sku: String,
    val title: String,
    val priceCents: Long,
)

data class ProductSummary(
    val sku: String,
    val label: String, // <- different name on purpose; see @FieldMap below
)

@MapTo(Product::class)
@MapTo(ProductSummary::class)
data class ProductResponse(
    val sku: String,
    @FieldMap(fieldName = "label", targetClass = ProductSummary::class)
    val title: String, // maps to Product.title by name, and to ProductSummary.label by rename
    val priceCents: Long,
)

fun main() = runMultipleTargetsDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runMultipleTargetsDemo() {
    val response = ProductResponse(sku = "KB-2026", title = "Split Keyboard", priceCents = 18_900)
    println("full domain object -> ${response.toProductResult().getOrThrow()}")
    println("list-row summary   -> ${response.toProductSummaryResult().getOrThrow()}")
}
