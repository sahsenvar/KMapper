package sample.basics

import com.sahsenvar.kmapper.annotations.MapTo

/**
 * BASICS 1 — your first mapping.
 *
 * `@MapTo(User::class)` on the wire model generates, at compile time:
 *
 *     fun UserResponse.toUser(): User
 *
 * Three things to notice:
 * 1. Fields match BY NAME — `id`, `name`, `age` need zero configuration.
 * 2. `id: String -> Long` converts automatically: built-in converters are discovered by type
 *    pair (here `LongStringConverter`), no annotation required.
 * 3. The mapper is PLAIN: it returns `User` directly and THROWS a typed, path-aware
 *    `MappingException` on a hard failure — see `sample.nullability.ResultBoundary` for how
 *    to handle that at your call site, and how to opt into a `Result`- or `Flow`-returning
 *    wrapper generated alongside it.
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
    // Happy path: the plain core returns the mapped value directly.
    val user = UserResponse(id = "42", name = "Grace Hopper", age = 85).toUser()
    println("mapped user        -> $user")

    // A hard failure THROWS: catch it wherever makes sense for the caller.
    val broken = runCatching { UserResponse(id = "not-a-number", name = "?", age = 0).toUser() }
    println("broken id outcome  -> isFailure=${broken.isFailure}")
    println("what went wrong    -> ${broken.exceptionOrNull()?.message}")
    // prints: Cannot convert id: kotlin.String -> kotlin.Long
}
