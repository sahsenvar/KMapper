@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A `@MapTo`/`@MapFrom` target or source that is a NESTED class (e.g. `OptionAssetModel.DailyBar`)
 * must be referenced in the generated mapper by its fully-enclosed name — not just its innermost
 * simple name. Building the `ClassName` from package + innermost simple name only (the pre-fix bug,
 * issue #18) emitted `import <pkg>.DailyBar` for a non-existent top-level class and a bare
 * `Result<DailyBar>`, so the generated file failed to compile with `unresolved reference`.
 *
 * The kctfork harness compiles the generated source as part of the same compilation, so the
 * regression surfaces directly as a COMPILATION_ERROR — a passing (OK) compile is the proof, and
 * the golden assertions pin the enclosing-qualified reference so a one-level-only fix can't sneak by.
 */
@OptIn(ExperimentalCompilerApi::class)
class NestedClassMappingTest {
    @Test
    fun `nested target class is referenced by its enclosing-qualified name`() {
        val src =
            SourceFile.kotlin(
                "NestedTarget.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo

                data class OptionAssetModel(val id: String = "") {
                    data class DailyBar(val time: String = "", val close: Double = 0.0)
                }

                @MapTo(OptionAssetModel.DailyBar::class)
                data class DailyBarRemote(val time: String, val close: Double)
                """.trimIndent(),
            )
        val (result, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val gen = compilation.generatedFile("DailyBarRemoteMappers.kt")
        assert(gen.contains("OptionAssetModel.DailyBar")) {
            "Expected the nested target to be referenced as OptionAssetModel.DailyBar in:\n$gen"
        }
    }

    @Test
    fun `nested target maps to a real enclosing instance at runtime`() {
        val src =
            SourceFile.kotlin(
                "NestedTargetRuntime.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo

                data class OptionAssetModel(val id: String = "") {
                    data class DailyBar(val time: String = "", val close: Double = 0.0)
                }

                @MapTo(OptionAssetModel.DailyBar::class)
                data class DailyBarRemote(val time: String, val close: Double)
                """.trimIndent(),
            )
        val (result, _) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val bar =
            result.invokeResultMapper(
                "DailyBarRemoteMappersKt",
                "toDailyBarResult",
                result.newInstance("DailyBarRemote", "2026-01-01", 1.5),
            ).getOrThrow()
        assertEquals("2026-01-01", bar!!.prop("time"))
        assertEquals(1.5, bar.prop("close"))
    }

    @Test
    fun `deeply nested target resolves every enclosing name`() {
        val src =
            SourceFile.kotlin(
                "DeeplyNested.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo

                data class YoutubeModel(val id: String = "") {
                    data class Item(val kind: String = "") {
                        data class Snippet(val title: String = "")
                    }
                }

                @MapTo(YoutubeModel.Item.Snippet::class)
                data class SnippetRemote(val title: String)
                """.trimIndent(),
            )
        val (result, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val gen = compilation.generatedFile("SnippetRemoteMappers.kt")
        assert(gen.contains("YoutubeModel.Item.Snippet")) {
            "Expected the 2-level-nested target to be referenced as YoutubeModel.Item.Snippet in:\n$gen"
        }
    }

    @Test
    fun `nested source via MapFrom resolves the enclosing-qualified receiver`() {
        val src =
            SourceFile.kotlin(
                "NestedSource.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapFrom

                data class OptionAssetModel(val id: String = "") {
                    data class DailyBar(val time: String = "", val close: Double = 0.0)
                }

                @MapFrom(OptionAssetModel.DailyBar::class)
                data class DailyBarDomain(val time: String, val close: Double)
                """.trimIndent(),
            )
        // Pre-fix, the generated extension receiver `fun DailyBar.…` imports a non-existent
        // top-level `DailyBar` → COMPILATION_ERROR. A clean OK compile is the proof for @MapFrom.
        val (result, _) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }
}
