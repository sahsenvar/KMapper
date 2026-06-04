package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guard test for FIX 1: the dead "ImmutableList conversion for UiModel" branch was deleted.
 *
 * Verifies that:
 *  1. A stdlib List<String> → List<String> mapping compiles successfully.
 *  2. The generated code uses `.map { }` for element-level copying (nested model path) and
 *     does NOT contain `toImmutableList` or `toImmutableSet` — those symbols were never
 *     imported and would cause a compile error if emitted.
 */
@OptIn(ExperimentalCompilerApi::class)
class StdlibCollectionNonImmutableTest {

    /**
     * stdlib List<NestedModel> → List<NestedDomain>: must use `.map { it.toTagDomain() }`
     * and must NOT emit `toImmutableList` / `toImmutableSet`.
     */
    @Test
    fun `stdlib List of nested model generates map and no toImmutableList`() {
        val src = SourceFile.kotlin(
            "StdlibList.kt",
            """
            import com.sahsenvar.kmapper.annotations.MapTo

            data class TagDomain(val name: String)

            @MapTo(TagDomain::class)
            data class TagRemote(val name: String)

            data class ContainerDomain(val tags: List<TagDomain>)

            @MapTo(ContainerDomain::class)
            data class ContainerRemote(val tags: List<TagRemote>)
            """.trimIndent()
        )
        val (r, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
        val gen = compilation.generatedFile("ContainerRemoteMappers.kt")

        // Must use .map { } for element-level conversion
        assert(gen.contains(".map·{") || gen.contains(".map {")) {
            "Expected .map { in generated code:\n$gen"
        }

        // Must NOT contain the deleted dead-branch calls
        assert(!gen.contains("toImmutableList")) {
            "Generated code must NOT contain toImmutableList (dead branch was deleted):\n$gen"
        }
        assert(!gen.contains("toImmutableSet")) {
            "Generated code must NOT contain toImmutableSet (dead branch was deleted):\n$gen"
        }
    }

    /**
     * stdlib List<String> → List<String> direct (no nested mapping): must compile and
     * must NOT emit toImmutableList.
     */
    @Test
    fun `stdlib List of String generates direct assign and no toImmutableList`() {
        val src = SourceFile.kotlin(
            "StdlibStringList.kt",
            """
            import com.sahsenvar.kmapper.annotations.MapTo

            data class StringsDomain(val items: List<String>)

            @MapTo(StringsDomain::class)
            data class StringsRemote(val items: List<String>)
            """.trimIndent()
        )
        val (r, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
        val gen = compilation.generatedFile("StringsRemoteMappers.kt")

        assert(!gen.contains("toImmutableList")) {
            "Generated code must NOT contain toImmutableList:\n$gen"
        }
        assert(!gen.contains("toImmutableSet")) {
            "Generated code must NOT contain toImmutableSet:\n$gen"
        }
    }
}
