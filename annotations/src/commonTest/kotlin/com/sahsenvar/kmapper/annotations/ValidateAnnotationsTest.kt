package com.sahsenvar.kmapper.annotations

import com.sahsenvar.kmapper.validation.builtin.NotBlankValidator
import com.sahsenvar.kmapper.validation.builtin.NotEmptyStringValidator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

// Compile-time shape check: if this class compiles, the redesigned annotations are
// well-formed on properties (targets, defaults, vararg position).
private data class AnnotationCompileCheck(
    @Validate(NotBlankValidator::class)
    @ConvertWith(onFail = OnFail.Throw)
    @ConvertTo(onFail = OnFail.Auto)
    @ConvertFrom(onFail = OnFail.Skip)
    val name: String,
    @IgnoreMap
    val notMapped: String = "",
    @IgnoreDefaultValue
    val flag: Boolean = false,
)

class ValidateAnnotationsTest :
    FunSpec({
        test("Validate is constructible with a single validator") {
            val annotation = Validate(NotBlankValidator::class)
            annotation.validators.toList() shouldBe listOf(NotBlankValidator::class)
        }

        test("Validate vararg accepts multiple validators and preserves order") {
            val annotation = Validate(NotBlankValidator::class, NotEmptyStringValidator::class)
            annotation.validators.toList() shouldBe
                listOf(NotBlankValidator::class, NotEmptyStringValidator::class)
        }

        test("Validate with no validators yields an empty array") {
            Validate().validators.size shouldBe 0
        }

        test("ConvertWith defaults: sentinel converter (keep auto-discovery) and OnFail.Auto") {
            val annotation = ConvertWith()
            annotation.use shouldBe com.sahsenvar.kmapper.converter.MapTypeConverter::class
            annotation.onFail shouldBe OnFail.Auto
        }

        test("ConvertTo and ConvertFrom share the ConvertWith parameter shape") {
            ConvertTo(onFail = OnFail.Throw).onFail shouldBe OnFail.Throw
            ConvertFrom(onFail = OnFail.Skip).onFail shouldBe OnFail.Skip
        }

        test("OnFail exposes exactly Auto, Throw, Skip") {
            OnFail.entries.map { it.name } shouldBe listOf("Auto", "Throw", "Skip")
        }
    })
