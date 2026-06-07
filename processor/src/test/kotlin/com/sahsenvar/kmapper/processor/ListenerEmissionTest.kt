@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals

class ListenerEmissionTest {
    @Test
    fun `generated mapper emits guarded listener dispatch`() {
        val src =
            SourceFile.kotlin(
                "L.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo
                data class UserDomain(val id: String)
                @MapTo(UserDomain::class) data class UserRemote(val id: String)
                """.trimIndent(),
            )
        val (r, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
        val gen = compilation.generatedFile("UserRemoteMappers.kt")
        assert(gen.contains("KMapper.hasListeners")) { "Missing KMapper.hasListeners in:\n$gen" }
        assert(gen.contains("onMapStart")) { "Missing onMapStart in:\n$gen" }
        assert(gen.contains("onMapComplete")) { "Missing onMapComplete in:\n$gen" }
    }
}
