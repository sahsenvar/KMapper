package com.sahsenvar.kmapper

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow as CoroutinesFlow
import kotlinx.coroutines.flow.flow as flowOf

/**
 * Chooses the RETURN SHAPE of a generated mapper — `@MapTo(X::class, wrapper = …)` /
 * `@MapFrom(X::class, wrapper = …)`.
 *
 * Every mapping always generates the plain core `fun Source.toX(): X`, which throws a
 * [MappingException] on a hard failure. A wrapper adds extra extension(s) on top of it, one per
 * `wrap` overload, named `to{Target}{suffix}` — e.g. [KtResult] adds
 * `fun Source.toXResult(): Result<X>`. Nested mappings always call the plain core, so wrappers
 * never affect how mappers compose.
 *
 * - [Default] — use the module-wide setting (Gradle `KMapper { wrapper = … }` or the KSP option
 *   `kmapper.wrapper`); when nothing is configured this is [None].
 * - [None] — only the plain `toX()`.
 * - [KtResult] — also `toXResult(): Result<X>`.
 * - [Flow] — also `toXFlow(): Flow<X>`, for both a single value and a `Flow` of sources.
 *
 * ## Writing your own wrapper
 *
 * The built-ins use no privileged mechanism — a user wrapper is written exactly the same way:
 * an `object` implementing [KMapperWrapper], annotated with [WrapperSuffix], with one or more
 * public `wrap` functions of the shape
 *
 * ```
 * fun <S, T> wrap(source: <R in terms of S>, map: (S) -> T): <W in terms of T>
 * ```
 *
 * For each `wrap` overload the compiler generates
 * `fun R<Source>.to{Target}{suffix}(…externals): W<Target> = YourWrapper.wrap(this) { it.to{Target}(…externals) }`.
 *
 * ```
 * @WrapperSuffix("Either")
 * object EitherWrapper : KMapperWrapper {
 *     fun <S, T> wrap(source: S, map: (S) -> T): Either<Throwable, T> = Either.catch { map(source) }
 * }
 *
 * @MapTo(UserDomain::class, wrapper = EitherWrapper::class)   // → toUserDomainEither()
 * data class UserDto(...)
 * ```
 */
interface KMapperWrapper {
    /** Resolves to the module-wide setting (Gradle `KMapper { wrapper = … }`), else [None]. */
    object Default : KMapperWrapper

    /** No wrapper: only the plain `toX(): X` is generated. */
    object None : KMapperWrapper

    /** Adds `toXResult(): Result<X>` — hard failures become `Result.failure`. */
    @WrapperSuffix("Result")
    object KtResult : KMapperWrapper {
        inline fun <S, T> wrap(
            source: S,
            map: (S) -> T,
        ): Result<T> = runCatching { map(source) }
    }

    /**
     * Adds two `toXFlow()` overloads: `Source.toXFlow(): Flow<X>` (a cold single-value flow —
     * the mapping runs, and may throw, on collection) and `Flow<Source>.toXFlow(): Flow<X>`
     * (maps every emitted source).
     */
    @WrapperSuffix("Flow")
    object Flow : KMapperWrapper {
        fun <S, T> wrap(
            source: S,
            map: (S) -> T,
        ): CoroutinesFlow<T> = flowOf { emit(map(source)) }

        fun <S, T> wrap(
            source: CoroutinesFlow<S>,
            map: (S) -> T,
        ): CoroutinesFlow<T> = source.map { map(it) }
    }
}

/**
 * Names the function suffix a [KMapperWrapper] generates: `@WrapperSuffix("Result")` →
 * `toXResult()`. Required on every wrapper that declares `wrap` functions.
 *
 * Retention is BINARY: wrappers usually live in a different module than the mapping that uses
 * them (the built-ins always do), so the consumer's KSP round reads this from the classpath.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class WrapperSuffix(
    val suffix: String,
)
