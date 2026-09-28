package com.sahsenvar.kmapper.itest

import com.sahsenvar.kmapper.KMapperWrapper
import com.sahsenvar.kmapper.WrapperSuffix
import com.sahsenvar.kmapper.annotations.MapTo

// ─── Return wrapper models — one shared domain shape, one data model per wrapper choice ────

data class WrapperTargetDomainModel(
    val id: Int,
    val name: String,
)

/** No `wrapper` argument and no module-wide `kmapper.wrapper` option: only the plain core. */
@MapTo(WrapperTargetDomainModel::class)
data class PlainWrapperDataModel(
    val id: String,
    val name: String,
)

/** `wrapper = KMapperWrapper.KtResult::class`: plain core + `toWrapperTargetDomainModelResult()`. */
@MapTo(WrapperTargetDomainModel::class, wrapper = KMapperWrapper.KtResult::class)
data class ResultWrapperDataModel(
    val id: String,
    val name: String,
)

/**
 * `wrapper = KMapperWrapper.Flow::class`: plain core + both `toWrapperTargetDomainModelFlow()`
 * overloads (single source, and `Flow<Source>`).
 */
@MapTo(WrapperTargetDomainModel::class, wrapper = KMapperWrapper.Flow::class)
data class FlowWrapperDataModel(
    val id: String,
    val name: String,
)

/**
 * A hand-written outcome type, used the same way the built-in wrappers are — no author-only
 * privilege (see project user–author parity principle).
 */
sealed interface Outcome<out T> {
    data class Ok<T>(
        val value: T,
    ) : Outcome<T>

    data class Failed(
        val error: Throwable,
    ) : Outcome<Nothing>
}

/** A user-written wrapper: two `wrap` overloads, exactly like [KMapperWrapper.KtResult]/[KMapperWrapper.Flow]. */
@WrapperSuffix("Outcome")
object OutcomeWrapper : KMapperWrapper {
    fun <S, T> wrap(
        source: S,
        map: (S) -> T,
    ): Outcome<T> = try {
        Outcome.Ok(map(source))
    } catch (error: Exception) {
        Outcome.Failed(error)
    }

    fun <S, T> wrap(
        source: List<S>,
        map: (S) -> T,
    ): List<Outcome<T>> = source.map { wrap(it, map) }
}

/** `wrapper = OutcomeWrapper::class`: plain core + one `toWrapperTargetDomainModelOutcome()` per `wrap` overload. */
@MapTo(WrapperTargetDomainModel::class, wrapper = OutcomeWrapper::class)
data class OutcomeWrapperDataModel(
    val id: String,
    val name: String,
)
