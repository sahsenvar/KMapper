@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals

class EnumMappingTest {
    /**
     * Wire value (String) → MappableEnum: generates entries.firstOrNull + UnknownEnumValue throw.
     */
    @Test
    fun `string-backed MappableEnum wire to enum`() {
        val src =
            SourceFile.kotlin(
                "E2.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo
                import com.sahsenvar.kmapper.MappableEnum

                enum class Status(override val wireValue: String) : MappableEnum<String> {
                    PENDING("PENDING"), SHIPPED("in_transit");
                }

                data class OrderDomain(val status: Status)

                @MapTo(OrderDomain::class)
                data class OrderRemote(val status: String)
                """.trimIndent(),
            )
        val (r, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
        val gen = compilation.generatedFile("OrderRemoteMappers.kt")
        assert(gen.contains("entries.firstOrNull")) {
            "Expected entries.firstOrNull in:\n$gen"
        }
        assert(gen.contains("UnknownEnumValue")) {
            "Expected UnknownEnumValue in:\n$gen"
        }
    }

    /**
     * Nullable wire field → nullable enum field: null passthrough, no UnknownEnumValue on null.
     */
    @Test
    fun `nullable string-backed MappableEnum wire to nullable enum`() {
        val src =
            SourceFile.kotlin(
                "E3.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo
                import com.sahsenvar.kmapper.MappableEnum

                enum class Status(override val wireValue: String) : MappableEnum<String> {
                    PENDING("PENDING"), SHIPPED("in_transit");
                }

                data class OrderDomain(val status: Status?)

                @MapTo(OrderDomain::class)
                data class OrderRemote(val status: String?)
                """.trimIndent(),
            )
        val (r, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
        val gen = compilation.generatedFile("OrderRemoteMappers.kt")
        // Nullable source: uses ?.let { ... } pattern
        assert(gen.contains("let") || gen.contains("?.")) {
            "Expected null-safe pattern in:\n$gen"
        }
        assert(gen.contains("entries.firstOrNull")) {
            "Expected entries.firstOrNull in:\n$gen"
        }
    }

    /**
     * Enum → wire value (wireValue property): generates src.wireValue expression.
     */
    @Test
    fun `MappableEnum to wire value`() {
        val src =
            SourceFile.kotlin(
                "E4.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo
                import com.sahsenvar.kmapper.MappableEnum

                enum class Status(override val wireValue: String) : MappableEnum<String> {
                    PENDING("PENDING"), SHIPPED("in_transit");
                }

                data class OrderWire(val status: String)

                @MapTo(OrderWire::class)
                data class OrderDomain(val status: Status)
                """.trimIndent(),
            )
        val (r, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
        val gen = compilation.generatedFile("OrderDomainMappers.kt")
        assert(gen.contains("wireValue")) {
            "Expected wireValue reference in:\n$gen"
        }
    }

    /**
     * Enum that does NOT implement MappableEnum and has no @UseMapTypeConverter → compilation error
     * with a message mentioning "MappableEnum".
     */
    @Test
    fun `enum without MappableEnum fails with clear error`() {
        val src =
            SourceFile.kotlin(
                "E5.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo

                enum class Plain { A, B }

                data class DDomain(val p: Plain)

                @MapTo(DDomain::class)
                data class DRemote(val p: String)
                """.trimIndent(),
            )
        val (r, _) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode)
        assert(r.messages.contains("MappableEnum", ignoreCase = true)) {
            "Expected MappableEnum in error:\n${r.messages}"
        }
    }
}
