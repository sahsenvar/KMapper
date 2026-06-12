package sample.fields

import com.sahsenvar.kmapper.annotations.MapTo
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * FIELDS 3 — values the wire simply does not have.
 *
 * A target field with NO matching source field and NO constructor default becomes a required
 * parameter on the generated function. This is how you inject context the response cannot
 * know: who fetched it, when, with which request id.
 *
 * Generated:
 *     fun PaymentResponse.toPaymentResult(fetchedAt: Instant, traceId: String): Result<Payment>
 */
data class Payment(
    val id: Long,
    val amountCents: Long,
    val fetchedAt: Instant, // not on the wire -> external parameter
    val traceId: String, //    not on the wire -> external parameter
)

@MapTo(Payment::class)
data class PaymentResponse(
    val id: Long,
    val amountCents: Long,
)

fun main() = runExternalParametersDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runExternalParametersDemo() {
    val payment = PaymentResponse(id = 7001, amountCents = 4_500)
        .toPaymentResult(fetchedAt = Clock.System.now(), traceId = "req-8c1f")
        .getOrThrow()
    println("payment with injected context -> $payment")
}
