package sample.collections

import arrow.core.NonEmptyList
import com.sahsenvar.kmapper.annotations.MapTo
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet

/**
 * COLLECTIONS 4 — custom containers via `@CollectionWrapper`.
 *
 * Non-stdlib containers (kotlinx-immutable, Arrow's NonEmptyList, or your own type) plug in
 * through tiny wrapper objects registered once in `@KMapperConfig(wrappers = ...)` — see
 * `sample.config.MappingConfig`. Element conversion still rides the normal rails INSIDE the
 * wrapper call; the wrapper only handles the container shell (and `unwrap` for the reverse
 * direction).
 *
 * Writing your own takes ~6 lines:
 *
 *     @CollectionWrapper(forType = Box::class)
 *     object BoxWrapper {
 *         fun <T> wrap(source: List<T>): Box<T> = Box(source)
 *         fun <T> unwrap(source: Box<T>): List<T> = source.values
 *     }
 */
data class Tag(
    val name: String,
)

data class Catalog(
    val tags: PersistentList<Tag>,
    val skus: PersistentSet<String>,
)

data class ReviewTeam(
    val members: NonEmptyList<String>, // empty input is a FAILURE for this type — by definition
)

@MapTo(Tag::class)
data class TagResponse(
    val name: String,
)

@MapTo(Catalog::class)
data class CatalogResponse(
    val tags: List<TagResponse>,
    val skus: List<String>,
)

@MapTo(ReviewTeam::class)
data class ReviewTeamResponse(
    val members: List<String>,
)

fun main() = runWrappedCollectionsDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runWrappedCollectionsDemo() {
    val catalog = CatalogResponse(
        tags = listOf(TagResponse("kotlin"), TagResponse("multiplatform")),
        skus = listOf("KB-2026", "KB-2026", "MX-1"), // sets deduplicate, as sets do
    ).toCatalogResult().getOrThrow()
    println("immutable containers -> $catalog")

    // NonEmptyList encodes "at least one" in the TYPE; an empty wire list cannot satisfy it.
    println("team of two  -> ${ReviewTeamResponse(listOf("ada", "grace")).toReviewTeamResult().getOrThrow()}")
    val empty = ReviewTeamResponse(emptyList()).toReviewTeamResult()
    println("empty source -> isFailure=${empty.isFailure} (${empty.exceptionOrNull()?.message})")
}
