@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Issue #37: two same-shaped enums in different packages (or just two distinct enum types)
 * previously failed to compile — the processor only recognized enum <-> wire-type (String/Int)
 * bridges, so an Enum -> Enum field fell into the wire-type check and errored with
 * "enum wire type mismatch: expected kotlin.String". KMapper now maps Enum -> Enum by matching
 * constant names (parity with the String<->Enum bridges), generating a compile-time `when`;
 * a source constant with no same-named target constant is a guided compile error instead.
 */
@OptIn(ExperimentalCompilerApi::class)
class EnumToEnumMappingTest {
    @Test
    fun `same-named enums map by constant name via a compile-time when`() {
        val src =
            SourceFile.kotlin(
                "E2E1.kt",
                """
                package a
                enum class DwFundType { CASH_RESERVE, FUND }
                """.trimIndent(),
            )
        val src2 =
            SourceFile.kotlin(
                "E2E2.kt",
                """
                package b
                enum class DwFundType { CASH_RESERVE, FUND }
                """.trimIndent(),
            )
        val modelSource =
            SourceFile.kotlin(
                "E2E3.kt",
                """
                package c

                import com.sahsenvar.kmapper.annotations.MapTo
                import a.DwFundType as SourceFundType
                import b.DwFundType as TargetFundType

                data class TargetModel(val type: TargetFundType)

                @MapTo(TargetModel::class)
                data class SourceRemote(val type: SourceFundType)
                """.trimIndent(),
            )
        val (result, compilation) = compile(src, src2, modelSource)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val gen = compilation.generatedFile("SourceRemoteMappers.kt")
        assert(gen.contains("when")) { "Expected a when expression in:\n$gen" }
        // KotlinPoet auto-aliases the colliding `DwFundType` imports (one per package) — each
        // when-branch still matches source entry to target entry by name.
        assert(gen.contains("import a.DwFundType as ADwFundType")) { "Expected an aliased import for a.DwFundType in:\n$gen" }
        assert(gen.contains("import b.DwFundType as BDwFundType")) { "Expected an aliased import for b.DwFundType in:\n$gen" }
        assert(gen.contains("ADwFundType.CASH_RESERVE -> BDwFundType.CASH_RESERVE")) {
            "Expected the matched CASH_RESERVE constant in:\n$gen"
        }
        assert(gen.contains("ADwFundType.FUND -> BDwFundType.FUND")) {
            "Expected the matched FUND constant in:\n$gen"
        }
    }

    @Test
    fun `enum to enum mapping round-trips at runtime`() {
        val src =
            SourceFile.kotlin(
                "E2E4.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo

                enum class SourceStatus { PENDING, SHIPPED }
                enum class TargetStatus { PENDING, SHIPPED }

                data class OrderDomain(val status: TargetStatus)

                @MapTo(OrderDomain::class)
                data class OrderRemote(val status: SourceStatus)
                """.trimIndent(),
            )
        val (result, _) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val mapped =
            result.invokeResultMapper(
                "OrderRemoteMappersKt",
                "toOrderDomainResult",
                result.newInstance("OrderRemote", result.enumValue("SourceStatus", "SHIPPED")),
            ).getOrThrow()
        assertEquals("SHIPPED", mapped!!.prop("status").toString())
    }

    @Test
    fun `nullable source enum maps null through and a value via the when`() {
        val src =
            SourceFile.kotlin(
                "E2E5.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo

                enum class SourceStatus { PENDING, SHIPPED }
                enum class TargetStatus { PENDING, SHIPPED }

                data class OrderDomain(val status: TargetStatus?)

                @MapTo(OrderDomain::class)
                data class OrderRemote(val status: SourceStatus?)
                """.trimIndent(),
            )
        val (result, _) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val nullResult =
            result.invokeResultMapper(
                "OrderRemoteMappersKt",
                "toOrderDomainResult",
                result.newInstance("OrderRemote", null),
            ).getOrThrow()
        assertEquals(null, nullResult!!.prop("status"))

        val valueResult =
            result.invokeResultMapper(
                "OrderRemoteMappersKt",
                "toOrderDomainResult",
                result.newInstance("OrderRemote", result.enumValue("SourceStatus", "PENDING")),
            ).getOrThrow()
        assertEquals("PENDING", valueResult!!.prop("status").toString())
    }

    @Test
    fun `a List of enums maps element-by-element via the when`() {
        val src =
            SourceFile.kotlin(
                "E2E6.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo

                enum class SourceStatus { PENDING, SHIPPED }
                enum class TargetStatus { PENDING, SHIPPED }

                data class OrdersDomain(val statuses: List<TargetStatus>)

                @MapTo(OrdersDomain::class)
                data class OrdersRemote(val statuses: List<SourceStatus>)
                """.trimIndent(),
            )
        val (result, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val gen = compilation.generatedFile("OrdersRemoteMappers.kt")
        assert(gen.contains("convertEach")) { "Expected an element seam in:\n$gen" }
        assert(gen.contains("when")) { "Expected the per-element when in:\n$gen" }
    }

    @Test
    fun `a source constant with no matching target constant fails at compile time`() {
        val src =
            SourceFile.kotlin(
                "E2E7.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo

                enum class SourceStatus { PENDING, SHIPPED, CANCELLED }
                enum class TargetStatus { PENDING, SHIPPED }

                data class OrderDomain(val status: TargetStatus)

                @MapTo(OrderDomain::class)
                data class OrderRemote(val status: SourceStatus)
                """.trimIndent(),
            )
        val (result, _) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assert(result.messages.contains("CANCELLED")) {
            "Expected the unmatched constant name CANCELLED in the error:\n${result.messages}"
        }
        assert(result.messages.contains("SourceStatus") && result.messages.contains("TargetStatus")) {
            "Expected both enum names in the error:\n${result.messages}"
        }
    }
}
