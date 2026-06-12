package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCompilerApi::class)
class CollectionNestedMappingTest {
    /**
     * Non-null List<NestedSource> → List<NestedDomain> where NestedSource is itself @MapTo-mapped.
     * Generated code must use `.map { it.toTagDomainResult().getOrThrow() }` (no safe-call `?.map`).
     */
    @Test
    fun `non-null list of mapped elements uses map not safe-call map`() {
        val src =
            SourceFile.kotlin(
                "CollNested.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo

                data class TagDomain(val name: String)
                data class ProductDomain(val tags: List<TagDomain>)

                @MapTo(TagDomain::class)
                data class TagRemote(val name: String)

                @MapTo(ProductDomain::class)
                data class ProductRemote(val tags: List<TagRemote>)
                """.trimIndent(),
            )
        val (r, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
        val gen = compilation.generatedFile("ProductRemoteMappers.kt")
        // Must use non-safe map (no ?.map), since tags is non-nullable
        assert(gen.contains(".map·{") || gen.contains(".map {")) {
            "Expected .map { (non-safe-call) in generated code:\n$gen"
        }
        // Must NOT use safe-call map for non-null source
        assert(!gen.contains("?.map")) {
            "Generated code must NOT use ?.map for non-null List source:\n$gen"
        }
        // Must call the element mapper
        assert(gen.contains("toTagDomainResult().getOrThrow()")) {
            "Expected toTagDomainResult().getOrThrow() element mapper call:\n$gen"
        }
    }

    /**
     * Nullable List<NestedSource>? → List<NestedDomain>?: null passthrough via ?.map.
     * Generated code must use `?.map { it.toTagDomainResult().getOrThrow() }`.
     */
    @Test
    fun `nullable list of mapped elements uses safe-call map`() {
        val src =
            SourceFile.kotlin(
                "CollNestedNullable.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo

                data class TagDomain(val name: String)
                data class ProductDomain(val tags: List<TagDomain>?)

                @MapTo(TagDomain::class)
                data class TagRemote(val name: String)

                @MapTo(ProductDomain::class)
                data class ProductRemote(val tags: List<TagRemote>?)
                """.trimIndent(),
            )
        val (r, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
        val gen = compilation.generatedFile("ProductRemoteMappers.kt")
        // Nullable source must use ?.map
        assert(gen.contains("?.map")) {
            "Expected ?.map for nullable List source:\n$gen"
        }
        assert(gen.contains("toTagDomainResult().getOrThrow()")) {
            "Expected toTagDomainResult().getOrThrow() element mapper call:\n$gen"
        }
    }
}
