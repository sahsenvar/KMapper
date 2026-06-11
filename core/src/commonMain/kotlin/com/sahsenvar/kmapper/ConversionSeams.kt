package com.sahsenvar.kmapper

import kotlin.jvm.JvmName

/**
 * Dispatches [event] to registered listeners. The [KMapper.hasListeners] guard keeps the
 * no-listener case ~zero-cost; dispatch itself isolates throwing listeners.
 */
@PublishedApi
internal fun reportDegradation(event: MappingDegradation) {
    if (KMapper.hasListeners) KMapper.dispatch { onDegradation(event) }
}

/**
 * Builds the typed exception for a broken conversion: a [MappingException] is path-prefixed
 * as-is via [MappingException.withPathPrefix] (no wrapping, type preserved); anything else
 * becomes [MappingException.TypeConversionFailed] carrying the original cause.
 */
@PublishedApi
internal fun toMappingException(
    path: String,
    from: String,
    to: String,
    cause: Throwable,
): MappingException = if (cause is MappingException) {
    cause.withPathPrefix(path)
} else {
    MappingException.TypeConversionFailed(path, from, to, cause)
}

/**
 * Absence guard for Direct/seam-less landing sites: null receiver →
 * [MappingException.RequiredFieldMissing] at [path]; non-null passes through unchanged.
 */
fun <T : Any> T?.orRequired(path: String): T = this ?: throw MappingException.RequiredFieldMissing(path)

/**
 * Hard cell (ladder rows 1/5, and `OnFail.Throw` on a non-null, no-default target), non-null
 * receiver: ok → converted value; broken → typed [MappingException] via [toMappingException].
 * Nothing is reported — hard failures surface, they are not absorbed.
 */
inline fun <S : Any, T : Any> S.convertOrFail(
    path: String,
    from: String,
    to: String,
    convert: (S) -> T,
): T = try {
    convert(this)
} catch (cause: Throwable) {
    throw toMappingException(path, from, to, cause)
}

/**
 * Hard cell (ladder rows 1/5) with absence guard, nullable receiver: absent →
 * [MappingException.RequiredFieldMissing] at [path]; otherwise behaves like the non-null
 * [convertOrFail].
 */
@JvmName("convertNullableOrFail") // JVM-only disambiguation: nullable receiver erases to the same signature
inline fun <S : Any, T : Any> S?.convertOrFail(
    path: String,
    from: String,
    to: String,
    convert: (S) -> T,
): T = orRequired(path).convertOrFail(path, from, to, convert)

/**
 * Nullable target, Auto (ladder rows 3/7): absent → null silent; sanctioned null → null
 * silent; broken → null + [MappingDegradation.AbsorbedConversionError] whose cause is the
 * TYPED exception from [toMappingException] (metric pipelines stay pair-aware).
 */
inline fun <S : Any, T : Any> S?.convertOrNull(
    path: String,
    from: String,
    to: String,
    convert: (S) -> T?,
): T? {
    if (this == null) return null
    return try {
        convert(this)
    } catch (cause: Throwable) {
        val typedCause = toMappingException(path, from, to, cause)
        reportDegradation(MappingDegradation.AbsorbedConversionError(path, from, to, typedCause))
        null
    }
}

/**
 * Nullable target, `OnFail.Throw`: broken → rethrow typed via [toMappingException] (hard);
 * absent and sanctioned null stay type-driven → null, silent.
 */
inline fun <S : Any, T : Any> S?.convertOrNullStrict(
    path: String,
    from: String,
    to: String,
    convert: (S) -> T?,
): T? = this?.let { source ->
    try {
        convert(source)
    } catch (cause: Throwable) {
        throw toMappingException(path, from, to, cause)
    }
}

/**
 * Defaulted target, Auto (ladder rows 2/4/6/8): absent → [fallback] silent; sanctioned null →
 * [fallback] silent; broken → [fallback] + [MappingDegradation.AbsorbedConversionError] whose
 * cause is the TYPED exception from [toMappingException].
 */
inline fun <S : Any, T : Any> S?.convertOrElse(
    fallback: T,
    path: String,
    from: String,
    to: String,
    convert: (S) -> T?,
): T {
    if (this == null) return fallback
    return try {
        convert(this) ?: fallback
    } catch (cause: Throwable) {
        val typedCause = toMappingException(path, from, to, cause)
        reportDegradation(MappingDegradation.AbsorbedConversionError(path, from, to, typedCause))
        fallback
    }
}

/**
 * Defaulted target, `OnFail.Throw`: broken → rethrow typed via [toMappingException] (hard);
 * absent and sanctioned null → [fallback], silent.
 */
inline fun <S : Any, T : Any> S?.convertOrElseStrict(
    fallback: T,
    path: String,
    from: String,
    to: String,
    convert: (S) -> T?,
): T {
    if (this == null) return fallback
    return try {
        convert(this) ?: fallback
    } catch (cause: Throwable) {
        throw toMappingException(path, from, to, cause)
    }
}

/**
 * Legacy path-less wrapper kept TEMPORARILY so currently-generated mapper code keeps
 * compiling until ladder codegen replaces the emission (plan Task 14).
 */
@Deprecated("Legacy wrapper; removed when ladder codegen lands (plan Task 14).")
inline fun <T> convertOrFail(
    from: String,
    to: String,
    block: () -> T,
): T = try {
    block()
} catch (mappingFailure: MappingException) {
    throw mappingFailure
} catch (cause: Throwable) {
    throw MappingException.TypeConversionFailed(path = "", from = from, to = to, cause = cause)
}
