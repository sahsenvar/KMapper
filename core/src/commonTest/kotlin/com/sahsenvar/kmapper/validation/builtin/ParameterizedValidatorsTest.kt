package com.sahsenvar.kmapper.validation.builtin

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

// The `@Validate` recipe under test: parameterized open-class base, user object with concrete arguments.
private object SkuValidator : RegexValidator(Regex("[A-Z]{3}-\\d{4}"), "must be a SKU like ABC-1234")

private object UsernameLengthValidator : StringLengthValidator(minLength = 3, maxLength = 20)

private object QuantityValidator : IntRangeValidator(1..999)

private object EpochMillisValidator : LongRangeValidator(0L..4_102_444_800_000L)

private object PercentageValidator : DoubleRangeValidator(0.0, 100.0)

private object LineItemCountValidator : CollectionSizeValidator(minSize = 1, maxSize = 3)

class ParameterizedValidatorsTest :
    FunSpec({
        context("RegexValidator (via object subclass)") {
            test("accepts a full match and reports the given reason otherwise") {
                SkuValidator.validate("ABC-1234").shouldBeNull()
                SkuValidator.validate("abc-1234") shouldBe "must be a SKU like ABC-1234"
            }
            withData(
                nameFn = { "rejects partial match '$it'" },
                "xABC-1234", //  pattern must match the WHOLE value, not a substring
                "ABC-1234x",
                " ABC-1234",
                "",
            ) { invalid ->
                SkuValidator.validate(invalid).shouldNotBeNull()
            }
        }

        context("StringLengthValidator") {
            withData(
                nameFn = { "accepts length ${it.length}" },
                "abc", //                  min boundary (3)
                "abcdefghijklmnopqrst", // max boundary (20)
                "hello",
            ) { valid ->
                UsernameLengthValidator.validate(valid).shouldBeNull()
            }
            test("rejects below min and above max with the offending length in the reason") {
                UsernameLengthValidator.validate("ab") shouldBe "length must be in 3..20 (was 2)"
                UsernameLengthValidator.validate("a".repeat(21)) shouldBe "length must be in 3..20 (was 21)"
                UsernameLengthValidator.validate("").shouldNotBeNull()
            }
            test("maxLength defaults to unbounded") {
                val minOnly = object : StringLengthValidator(minLength = 1) {}
                minOnly.validate("x".repeat(10_000)).shouldBeNull()
            }
            test("misconfigured bounds fail at construction, not at validation time") {
                shouldThrow<IllegalArgumentException> { object : StringLengthValidator(minLength = -1) {} }
                shouldThrow<IllegalArgumentException> { object : StringLengthValidator(minLength = 5, maxLength = 4) {} }
            }
            test("property: accepted exactly when length is in range") {
                checkAll(Arb.string(0..30)) { candidate ->
                    val isAccepted = UsernameLengthValidator.validate(candidate) == null
                    isAccepted shouldBe (candidate.length in 3..20)
                }
            }
        }

        context("IntRangeValidator / LongRangeValidator") {
            test("boundaries are inclusive, outside is rejected with the value in the reason") {
                QuantityValidator.validate(1).shouldBeNull()
                QuantityValidator.validate(999).shouldBeNull()
                QuantityValidator.validate(0) shouldBe "must be in 1..999 (was 0)"
                QuantityValidator.validate(1000).shouldNotBeNull()
                QuantityValidator.validate(Int.MIN_VALUE).shouldNotBeNull()
            }
            test("Long variant covers the same contract") {
                EpochMillisValidator.validate(0L).shouldBeNull()
                EpochMillisValidator.validate(4_102_444_800_000L).shouldBeNull()
                EpochMillisValidator.validate(-1L).shouldNotBeNull()
                EpochMillisValidator.validate(Long.MAX_VALUE).shouldNotBeNull()
            }
            test("an empty range is a configuration error") {
                shouldThrow<IllegalArgumentException> { object : IntRangeValidator(IntRange.EMPTY) {} }
                shouldThrow<IllegalArgumentException> { object : LongRangeValidator(5L..4L) {} }
            }
            test("property: accepted exactly when in range") {
                checkAll(Arb.int()) { candidate ->
                    (QuantityValidator.validate(candidate) == null) shouldBe (candidate in 1..999)
                }
            }
        }

        context("DoubleRangeValidator") {
            withData(
                nameFn = { "accepts $it" },
                0.0, // min boundary
                100.0, // max boundary
                50.5,
            ) { valid ->
                PercentageValidator.validate(valid).shouldBeNull()
            }
            withData(
                nameFn = { "rejects $it" },
                -0.0001,
                100.0001,
                Double.NaN, //               NaN is never in range
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
            ) { invalid ->
                PercentageValidator.validate(invalid).shouldNotBeNull()
            }
            test("NaN bounds and inverted bounds are configuration errors") {
                shouldThrow<IllegalArgumentException> { object : DoubleRangeValidator(Double.NaN, 1.0) {} }
                shouldThrow<IllegalArgumentException> { object : DoubleRangeValidator(2.0, 1.0) {} }
            }
        }

        context("sign validators") {
            test("Positive rejects zero, NonNegative accepts it") {
                PositiveIntValidator.validate(0) shouldBe "must be positive (was 0)"
                NonNegativeIntValidator.validate(0).shouldBeNull()
                PositiveLongValidator.validate(0L).shouldNotBeNull()
                NonNegativeLongValidator.validate(0L).shouldBeNull()
                PositiveDoubleValidator.validate(0.0).shouldNotBeNull()
                NonNegativeDoubleValidator.validate(0.0).shouldBeNull()
            }
            test("extremes behave by sign") {
                PositiveIntValidator.validate(Int.MAX_VALUE).shouldBeNull()
                NonNegativeIntValidator.validate(Int.MIN_VALUE).shouldNotBeNull()
                PositiveLongValidator.validate(Long.MAX_VALUE).shouldBeNull()
                NonNegativeLongValidator.validate(Long.MIN_VALUE).shouldNotBeNull()
            }
            test("Double edge values: NaN always rejected, -0.0 counts as zero") {
                PositiveDoubleValidator.validate(Double.NaN).shouldNotBeNull()
                NonNegativeDoubleValidator.validate(Double.NaN).shouldNotBeNull()
                NonNegativeDoubleValidator.validate(-0.0).shouldBeNull() // IEEE: -0.0 == 0.0
                PositiveDoubleValidator.validate(-0.0).shouldNotBeNull()
                PositiveDoubleValidator.validate(Double.MIN_VALUE).shouldBeNull() // smallest positive
            }
        }

        context("FiniteDoubleValidator") {
            withData(
                nameFn = { "rejects $it" },
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
            ) { invalid ->
                FiniteDoubleValidator.validate(invalid).shouldNotBeNull()
            }
            test("accepts finite extremes") {
                FiniteDoubleValidator.validate(Double.MAX_VALUE).shouldBeNull()
                FiniteDoubleValidator.validate(-Double.MAX_VALUE).shouldBeNull()
                FiniteDoubleValidator.validate(0.0).shouldBeNull()
            }
        }

        context("CollectionSizeValidator") {
            test("boundaries are inclusive across collection kinds") {
                LineItemCountValidator.validate(listOf(1)).shouldBeNull()
                LineItemCountValidator.validate(setOf(1, 2, 3)).shouldBeNull()
                LineItemCountValidator.validate(emptyList<Int>()) shouldBe "size must be in 1..3 (was 0)"
                LineItemCountValidator.validate(listOf(1, 2, 3, 4)).shouldNotBeNull()
            }
            test("misconfigured bounds fail at construction") {
                shouldThrow<IllegalArgumentException> { object : CollectionSizeValidator(minSize = -1) {} }
                shouldThrow<IllegalArgumentException> { object : CollectionSizeValidator(minSize = 2, maxSize = 1) {} }
            }
        }
    })
