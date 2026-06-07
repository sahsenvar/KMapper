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
 * Runtime-execution tests for the KSP mapping processor.
 *
 * These tests compile source code with the processor attached (via [compile]), then
 * instantiate the generated classes and invoke the generated mapper functions at
 * runtime using reflection helpers from [RuntimeExecSupport].
 *
 * A FAILURE here is a real production bug — do NOT weaken these assertions.
 */
class BasicMappingRuntimeTest {
    /**
     * Test (a): generated mapper copies all fields correctly at runtime.
     */
    @Test
    fun `mapper copies fields at runtime`() {
        val (result, _) =
            compile(
                SourceFile.kotlin(
                    "M.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo
                    data class UserDomain(val id: String, val email: String)
                    @MapTo(UserDomain::class)
                    data class UserRemote(val id: String, val email: String)
                    """.trimIndent(),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val domain =
            result.invokeMapper(
                "UserRemoteMappersKt",
                "toUserDomain",
                result.newInstance("UserRemote", "42", "a@b.com"),
            )!!

        assertEquals("42", domain.prop("id"))
        assertEquals("a@b.com", domain.prop("email"))
    }

    /**
     * Test (b): nullable→non-null field with null input throws RequiredFieldMissing at runtime.
     * Asserts by qualified name to handle cross-classloader identity.
     */
    @Test
    fun `nullable-to-nonnull null throws RequiredFieldMissing at runtime`() {
        val (result, _) =
            compile(
                SourceFile.kotlin(
                    "N.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo
                    data class D(val id: String)
                    @MapTo(D::class)
                    data class R(val id: String?)
                    """.trimIndent(),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val ex =
            assertFails {
                result.invokeMapper("RMappersKt", "toD", result.newInstance("R", null as String?))
            }
        assertTrue(
            ex::class.qualifiedName!!.contains("RequiredFieldMissing"),
            "Expected RequiredFieldMissing but got: ${ex::class.qualifiedName} — ${ex.message}",
        )
    }

    /**
     * Test (c): built-in String→Int converter produces the correct integer value at runtime.
     */
    @Test
    fun `built-in String to Int converts at runtime`() {
        val (result, _) =
            compile(
                SourceFile.kotlin(
                    "C.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo
                    data class CountD(val n: Int)
                    @MapTo(CountD::class)
                    data class CountR(val n: String)
                    """.trimIndent(),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val domain =
            result.invokeMapper(
                "CountRMappersKt",
                "toCountD",
                result.newInstance("CountR", "7"),
            )!!

        assertEquals(7, domain.prop("n"))
    }

    /**
     * Test (d): malformed String input for Int target wraps as TypeConversionFailed at runtime.
     *
     * If this test fails with a raw NumberFormatException instead of TypeConversionFailed,
     * that is a REAL bug: the generated convertOrFail wrapping has a hole and raw converter
     * exceptions escape to callers.
     */
    @Test
    fun `bad conversion input wraps as TypeConversionFailed`() {
        val (result, _) =
            compile(
                SourceFile.kotlin(
                    "C2.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo
                    data class CountD(val n: Int)
                    @MapTo(CountD::class)
                    data class CountR(val n: String)
                    """.trimIndent(),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val ex =
            assertFails {
                result.invokeMapper("CountRMappersKt", "toCountD", result.newInstance("CountR", "abc"))
            }
        assertTrue(
            ex::class.qualifiedName!!.contains("TypeConversionFailed"),
            "Expected TypeConversionFailed but got: ${ex::class.qualifiedName} — ${ex.message}\n" +
                "This is a real production bug: raw converter exception escapes convertOrFail wrapping.",
        )
    }

    /**
     * Test (e): @MapDefaultValue substitutes the default expression when the source field is null.
     */
    @Test
    fun `MapDefaultValue substitutes default when source is null at runtime`() {
        val (result, _) =
            compile(
                SourceFile.kotlin(
                    "D.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo
                    import com.sahsenvar.kmapper.annotations.MapDefaultValue
                    data class ProfileD(val name: String, val score: Int)
                    @MapTo(ProfileD::class)
                    data class ProfileR(
                        val name: String,
                        @MapDefaultValue("0")
                        val score: String?
                    )
                    """.trimIndent(),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val domain =
            result.invokeMapper(
                "ProfileRMappersKt",
                "toProfileD",
                result.newInstance("ProfileR", "Alice", null as String?),
            )!!

        assertEquals("Alice", domain.prop("name"))
        assertEquals(0, domain.prop("score"))
    }
}
