package sample.converters

import com.sahsenvar.kmapper.annotations.MapTo
import kotlinx.datetime.Instant

/**
 * CONVERTERS 1 — built-ins you get for free.
 *
 * Every primitive type pair ships as a converter OBJECT in `kmapper-core`, discovered by type
 * pair with no annotation:
 * - lossless widenings (`Int -> Long`, `Float -> Double`, ...) just work;
 * - `String` pairs parse/format (`"42" -> 42L`, `7 -> "7"`);
 * - `Instant <-> String` (ISO-8601) and `Instant <-> Long` (epoch millis).
 *
 * LOSSY directions are a COMPILE error with a guiding reason, not a runtime surprise.
 * Uncomment the block at the bottom to see:
 *
 *     Long -> Int narrows and can truncate; convert explicitly if intended.
 */
data class Telemetry(
    val deviceId: Long, //        "8821" -> 8821L         (String pair, parse direction)
    val temperature: Double, //   21.5f -> 21.5           (lossless widening)
    val recordedAt: Instant, //   "2026-06-12T03:00:00Z"  (ISO-8601)
    val label: String, //         9001 -> "9001"          (String pair, format direction)
)

@MapTo(Telemetry::class)
data class TelemetryPacket(
    val deviceId: String,
    val temperature: Float,
    val recordedAt: String,
    val label: Int,
)

fun main() = runBuiltInConvertersDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runBuiltInConvertersDemo() {
    val telemetry = TelemetryPacket(
        deviceId = "8821",
        temperature = 21.5f,
        recordedAt = "2026-06-12T03:00:00Z",
        label = 9001,
    ).toTelemetryResult().getOrThrow()
    println("all built-in conversions -> $telemetry")
}

// Lossy/ambiguous pairs refuse to compile — each with its own reason:
//
// data class Narrowed(val count: Int)
// @MapTo(Narrowed::class) data class WirePacket(val count: Long)
//   error: Long -> Int narrows and can truncate; convert explicitly if intended.
//
// data class Flagged(val active: Boolean)
// @MapTo(Flagged::class) data class LegacyRow(val active: Int)
//   error: Int -> Boolean has no canonical semantics (is 2 true?). Write a custom converter.
