package sample.basics

import com.sahsenvar.kmapper.annotations.MapTo

/**
 * BASICS 1 — your first mapping.
 *
 * `@MapTo(User::class)` on the wire model generates, at compile time:
 *
 *     fun UserResponse.toUserResult(): Result<User>
 *
 * Three things to notice:
 * 1. Fields match BY NAME — `id`, `name`, `age` need zero configuration.
 * 2. `id: String -> Long` converts automatically: built-in converters are discovered by type
 *    pair (here `LongStringConverter`), no annotation required.
 * 3. The mapper returns `Result<User>`, never throws. A malformed `id` becomes
 *    `Result.failure` carrying a typed, path-aware exception — your call site decides what
 *    happens next (see `sample.nullability.ResultBoundary` for production patterns).
 */
data class User(
    val id: Long,
    val name: String,
    val age: Int,
)

@MapTo(User::class)
data class UserResponse(
    val id: String,
    val name: String,
    val age: Int,
)

fun main() = runBasicMappingDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runBasicMappingDemo() {
    // Happy path: unwrap when you are SURE (tests, scripts) or have decided to crash on bad data.
    val user = UserResponse(id = "42", name = "Grace Hopper", age = 85).toUserResult().getOrThrow()
    println("mapped user        -> $user")

    // Failure is a VALUE, not an exception: the boundary contains it.
    val broken = UserResponse(id = "not-a-number", name = "?", age = 0).toUserResult()
    println("broken id outcome  -> isFailure=${broken.isFailure}")
    println("what went wrong    -> ${broken.exceptionOrNull()?.message}")
    // prints: Cannot convert id: kotlin.String -> kotlin.Long
}
