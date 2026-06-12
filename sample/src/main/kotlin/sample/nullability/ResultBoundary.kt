package sample.nullability

import com.sahsenvar.kmapper.annotations.MapTo

/**
 * NULLABILITY 2 — living with `Result` in production.
 *
 * Generated mappers NEVER throw at you; failure arrives as a value. That single decision gives
 * you three call-site patterns — pick per situation, not per project:
 */
data class Order(
    val id: Long,
    val totalCents: Long,
)

@MapTo(Order::class)
data class OrderResponse(
    val id: String,
    val totalCents: String,
)

sealed interface ScreenState {
    data class Content(val order: Order) : ScreenState
    data class Error(val reason: String) : ScreenState
}

fun main() = runResultBoundaryDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runResultBoundaryDemo() {
    val good = OrderResponse(id = "1", totalCents = "129900")
    val bad = OrderResponse(id = "1", totalCents = "12,990.00") // thousands separator -> not a Long

    // PATTERN 1 — UI: fold the Result into screen state. One order fails, the APP does not.
    fun render(response: OrderResponse): ScreenState = response.toOrderResult().fold(
        onSuccess = { ScreenState.Content(it) },
        onFailure = { ScreenState.Error(it.message ?: "mapping failed") },
    )
    println("render(good) -> ${render(good)}")
    println("render(bad)  -> ${render(bad)}")

    // PATTERN 2 — the production golden path: degrade gracefully BUT keep the evidence.
    val order = bad.toOrderResult()
        .onFailure { println("  [log] order mapping failed: ${it.message}") } // -> your logger/metrics
        .getOrElse { Order(id = -1, totalCents = 0) }
    println("fallback order -> $order")

    // PATTERN 3 — deliberate strictness (tests, CI, debug builds): crash by CHOICE.
    //   if (BuildConfig.DEBUG) response.toOrderResult().getOrThrow() else ...
    runCatching { bad.toOrderResult().getOrThrow() }
        .onFailure { println("getOrThrow rethrew: ${it::class.simpleName}") }
}
