package sample.nullability

import com.sahsenvar.kmapper.KMapperWrapper
import com.sahsenvar.kmapper.MappingException
import com.sahsenvar.kmapper.annotations.MapTo
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

/**
 * NULLABILITY 2 — living with the plain core in production, and the wrappers on top of it.
 *
 * The generated mapper is PLAIN by default: `fun OrderResponse.toOrder(): Order` returns the
 * mapped value directly and THROWS a typed [MappingException] on a hard failure — an ordinary
 * Kotlin function, nothing to unwrap. That gives you three call-site patterns for handling the
 * throw — pick per situation, not per project:
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

    // PATTERN 1 — UI: catch at the boundary and fold into screen state. One order fails, the
    // APP does not.
    fun render(response: OrderResponse): ScreenState = try {
        ScreenState.Content(response.toOrder())
    } catch (failure: MappingException) {
        ScreenState.Error(failure.message ?: "mapping failed")
    }
    println("render(good) -> ${render(good)}")
    println("render(bad)  -> ${render(bad)}")

    // PATTERN 2 — the production golden path: degrade gracefully BUT keep the evidence.
    // `runCatching` turns the throw into a Result for you, right at this call site.
    val order = runCatching { bad.toOrder() }
        .onFailure { println("  [log] order mapping failed: ${it.message}") } // -> your logger/metrics
        .getOrElse { Order(id = -1, totalCents = 0) }
    println("fallback order -> $order")

    // PATTERN 3 — deliberate strictness (tests, CI, debug builds): let it crash by CHOICE.
    // That IS the plain default — nothing to opt into, `bad.toOrder()` alone would do it.
    runCatching { bad.toOrder() }
        .onFailure { println("toOrder() threw: ${it::class.simpleName}") }

    // ─── Would rather get the failure back AS A VALUE at every call site? Ask for a wrapper. ──
    //
    // `@MapTo(X::class, wrapper = KMapperWrapper.KtResult::class)` adds `toXResult(): Result<X>`
    // ON TOP of the plain core (which is still generated, unconditionally) — same PATTERN 1/2
    // above, but as a `Result` instead of a try/catch:
    val resultOutcome = OrderResultResponse(id = "1", totalCents = "12,990.00").toOrderResult()
    println("KtResult wrapper -> isFailure=${resultOutcome.isFailure}, message=${resultOutcome.exceptionOrNull()?.message}")

    // `wrapper = KMapperWrapper.Flow::class` adds `toXFlow(): Flow<X>` instead — a cold flow
    // that runs the mapping (and may throw) on collection; handy when the rest of your
    // pipeline already speaks Flow.
    runBlocking {
        val fromFlow = runCatching {
            OrderFlowResponse(id = "1", totalCents = "129900").toOrderFlow().toList().single()
        }
        println("Flow wrapper -> $fromFlow")
    }
}

@MapTo(Order::class, wrapper = KMapperWrapper.KtResult::class)
data class OrderResultResponse(
    val id: String,
    val totalCents: String,
)

@MapTo(Order::class, wrapper = KMapperWrapper.Flow::class)
data class OrderFlowResponse(
    val id: String,
    val totalCents: String,
)
