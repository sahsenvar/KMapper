package sample.nullability

import com.sahsenvar.kmapper.annotations.MapTo

/**
 * NULLABILITY 1 — the fallback ladder, KMapper's default behavior on every field:
 *
 *     converted value  >  declared constructor default  >  null (if the target allows it)  >  error
 *
 * Read each target field below as a ladder declaration:
 * - `nickname: String?`        -> absence is fine (null), and a BROKEN value also lands as null
 *                                 (reported to the sink — never silent, see sample.observability).
 * - `retries: Int = 3`         -> absence uses the default silently; a broken value uses the
 *                                 default too, but is REPORTED.
 * - `id: Long` (no default)    -> no declared escape: absence or breakage is a hard failure.
 *
 * Strictness is not a switch — it is the ABSENCE of declared fallbacks.
 */
data class Profile(
    val id: Long, //               hard: no escape declared
    val nickname: String?, //      absence -> null; broken -> null (reported)
    val retries: Int = 3, //       absence -> 3 (silent); broken -> 3 (reported)
)

@MapTo(Profile::class)
data class ProfileResponse(
    val id: String,
    val nickname: String?,
    val retries: String?,
)

fun main() = runFallbackLadderDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runFallbackLadderDemo() {
    // Everything present and clean.
    println(ProfileResponse("1", "grace", "5").toProfileResult().getOrThrow())
    //  Profile(id=1, nickname=grace, retries=5)

    // Absence follows the type: nickname -> null, retries -> default 3. Silent, by declaration.
    println(ProfileResponse("2", null, null).toProfileResult().getOrThrow())
    //  Profile(id=2, nickname=null, retries=3)

    // Brokenness absorbed where an escape exists ("abc" is not an Int -> default 3, REPORTED).
    println(ProfileResponse("3", "ada", "abc").toProfileResult().getOrThrow())
    //  Profile(id=3, nickname=ada, retries=3)

    // No escape on id -> the whole mapping fails, as a value.
    val failure = ProfileResponse("oops", null, null).toProfileResult()
    println("hard failure -> ${failure.exceptionOrNull()?.message}")
    //  Cannot convert id: kotlin.String -> kotlin.Long
}
