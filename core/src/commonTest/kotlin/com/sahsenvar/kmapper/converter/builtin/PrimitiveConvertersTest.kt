package com.sahsenvar.kmapper.converter.builtin

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class PrimitiveConvertersTest {
    // ---- StringIntConverter ----

    @Test fun `StringInt convertToNonNull round-trip`() {
        StringIntConverter.convertToNonNull("42") shouldBe 42
        StringIntConverter.convertToNonNull("-1") shouldBe -1
        StringIntConverter.convertToNonNull("0") shouldBe 0
    }

    @Test fun `StringInt convertFromNonNull round-trip`() {
        StringIntConverter.convertFromNonNull(42) shouldBe "42"
        StringIntConverter.convertFromNonNull(-1) shouldBe "-1"
        StringIntConverter.convertFromNonNull(0) shouldBe "0"
    }

    @Test fun `StringInt convertTo null passthrough`() {
        StringIntConverter.convertTo(null).shouldBeNull()
    }

    @Test fun `StringInt convertFrom null passthrough`() {
        StringIntConverter.convertFrom(null).shouldBeNull()
    }

    @Test fun `StringInt malformed input throws NumberFormatException not wrapped by converter`() {
        shouldThrow<NumberFormatException> { StringIntConverter.convertToNonNull("abc") }
    }

    // ---- StringLongConverter ----

    @Test fun `StringLong round-trip`() {
        StringLongConverter.convertToNonNull("9999999999") shouldBe 9999999999L
        StringLongConverter.convertFromNonNull(9999999999L) shouldBe "9999999999"
    }

    @Test fun `StringLong null passthrough`() {
        StringLongConverter.convertTo(null).shouldBeNull()
        StringLongConverter.convertFrom(null).shouldBeNull()
    }

    @Test fun `StringLong malformed throws NumberFormatException`() {
        shouldThrow<NumberFormatException> { StringLongConverter.convertToNonNull("xyz") }
    }

    // ---- StringDoubleConverter ----

    @Test fun `StringDouble round-trip`() {
        StringDoubleConverter.convertToNonNull("3.14") shouldBe 3.14
        StringDoubleConverter.convertFromNonNull(3.14) shouldBe "3.14"
    }

    @Test fun `StringDouble null passthrough`() {
        StringDoubleConverter.convertTo(null).shouldBeNull()
        StringDoubleConverter.convertFrom(null).shouldBeNull()
    }

    @Test fun `StringDouble malformed throws NumberFormatException`() {
        shouldThrow<NumberFormatException> { StringDoubleConverter.convertToNonNull("notanumber") }
    }

    // ---- StringFloatConverter ----

    @Test fun `StringFloat round-trip`() {
        StringFloatConverter.convertToNonNull("1.5") shouldBe 1.5f
        StringFloatConverter.convertFromNonNull(1.5f) shouldBe "1.5"
    }

    @Test fun `StringFloat null passthrough`() {
        StringFloatConverter.convertTo(null).shouldBeNull()
        StringFloatConverter.convertFrom(null).shouldBeNull()
    }

    @Test fun `StringFloat malformed throws NumberFormatException`() {
        shouldThrow<NumberFormatException> { StringFloatConverter.convertToNonNull("bad") }
    }

    // ---- StringBooleanConverter ----
    // kotlin.toBoolean() returns true only for "true" (case-insensitive), false for anything else

    @Test fun `StringBoolean true string converts to true`() {
        StringBooleanConverter.convertToNonNull("true") shouldBe true
        StringBooleanConverter.convertToNonNull("TRUE") shouldBe true
    }

    @Test fun `StringBoolean non-true string converts to false per kotlin toBoolean semantics`() {
        StringBooleanConverter.convertToNonNull("false") shouldBe false
        StringBooleanConverter.convertToNonNull("abc") shouldBe false
        StringBooleanConverter.convertToNonNull("1") shouldBe false
    }

    @Test fun `StringBoolean convertFromNonNull round-trip`() {
        StringBooleanConverter.convertFromNonNull(true) shouldBe "true"
        StringBooleanConverter.convertFromNonNull(false) shouldBe "false"
    }

    @Test fun `StringBoolean null passthrough`() {
        StringBooleanConverter.convertTo(null).shouldBeNull()
        StringBooleanConverter.convertFrom(null).shouldBeNull()
    }

    // ---- IntLongConverter ----

    @Test fun `IntLong widen Int to Long`() {
        IntLongConverter.convertToNonNull(42) shouldBe 42L
        IntLongConverter.convertToNonNull(Int.MIN_VALUE) shouldBe Int.MIN_VALUE.toLong()
        IntLongConverter.convertToNonNull(Int.MAX_VALUE) shouldBe Int.MAX_VALUE.toLong()
    }

    @Test fun `IntLong narrow Long to Int in range`() {
        IntLongConverter.convertFromNonNull(42L) shouldBe 42
        IntLongConverter.convertFromNonNull(0L) shouldBe 0
        IntLongConverter.convertFromNonNull(Int.MIN_VALUE.toLong()) shouldBe Int.MIN_VALUE
        IntLongConverter.convertFromNonNull(Int.MAX_VALUE.toLong()) shouldBe Int.MAX_VALUE
    }

    @Test fun `IntLong null passthrough`() {
        IntLongConverter.convertTo(null).shouldBeNull()
        IntLongConverter.convertFrom(null).shouldBeNull()
    }

    @Test fun `IntLong out-of-range Long above MAX_VALUE throws IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            IntLongConverter.convertFromNonNull(Int.MAX_VALUE.toLong() + 1L)
        }
    }

    @Test fun `IntLong out-of-range Long below MIN_VALUE throws IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            IntLongConverter.convertFromNonNull(Int.MIN_VALUE.toLong() - 1L)
        }
    }
}
