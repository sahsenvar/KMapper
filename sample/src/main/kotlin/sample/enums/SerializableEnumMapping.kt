package sample.enums

import com.sahsenvar.kmapper.annotations.MapTo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ENUMS 2 — `@Serializable` enums, the `MappableEnum`-free path.
 *
 * If your enum is already a kotlinx.serialization `@Serializable` enum, you don't repeat the
 * wire values: KMapper reads each entry's wire value from its `@SerialName` (else the entry's
 * own name — exactly how it serializes in JSON) and generates a compile-time `when`. No runtime
 * kotlinx-serialization dependency; `kmapper-core` never sees it.
 *
 * Same semantics as [EnumMapping]: unknown wire values ride the ladder — hard at a non-null
 * target, absorbed to null (and reported) at a nullable one. (If an enum implements BOTH
 * MappableEnum and @Serializable, MappableEnum wins.)
 */
@Serializable
enum class Priority {
    @SerialName("low")
    LOW,

    @SerialName("high")
    HIGH,
    URGENT, // no @SerialName -> wire value is the entry name, "URGENT"
}

data class Ticket(
    val id: Long,
    val priority: Priority, //  strict: an unknown priority is a contract violation here
)

data class TicketView(
    val id: Long,
    val priority: Priority?, //  forward-compatible: unknown -> null (reported)
)

@MapTo(Ticket::class)
@MapTo(TicketView::class)
data class TicketPayload(
    val id: Long,
    val priority: String,
)

fun main() = runSerializableEnumMappingDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runSerializableEnumMappingDemo() {
    // @SerialName decodes; a bare entry name decodes by its own name.
    println("@SerialName  -> ${TicketPayload(1, "low").toTicketResult().getOrThrow()}")
    //  Ticket(id=1, priority=LOW)
    println("entry name   -> ${TicketPayload(2, "URGENT").toTicketResult().getOrThrow()}")
    //  Ticket(id=2, priority=URGENT)

    val unknown = TicketPayload(id = 3, priority = "frozen") // not a known serial name
    println("strict fails -> ${unknown.toTicketResult().exceptionOrNull()?.message}")
    //  Unknown wire value 'frozen' for enum Priority at priority

    println("view absorbs -> ${unknown.toTicketViewResult().getOrThrow()}")
    //  TicketView(id=3, priority=null)   (+ a degradation report)
}
