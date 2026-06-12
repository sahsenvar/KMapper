package sample.collections

import com.sahsenvar.kmapper.annotations.MapTo

/**
 * COLLECTIONS 1 — lists, and the "1 bad element must not kill 99 good ones" default.
 *
 * Elements ride the same rails as scalar fields (nested mappers, converters). The DEFAULT
 * element policy is salvage: a broken or null source element is SKIPPED — and every skip is
 * REPORTED to the degradation sink with an indexed path like `items[1]` (see
 * `sample.observability` for listening). Container-level absence still follows the scalar
 * ladder: a missing list uses the `= emptyList()` default below.
 */
data class FeedItem(
    val id: Long,
    val title: String,
)

data class Feed(
    val items: List<FeedItem> = emptyList(),
)

@MapTo(FeedItem::class)
data class FeedItemResponse(
    val id: String,
    val title: String,
)

@MapTo(Feed::class)
data class FeedResponse(
    val items: List<FeedItemResponse>?,
)

fun main() = runListMappingDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runListMappingDemo() {
    // One rotten element (id is not a number) -> 2 of 3 survive; the drop is reported, not silent.
    val feed = FeedResponse(
        items = listOf(
            FeedItemResponse("1", "Ladder basics"),
            FeedItemResponse("oops", "Broken row from a flaky backend"),
            FeedItemResponse("3", "Wrappers deep dive"),
        ),
    ).toFeedResult().getOrThrow()
    println("salvaged feed (2 of 3) -> $feed")

    // Absent list -> container ladder -> the declared default (emptyList), silently.
    println("absent list            -> ${FeedResponse(items = null).toFeedResult().getOrThrow()}")
}
