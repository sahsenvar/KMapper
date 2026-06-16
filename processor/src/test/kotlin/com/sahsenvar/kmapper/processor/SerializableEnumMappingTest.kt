@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.sahsenvar.kmapper.MappingException
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Enums opt into mapping via kotlinx.serialization `@Serializable` + `@SerialName` as an
 * alternative to `MappableEnum`. The processor reads the annotations by FQN (no runtime
 * kotlinx-serialization dependency) and generates a compile-time `when` over the per-entry
 * wire values (the `@SerialName` argument, else the entry's declared name — matching how the
 * enum actually serializes in JSON). `MappableEnum` wins when an enum has both.
 */
@OptIn(ExperimentalCompilerApi::class)
class SerializableEnumMappingTest {
    // ACTIVE carries an explicit serial name; BANNED falls back to its entry name.
    private val serializableStatusEnum =
        """
        import kotlinx.serialization.Serializable
        import kotlinx.serialization.SerialName

        @Serializable
        enum class Status {
            @SerialName("active") ACTIVE,
            BANNED
        }
        """.trimIndent()

    @Test
    fun `wire String to Serializable enum generates a when over serial names`() {
        val src =
            SourceFile.kotlin(
                "SFromWire.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo
                $serializableStatusEnum

                data class AccountDomain(val status: Status)

                @MapTo(AccountDomain::class)
                data class AccountRemote(val status: String)
                """.trimIndent(),
            )
        val (result, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val gen = compilation.generatedFile("AccountRemoteMappers.kt")
        assert(gen.contains("when")) { "Expected a when expression in:\n$gen" }
        assert(gen.contains("\"active\"")) { "Expected the @SerialName wire literal 'active' in:\n$gen" }
        assert(gen.contains("\"BANNED\"")) { "Expected the entry-name wire literal 'BANNED' in:\n$gen" }
        assert(gen.contains("Status.ACTIVE")) { "Expected the enum entry reference in:\n$gen" }
        assert(gen.contains("UnknownEnumValue")) { "Expected the unknown-value throw in:\n$gen" }
        assert(!gen.contains("wireValue")) { "Serializable enums must NOT read MappableEnum.wireValue:\n$gen" }
    }

    @Test
    fun `Serializable enum to wire String generates a when emitting serial names`() {
        val src =
            SourceFile.kotlin(
                "SToWire.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo
                $serializableStatusEnum

                data class AccountWire(val status: String)

                @MapTo(AccountWire::class)
                data class AccountDomain(val status: Status)
                """.trimIndent(),
            )
        val (result, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val gen = compilation.generatedFile("AccountDomainMappers.kt")
        assert(gen.contains("when")) { "Expected a when expression in:\n$gen" }
        assert(gen.contains("Status.ACTIVE")) { "Expected the enum entry in the when in:\n$gen" }
        assert(gen.contains("\"active\"")) { "Expected the @SerialName wire literal in:\n$gen" }
        assert(!gen.contains("wireValue")) { "Serializable enums must NOT read MappableEnum.wireValue:\n$gen" }
    }

    @Test
    fun `fromWire round-trips at runtime and an unknown value fails hard with the field path`() {
        val src =
            SourceFile.kotlin(
                "SRuntime.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo
                $serializableStatusEnum

                data class AccountDomain(val status: Status)

                @MapTo(AccountDomain::class)
                data class AccountRemote(val status: String)
                """.trimIndent(),
            )
        val (result, _) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        // @SerialName value decodes; bare entry name decodes too.
        val active = result.invokeResultMapper("AccountRemoteMappersKt", "toAccountDomainResult", result.newInstance("AccountRemote", "active")).getOrThrow()
        assertEquals("ACTIVE", active!!.prop("status").toString())
        val banned = result.invokeResultMapper("AccountRemoteMappersKt", "toAccountDomainResult", result.newInstance("AccountRemote", "BANNED")).getOrThrow()
        assertEquals("BANNED", banned!!.prop("status").toString())

        // Unknown wire value at a non-null target → hard UnknownEnumValue carrying the field path.
        val outcome = result.invokeResultMapper("AccountRemoteMappersKt", "toAccountDomainResult", result.newInstance("AccountRemote", "teleported"))
        assertTrue(outcome.isFailure)
        val exception = outcome.exceptionOrNull()
        assertTrue(exception is MappingException.UnknownEnumValue, "expected UnknownEnumValue, got $exception")
        assertEquals("status", (exception as MappingException.UnknownEnumValue).path)
        assertEquals("teleported", exception.value)
    }

    @Test
    fun `nullable serializable enum target absorbs an unknown wire value to null`() {
        val src =
            SourceFile.kotlin(
                "SNullable.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo
                $serializableStatusEnum

                data class AccountDomain(val status: Status?)

                @MapTo(AccountDomain::class)
                data class AccountRemote(val status: String?)
                """.trimIndent(),
            )
        val (result, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val gen = compilation.generatedFile("AccountRemoteMappers.kt")
        assert(gen.contains("convertOrNull(\"status\"")) { "Expected the null-absorbing seam in:\n$gen" }

        // Unknown value → null (absorbed); known value still maps; null passes through.
        val unknown = result.invokeResultMapper("AccountRemoteMappersKt", "toAccountDomainResult", result.newInstance("AccountRemote", "teleported")).getOrThrow()
        assertEquals(null, unknown!!.prop("status"))
        val known = result.invokeResultMapper("AccountRemoteMappersKt", "toAccountDomainResult", result.newInstance("AccountRemote", "active")).getOrThrow()
        assertEquals("ACTIVE", known!!.prop("status").toString())
    }

    @Test
    fun `MappableEnum wins when an enum has both MappableEnum and @Serializable`() {
        val src =
            SourceFile.kotlin(
                "SPrecedence.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo
                import com.sahsenvar.kmapper.MappableEnum
                import kotlinx.serialization.Serializable
                import kotlinx.serialization.SerialName

                @Serializable
                enum class Status(override val wireValue: String) : MappableEnum<String> {
                    @SerialName("ignored") ACTIVE("active"),
                    BANNED("banned")
                }

                data class AccountDomain(val status: Status)

                @MapTo(AccountDomain::class)
                data class AccountRemote(val status: String)
                """.trimIndent(),
            )
        val (result, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val gen = compilation.generatedFile("AccountRemoteMappers.kt")
        // MappableEnum path: reads wireValue, no when, and the @SerialName is ignored entirely.
        assert(gen.contains("wireValue")) { "Expected the MappableEnum wireValue path in:\n$gen" }
        assert(gen.contains("entries.firstOrNull")) { "Expected the MappableEnum entries lookup in:\n$gen" }
        assert(!gen.contains("\"ignored\"")) { "@SerialName must be ignored when MappableEnum is present:\n$gen" }
    }

    @Test
    fun `serializable enum mapped to a non-String wire fails with a clear mismatch`() {
        val src =
            SourceFile.kotlin(
                "SMismatch.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo
                $serializableStatusEnum

                data class AccountDomain(val status: Status)

                @MapTo(AccountDomain::class)
                data class AccountRemote(val status: Int)
                """.trimIndent(),
            )
        val (result, _) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assert(result.messages.contains("expected kotlin.String")) {
            "Expected a String wire-type mismatch error in:\n${result.messages}"
        }
    }

    @Test
    fun `duplicate serial names fail at compile time`() {
        val src =
            SourceFile.kotlin(
                "SDuplicate.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo
                import kotlinx.serialization.Serializable
                import kotlinx.serialization.SerialName

                @Serializable
                enum class Status {
                    @SerialName("x") ACTIVE,
                    @SerialName("x") BANNED
                }

                data class AccountDomain(val status: Status)

                @MapTo(AccountDomain::class)
                data class AccountRemote(val status: String)
                """.trimIndent(),
            )
        val (result, _) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assert(result.messages.contains("duplicate wire value", ignoreCase = true)) {
            "Expected a duplicate-wire-value error in:\n${result.messages}"
        }
    }

    @Test
    fun `a List of serializable enums rides the element seam`() {
        val src =
            SourceFile.kotlin(
                "SCollection.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo
                $serializableStatusEnum

                data class RolesDomain(val statuses: List<Status>)

                @MapTo(RolesDomain::class)
                data class RolesRemote(val statuses: List<String>)
                """.trimIndent(),
            )
        val (result, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val gen = compilation.generatedFile("RolesRemoteMappers.kt")
        assert(gen.contains("convertEach")) { "Expected an element seam in:\n$gen" }
        assert(gen.contains("when")) { "Expected the per-element when in:\n$gen" }
        assert(gen.contains("Status.ACTIVE")) { "Expected the enum entry in the element when in:\n$gen" }
    }

    @Test
    fun `enum that is neither MappableEnum nor @Serializable errors mentioning both options`() {
        val src =
            SourceFile.kotlin(
                "SPlain.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo

                enum class Plain { A, B }

                data class AccountDomain(val status: Plain)

                @MapTo(AccountDomain::class)
                data class AccountRemote(val status: String)
                """.trimIndent(),
            )
        val (result, _) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assert(result.messages.contains("MappableEnum") && result.messages.contains("@Serializable")) {
            "Expected the error to mention both MappableEnum and @Serializable in:\n${result.messages}"
        }
    }
}
