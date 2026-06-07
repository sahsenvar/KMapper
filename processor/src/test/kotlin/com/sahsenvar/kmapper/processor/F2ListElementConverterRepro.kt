package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test

@OptIn(ExperimentalCompilerApi::class)
class F2ListElementConverterRepro {
    // F2: List<String> -> List<Int> (eleman tip donusumu uygulanmiyor)
    // Skaler String->Int builtin StringIntConverter ile CALISIRken, ayni
    // donusum liste ELEMANINA uygulanmiyor. Beklenti: KSP error YOK,
    // uretilen kod `xs = xs` ham kopya, Kotlin compile FAIL.
    @Test
    fun `F2 repro list element scalar conversion not applied`() {
        val src =
            SourceFile.kotlin(
                "F2.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo

                data class Dst(val xs: List<Int>)

                @MapTo(Dst::class)
                data class Src(val xs: List<String>)
                """.trimIndent(),
            )
        val (r, compilation) = compile(src)
        println("=== F2 EXIT CODE: ${'$'}{r.exitCode} ===")
        println("=== F2 MESSAGES ===")
        println(r.messages)
        runCatching {
            val gen = compilation.generatedFile("SrcMappers.kt")
            println("=== GENERATED SrcMappers.kt ===")
            println(gen)
        }.onFailure { println("no generated file: ${'$'}{it.message}") }
        println("=== isOK=${'$'}{r.exitCode == KotlinCompilation.ExitCode.OK} ===")
    }
}
