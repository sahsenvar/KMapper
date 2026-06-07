@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals

class BasicMappingTest {
    @Test
    fun `nested mapping with required-field null check`() {
        val src =
            SourceFile.kotlin(
                "Models.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo

                data class AddressDomain(val city: String)
                data class UserDomain(val id: String, val address: AddressDomain)

                @MapTo(AddressDomain::class)
                data class AddressRemote(val city: String?)

                @MapTo(UserDomain::class)
                data class UserRemote(val id: String?, val address: AddressRemote?)
                """.trimIndent(),
            )

        val (result, compilation) = compile(src)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Compilation failed:\n${result.messages}",
        )

        val gen = compilation.generatedFile("UserRemoteMappers.kt")
        assert(gen.contains("fun UserRemote.toUserDomain()")) { "Missing function:\n$gen" }
        assert(gen.contains("throw MappingException.RequiredFieldMissing(\"id\")")) {
            "Missing RequiredFieldMissing for 'id':\n$gen"
        }
        // address is nullable AddressRemote?, so the generated code uses ?. safe-call + null check
        assert(gen.contains("address = address")) { "Missing address mapping:\n$gen" }
        assert(gen.contains("toAddressDomain()")) { "Missing nested toAddressDomain() call:\n$gen" }
    }

    @Test
    fun `reverse mapping via MapFrom`() {
        val src =
            SourceFile.kotlin(
                "Rev.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapFrom

                data class TagDomain(val name: String)

                @MapFrom(TagDomain::class)
                data class TagRemote(val name: String)
                """.trimIndent(),
            )

        val (result, compilation) = compile(src)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Compilation failed:\n${result.messages}",
        )

        val gen = compilation.generatedFile("TagDomainMappers.kt")
        assert(gen.contains("fun TagDomain.toTagRemote()")) { "Missing reverse function:\n$gen" }
    }

    @Test
    fun `built-in String to Int conversion`() {
        val src =
            SourceFile.kotlin(
                "Conv.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo

                data class CountDomain(val n: Int)

                @MapTo(CountDomain::class)
                data class CountRemote(val n: String)
                """.trimIndent(),
            )

        val (result, compilation) = compile(src)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Compilation failed:\n${result.messages}",
        )

        val gen = compilation.generatedFile("CountRemoteMappers.kt")
        assert(gen.contains("StringIntConverter")) { "Missing StringIntConverter in:\n$gen" }
        assert(gen.contains("convertToNonNull(n)")) { "Missing convertToNonNull call in:\n$gen" }
    }
}
