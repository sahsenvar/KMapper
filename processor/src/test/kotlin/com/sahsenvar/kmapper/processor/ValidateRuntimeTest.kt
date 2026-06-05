@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Runtime-execution tests for the @ValidateFrom / @ValidateTo validation seam.
 *
 * Each test compiles source strings that embed tiny inline Validator object definitions
 * (so :processor tests have no extra module dependency), then classloads the result
 * and invokes the generated mapper via reflection helpers.
 */
class ValidateRuntimeTest {

    // -----------------------------------------------------------------------
    // @ValidateFrom on non-null String — blank source throws ValidationFailed
    // -----------------------------------------------------------------------

    @Test
    fun `ValidateFrom blank source throws ValidationFailed at runtime`() {
        val (result, _) = compile(
            SourceFile.kotlin(
                "VFR1.kt", """
                import com.sahsenvar.kmapper.annotations.MapTo
                import com.sahsenvar.kmapper.annotations.ValidateFrom
                import com.sahsenvar.kmapper.validation.Validator

                data class TitleDomain(val title: String)

                object NotBlankValidator : Validator<String>(String::class) {
                    override fun validate(value: String): String? =
                        if (value.isBlank()) "must not be blank" else null
                }

                @MapTo(TitleDomain::class)
                data class TitleRemote(
                    @ValidateFrom(NotBlankValidator::class) val title: String
                )
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        // Blank string should throw
        val ex = assertFails {
            result.invokeMapper(
                "TitleRemoteMappersKt", "toTitleDomain",
                result.newInstance("TitleRemote", "   ")
            )
        }
        assertTrue(
            ex::class.qualifiedName!!.contains("ValidationFailed"),
            "Expected ValidationFailed but got: ${ex::class.qualifiedName} — ${ex.message}"
        )

        // Valid string should succeed
        val domain = result.invokeMapper(
            "TitleRemoteMappersKt", "toTitleDomain",
            result.newInstance("TitleRemote", "hello")
        )!!
        assertEquals("hello", domain.prop("title"))
    }

    // -----------------------------------------------------------------------
    // @ValidateTo on non-null String — invalid result throws ValidationFailed
    // -----------------------------------------------------------------------

    @Test
    fun `ValidateTo invalid result throws ValidationFailed at runtime`() {
        val (result, _) = compile(
            SourceFile.kotlin(
                "VTR1.kt", """
                import com.sahsenvar.kmapper.annotations.MapTo
                import com.sahsenvar.kmapper.annotations.ValidateTo
                import com.sahsenvar.kmapper.validation.Validator

                data class CodeDomain(val code: String)

                object NotBlankValidator : Validator<String>(String::class) {
                    override fun validate(value: String): String? =
                        if (value.isBlank()) "must not be blank" else null
                }

                @MapTo(CodeDomain::class)
                data class CodeRemote(
                    @ValidateTo(NotBlankValidator::class) val code: String
                )
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        // Blank string (maps direct String→String, result is blank) should throw
        val ex = assertFails {
            result.invokeMapper(
                "CodeRemoteMappersKt", "toCodeDomain",
                result.newInstance("CodeRemote", "  ")
            )
        }
        assertTrue(
            ex::class.qualifiedName!!.contains("ValidationFailed"),
            "Expected ValidationFailed but got: ${ex::class.qualifiedName} — ${ex.message}"
        )

        // Valid string should succeed
        val domain = result.invokeMapper(
            "CodeRemoteMappersKt", "toCodeDomain",
            result.newInstance("CodeRemote", "VALID")
        )!!
        assertEquals("VALID", domain.prop("code"))
    }

    // -----------------------------------------------------------------------
    // @ValidateFrom on nullable source — null skips validation, blank throws
    // -----------------------------------------------------------------------

    @Test
    fun `ValidateFrom nullable source - null skips validation`() {
        val (result, _) = compile(
            SourceFile.kotlin(
                "VFR2.kt", """
                import com.sahsenvar.kmapper.annotations.MapTo
                import com.sahsenvar.kmapper.annotations.ValidateFrom
                import com.sahsenvar.kmapper.annotations.MapDefaultValue
                import com.sahsenvar.kmapper.validation.Validator

                data class LabelDomain(val label: String)

                object NotBlankValidator : Validator<String>(String::class) {
                    override fun validate(value: String): String? =
                        if (value.isBlank()) "must not be blank" else null
                }

                @MapTo(LabelDomain::class)
                data class LabelRemote(
                    @ValidateFrom(NotBlankValidator::class)
                    @MapDefaultValue("\"default\"")
                    val label: String?
                )
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        // null source — should NOT throw (validation skipped), returns default
        val domainFromNull = result.invokeMapper(
            "LabelRemoteMappersKt", "toLabelDomain",
            result.newInstance("LabelRemote", null as String?)
        )!!
        assertEquals("default", domainFromNull.prop("label"))

        // non-blank non-null — should succeed
        val domainFromValid = result.invokeMapper(
            "LabelRemoteMappersKt", "toLabelDomain",
            result.newInstance("LabelRemote", "hello")
        )!!
        assertEquals("hello", domainFromValid.prop("label"))

        // blank non-null — should throw
        val ex = assertFails {
            result.invokeMapper(
                "LabelRemoteMappersKt", "toLabelDomain",
                result.newInstance("LabelRemote", "  ")
            )
        }
        assertTrue(
            ex::class.qualifiedName!!.contains("ValidationFailed"),
            "Expected ValidationFailed but got: ${ex::class.qualifiedName} — ${ex.message}"
        )
    }

    // -----------------------------------------------------------------------
    // No validation annotations — existing behaviour unchanged (regression)
    // -----------------------------------------------------------------------

    @Test
    fun `no validation annotations - mapper works as before`() {
        val (result, _) = compile(
            SourceFile.kotlin(
                "NoValR.kt", """
                import com.sahsenvar.kmapper.annotations.MapTo
                data class SimpleDomain(val x: String)
                @MapTo(SimpleDomain::class)
                data class SimpleRemote(val x: String)
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val domain = result.invokeMapper(
            "SimpleRemoteMappersKt", "toSimpleDomain",
            result.newInstance("SimpleRemote", "hello")
        )!!
        assertEquals("hello", domain.prop("x"))
    }
}
