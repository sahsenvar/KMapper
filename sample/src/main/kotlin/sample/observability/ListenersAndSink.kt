package sample.observability

import com.sahsenvar.kmapper.KMapper
import com.sahsenvar.kmapper.MappingDegradation
import com.sahsenvar.kmapper.MappingListener
import com.sahsenvar.kmapper.annotations.MapTo

/**
 * OBSERVABILITY — the degradation sink: lenient, but never blind.
 *
 * Whenever the ladder ABSORBS a broken value (into a default, a null, or by skipping a
 * collection element), the event is reported through the process-wide listener registry:
 * typed, path-carrying, with the original cause. Declared-absence flows (null -> null,
 * absent -> default) stay silent — they are your design, not a degradation.
 *
 * The classic deployment pattern, in one line each:
 *
 *     // debug builds: surface every absorbed problem immediately
 *     if (BuildConfig.DEBUG) KMapper.addListener(CrashOnDegradation)
 *     // production: count + log, keep serving
 *     else KMapper.addListener(MetricsListener)
 *
 * Listeners are observers BY CONTRACT: one that throws is isolated and suppressed — it can
 * never change a mapping's outcome.
 */
data class Snapshot(
    val cpuLoad: Double?, //   broken -> null (reported)
    val retries: Int = 3, //   broken -> default (reported)
)

@MapTo(Snapshot::class)
data class SnapshotPacket(
    val cpuLoad: String?,
    val retries: String?,
)

fun main() = runListenersAndSinkDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runListenersAndSinkDemo() {
    val seen = mutableListOf<String>()
    val listener = object : MappingListener {
        override fun onDegradation(event: MappingDegradation) {
            // event types: AbsorbedConversionError, DroppedBrokenElement, DroppedNullElement,
            //              DuplicateKey, ConvergedDuplicateElement
            seen += event.toString()
        }
    }

    KMapper.addListener(listener)
    try {
        val snapshot = SnapshotPacket(cpuLoad = "not-a-double", retries = "many")
            .toSnapshotResult()
            .getOrThrow()
        println("mapped (degraded but alive) -> $snapshot")
        //  Snapshot(cpuLoad=null, retries=3)

        println("what the sink saw:")
        seen.forEach { println("  - $it") }
        //  - AbsorbedConversionError(path=cpuLoad, kotlin.String -> kotlin.Double, cause=...)
        //  - AbsorbedConversionError(path=retries, kotlin.String -> kotlin.Int, cause=...)

        // Declared absence is SILENT — re-run with nulls and watch nothing arrive:
        seen.clear()
        SnapshotPacket(cpuLoad = null, retries = null).toSnapshotResult().getOrThrow()
        println("absent inputs reported ${seen.size} events (absence is your design, not a bug)")
    } finally {
        KMapper.removeListener(listener) // always pair add/remove — the registry is process-wide
    }
}
