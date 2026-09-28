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
     * Generated code must ride the element seam without a safe call:
     * `tags.convertEachOrSkip("tags", "TagRemote", "TagDomain") { it.toTagDomain() }`.
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
        // Must ride the skip-rung element seam without a safe call, since tags is non-nullable
        assert(gen.contains("tags.convertEachOrSkip(\"tags\", \"TagRemote\", \"TagDomain\")")) {
            "Expected non-safe-call convertEachOrSkip in generated code:\n$gen"
        }
        // Must NOT use a safe-call chain for a non-null source
        assert(!gen.contains("?.convertEachOrSkip")) {
            "Generated code must NOT use ?.convertEachOrSkip for non-null List source:\n$gen"
        }
        // Must call the plain core element mapper directly (no Result boundary, no getOrThrow)
        assert(gen.contains("toTagDomain()")) {
            "Expected toTagDomain() element mapper call:\n$gen"
        }
    }

    /**
     * Nullable List<NestedSource>? → List<NestedDomain>?: null passthrough via a safe-called
     * element seam chain — `tags?.convertEachOrSkip(...) { it.toTagDomain() }`.
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
        // Nullable source must safe-call into the element seam
        assert(gen.contains("tags?.convertEachOrSkip(")) {
            "Expected tags?.convertEachOrSkip( for nullable List source:\n$gen"
        }
        assert(gen.contains("toTagDomain()")) {
            "Expected toTagDomain() element mapper call:\n$gen"
        }
    }
}
