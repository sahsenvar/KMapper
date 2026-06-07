package com.sahsenvar.kmapper

/**
 * Wraps a converter call so that any non-[MappingException] throwable is re-thrown
 * as [MappingException.TypeConversionFailed], preserving the original cause.
 *
 * Generated mappers use this around every built-in or custom converter call so that
 * callers always receive a typed [MappingException] rather than a raw converter exception.
 *
 * @param from  FQN of the source type (for error reporting)
 * @param to    FQN of the target type (for error reporting)
 * @param block The converter call to execute
 */
inline fun <T> convertOrFail(
    from: String,
    to: String,
    block: () -> T,
): T = try {
    block()
} catch (e: MappingException) {
    throw e
} catch (e: Throwable) {
    throw MappingException.TypeConversionFailed(from, to, e)
}
