@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Compile-only (generated-source) tests for the @ValidateFrom / @ValidateTo codegen.
 * Each test inspects the KSP-generated .kt file for the expected emission shape.
 */
class ValidateCodegenTest {
    // -----------------------------------------------------------------------
    // @ValidateFrom — non-null source field
    // -----------------------------------------------------------------------

    @Test
    fun `ValidateFrom on non-null field emits non-null validate call`() {
        val src =
            SourceFile.kotlin(
                "VF1.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo
                import com.sahsenvar.kmapper.annotations.ValidateFrom
                import com.sahsenvar.kmapper.validation.Validator

                data class NameDomain(val name: String)

                object TestNotBlank : Validator<String>(String::class) {
                    override fun validate(value: String): String? =
                        if (value.isBlank()) "must not be blank" else null
                }

                @MapTo(NameDomain::class)
                data class NameRemote(
                    @ValidateFrom(TestNotBlank::class) val name: String
                )
                """.trimIndent(),
            )
        val (result, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val gen = compilation.generatedFile("NameRemoteMappers.kt")
        // Must contain a run { } block
        assert(gen.contains("run {")) { "Expected 'run {' in:\n$gen" }
        // Must validate the source field
        assert(gen.contains("TestNotBlank.validate")) { "Expected TestNotBlank.validate in:\n$gen" }
        // Non-null form: no ?.let wrapper around the validate call
        assert(gen.contains("ValidationFailed")) { "Expected ValidationFailed in:\n$gen" }
        // Must NOT wrap with ?.let for a non-null source
        assert(!gen.contains("name?.let { __s ->")) { "Should NOT have nullable guard for non-null source:\n$gen" }
    }

    // -----------------------------------------------------------------------
    // @ValidateFrom — nullable source field
    // -----------------------------------------------------------------------

    @Test
    fun `ValidateFrom on nullable source field emits nullable guard`() {
        val src =
            SourceFile.kotlin(
                "VF2.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo
                import com.sahsenvar.kmapper.annotations.ValidateFrom
                import com.sahsenvar.kmapper.annotations.MapDefaultValue
                import com.sahsenvar.kmapper.validation.Validator

                data class TagDomain(val tag: String)

                object TestNotBlank : Validator<String>(String::class) {
                    override fun validate(value: String): String? =
                        if (value.isBlank()) "must not be blank" else null
                }

                @MapTo(TagDomain::class)
                data class TagRemote(
                    @ValidateFrom(TestNotBlank::class)
                    @MapDefaultValue("\"unknown\"")
                    val tag: String?
                )
                """.trimIndent(),
            )
        val (result, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val gen = compilation.generatedFile("TagRemoteMappers.kt")
        // Nullable form: wraps with ?.let { __s ->
        assert(gen.contains("?.let { __s ->")) { "Expected nullable guard '?.let { __s ->' in:\n$gen" }
        assert(gen.contains("TestNotBlank.validate")) { "Expected TestNotBlank.validate in:\n$gen" }
        assert(gen.contains("ValidationFailed")) { "Expected ValidationFailed in:\n$gen" }
    }

    // -----------------------------------------------------------------------
    // @ValidateTo — non-null target field
    // -----------------------------------------------------------------------

    @Test
    fun `ValidateTo on non-null target field emits non-null validate call on __result`() {
        val src =
            SourceFile.kotlin(
                "VT1.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo
                import com.sahsenvar.kmapper.annotations.ValidateTo
                import com.sahsenvar.kmapper.validation.Validator

                data class EmailDomain(val email: String)

                object TestNotEmpty : Validator<String>(String::class) {
                    override fun validate(value: String): String? =
                        if (value.isEmpty()) "must not be empty" else null
                }

                @MapTo(EmailDomain::class)
                data class EmailRemote(
                    @ValidateTo(TestNotEmpty::class) val email: String
                )
                """.trimIndent(),
            )
        val (result, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val gen = compilation.generatedFile("EmailRemoteMappers.kt")
        assert(gen.contains("run {")) { "Expected 'run {' in:\n$gen" }
        assert(gen.contains("val __result =")) { "Expected 'val __result =' in:\n$gen" }
        assert(gen.contains("TestNotEmpty.validate")) { "Expected TestNotEmpty.validate in:\n$gen" }
        assert(gen.contains("ValidationFailed")) { "Expected ValidationFailed in:\n$gen" }
        // non-null target: no ?.let { __r -> guard
        assert(!gen.contains("__result?.let { __r ->")) { "Should NOT have nullable guard for non-null target:\n$gen" }
    }

    // -----------------------------------------------------------------------
    // No annotations → zero-cost passthrough (regression)
    // -----------------------------------------------------------------------

    @Test
    fun `field with no validation annotations generates unchanged code`() {
        val src =
            SourceFile.kotlin(
                "NoVal.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo

                data class PlainDomain(val value: String)

                @MapTo(PlainDomain::class)
                data class PlainRemote(val value: String)
                """.trimIndent(),
            )
        val (result, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val gen = compilation.generatedFile("PlainRemoteMappers.kt")
        // No run{} block, no validation noise
        assert(!gen.contains("run {")) { "Unexpected 'run {' block for no-validation field:\n$gen" }
        assert(!gen.contains("ValidationFailed")) { "Unexpected ValidationFailed for no-validation field:\n$gen" }
    }

    // -----------------------------------------------------------------------
    // Exception field arg is TARGET field name
    // -----------------------------------------------------------------------

    @Test
    fun `ValidationFailed uses target field name in thrown exception`() {
        val src =
            SourceFile.kotlin(
                "TargetName.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo
                import com.sahsenvar.kmapper.annotations.ValidateFrom
                import com.sahsenvar.kmapper.annotations.FieldMap
                import com.sahsenvar.kmapper.validation.Validator

                data class OrderDomain(val orderId: String)

                object TestNotBlank : Validator<String>(String::class) {
                    override fun validate(value: String): String? =
                        if (value.isBlank()) "must not be blank" else null
                }

                @MapTo(OrderDomain::class)
                data class OrderRemote(
                    @FieldMap(fieldName = "orderId", targetClass = OrderDomain::class)
                    @ValidateFrom(TestNotBlank::class)
                    val id: String
                )
                """.trimIndent(),
            )
        val (result, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val gen = compilation.generatedFile("OrderRemoteMappers.kt")
        // The exception field arg must be "orderId" (target), not "id" (source)
        assert(gen.contains("\"orderId\"")) { "Expected target field name 'orderId' in exception:\n$gen" }
    }
}
