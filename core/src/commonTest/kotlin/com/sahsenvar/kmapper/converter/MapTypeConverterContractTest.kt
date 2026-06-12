package com.sahsenvar.kmapper.converter

import com.sahsenvar.kmapper.MappingException
import com.sahsenvar.kmapper.converter.builtin.FloatIntConverter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/** Overrides only the reverse direction; the forward direction stays the unsupported default. */
private object ForwardOnlyConverter : MapTypeConverter<Long, Int>(Long::class, Int::class) {
    override fun convertFrom(target: Int): Long = target.toLong()
}

/** Overrides only the forward direction; the reverse direction stays the unsupported default. */
private object ReverseUnsupportedConverter : MapTypeConverter<Long, Int>(Long::class, Int::class) {
    override fun convertTo(source: Long): Int = source.toInt()
}

/** Declares blank string as a sanctioned no-value input on the OrNull variant only. */
private object SanctionedNullConverter : MapTypeConverter<Int, String>(Int::class, String::class) {
    override fun convertTo(source: Int): String = source.toString()

    override fun convertFrom(target: String): Int = target.toInt()

    override fun convertFromOrNull(target: String): Int? = if (target.isBlank()) null else target.toInt()
}

class MapTypeConverterContractTest :
    FunSpec({
        test("overridden direction works") {
            ForwardOnlyConverter.convertFrom(7) shouldBe 7L
        }

        test("non-overridden direction throws UnsupportedConversion with the pair in the message") {
            val failure = shouldThrow<MappingException.UnsupportedConversion> { ForwardOnlyConverter.convertTo(7L) }
            failure.message!! shouldContain "Long -> Int"
        }

        test("non-overridden convertFrom reports the reverse direction (T -> S), not S -> T") {
            val failure = shouldThrow<MappingException.UnsupportedConversion> { ReverseUnsupportedConverter.convertFrom(5) }
            // Assert the headline direction: the shared guidance text cites "Long -> Int" as a
            // generic lossy-policy example, so the check targets the "conversion is unsupported" line.
            failure.message!! shouldContain "Int -> Long conversion is unsupported"
            failure.message!! shouldNotContain "Long -> Int conversion is unsupported"
        }

        test("annotated stub via unsupported() reports a direction-neutral message naming both types") {
            val failure = shouldThrow<MappingException.UnsupportedConversion> { FloatIntConverter.convertFrom(1) }
            failure.message!! shouldContain "Float"
            failure.message!! shouldContain "Int"
            failure.message!! shouldContain "between"
        }

        test("non-overridden OrNull variant inherits the unsupported behavior via delegation") {
            shouldThrow<MappingException.UnsupportedConversion> { ForwardOnlyConverter.convertToOrNull(7L) }
        }

        test("OrNull default delegates to the total method") {
            SanctionedNullConverter.convertToOrNull(5) shouldBe "5"
            shouldThrow<NumberFormatException> { SanctionedNullConverter.convertFromOrNull("abc") }
        }

        test("sanctioned null returns null instead of throwing for declared inputs") {
            SanctionedNullConverter.convertFromOrNull("") shouldBe null
            SanctionedNullConverter.convertFromOrNull("   ") shouldBe null
            shouldThrow<NumberFormatException> { SanctionedNullConverter.convertFrom("") } // total stays total
        }

        test("sanctioned-null override still converts legitimate inputs") {
            SanctionedNullConverter.convertFromOrNull("42") shouldBe 42
        }
    })
