package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for Set<T> → Set<T> collection mapping: a Set target must select the Set-producing
 * element seams (convertEachOrSkipToSet) so the produced value matches the Set<T> target
 * type; List targets must keep the List-shaped seam.
 */
@OptIn(ExperimentalCompilerApi::class)
class SetCollectionMappingTest {
    /**
     * Non-null Set<TagRemote> → Set<TagDomain> where TagRemote is @MapTo(TagDomain::class).
     * Generated code must:
     *   - compile (exit code OK)
     *   - ride the Set seam: `tags.convertEachOrSkipToSet(...) { it.toTagDomainResult().getOrThrow() }`
     *   - NOT use a safe-call chain (non-null source)
     */
    @Test
    fun `non-null Set of mapped elements emits map toSet`() {
        val src =
            SourceFile.kotlin(
                "SetNested.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo

                data class TagDomain(val name: String)
                data class ProductDomain(val tags: Set<TagDomain>)

                @MapTo(TagDomain::class)
                data class TagRemote(val name: String)

                @MapTo(ProductDomain::class)
                data class ProductRemote(val tags: Set<TagRemote>)
                """.trimIndent(),
            )
        val (r, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
        val gen = compilation.generatedFile("ProductRemoteMappers.kt")

        // Must call the element mapper
        assert(gen.contains("toTagDomainResult().getOrThrow()")) {
            "Expected toTagDomainResult().getOrThrow() element mapper call:\n$gen"
        }
        // Must select the Set-producing seam so the result type is Set, not List
        assert(gen.contains("tags.convertEachOrSkipToSet(\"tags\", \"TagRemote\", \"TagDomain\")")) {
            "Expected convertEachOrSkipToSet for Set target in generated code:\n$gen"
        }
        // Must NOT use a safe-call chain for non-null source
        assert(!gen.contains("?.convertEachOrSkipToSet")) {
            "Generated code must NOT use ?.convertEachOrSkipToSet for non-null Set source:\n$gen"
        }
    }

    /**
     * Nullable Set<TagRemote>? → Set<TagDomain>?: null-safe form.
     * Generated code must use `tags?.convertEachOrSkipToSet(...) { ... }`.
     */
    @Test
    fun `nullable Set of mapped elements emits safe-call map toSet`() {
        val src =
            SourceFile.kotlin(
                "SetNestedNullable.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo

                data class TagDomain(val name: String)
                data class ProductDomain(val tags: Set<TagDomain>?)

                @MapTo(TagDomain::class)
                data class TagRemote(val name: String)

                @MapTo(ProductDomain::class)
                data class ProductRemote(val tags: Set<TagRemote>?)
                """.trimIndent(),
            )
        val (r, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
        val gen = compilation.generatedFile("ProductRemoteMappers.kt")

        // Must call the element mapper
        assert(gen.contains("toTagDomainResult().getOrThrow()")) {
            "Expected toTagDomainResult().getOrThrow() element mapper call:\n$gen"
        }
        // Nullable source must safe-call into the Set seam
        assert(gen.contains("tags?.convertEachOrSkipToSet(")) {
            "Expected tags?.convertEachOrSkipToSet( for nullable Set source:\n$gen"
        }
    }

    /**
     * Guard: List<TagRemote> → List<TagDomain> must keep the List-shaped seam.
     * Confirms the Set seams only apply to Set targets.
     */
    @Test
    fun `List target does NOT emit toSet`() {
        val src =
            SourceFile.kotlin(
                "ListNestedGuard.kt",
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

        // Must call the element mapper
        assert(gen.contains("toTagDomainResult().getOrThrow()")) {
            "Expected toTagDomainResult().getOrThrow() element mapper call:\n$gen"
        }
        // Must keep the List-shaped seam for a List target
        assert(gen.contains("convertEachOrSkip(") && !gen.contains("convertEachOrSkipToSet")) {
            "List target must use convertEachOrSkip, not the Set seam:\n$gen"
        }
    }
}
