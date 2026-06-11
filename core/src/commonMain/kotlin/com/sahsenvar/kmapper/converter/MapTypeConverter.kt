package com.sahsenvar.kmapper.converter

import com.sahsenvar.kmapper.MappingException
import com.sahsenvar.kmapper.unsupportedConversionMessage
import kotlin.reflect.KClass

/**
 * Bidirectional type converter between a source type [S] and a target type [T].
 *
 * Built-in and user converters share this exact base — same discovery, same calling
 * convention, same way of declaring an intentionally-unsupported direction (full parity).
 *
 * Override the direction(s) you support; a non-overridden direction throws
 * [MappingException.UnsupportedConversion] as a runtime safety net (generated code never
 * calls a direction the processor knows is unsupported). Declare an intentionally
 * unsupported direction with an [UnsupportedDirection]-annotated `= unsupported()` stub.
 *
 * The `OrNull` variants exist for *sanctioned null*: override one to declare "this input
 * has no legitimate counterpart" (e.g. blank string carries no Int). A sanctioned null is
 * silent (legitimate flow, not a degradation) and survives `onFail = Throw`; the total
 * method stays total and keeps throwing for the same input.
 */
abstract class MapTypeConverter<S : Any, T : Any>(
    val sourceType: KClass<S>,
    val targetType: KClass<T>,
) {
    /** Total forward conversion (S -> T). Override if the direction is supported. */
    open fun convertTo(source: S): T = unsupported(defaultUnsupportedMessage())

    /** Total reverse conversion (T -> S). Override if the direction is supported. */
    open fun convertFrom(target: T): S = unsupported(defaultUnsupportedMessage())

    /**
     * Sanctioned-null forward variant — override to declare inputs with no legitimate
     * counterpart. The default delegates to [convertTo], so plain converters need not
     * override it.
     */
    open fun convertToOrNull(source: S): T? = convertTo(source)

    /**
     * Sanctioned-null reverse variant — override to declare inputs with no legitimate
     * counterpart. The default delegates to [convertFrom], so plain converters need not
     * override it.
     */
    open fun convertFromOrNull(target: T): S? = convertFrom(target)

    /** For annotated unsupported-direction stubs: `= unsupported()` (default message). */
    protected fun unsupported(): Nothing = unsupported(defaultUnsupportedMessage())

    /** Protected so authors can reject a shape from their own override bodies. */
    protected fun unsupported(message: String): Nothing = throw MappingException.UnsupportedConversion(message)

    // Sanctioned `simpleName` use (error-message path): sourceType/targetType are
    // compile-time-known class references supplied by the converter author, so the names
    // are stable literals, not reflection over user data.
    private fun defaultUnsupportedMessage(): String = unsupportedConversionMessage(sourceType.simpleName ?: "?", targetType.simpleName ?: "?")
}
