@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversionWrapTest {

    @Test
    fun `built-in conversion is wrapped in convertOrFail`() {
        val src = SourceFile.kotlin(
            "M.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            data class CountDomain(val n: Int)
            @MapTo(CountDomain::class) data class CountRemote(val n: String)
        """.trimIndent()
        )
        val (r, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
        val gen = compilation.generatedFile("CountRemoteMappers.kt")
        assert(gen.contains("convertOrFail(")) { "Missing convertOrFail in:\n$gen" }
        assert(gen.contains("StringIntConverter.convertToNonNull")) {
            "Missing StringIntConverter.convertToNonNull in:\n$gen"
        }
    }
}
